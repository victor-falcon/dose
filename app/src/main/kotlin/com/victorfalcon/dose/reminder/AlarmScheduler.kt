package com.victorfalcon.dose.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.victorfalcon.dose.domain.repository.MedicationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Arms exact alarms per pending occurrence; the receiver reschedules re-nags. */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MedicationRepository,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(occurrenceId: Long, at: LocalDateTime) {
        val triggerAtMillis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = firePendingIntent(occurrenceId)
        // USE_EXACT_ALARM is declared (medication reminders qualify); fall back to an
        // inexact idle alarm if exact scheduling is somehow revoked.
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    fun cancel(occurrenceId: Long) = alarmManager.cancel(firePendingIntent(occurrenceId))

    /** Re-arm alarms for every pending dose within the window; overdue ones fire ~now. */
    suspend fun syncUpcoming(now: LocalDateTime = LocalDateTime.now()) {
        val pending = repository.getPendingOccurrencesUntil(now.plusHours(Reminders.WINDOW_HOURS))
        pending.forEach { schedule(it.id, maxOf(it.scheduledAt, now)) }
    }

    private fun firePendingIntent(occurrenceId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = Reminders.ACTION_FIRE
            putExtra(Reminders.EXTRA_OCCURRENCE_ID, occurrenceId)
        }
        return PendingIntent.getBroadcast(
            context,
            Reminders.requestCode(occurrenceId, 0),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
