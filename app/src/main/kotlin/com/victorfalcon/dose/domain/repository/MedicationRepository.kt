package com.victorfalcon.dose.domain.repository

import com.victorfalcon.dose.domain.model.DoseOccurrence
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.DoseView
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

    /** Doses joined with medication info in [start, end), for Today / History. */
    fun observeDosesBetween(start: LocalDateTime, end: LocalDateTime): Flow<List<DoseView>>

    /** Active PRN (as-needed) medications, which have no scheduled occurrences. */
    fun observeAsNeededMedications(): Flow<List<Medication>>

    /** Insert or update a medication and its single schedule; returns the medication id. */
    suspend fun saveMedicationWithSchedule(medication: Medication, schedule: Schedule): Long

    suspend fun setOccurrenceStatus(id: Long, status: DoseStatus, takenAt: LocalDateTime?)

    /** Record an ad-hoc taken dose for a PRN medication. */
    suspend fun logAsNeededDose(medicationId: Long, at: LocalDateTime)

    suspend fun getOccurrence(id: Long): DoseOccurrence?

    /** Pending doses due before [until] (includes overdue), for the alarm scheduler. */
    suspend fun getPendingOccurrencesUntil(until: LocalDateTime): List<DoseOccurrence>

    /** Bulk-flip still-pending doses older than [threshold] to MISSED; returns the count. */
    suspend fun markMissedBefore(threshold: LocalDateTime): Int
}
