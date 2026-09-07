package com.victorfalcon.dose.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.victorfalcon.dose.data.SettingsRepository
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.repository.MedicationRepository
import com.victorfalcon.dose.widget.WidgetRefresher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.victorfalcon.dose.domain.model.DoseOccurrence
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

/** Handles alarm fires, notification actions, and reboot — all reminder broadcasts. */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: MedicationRepository
    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var notifier: NotificationHelper
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var widgetRefresher: WidgetRefresher

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val action = intent.action
        val id = intent.getLongExtra(Reminders.EXTRA_OCCURRENCE_ID, -1L)
        scope.launch {
            try {
                when (action) {
                    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                        scheduler.syncUpcoming()
                        scheduler.scheduleDailyRollover()
                        widgetRefresher.refresh()
                    }
                    Reminders.ACTION_FIRE -> handleFire(id)
                    Reminders.ACTION_TAKEN -> resolve(id, DoseStatus.TAKEN, LocalDateTime.now())
                    Reminders.ACTION_SKIP -> resolve(id, DoseStatus.SKIPPED, null)
                    Reminders.ACTION_SNOOZE -> snooze(id)
                    Reminders.ACTION_TAKE_ALL -> takeAll(intent.hourMinutes())
                    Reminders.ACTION_SNOOZE_ALL -> snoozeAll(intent.hourMinutes())
                    Reminders.ACTION_DATE_ROLL -> {
                        widgetRefresher.refresh()
                        scheduler.scheduleDailyRollover() // re-arm for the next night
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleFire(id: Long) {
        val occurrence = repository.getOccurrence(id) ?: return
        when (val step = ReminderPolicy.step(occurrence.scheduledAt, LocalDateTime.now(), occurrence.status)) {
            ReminderStep.Done -> notifier.cancel(id)
            ReminderStep.Miss -> {
                repository.setOccurrenceStatus(id, DoseStatus.MISSED, null)
                notifier.cancel(id)
            }
            is ReminderStep.Notify -> {
                notifyFor(occurrence)
                step.nextAt?.let { scheduler.schedule(id, it) }
            }
        }
    }

    /**
     * One dose gets its own notification; an hour with several gets a single grouped one, so
     * three pills at 2 pm don't post three separate reminders.
     */
    private suspend fun notifyFor(occurrence: DoseOccurrence) {
        val siblings = pendingAt(occurrence.scheduledAt)
        if (siblings.size > 1) {
            // Drop any per-dose notifications already posted for this hour.
            siblings.forEach { notifier.cancel(it.id) }
            notifier.showGroup(
                occurrence.scheduledAt,
                siblings.map { it to repository.getMedication(it.medicationId) },
            )
        } else {
            notifier.show(occurrence, repository.getMedication(occurrence.medicationId))
        }
    }

    private suspend fun resolve(id: Long, status: DoseStatus, takenAt: LocalDateTime?) {
        if (id < 0) return
        val occurrence = repository.getOccurrence(id)
        repository.setOccurrenceStatus(id, status, takenAt)
        scheduler.cancel(id)
        notifier.cancel(id)
        // Keep the hour's grouped notification honest: refresh it, or drop it when done.
        occurrence?.let { refreshGroup(it.scheduledAt) }
    }

    private suspend fun refreshGroup(scheduledAt: LocalDateTime) {
        val hourMinutes = scheduledAt.toLocalTime().toSecondOfDay() / 60
        val pending = pendingAt(scheduledAt)
        when {
            pending.isEmpty() -> notifier.cancelGroup(hourMinutes)
            pending.size > 1 -> notifier.showGroup(
                scheduledAt,
                pending.map { it to repository.getMedication(it.medicationId) },
            )
            else -> notifier.cancelGroup(hourMinutes)
        }
    }

    private suspend fun takeAll(hourMinutes: Int) {
        if (hourMinutes < 0) return
        val at = LocalDate.now().atTime(LocalTime.ofSecondOfDay(hourMinutes * 60L))
        val now = LocalDateTime.now()
        pendingAt(at).forEach { dose ->
            repository.setOccurrenceStatus(dose.id, DoseStatus.TAKEN, now)
            scheduler.cancel(dose.id)
            notifier.cancel(dose.id)
        }
        notifier.cancelGroup(hourMinutes)
    }

    private suspend fun snoozeAll(hourMinutes: Int) {
        if (hourMinutes < 0) return
        val at = LocalDate.now().atTime(LocalTime.ofSecondOfDay(hourMinutes * 60L))
        val minutes = settings.settings.first().defaultSnoozeMinutes
        val target = LocalDateTime.now().plusMinutes(minutes.toLong())
        pendingAt(at).forEach { scheduler.schedule(it.id, target) }
        notifier.cancelGroup(hourMinutes)
    }

    /** The still-owed doses scheduled at exactly [at] — one hour's group. */
    private suspend fun pendingAt(at: LocalDateTime): List<DoseOccurrence> =
        repository.observeOccurrencesBetween(at, at.plusMinutes(1))
            .first()
            .filter { it.status == DoseStatus.PENDING }

    private fun Intent.hourMinutes(): Int = getIntExtra(Reminders.EXTRA_HOUR_MINUTES, -1)

    private suspend fun snooze(id: Long) {
        if (id < 0) return
        val minutes = settings.settings.first().defaultSnoozeMinutes
        scheduler.schedule(id, LocalDateTime.now().plusMinutes(minutes.toLong()))
        notifier.cancel(id)
    }
}
