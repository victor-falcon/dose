package com.victorfalcon.dose.reminder

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.victorfalcon.dose.MainActivity
import com.victorfalcon.dose.R
import com.victorfalcon.dose.domain.model.DoseOccurrence
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.Medication
import com.victorfalcon.dose.domain.repository.MedicationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MedicationRepository,
) {
    private val manager = NotificationManagerCompat.from(context)
    private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    /** Idempotent: creating an existing channel is a no-op. */
    private fun ensureChannel() {
        val channel = NotificationChannelCompat.Builder(Reminders.CHANNEL_ID, NotificationManager.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.app_name))
            .build()
        manager.createNotificationChannel(channel)
    }

    fun show(occurrence: DoseOccurrence, medication: Medication?) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return // no permission -> nothing to post (Today still reflects the dose)
        }
        ensureChannel()
        val id = occurrence.id
        val title = medication?.name ?: context.getString(R.string.app_name)
        val time = occurrence.scheduledAt.format(timeFormatter)
        val text = listOfNotNull(medication?.dosage, time).joinToString(" · ")

        val notification = NotificationCompat.Builder(context, Reminders.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(id))
            .addAction(0, context.getString(R.string.dose_taken), actionIntent(Reminders.ACTION_TAKEN, id, 1))
            .addAction(0, context.getString(R.string.dose_snooze), actionIntent(Reminders.ACTION_SNOOZE, id, 3))
            .addAction(0, context.getString(R.string.dose_skip), actionIntent(Reminders.ACTION_SKIP, id, 2))
            .build()
        manager.notify(id.toInt(), notification)
    }

    /**
     * Several doses at the same time: one notification for the hour instead of three that stack
     * on top of each other. "Take all" resolves them in a single tap; opening it lands on the
     * focus screen, which then walks through them one by one.
     */
    fun showGroup(scheduledAt: LocalDateTime, doses: List<Pair<DoseOccurrence, Medication?>>) {
        if (doses.isEmpty()) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel()
        val hourMinutes = scheduledAt.toLocalTime().toSecondOfDay() / 60
        val time = scheduledAt.format(timeFormatter)
        val firstId = doses.first().first.id
        val names = doses.mapNotNull { it.second?.name }.joinToString(" · ")

        val notification = NotificationCompat.Builder(context, Reminders.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.notification_group_title, doses.size, time))
            .setContentText(names)
            .setStyle(NotificationCompat.BigTextStyle().bigText(names))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            // Re-posted whenever one of the doses is resolved, so it must not buzz again.
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent(firstId))
            .addAction(
                0,
                context.getString(R.string.notification_take_all, doses.size),
                groupActionIntent(Reminders.ACTION_TAKE_ALL, hourMinutes, 1),
            )
            .addAction(0, context.getString(R.string.notification_open), contentIntent(firstId))
            .addAction(
                0,
                context.getString(R.string.dose_snooze),
                groupActionIntent(Reminders.ACTION_SNOOZE_ALL, hourMinutes, 2),
            )
            .build()
        manager.notify(Reminders.groupNotificationId(hourMinutes), notification)
    }

    /**
     * One dose of an hour just got answered: re-post the hour's group for whatever is still
     * owed, or drop it once fewer than two doses are left. Cancelling outright would take the
     * siblings' only reminder down with it.
     */
    suspend fun refreshGroup(scheduledAt: LocalDateTime) {
        val pending = repository.observeOccurrencesBetween(scheduledAt, scheduledAt.plusMinutes(1))
            .first()
            .filter { it.status == DoseStatus.PENDING }
        if (pending.size > 1) {
            showGroup(scheduledAt, pending.map { it to repository.getMedication(it.medicationId) })
        } else {
            cancelGroup(scheduledAt.toLocalTime().toSecondOfDay() / 60)
        }
    }

    fun cancel(occurrenceId: Long) = manager.cancel(occurrenceId.toInt())

    fun cancelGroup(hourMinutes: Int) = manager.cancel(Reminders.groupNotificationId(hourMinutes))

    private fun groupActionIntent(action: String, hourMinutes: Int, offset: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            this.action = action
            putExtra(Reminders.EXTRA_HOUR_MINUTES, hourMinutes)
        }
        return PendingIntent.getBroadcast(
            context,
            Reminders.groupRequestCode(hourMinutes, offset),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionIntent(action: String, occurrenceId: Long, offset: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            this.action = action
            putExtra(Reminders.EXTRA_OCCURRENCE_ID, occurrenceId)
        }
        return PendingIntent.getBroadcast(
            context,
            Reminders.requestCode(occurrenceId, offset),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun contentIntent(occurrenceId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(Reminders.EXTRA_OCCURRENCE_ID, occurrenceId)
        }
        return PendingIntent.getActivity(
            context,
            Reminders.requestCode(occurrenceId, 4),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
