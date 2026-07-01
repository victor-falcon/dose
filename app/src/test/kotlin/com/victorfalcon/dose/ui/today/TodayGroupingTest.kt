package com.victorfalcon.dose.ui.today

import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.DoseView
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class TodayGroupingTest {

    private fun dose(id: Long, at: LocalDateTime) = DoseView(
        occurrenceId = id,
        medicationId = 1,
        name = "Med $id",
        dosage = null,
        scheduledAt = at,
        status = DoseStatus.PENDING,
        takenAt = null,
    )

    @Test fun `groups by time of day and orders chronologically`() {
        val day = java.time.LocalDate.of(2026, 7, 1)
        val doses = listOf(
            dose(1, LocalDateTime.of(day, LocalTime.of(20, 0))),
            dose(2, LocalDateTime.of(day, LocalTime.of(8, 0))),
            dose(3, LocalDateTime.of(day, LocalTime.of(8, 0))),
        )
        val groups = groupByTime(doses)

        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), groups.map { it.time })
        assertEquals(listOf(2L, 3L), groups.first().doses.map { it.occurrenceId }) // both 8:00 doses
        assertEquals(1, groups.last().doses.size)
    }

    @Test fun `empty input yields no groups`() {
        assertEquals(emptyList<DoseGroup>(), groupByTime(emptyList()))
    }
}
