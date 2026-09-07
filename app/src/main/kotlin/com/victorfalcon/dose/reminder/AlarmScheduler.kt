package com.victorfalcon.dose.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.victorfalcon.dose.domain.repository.MedicationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
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
        // ponytail: only SCHEDULE_EXACT_ALARM is declared -- USE_EXACT_ALARM is reserved for
        // alarm-clock and calendar apps, so exact alarms need the user's grant. Today nags for
        // it; without the grant an inexact idle alarm still fires, just later.
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    fun cancel(occurrenceId: Long) = alarmManager.cancel(firePendingIntent(occurrenceId))

    /** Arm an exact alarm at next local midnight so the widget rolls over to the new day
     *  even with no user interaction. The receiver re-arms it for the following night. */
    fun scheduleDailyRollover() {
        val nextMidnight = LocalDate.now().plusDays(1).atStartOfDay()
        val triggerAtMillis = nextMidnight.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(context, ReminderReceiver::class.java).apply { action = Reminders.ACTION_DATE_ROLL }
        val pi = PendingIntent.getBroadcast(
            context,
            Reminders.MIDNIGHT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

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
