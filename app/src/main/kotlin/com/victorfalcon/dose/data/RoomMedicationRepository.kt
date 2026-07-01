package com.victorfalcon.dose.data

import com.victorfalcon.dose.domain.model.DoseOccurrence
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.DoseView
import com.victorfalcon.dose.domain.model.Medication
import com.victorfalcon.dose.domain.model.Schedule
import com.victorfalcon.dose.domain.repository.MedicationRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomMedicationRepository @Inject constructor(
    private val dao: DoseDao,
) : MedicationRepository {

    override fun observeActiveMedications(): Flow<List<Medication>> = dao.observeActiveMedications()

    override suspend fun getMedication(id: Long): Medication? = dao.getMedication(id)

    override suspend fun getSchedule(medicationId: Long): Schedule? =
        dao.getScheduleForMedication(medicationId)

    override fun observeOccurrencesBetween(start: LocalDateTime, end: LocalDateTime): Flow<List<DoseOccurrence>> =
        dao.observeOccurrencesBetween(start, end)

    override fun observeDosesBetween(start: LocalDateTime, end: LocalDateTime): Flow<List<DoseView>> =
        dao.observeDosesBetween(start, end)

    override fun observeAsNeededMedications(): Flow<List<Medication>> =
        dao.observeAsNeededMedications()

    // ponytail: no explicit @Transaction. Single-writer local app; wrap both inserts
    // in a DAO @Transaction only if a partial write ever leaves a med without a schedule.
    override suspend fun saveMedicationWithSchedule(medication: Medication, schedule: Schedule): Long {
        val medicationId = if (medication.id == 0L) {
            dao.insertMedication(medication)
        } else {
            dao.updateMedication(medication)
            medication.id
        }
        val linked = schedule.copy(medicationId = medicationId)
        if (linked.id == 0L) dao.insertSchedule(linked) else dao.updateSchedule(linked)
        return medicationId
    }

    override suspend fun setOccurrenceStatus(id: Long, status: DoseStatus, takenAt: LocalDateTime?) =
        dao.updateOccurrenceStatus(id, status, takenAt)

    // ponytail: the unique (medicationId, scheduledAt) index means two PRN logs in the
    // same second collide and the second is ignored. Not a real-world scenario for v1.
    override suspend fun logAsNeededDose(medicationId: Long, at: LocalDateTime) {
        dao.insertOccurrences(
            listOf(
                DoseOccurrence(
                    medicationId = medicationId,
                    scheduledAt = at,
                    status = DoseStatus.TAKEN,
                    takenAt = at,
                ),
            ),
        )
    }
}
