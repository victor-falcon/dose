package com.victorfalcon.dose.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.victorfalcon.dose.data.SettingsRepository
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.repository.MedicationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

/** Handles alarm fires, notification actions, and reboot — all reminder broadcasts. */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: MedicationRepository
    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var notifier: NotificationHelper
    @Inject lateinit var settings: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val action = intent.action
        val id = intent.getLongExtra(Reminders.EXTRA_OCCURRENCE_ID, -1L)
        scope.launch {
            try {
                when (action) {
                    Intent.ACTION_BOOT_COMPLETED -> scheduler.syncUpcoming()
                    Reminders.ACTION_FIRE -> handleFire(id)
                    Reminders.ACTION_TAKEN -> resolve(id, DoseStatus.TAKEN, LocalDateTime.now())
                    Reminders.ACTION_SKIP -> resolve(id, DoseStatus.SKIPPED, null)
                    Reminders.ACTION_SNOOZE -> snooze(id)
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
                notifier.show(occurrence, repository.getMedication(occurrence.medicationId))
                step.nextAt?.let { scheduler.schedule(id, it) }
            }
        }
    }

    private suspend fun resolve(id: Long, status: DoseStatus, takenAt: LocalDateTime?) {
        if (id < 0) return
        repository.setOccurrenceStatus(id, status, takenAt)
        scheduler.cancel(id)
        notifier.cancel(id)
    }

    private suspend fun snooze(id: Long) {
        if (id < 0) return
        val minutes = settings.settings.first().defaultSnoozeMinutes
        scheduler.schedule(id, LocalDateTime.now().plusMinutes(minutes.toLong()))
        notifier.cancel(id)
    }
}
