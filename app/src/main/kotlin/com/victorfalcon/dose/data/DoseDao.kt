package com.victorfalcon.dose.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.victorfalcon.dose.domain.model.DoseOccurrence
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.DoseView
import com.victorfalcon.dose.domain.model.Medication
import com.victorfalcon.dose.domain.model.Schedule
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/** Single DAO for the three tables; the repository is the public seam over it. */
@Dao
interface DoseDao {

    // --- Medications ---
    @Insert suspend fun insertMedication(medication: Medication): Long

    @Update suspend fun updateMedication(medication: Medication)

    @Query("SELECT * FROM medications WHERE active = 1 ORDER BY name COLLATE NOCASE")
    fun observeActiveMedications(): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getMedication(id: Long): Medication?

    @Query("SELECT * FROM medications WHERE id = :id")
    fun observeMedication(id: Long): Flow<Medication?>

    @Query("UPDATE medications SET active = :active WHERE id = :id")
    suspend fun setMedicationActive(id: Long, active: Boolean)

    // --- Schedules ---
    @Insert suspend fun insertSchedule(schedule: Schedule): Long

    @Update suspend fun updateSchedule(schedule: Schedule)

    @Query("SELECT * FROM schedules WHERE medicationId = :medicationId")
    suspend fun getScheduleForMedication(medicationId: Long): Schedule?

    @Query("SELECT * FROM schedules WHERE medicationId = :medicationId")
    fun observeScheduleForMedication(medicationId: Long): Flow<Schedule?>

    @Query("SELECT s.* FROM schedules s JOIN medications m ON m.id = s.medicationId WHERE m.active = 1")
    suspend fun getActiveSchedules(): List<Schedule>

    // --- Occurrences ---
    // IGNORE on the unique (medicationId, scheduledAt) index -> re-materializing is idempotent.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOccurrences(occurrences: List<DoseOccurrence>): List<Long>

    @Query("SELECT * FROM dose_occurrences WHERE scheduledAt >= :start AND scheduledAt < :end ORDER BY scheduledAt")
    fun observeOccurrencesBetween(start: LocalDateTime, end: LocalDateTime): Flow<List<DoseOccurrence>>

    // Doses joined with their medication name/dosage, for Today and History.
    @Query(
        """
        SELECT o.id AS occurrenceId, o.medicationId AS medicationId, m.name AS name, m.dosage AS dosage,
               m.image AS image, o.scheduledAt AS scheduledAt, o.status AS status, o.takenAt AS takenAt
        FROM dose_occurrences o
        JOIN medications m ON m.id = o.medicationId
        WHERE o.scheduledAt >= :start AND o.scheduledAt < :end
          AND (:activeOnly = 0 OR m.active = 1)
        ORDER BY o.scheduledAt
        """,
    )
    fun observeDosesBetween(start: LocalDateTime, end: LocalDateTime, activeOnly: Boolean): Flow<List<DoseView>>

    // All doses for one medication, most recent first (per-medication history).
    @Query(
        """
        SELECT o.id AS occurrenceId, o.medicationId AS medicationId, m.name AS name, m.dosage AS dosage,
               m.image AS image, o.scheduledAt AS scheduledAt, o.status AS status, o.takenAt AS takenAt
        FROM dose_occurrences o
        JOIN medications m ON m.id = o.medicationId
        WHERE o.medicationId = :medicationId
        ORDER BY o.scheduledAt DESC
        """,
    )
    fun observeMedicationDoses(medicationId: Long): Flow<List<DoseView>>

    // Editing regenerates only future pending doses; past + resolved rows stay frozen.
    @Query("DELETE FROM dose_occurrences WHERE medicationId = :medicationId AND status = 'PENDING' AND scheduledAt >= :from")
    suspend fun deleteFuturePending(medicationId: Long, from: LocalDateTime): Int

    // ponytail: 'AS_NEEDED' literal must match ScheduleType.AS_NEEDED.name (stored via converter).
    @Query(
        """
        SELECT m.* FROM medications m
        JOIN schedules s ON s.medicationId = m.id
        WHERE m.active = 1 AND s.type = 'AS_NEEDED'
        ORDER BY m.name COLLATE NOCASE
        """,
    )
    fun observeAsNeededMedications(): Flow<List<Medication>>

    @Query("SELECT * FROM dose_occurrences WHERE medicationId = :medicationId AND scheduledAt >= :from ORDER BY scheduledAt")
    suspend fun getOccurrencesFrom(medicationId: Long, from: LocalDateTime): List<DoseOccurrence>

    @Query("SELECT * FROM dose_occurrences WHERE id = :id")
    suspend fun getOccurrence(id: Long): DoseOccurrence?

    @Query("UPDATE dose_occurrences SET status = :status, takenAt = :takenAt WHERE id = :id")
    suspend fun updateOccurrenceStatus(id: Long, status: DoseStatus, takenAt: LocalDateTime?)

    // Pending doses due before :until (includes overdue) -> the alarm scheduler arms these.
    @Query("SELECT * FROM dose_occurrences WHERE status = 'PENDING' AND scheduledAt < :until ORDER BY scheduledAt")
    suspend fun getPendingOccurrencesUntil(until: LocalDateTime): List<DoseOccurrence>

    // Backstop MISSED sweep. 'PENDING'/'MISSED' must match the DoseStatus enum names.
    @Query("UPDATE dose_occurrences SET status = 'MISSED' WHERE status = 'PENDING' AND scheduledAt < :threshold")
    suspend fun markMissedBefore(threshold: LocalDateTime): Int

    // --- Backup / restore ---
    @Query("SELECT * FROM medications") suspend fun getAllMedications(): List<Medication>

    @Query("SELECT * FROM schedules") suspend fun getAllSchedules(): List<Schedule>

    @Query("SELECT * FROM dose_occurrences") suspend fun getAllOccurrences(): List<DoseOccurrence>

    @Query("DELETE FROM medications") suspend fun deleteAllMedications() // cascades to schedules + occurrences

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedications(medications: List<Medication>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<Schedule>)

    /** Replace the whole database in one transaction (import). FK order: meds -> schedules -> occurrences. */
    @Transaction
    suspend fun replaceAll(
        medications: List<Medication>,
        schedules: List<Schedule>,
        occurrences: List<DoseOccurrence>,
    ) {
        deleteAllMedications()
        insertMedications(medications)
        insertSchedules(schedules)
        insertOccurrences(occurrences)
    }
}
