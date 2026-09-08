package com.victorfalcon.dose.ui.dose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorfalcon.dose.data.SettingsRepository
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.DoseView
import com.victorfalcon.dose.domain.repository.MedicationRepository
import com.victorfalcon.dose.reminder.AlarmScheduler
import com.victorfalcon.dose.reminder.NotificationHelper
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

/** Still waiting for an answer at [now]: a snooze moves when a dose is owed, it doesn't resolve it. */
fun DoseView.isOwed(now: LocalDateTime): Boolean =
    status == DoseStatus.PENDING && !(snoozedUntil ?: scheduledAt).isAfter(now)

/**
 * What the user owes right now, one dose at a time. Opened from a notification or from a dose in
 * the widget, so it always lands on a specific dose — and then walks through everything else
 * that is already due instead of dropping the user back on the home screen.
 *
 * The queue is *not* "this hour": a creatine owed since 11:00 and an Adiro due at 13:00 are one
 * queue at 13:00, and just the creatine at 12:00.
 */
fun doseQueue(doses: List<DoseView>, openedWith: Long, now: LocalDateTime): List<Long> {
    val owed = doses
        .filter { it.isOwed(now) }
        .sortedBy { it.scheduledAt }
        .map { it.occurrenceId }
    // The dose we were opened with is the one the user just tapped, so it answers first.
    return owed.filter { it == openedWith } + owed.filter { it != openedWith }
}

data class DoseFocusUiState(
    val time: LocalTime? = null,
    /** The dose being asked for right now. `null` with a [queue] means the queue is done. */
    val current: DoseView? = null,
    /** The queue snapshotted when the screen opened — one dot each, stable while it's open. */
    val queue: List<DoseView> = emptyList(),
    /** Where [current] sits in [queue]; past the end once everything is answered. */
    val currentIndex: Int = 0,
    /** Queued doses still waiting for an answer, [current] first — what "take all" acts on. */
    val remaining: List<DoseView> = emptyList(),
    /** The medication's own instructions — what you want to read *while* taking it. */
    val notes: String? = null,
    val dayTaken: Int = 0,
    val dayTotal: Int = 0,
    /** Nothing pending left today, so "nothing else until tomorrow" is actually true. */
    val dayFinished: Boolean = false,
    val snoozeMinutes: Int = 10,
    val loading: Boolean = true,
) {
    val total: Int get() = queue.size
    val position: Int get() = (currentIndex + 1).coerceAtMost(total)
    val isQueue: Boolean get() = total > 1
    val finished: Boolean get() = !loading && current == null
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DoseFocusViewModel @Inject constructor(
    private val repository: MedicationRepository,
    private val settings: SettingsRepository,
    private val scheduler: AlarmScheduler,
    private val notifier: NotificationHelper,
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
            val dayDoses = repository.observeDosesBetween(
                day.atStartOfDay(),
                day.plusDays(1).atStartOfDay(),
                activeOnly = true,
            )
            // Fixed for as long as the screen stays open, so answering a dose doesn't shrink the
            // dots under the user's finger. Coming back after the screen has been away re-reads
            // what is owed by then.
            val queueIds = doseQueue(dayDoses.first(), openedWith = id, now = LocalDateTime.now())
            // Notes belong to the medication, not the dose, so they're read once per medication.
            val notesByMedication = mutableMapOf<Long, String?>()
            emitAll(
                combine(dayDoses, settings.settings) { doses, prefs ->
                    val byId = doses.associateBy { it.occurrenceId }
                    val queue = queueIds.mapNotNull { byId[it] }
                    // A snoozed dose keeps its dot but drops out of the walk — wherever it was
                    // snoozed from — and comes back once the snooze runs out.
                    val remaining = queue.filter { it.isOwed(LocalDateTime.now()) }
                    val current = remaining.firstOrNull()
                    DoseFocusUiState(
                        // Follows the dose being asked for, which may be an earlier hour.
                        time = (current?.scheduledAt ?: occurrence.scheduledAt).toLocalTime(),
                        current = current,
                        queue = queue,
                        currentIndex = current?.let { queue.indexOf(it) } ?: queue.size,
                        remaining = remaining,
                        notes = current?.let { dose ->
                            notesByMedication.getOrPut(dose.medicationId) {
                                repository.getMedication(dose.medicationId)?.notes
                            }
                        }?.takeIf { it.isNotBlank() },
                        dayTaken = doses.count { it.status == DoseStatus.TAKEN },
                        dayTotal = doses.size,
                        dayFinished = doses.none { it.status == DoseStatus.PENDING },
                        snoozeMinutes = prefs.defaultSnoozeMinutes,
                        loading = false,
                    )
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DoseFocusUiState())

    fun bind(id: Long) { occurrenceId.value = id }

    fun take(dose: DoseView) = resolve(dose, DoseStatus.TAKEN, LocalDateTime.now())

    fun skip(dose: DoseView) = resolve(dose, DoseStatus.SKIPPED, null)

    /** The bulk action the widget and the grouped notification promise. */
    fun takeAll() {
        val doses = uiState.value.remaining
        viewModelScope.launch {
            val now = LocalDateTime.now()
            doses.forEach { repository.setOccurrenceStatus(it.occurrenceId, DoseStatus.TAKEN, now) }
            // Cleanup comes after the whole batch, so the hour's group isn't re-posted for a
            // sibling that this same pass has already taken.
            doses.forEach { clearReminder(it) }
        }
    }

    fun snooze(dose: DoseView) {
        viewModelScope.launch {
            val minutes = settings.settings.first().defaultSnoozeMinutes
            scheduler.snooze(dose.occurrenceId, LocalDateTime.now().plusMinutes(minutes.toLong()))
            clearNotification(dose)
        }
    }

    private fun resolve(dose: DoseView, status: DoseStatus, takenAt: LocalDateTime?) {
        viewModelScope.launch {
            repository.setOccurrenceStatus(dose.occurrenceId, status, takenAt)
            clearReminder(dose)
        }
    }

    /** Answering a dose here has to leave as little behind as `ReminderReceiver.resolve` does. */
    private suspend fun clearReminder(dose: DoseView) {
        scheduler.cancel(dose.occurrenceId)
        clearNotification(dose)
    }

    private suspend fun clearNotification(dose: DoseView) {
        notifier.cancel(dose.occurrenceId)
        notifier.refreshGroup(dose.scheduledAt)
    }
}
