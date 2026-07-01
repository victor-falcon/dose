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
import com.victorfalcon.dose.domain.model.Medication
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
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

    fun cancel(occurrenceId: Long) = manager.cancel(occurrenceId.toInt())

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
