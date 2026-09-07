package com.victorfalcon.dose.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.victorfalcon.dose.data.OccurrenceMaterializer
import com.victorfalcon.dose.domain.repository.MedicationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDateTime

/**
 * Periodic upkeep + backstop: roll the materialization window forward for active meds,
 * flip long-overdue pending doses to MISSED, and re-arm alarms for the upcoming window
 * (covers process death / lost alarms). Exact timing stays with the alarms.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: MedicationRepository,
    private val materializer: OccurrenceMaterializer,
    private val scheduler: AlarmScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = LocalDateTime.now()
        // Keep ~60 days materialized ahead (idempotent). ponytail: no pruning of old rows —
        // data is tiny; add a retention delete only if the DB ever grows enough to matter.
        repository.getActiveSchedules().forEach { materializer.materialize(it) }
        repository.markMissedBefore(now.minusHours(ReminderPolicy.GRACE_HOURS))
        scheduler.syncUpcoming(now)
        scheduler.scheduleDailyRollover() // keep the midnight widget rollover armed
        return Result.success()
    }
}
