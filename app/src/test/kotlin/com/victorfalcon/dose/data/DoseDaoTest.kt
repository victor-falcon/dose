package com.victorfalcon.dose.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.victorfalcon.dose.domain.OccurrenceGenerator
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.Medication
import com.victorfalcon.dose.domain.model.Schedule
import com.victorfalcon.dose.domain.model.ScheduleType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric ships shadows up to API 35; 34 is safely supported.
class DoseDaoTest {

    private lateinit var db: DoseDatabase
    private lateinit var dao: DoseDao
    private val repo get() = RoomMedicationRepository(dao)
    private val materializer get() = OccurrenceMaterializer(dao, OccurrenceGenerator())

    private val today = LocalDate.of(2026, 7, 1) // a Wednesday

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DoseDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.doseDao()
    }

    @After fun teardown() = db.close()

    @Test fun `round-trips a medication and its schedule with converted fields`() = runTest {
        val schedule = Schedule(
            medicationId = 0,
            type = ScheduleType.WEEKLY,
            startDate = today,
            times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 30)),
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        )
        val medId = repo.saveMedicationWithSchedule(
            Medication(name = "Vitamin D", dosage = "1000 IU", notes = "with food"),
            schedule,
        )

        val loadedMed = repo.getMedication(medId)!!
        assertEquals("Vitamin D", loadedMed.name)
        assertEquals("1000 IU", loadedMed.dosage)

        val loadedSchedule = repo.getSchedule(medId)!!
        assertEquals(ScheduleType.WEEKLY, loadedSchedule.type)
        assertEquals(today, loadedSchedule.startDate)
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 30)), loadedSchedule.times)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), loadedSchedule.daysOfWeek)
    }

    @Test fun `materializes occurrences and re-running does not duplicate`() = runTest {
        val medId = repo.saveMedicationWithSchedule(
            Medication(name = "Metformin"),
            Schedule(
                medicationId = 0,
                type = ScheduleType.DAILY_TIMES,
                startDate = today,
                times = listOf(LocalTime.of(9, 0), LocalTime.of(21, 0)),
            ),
        )
        val schedule = repo.getSchedule(medId)!!

        materializer.materialize(schedule, today = today, windowDays = 6) // 7 days x 2 = 14
        materializer.materialize(schedule, today = today, windowDays = 6) // idempotent re-run

        val start = LocalDateTime.of(today, LocalTime.MIN)
        val occurrences = repo.observeOccurrencesBetween(start, start.plusDays(7)).first()
        assertEquals(14, occurrences.size)
        assertTrue(occurrences.all { it.status == DoseStatus.PENDING })
        assertTrue(occurrences.zipWithNext().all { (a, b) -> !a.scheduledAt.isAfter(b.scheduledAt) })
    }

    @Test fun `doses view joins the medication name and dosage`() = runTest {
        val medId = repo.saveMedicationWithSchedule(
            Medication(name = "Metformin", dosage = "500 mg"),
            Schedule(medicationId = 0, type = ScheduleType.DAILY_TIMES, startDate = today, times = listOf(LocalTime.of(9, 0))),
        )
        materializer.materialize(repo.getSchedule(medId)!!, today = today, windowDays = 0)

        val start = LocalDateTime.of(today, LocalTime.MIN)
        val dose = repo.observeDosesBetween(start, start.plusDays(1)).first().single()
        assertEquals("Metformin", dose.name)
        assertEquals("500 mg", dose.dosage)
        assertEquals(medId, dose.medicationId)
    }

    @Test fun `logging a PRN dose records a taken occurrence and lists the med as needed`() = runTest {
        val medId = repo.saveMedicationWithSchedule(
            Medication(name = "Ibuprofen"),
            Schedule(medicationId = 0, type = ScheduleType.AS_NEEDED, startDate = today),
        )
        assertEquals("Ibuprofen", repo.observeAsNeededMedications().first().single().name)

        val at = LocalDateTime.of(today, LocalTime.of(14, 30))
        repo.logAsNeededDose(medId, at)

        val start = LocalDateTime.of(today, LocalTime.MIN)
        val dose = repo.observeDosesBetween(start, start.plusDays(1)).first().single()
        assertEquals(DoseStatus.TAKEN, dose.status)
        assertEquals(at, dose.takenAt)
    }

    @Test fun `pending sweep queries respect the cutoff and threshold`() = runTest {
        val medId = repo.saveMedicationWithSchedule(
            Medication(name = "Metformin"),
            Schedule(
                medicationId = 0,
                type = ScheduleType.DAILY_TIMES,
                startDate = today,
                times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            ),
        )
        materializer.materialize(repo.getSchedule(medId)!!, today = today, windowDays = 1) // today + tomorrow = 4

        // Only today's two doses fall before tomorrow midnight.
        val untilTomorrow = LocalDateTime.of(today.plusDays(1), LocalTime.MIN)
        assertEquals(2, repo.getPendingOccurrencesUntil(untilTomorrow).size)

        // Threshold at noon flips only the 08:00 dose; the rest stay pending.
        val missed = repo.markMissedBefore(LocalDateTime.of(today, LocalTime.NOON))
        assertEquals(1, missed)
        assertEquals(3, repo.getPendingOccurrencesUntil(untilTomorrow.plusDays(1)).size)
    }

    @Test fun `marking an occurrence taken persists status and takenAt`() = runTest {
        val medId = repo.saveMedicationWithSchedule(
            Medication(name = "Aspirin"),
            Schedule(medicationId = 0, type = ScheduleType.DAILY_TIMES, startDate = today, times = listOf(LocalTime.of(8, 0))),
        )
        materializer.materialize(repo.getSchedule(medId)!!, today = today, windowDays = 0)

        val start = LocalDateTime.of(today, LocalTime.MIN)
        val occ = repo.observeOccurrencesBetween(start, start.plusDays(1)).first().single()
        assertNull(occ.takenAt)

        val takenAt = LocalDateTime.of(today, LocalTime.of(8, 5))
        repo.setOccurrenceStatus(occ.id, DoseStatus.TAKEN, takenAt)

        val updated = dao.getOccurrence(occ.id)!!
        assertEquals(DoseStatus.TAKEN, updated.status)
        assertEquals(takenAt, updated.takenAt)
    }
}
