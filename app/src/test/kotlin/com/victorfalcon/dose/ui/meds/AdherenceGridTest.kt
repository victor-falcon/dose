package com.victorfalcon.dose.ui.meds

import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.DoseView
import com.victorfalcon.dose.domain.model.Schedule
import com.victorfalcon.dose.domain.model.ScheduleType
import com.victorfalcon.dose.ui.common.DoseCellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** The detail screen's grid: one row per scheduled hour, one column per day. */
class AdherenceGridTest {

    private val today = LocalDate.of(2026, 1, 16)
    private val schedule = Schedule(
        id = 1,
        medicationId = 1,
        type = ScheduleType.DAILY_TIMES,
        startDate = today.minusDays(30),
        times = listOf(LocalTime.of(8, 0), LocalTime.of(22, 0)),
    )

    private fun dose(day: LocalDate, hour: Int, status: DoseStatus) = DoseView(
        occurrenceId = day.toEpochDay() * 100 + hour,
        medicationId = 1,
        name = "Metformina",
        dosage = null,
        scheduledAt = day.atTime(hour, 0),
        status = status,
        takenAt = null,
    )

    @Test fun `rows are the scheduled hours and columns end today`() {
        val (days, grid) = adherenceGrid(
            doses = listOf(
                dose(today, 8, DoseStatus.TAKEN),
                dose(today, 22, DoseStatus.PENDING),
            ),
            schedule = schedule,
            today = today,
        )

        assertEquals(GRID_DAYS, days.size)
        assertEquals(today, days.last())
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(22, 0)), grid.map { it.time })
        assertEquals(DoseCellState.TAKEN, grid.first().cells.last())
        assertEquals(DoseCellState.PENDING, grid.last().cells.last())
        assertNull(grid.first().cells.first()) // nothing recorded two weeks back
    }

    @Test fun `hours the pauta no longer lists still show their history`() {
        val (_, grid) = adherenceGrid(
            doses = listOf(dose(today.minusDays(3), 14, DoseStatus.MISSED)),
            schedule = schedule,
            today = today,
        )

        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(14, 0), LocalTime.of(22, 0)), grid.map { it.time })
        assertEquals(DoseCellState.MISSED, grid[1].cells[GRID_DAYS - 4])
    }

    @Test fun `adherence counts doses and leaves pending ones out of the ratio`() {
        val stats = adherenceStats(
            doses = listOf(
                dose(today, 8, DoseStatus.TAKEN),
                dose(today, 22, DoseStatus.PENDING),
                dose(today.minusDays(1), 8, DoseStatus.TAKEN),
                dose(today.minusDays(1), 22, DoseStatus.MISSED),
            ),
            today = today,
        )

        assertEquals(2, stats.taken)
        assertEquals(3, stats.resolved)
        assertEquals(1, stats.pending)
        assertEquals(67, stats.percent)
    }

    @Test fun `doses outside the window are ignored`() {
        val stats = adherenceStats(
            doses = listOf(dose(today.minusDays(GRID_DAYS.toLong()), 8, DoseStatus.MISSED)),
            today = today,
        )

        assertEquals(0, stats.resolved)
        assertEquals(0, stats.percent)
    }
}
