package com.victorfalcon.dose.data.backup

import com.victorfalcon.dose.domain.model.DoseOccurrence
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.Medication
import com.victorfalcon.dose.domain.model.Schedule
import com.victorfalcon.dose.domain.model.ScheduleType
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * On-disk backup shape. java.time values are ISO strings and enums are their names,
 * so the JSON is portable and human-readable. [version] guards future format changes.
 */
@Serializable
data class Backup(
    val version: Int = CURRENT_VERSION,
    val medications: List<BackupMedication>,
    val schedules: List<BackupSchedule>,
    val occurrences: List<BackupOccurrence>,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class BackupMedication(
    val id: Long,
    val name: String,
    val dosage: String? = null,
    val notes: String? = null,
    val active: Boolean = true,
    val image: String? = null,
)

@Serializable
data class BackupSchedule(
    val id: Long,
    val medicationId: Long,
    val type: String,
    val startDate: String,
    val times: List<String> = emptyList(),
    val daysOfWeek: List<Int> = emptyList(),
    val intervalDays: Int = 1,
    val cycleActiveDays: Int = 0,
    val cycleRestDays: Int = 0,
)

@Serializable
data class BackupOccurrence(
    val id: Long,
    val medicationId: Long,
    val scheduledAt: String,
    val status: String,
    val takenAt: String? = null,
)

fun Medication.toBackup() = BackupMedication(id, name, dosage, notes, active, image)
fun BackupMedication.toDomain() = Medication(id, name, dosage, notes, active, image)

fun Schedule.toBackup() = BackupSchedule(
    id = id,
    medicationId = medicationId,
    type = type.name,
    startDate = startDate.toString(),
    times = times.map { it.toString() },
    daysOfWeek = daysOfWeek.map { it.value },
    intervalDays = intervalDays,
    cycleActiveDays = cycleActiveDays,
    cycleRestDays = cycleRestDays,
)

fun BackupSchedule.toDomain() = Schedule(
    id = id,
    medicationId = medicationId,
    type = ScheduleType.valueOf(type),
    startDate = LocalDate.parse(startDate),
    times = times.map { LocalTime.parse(it) },
    daysOfWeek = daysOfWeek.map { DayOfWeek.of(it) }.toSet(),
    intervalDays = intervalDays,
    cycleActiveDays = cycleActiveDays,
    cycleRestDays = cycleRestDays,
)

fun DoseOccurrence.toBackup() = BackupOccurrence(id, medicationId, scheduledAt.toString(), status.name, takenAt?.toString())
fun BackupOccurrence.toDomain() = DoseOccurrence(
    id = id,
    medicationId = medicationId,
    scheduledAt = LocalDateTime.parse(scheduledAt),
    status = DoseStatus.valueOf(status),
    takenAt = takenAt?.let(LocalDateTime::parse),
)
