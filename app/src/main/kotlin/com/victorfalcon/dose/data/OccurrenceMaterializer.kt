package com.victorfalcon.dose.data

import com.victorfalcon.dose.domain.OccurrenceGenerator
import com.victorfalcon.dose.domain.model.Schedule
import java.time.LocalDate
import javax.inject.Inject

/**
 * Persists a rolling window of [DoseOccurrence]s from a [Schedule]. Idempotent: the
 * DAO ignores rows that collide on the unique (medicationId, scheduledAt) index, so
 * re-running (daily upkeep, edits) never duplicates. Generation itself is pure.
 */
class OccurrenceMaterializer @Inject constructor(
    private val dao: DoseDao,
    private val generator: OccurrenceGenerator,
) {
    /** Materialize [windowDays] ahead from [today] for one schedule. */
    suspend fun materialize(
        schedule: Schedule,
        today: LocalDate = LocalDate.now(),
        windowDays: Long = WINDOW_DAYS,
    ) {
        val occurrences = generator.generate(schedule, today, today.plusDays(windowDays))
        if (occurrences.isNotEmpty()) dao.insertOccurrences(occurrences)
    }

    companion object {
        const val WINDOW_DAYS = 60L
    }
}
