package com.victorfalcon.dose.ui.meds

import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.DoseView
import com.victorfalcon.dose.domain.model.Medication
import com.victorfalcon.dose.domain.model.Schedule
import com.victorfalcon.dose.domain.model.ScheduleType
import com.victorfalcon.dose.ui.common.DoseCellState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * The week strip is per dose, not per day: a medication taken three times a day must be able to
 * say "2 of 3" on a given day instead of collapsing into one verdict.
 */
class MedicationAdherenceTest {

    private val today = LocalDate.of(2026, 1, 16)
    private val windowStart = today.minusDays((HISTORY_DAYS - 1).toLong())
    private val med = Medication(id = 1, name = "Metformina")
    private val schedule = Schedule(
        id = 1,
        medicationId = 1,
        type = ScheduleType.DAILY_TIMES,
        startDate = windowStart,
        times = listOf(LocalTime.of(8, 0), LocalTime.of(14, 0), LocalTime.of(22, 0)),
    )

    private fun dose(day: LocalDate, hour: Int, status: DoseStatus, id: Long = day.dayOfMonth * 100L + hour) =
        DoseView(
            occurrenceId = id,
            medicationId = 1,
            name = "Metformina",
            dosage = "850 mg",
            scheduledAt = day.atTime(hour, 0),
            status = status,
            takenAt = null,
        )

    @Test fun `a day keeps one cell per dose, in time order`() {
        val day = windowStart
        val row = weekRows(
            medications = listOf(med),
            doses = listOf(
                dose(day, 22, DoseStatus.MISSED),
                dose(day, 8, DoseStatus.TAKEN),
                dose(day, 14, DoseStatus.SKIPPED),
            ),
            schedules = listOf(schedule),
            windowStart = windowStart,
        ).single()

        assertEquals(
            listOf(DoseCellState.TAKEN, DoseCellState.SKIPPED, DoseCellState.MISSED),
            row.week.first().cells,
        )
        assertEquals(3, row.dosesPerDay)
    }

    @Test fun `weekly count is doses, not days`() {
        val doses = listOf(
            dose(windowStart, 8, DoseStatus.TAKEN),
            dose(windowStart, 14, DoseStatus.TAKEN),
            dose(windowStart, 22, DoseStatus.MISSED),
            dose(today, 8, DoseStatus.TAKEN),
            dose(today, 14, DoseStatus.PENDING),
        )
        val row = weekRows(listOf(med), doses, listOf(schedule), windowStart).single()

        assertEquals(3, row.takenThisWeek)
        assertEquals(5, row.dosesThisWeek)
    }

    @Test fun `days with nothing due have no cells`() {
        val row = weekRows(
            listOf(med),
            listOf(dose(today, 8, DoseStatus.TAKEN)),
            listOf(schedule),
            windowStart,
        ).single()

        assertEquals(HISTORY_DAYS, row.week.size)
        assertEquals(emptyList<DoseCellState>(), row.week.first().cells)
        assertEquals(listOf(DoseCellState.TAKEN), row.week.last().cells)
    }

    @Test fun `medications without doses still get a full week`() {
        val row = weekRows(listOf(med), emptyList(), emptyList(), windowStart).single()

        assertEquals(HISTORY_DAYS, row.week.size)
        assertEquals(0, row.dosesThisWeek)
        assertEquals(0, row.dosesPerDay)
        assertEquals(null, row.schedule)
    }
}
