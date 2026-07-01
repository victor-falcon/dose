package com.victorfalcon.dose.ui.editor

import com.victorfalcon.dose.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class MedEditorUiStateTest {

    private val base = MedEditorUiState(name = "Vitamin D", startDate = LocalDate.of(2026, 7, 1))

    @Test fun `name is required`() {
        assertFalse(base.copy(name = "  ").isValid)
        assertTrue(base.isValid)
    }

    @Test fun `scheduled types need at least one time`() {
        assertFalse(base.copy(type = ScheduleType.DAILY_TIMES, times = emptyList()).isValid)
        assertTrue(base.copy(type = ScheduleType.DAILY_TIMES, times = listOf(LocalTime.NOON)).isValid)
    }

    @Test fun `as-needed is valid without times`() {
        assertTrue(base.copy(type = ScheduleType.AS_NEEDED, times = emptyList()).isValid)
    }

    @Test fun `weekly needs a day, interval and cycle need sane numbers`() {
        assertFalse(base.copy(type = ScheduleType.WEEKLY, daysOfWeek = emptySet()).isValid)
        assertFalse(base.copy(type = ScheduleType.INTERVAL, intervalDays = 0).isValid)
        assertFalse(base.copy(type = ScheduleType.CYCLIC, cycleActiveDays = 0, cycleRestDays = 7).isValid)
        assertFalse(base.copy(type = ScheduleType.CYCLIC, cycleActiveDays = 21, cycleRestDays = 0).isValid)
        assertTrue(base.copy(type = ScheduleType.CYCLIC, cycleActiveDays = 21, cycleRestDays = 7).isValid)
    }

    @Test fun `mapping trims text, drops blanks, and sorts times`() {
        val state = base.copy(
            name = "  Metformin ",
            dosage = "  ",
            notes = "with food",
            type = ScheduleType.DAILY_TIMES,
            times = listOf(LocalTime.of(21, 0), LocalTime.of(9, 0)),
        )
        val med = state.toMedication(id = 5)
        assertEquals(5L, med.id)
        assertEquals("Metformin", med.name)
        assertEquals(null, med.dosage) // blank -> null
        assertEquals("with food", med.notes)

        val schedule = state.toSchedule(medicationId = 5)
        assertEquals(listOf(LocalTime.of(9, 0), LocalTime.of(21, 0)), schedule.times) // sorted
    }

    @Test fun `mapping clears fields irrelevant to the chosen type`() {
        val weekly = base.copy(
            type = ScheduleType.WEEKLY,
            daysOfWeek = setOf(DayOfWeek.MONDAY),
        ).toSchedule()
        assertEquals(setOf(DayOfWeek.MONDAY), weekly.daysOfWeek)

        val prn = base.copy(type = ScheduleType.AS_NEEDED, times = listOf(LocalTime.NOON)).toSchedule()
        assertTrue(prn.times.isEmpty()) // AS_NEEDED persists no times
    }
}
