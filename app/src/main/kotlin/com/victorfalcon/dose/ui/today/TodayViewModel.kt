package com.victorfalcon.dose.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.DoseView
import com.victorfalcon.dose.domain.model.Medication
import com.victorfalcon.dose.data.SettingsRepository
import com.victorfalcon.dose.domain.repository.MedicationRepository
import com.victorfalcon.dose.reminder.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

data class DoseGroup(val time: LocalTime, val doses: List<DoseView>)

data class TodayUiState(
    /** The dose due right now — the one the hero card asks for. */
    val hero: DoseView? = null,
    /** Everything else today, grouped by scheduled time (a time can hold several doses). */
    val rest: List<DoseGroup> = emptyList(),
    val asNeeded: List<Medication> = emptyList(),
    val takenCount: Int = 0,
    val totalCount: Int = 0,
    val snoozeMinutes: Int = 10,
    val loading: Boolean = false,
) {
    val isEmpty: Boolean get() = !loading && totalCount == 0 && asNeeded.isEmpty()
    val allDone: Boolean get() = totalCount > 0 && hero == null
    val progress: Float get() = if (totalCount == 0) 0f else takenCount.toFloat() / totalCount
}

/** Groups doses by their time of day, chronologically. Pure -> unit-testable. */
fun groupByTime(doses: List<DoseView>): List<DoseGroup> =
    doses.groupBy { it.scheduledAt.toLocalTime() }
        .toSortedMap()
        .map { (time, list) -> DoseGroup(time, list) }

/**
 * Splits the day into the dose to take now (the first still-pending one) and the rest, which
 * keeps every other dose — earlier ones already resolved included — grouped by hour. Pure.
 */
fun todayState(doses: List<DoseView>, asNeeded: List<Medication>, snoozeMinutes: Int = 10): TodayUiState {
    val hero = doses.filter { it.status == DoseStatus.PENDING }.minByOrNull { it.scheduledAt }
    return TodayUiState(
        hero = hero,
        rest = groupByTime(doses.filter { it.occurrenceId != hero?.occurrenceId }),
        asNeeded = asNeeded,
        takenCount = doses.count { it.status == DoseStatus.TAKEN },
        totalCount = doses.size,
        snoozeMinutes = snoozeMinutes,
        loading = false,
    )
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: MedicationRepository,
    private val settings: SettingsRepository,
    private val scheduler: AlarmScheduler,
) : ViewModel() {

    // ponytail: window fixed at VM creation; left open past midnight it won't roll over
    // until recreated. Fine for v1 (the reminder engine, not this screen, is the clock).
    private val today: LocalDate = LocalDate.now()

    val uiState: StateFlow<TodayUiState> = combine(
        repository.observeDosesBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay(), activeOnly = true),
        repository.observeAsNeededMedications(),
        settings.settings,
    ) { doses, asNeeded, settings ->
        todayState(doses, asNeeded, settings.defaultSnoozeMinutes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState(loading = true))

    fun markTaken(occurrenceId: Long) = setStatus(occurrenceId, DoseStatus.TAKEN, LocalDateTime.now())
    fun skip(occurrenceId: Long) = setStatus(occurrenceId, DoseStatus.SKIPPED, null)
    fun undo(occurrenceId: Long) = setStatus(occurrenceId, DoseStatus.PENDING, null)

    private fun setStatus(id: Long, status: DoseStatus, takenAt: LocalDateTime?) {
        viewModelScope.launch { repository.setOccurrenceStatus(id, status, takenAt) }
    }

    /** Push the dose's next reminder back by the user's default snooze. */
    fun snooze(occurrenceId: Long) {
        viewModelScope.launch {
            val minutes = settings.settings.first().defaultSnoozeMinutes
            scheduler.schedule(occurrenceId, LocalDateTime.now().plusMinutes(minutes.toLong()))
        }
    }

    fun logNow(medicationId: Long) {
        viewModelScope.launch { repository.logAsNeededDose(medicationId, LocalDateTime.now()) }
    }
}
