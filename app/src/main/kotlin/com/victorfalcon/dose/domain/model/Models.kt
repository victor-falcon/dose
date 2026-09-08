package com.victorfalcon.dose.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

// ponytail: domain models double as Room entities (single-module app). Split into
// separate @Entity + mappers only if the domain ever needs to be framework-free.

/** A medication or supplement the user tracks. */
@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dosage: String? = null, // free text in v1, e.g. "500 mg", "1 pill"
    val notes: String? = null,
    val active: Boolean = true, // soft-delete: false keeps history but stops new occurrences
    val image: String? = null, // key into the predefined image set; null -> placeholder
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
 * One schedule per medication (unique index on [medicationId]).
 */
@Entity(
    tableName = "schedules",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["medicationId"], unique = true)],
)
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
 * regenerates only future PENDING rows. The unique (medicationId, scheduledAt)
 * index makes re-materializing the rolling window idempotent.
 */
@Entity(
    tableName = "dose_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["medicationId", "scheduledAt"], unique = true)],
)
data class DoseOccurrence(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val scheduledAt: LocalDateTime,
    val status: DoseStatus = DoseStatus.PENDING,
    val takenAt: LocalDateTime? = null,
    /** Reminder pushed back to this moment; cleared as soon as the dose is resolved. */
    val snoozedUntil: LocalDateTime? = null,
)

/** A dose joined with its medication, for display on Today / History. Not a table. */
data class DoseView(
    val occurrenceId: Long,
    val medicationId: Long,
    val name: String,
    val dosage: String?,
    val image: String? = null,
    val scheduledAt: LocalDateTime,
    val status: DoseStatus,
    val takenAt: LocalDateTime?,
    val snoozedUntil: LocalDateTime? = null,
)
