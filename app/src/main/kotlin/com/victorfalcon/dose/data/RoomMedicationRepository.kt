package com.victorfalcon.dose.data

import com.victorfalcon.dose.domain.model.DoseOccurrence
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.DoseView
import com.victorfalcon.dose.domain.model.Medication
import com.victorfalcon.dose.domain.model.Schedule
import com.victorfalcon.dose.domain.repository.MedicationRepository
import com.victorfalcon.dose.widget.WidgetRefresher
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomMedicationRepository @Inject constructor(
    private val dao: DoseDao,
    private val widgetRefresher: WidgetRefresher,
) : MedicationRepository {

    override fun observeActiveMedications(): Flow<List<Medication>> = dao.observeActiveMedications()

    override suspend fun getMedication(id: Long): Medication? = dao.getMedication(id)

    override fun observeMedication(id: Long): Flow<Medication?> = dao.observeMedication(id)

    override suspend fun getSchedule(medicationId: Long): Schedule? =
        dao.getScheduleForMedication(medicationId)

    override fun observeSchedule(medicationId: Long): Flow<Schedule?> =
        dao.observeScheduleForMedication(medicationId)

    override suspend fun getActiveSchedules(): List<Schedule> = dao.getActiveSchedules()

    override fun observeActiveSchedules(): Flow<List<Schedule>> = dao.observeActiveSchedules()

    override fun observeMedicationDoses(medicationId: Long): Flow<List<DoseView>> =
        dao.observeMedicationDoses(medicationId)

    override suspend fun deleteFuturePendingOccurrences(medicationId: Long, from: LocalDateTime): Int {
        val count = dao.deleteFuturePending(medicationId, from)
        widgetRefresher.refresh()
        return count
    }

    // ponytail: alarms for the deleted future doses aren't cancelled here; when they fire
    // the receiver finds no occurrence and no-ops. syncUpcoming re-arms the current set.
    override suspend fun archiveMedication(id: Long) {
        dao.setMedicationActive(id, active = false)
        dao.deleteFuturePending(id, LocalDateTime.now())
        widgetRefresher.refresh()
    }

    override fun observeOccurrencesBetween(start: LocalDateTime, end: LocalDateTime): Flow<List<DoseOccurrence>> =
        dao.observeOccurrencesBetween(start, end)

    override fun observeDosesBetween(start: LocalDateTime, end: LocalDateTime, activeOnly: Boolean): Flow<List<DoseView>> =
        dao.observeDosesBetween(start, end, activeOnly)

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
        widgetRefresher.refresh()
        return medicationId
    }

    override suspend fun setOccurrenceStatus(id: Long, status: DoseStatus, takenAt: LocalDateTime?) {
        dao.updateOccurrenceStatus(id, status, takenAt)
        widgetRefresher.refresh()
    }

    override suspend fun snoozeOccurrence(id: Long, until: LocalDateTime) {
        dao.snoozeOccurrence(id, until)
        widgetRefresher.refresh()
    }

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
        widgetRefresher.refresh()
    }

    override suspend fun getOccurrence(id: Long): DoseOccurrence? = dao.getOccurrence(id)

    override suspend fun getPendingOccurrencesUntil(until: LocalDateTime): List<DoseOccurrence> =
        dao.getPendingOccurrencesUntil(until)

    override suspend fun markMissedBefore(threshold: LocalDateTime): Int {
        val count = dao.markMissedBefore(threshold)
        widgetRefresher.refresh()
        return count
    }
}
