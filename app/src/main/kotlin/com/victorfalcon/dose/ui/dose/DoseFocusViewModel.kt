package com.victorfalcon.dose.ui.dose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorfalcon.dose.data.SettingsRepository
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.DoseView
import com.victorfalcon.dose.domain.repository.MedicationRepository
import com.victorfalcon.dose.reminder.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

/**
 * One hour's worth of doses, one at a time. Opened from a notification or from a dose in the
 * widget, so it always lands on a specific dose — and when that hour has several doses it walks
 * through them instead of dropping the user into a list.
 */
data class DoseFocusUiState(
    val time: LocalTime? = null,
    /** The dose being asked for right now. `null` with [total] > 0 means the hour is finished. */
    val current: DoseView? = null,
    /** Doses still pending at this hour, current one included. */
    val remaining: List<DoseView> = emptyList(),
    /** How many doses this hour has in total, and how many are already resolved. */
    val total: Int = 0,
    val resolved: Int = 0,
    /** The medication's own instructions — what you want to read *while* taking it. */
    val notes: String? = null,
    /** The whole day, for the progress dots. */
    val dayTaken: Int = 0,
    val dayTotal: Int = 0,
    val snoozeMinutes: Int = 10,
    val loading: Boolean = true,
) {
    val position: Int get() = (resolved + 1).coerceAtMost(total)
    val isQueue: Boolean get() = total > 1
    val finished: Boolean get() = !loading && current == null
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DoseFocusViewModel @Inject constructor(
    private val repository: MedicationRepository,
    private val settings: SettingsRepository,
    private val scheduler: AlarmScheduler,
) : ViewModel() {

    private val occurrenceId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<DoseFocusUiState> = occurrenceId.filterNotNull().flatMapLatest { id ->
        flow {
            val occurrence = repository.getOccurrence(id)
            if (occurrence == null) {
                emit(DoseFocusUiState(loading = false))
                return@flow
            }
            val day = occurrence.scheduledAt.toLocalDate()
            // Notes belong to the medication, not the dose, so they're read once per hour.
            val notesByMedication = mutableMapOf<Long, String?>()
            emitAll(
                combine(
                    repository.observeDosesBetween(
                        day.atStartOfDay(),
                        day.plusDays(1).atStartOfDay(),
                        activeOnly = true,
                    ),
                    settings.settings,
                ) { doses, prefs ->
                    val siblings = doses
                        .filter { it.scheduledAt == occurrence.scheduledAt }
                        .sortedBy { it.occurrenceId }
                    val pending = siblings.filter { it.status == DoseStatus.PENDING }
                    val current = pending.firstOrNull { it.occurrenceId == id } ?: pending.firstOrNull()
                    DoseFocusUiState(
                        time = occurrence.scheduledAt.toLocalTime(),
                        // Stay on the dose we were opened with until it's resolved, then walk on.
                        current = current,
                        notes = current?.let { dose ->
                            notesByMedication.getOrPut(dose.medicationId) {
                                repository.getMedication(dose.medicationId)?.notes
                            }
                        }?.takeIf { it.isNotBlank() },
                        remaining = pending,
                        total = siblings.size,
                        resolved = siblings.size - pending.size,
                        dayTaken = doses.count { it.status == DoseStatus.TAKEN },
                        dayTotal = doses.size,
                        snoozeMinutes = prefs.defaultSnoozeMinutes,
                        loading = false,
                    )
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DoseFocusUiState())

    fun bind(id: Long) { occurrenceId.value = id }

    fun take(occurrenceId: Long) = setStatus(occurrenceId, DoseStatus.TAKEN, LocalDateTime.now())

    fun skip(occurrenceId: Long) = setStatus(occurrenceId, DoseStatus.SKIPPED, null)

    /** The bulk action the widget and the grouped notification promise. */
    fun takeAll() {
        val ids = uiState.value.remaining.map { it.occurrenceId }
        viewModelScope.launch {
            val now = LocalDateTime.now()
            ids.forEach { repository.setOccurrenceStatus(it, DoseStatus.TAKEN, now) }
        }
    }

    fun snooze(occurrenceId: Long) {
        viewModelScope.launch {
            val minutes = settings.settings.first().defaultSnoozeMinutes
            scheduler.schedule(occurrenceId, LocalDateTime.now().plusMinutes(minutes.toLong()))
        }
    }

    private fun setStatus(id: Long, status: DoseStatus, takenAt: LocalDateTime?) {
        viewModelScope.launch { repository.setOccurrenceStatus(id, status, takenAt) }
    }
}
