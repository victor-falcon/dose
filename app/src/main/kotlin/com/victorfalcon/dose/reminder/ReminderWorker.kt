package com.victorfalcon.dose.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.victorfalcon.dose.domain.repository.MedicationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDateTime

/**
 * Periodic backstop: flip long-overdue pending doses to MISSED and re-arm alarms for
 * the upcoming window (covers process death / lost alarms). Exact timing stays with
 * the alarms; this just guarantees eventual consistency.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: MedicationRepository,
    private val scheduler: AlarmScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = LocalDateTime.now()
        repository.markMissedBefore(now.minusHours(ReminderPolicy.GRACE_HOURS))
        scheduler.syncUpcoming(now)
        return Result.success()
    }
}
