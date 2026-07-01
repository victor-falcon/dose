package com.victorfalcon.dose.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** A medication or supplement the user tracks. */
data class Medication(
    val id: Long = 0,
    val name: String,
    val dosage: String? = null, // free text in v1, e.g. "500 mg", "1 pill"
    val notes: String? = null,
    val active: Boolean = true, // soft-delete: false keeps history but stops new occurrences
)

enum class ScheduleType {
    DAILY_TIMES, // every day at the given times
    WEEKLY,      // only on daysOfWeek
    INTERVAL,    // every intervalDays from startDate
    CYCLIC,      // cycleActiveDays "on" then cycleRestDays "off", repeating
    AS_NEEDED,   // PRN: no reminders, logged manually
}

/**
 * The rule for one medication. All types share [times] (the times of day to
 * take it) and [startDate] (the anchor). Type-specific fields default to unused.
 */
data class Schedule(
    val id: Long = 0,
    val medicationId: Long,
    val type: ScheduleType,
    val startDate: LocalDate,
    val times: List<LocalTime> = emptyList(),
    val daysOfWeek: Set<DayOfWeek> = emptySet(), // WEEKLY
    val intervalDays: Int = 1,                   // INTERVAL
    val cycleActiveDays: Int = 0,                // CYCLIC
    val cycleRestDays: Int = 0,                  // CYCLIC
)

enum class DoseStatus { PENDING, TAKEN, SKIPPED, MISSED }

/**
 * A materialized planned dose. Rows in the future drive alarms and the Today
 * screen; past rows are the immutable adherence history. Editing a schedule
 * regenerates only future PENDING rows.
 */
data class DoseOccurrence(
    val id: Long = 0,
    val medicationId: Long,
    val scheduledAt: LocalDateTime,
    val status: DoseStatus = DoseStatus.PENDING,
    val takenAt: LocalDateTime? = null,
)
