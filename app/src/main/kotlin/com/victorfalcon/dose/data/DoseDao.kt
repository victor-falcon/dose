package com.victorfalcon.dose.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.victorfalcon.dose.domain.model.DoseOccurrence
import com.victorfalcon.dose.domain.model.DoseStatus
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

    // --- Schedules ---
    @Insert suspend fun insertSchedule(schedule: Schedule): Long

    @Update suspend fun updateSchedule(schedule: Schedule)

    @Query("SELECT * FROM schedules WHERE medicationId = :medicationId")
    suspend fun getScheduleForMedication(medicationId: Long): Schedule?

    // --- Occurrences ---
    // IGNORE on the unique (medicationId, scheduledAt) index -> re-materializing is idempotent.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOccurrences(occurrences: List<DoseOccurrence>): List<Long>

    @Query("SELECT * FROM dose_occurrences WHERE scheduledAt >= :start AND scheduledAt < :end ORDER BY scheduledAt")
    fun observeOccurrencesBetween(start: LocalDateTime, end: LocalDateTime): Flow<List<DoseOccurrence>>

    @Query("SELECT * FROM dose_occurrences WHERE medicationId = :medicationId AND scheduledAt >= :from ORDER BY scheduledAt")
    suspend fun getOccurrencesFrom(medicationId: Long, from: LocalDateTime): List<DoseOccurrence>

    @Query("SELECT * FROM dose_occurrences WHERE id = :id")
    suspend fun getOccurrence(id: Long): DoseOccurrence?

    @Query("UPDATE dose_occurrences SET status = :status, takenAt = :takenAt WHERE id = :id")
    suspend fun updateOccurrenceStatus(id: Long, status: DoseStatus, takenAt: LocalDateTime?)
}
