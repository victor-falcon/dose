package com.victorfalcon.dose.ui.history

import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.DoseView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class HistoryLogicTest {

    private val day = LocalDateTime.of(2026, 7, 1, 8, 0)

    private fun dose(status: DoseStatus, at: LocalDateTime = day) = DoseView(
        occurrenceId = 0, medicationId = 1, name = "Med", dosage = null,
        scheduledAt = at, status = status, takenAt = null,
    )

    @Test fun `day mark reflects the worst-to-best combination`() {
        assertEquals(DayMark.NONE, dayMark(emptyList()))
        assertEquals(DayMark.SCHEDULED, dayMark(listOf(dose(DoseStatus.PENDING), dose(DoseStatus.PENDING))))
        assertEquals(DayMark.ALL_TAKEN, dayMark(listOf(dose(DoseStatus.TAKEN), dose(DoseStatus.TAKEN))))
        assertEquals(DayMark.MISSED, dayMark(listOf(dose(DoseStatus.TAKEN), dose(DoseStatus.MISSED))))
        assertEquals(DayMark.PARTIAL, dayMark(listOf(dose(DoseStatus.TAKEN), dose(DoseStatus.SKIPPED))))
    }

    @Test fun `adherence counts taken over past-due doses in the window`() {
        val now = LocalDateTime.of(2026, 7, 10, 12, 0)
        val doses = listOf(
            dose(DoseStatus.TAKEN, now.minusDays(1)),
            dose(DoseStatus.MISSED, now.minusDays(2)),
            dose(DoseStatus.SKIPPED, now.minusDays(3)),
            dose(DoseStatus.TAKEN, now.minusDays(20)),   // inside 30d, outside 7d
            dose(DoseStatus.PENDING, now.plusDays(1)),   // future -> excluded
        )
        val a7 = adherence(doses, now, days = 7)
        assertEquals(1, a7.taken)
        assertEquals(3, a7.total) // taken + missed + skipped within 7 days
        assertEquals(33, a7.percent)

        val a30 = adherence(doses, now, days = 30)
        assertEquals(2, a30.taken)
        assertEquals(4, a30.total)
        assertEquals(50, a30.percent)
    }

    @Test fun `adherence percent is null with no past-due doses`() {
        val now = LocalDateTime.of(2026, 7, 10, 12, 0)
        assertNull(adherence(emptyList(), now, days = 7).percent)
    }
}
