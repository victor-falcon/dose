package com.victorfalcon.dose.domain.repository

import com.victorfalcon.dose.domain.model.DoseOccurrence
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.Medication
import com.victorfalcon.dose.domain.model.Schedule
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/** Read/write seam over persistence. Room-backed impl lives in the data layer. */
interface MedicationRepository {
    fun observeActiveMedications(): Flow<List<Medication>>

    suspend fun getMedication(id: Long): Medication?

    suspend fun getSchedule(medicationId: Long): Schedule?

    fun observeOccurrencesBetween(start: LocalDateTime, end: LocalDateTime): Flow<List<DoseOccurrence>>

    /** Insert or update a medication and its single schedule; returns the medication id. */
    suspend fun saveMedicationWithSchedule(medication: Medication, schedule: Schedule): Long

    suspend fun setOccurrenceStatus(id: Long, status: DoseStatus, takenAt: LocalDateTime?)
}
