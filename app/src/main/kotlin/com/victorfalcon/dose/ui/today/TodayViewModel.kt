package com.victorfalcon.dose.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.DoseView
import com.victorfalcon.dose.domain.model.Medication
import com.victorfalcon.dose.domain.repository.MedicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

data class DoseGroup(val time: LocalTime, val doses: List<DoseView>)

data class TodayUiState(
    val groups: List<DoseGroup> = emptyList(),
    val asNeeded: List<Medication> = emptyList(),
    val loading: Boolean = false,
) {
    val isEmpty: Boolean get() = !loading && groups.isEmpty() && asNeeded.isEmpty()
}

/** Groups doses by their time of day, chronologically. Pure -> unit-testable. */
fun groupByTime(doses: List<DoseView>): List<DoseGroup> =
    doses.groupBy { it.scheduledAt.toLocalTime() }
        .toSortedMap()
        .map { (time, list) -> DoseGroup(time, list) }

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: MedicationRepository,
) : ViewModel() {

    // ponytail: window fixed at VM creation; left open past midnight it won't roll over
    // until recreated. Fine for v1 (the reminder engine, not this screen, is the clock).
    private val today: LocalDate = LocalDate.now()

    val uiState: StateFlow<TodayUiState> = combine(
        repository.observeDosesBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay(), activeOnly = true),
        repository.observeAsNeededMedications(),
    ) { doses, asNeeded ->
        TodayUiState(groups = groupByTime(doses), asNeeded = asNeeded, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState(loading = true))

    fun markTaken(occurrenceId: Long) = setStatus(occurrenceId, DoseStatus.TAKEN, LocalDateTime.now())
    fun skip(occurrenceId: Long) = setStatus(occurrenceId, DoseStatus.SKIPPED, null)
    fun undo(occurrenceId: Long) = setStatus(occurrenceId, DoseStatus.PENDING, null)

    private fun setStatus(id: Long, status: DoseStatus, takenAt: LocalDateTime?) {
        viewModelScope.launch { repository.setOccurrenceStatus(id, status, takenAt) }
    }

    fun logNow(medicationId: Long) {
        viewModelScope.launch { repository.logAsNeededDose(medicationId, LocalDateTime.now()) }
    }
}
