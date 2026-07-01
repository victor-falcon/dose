package com.victorfalcon.dose.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.victorfalcon.dose.data.DoseDatabase
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.Medication
import com.victorfalcon.dose.domain.model.Schedule
import com.victorfalcon.dose.domain.model.ScheduleType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
@Config(sdk = [34])
class BackupManagerTest {

    private lateinit var db: DoseDatabase
    private lateinit var manager: BackupManager
    private val today = LocalDate.of(2026, 7, 1)

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DoseDatabase::class.java,
        ).allowMainThreadQueries().build()
        manager = BackupManager(db.doseDao())
    }

    @After fun teardown() = db.close()

    @Test fun `export then clear then import restores everything`() = runTest {
        val dao = db.doseDao()
        val medId = dao.insertMedication(Medication(name = "Metformin", dosage = "500 mg", notes = "with food"))
        dao.insertSchedule(
            Schedule(
                medicationId = medId,
                type = ScheduleType.WEEKLY,
                startDate = today,
                times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            ),
        )
        dao.insertOccurrences(
            listOf(
                com.victorfalcon.dose.domain.model.DoseOccurrence(
                    medicationId = medId,
                    scheduledAt = LocalDateTime.of(today, LocalTime.of(8, 0)),
                    status = DoseStatus.TAKEN,
                    takenAt = LocalDateTime.of(today, LocalTime.of(8, 5)),
                ),
            ),
        )

        val json = manager.export()

        dao.deleteAllMedications()
        assertEquals(0, dao.getAllMedications().size)
        assertEquals(0, dao.getAllOccurrences().size) // cascade cleared

        manager.import(json)

        val med = dao.getAllMedications().single()
        assertEquals("Metformin", med.name)
        assertEquals("with food", med.notes)

        val schedule = dao.getAllSchedules().single()
        assertEquals(ScheduleType.WEEKLY, schedule.type)
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), schedule.times)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), schedule.daysOfWeek)

        val occurrence = dao.getAllOccurrences().single()
        assertEquals(DoseStatus.TAKEN, occurrence.status)
        assertEquals(LocalDateTime.of(today, LocalTime.of(8, 5)), occurrence.takenAt)
    }
}
