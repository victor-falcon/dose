package com.victorfalcon.dose.ui.meds

import com.victorfalcon.dose.domain.model.DoseStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MedicationAdherenceTest {
    private val start = LocalDate.of(2026, 1, 10)

    @Test fun `before startDate is unknown`() {
        assertEquals(DayAdherence.UNKNOWN, dayAdherence(start.minusDays(1), listOf(DoseStatus.TAKEN), start))
    }

    @Test fun `no doses that day is not scheduled`() {
        assertEquals(DayAdherence.NOT_SCHEDULED, dayAdherence(start, emptyList(), start))
    }

    @Test fun `all taken is taken`() {
        assertEquals(DayAdherence.TAKEN, dayAdherence(start, listOf(DoseStatus.TAKEN, DoseStatus.TAKEN), start))
    }

    @Test fun `worst status wins - missed beats skipped and taken`() {
        assertEquals(DayAdherence.MISSED, dayAdherence(start, listOf(DoseStatus.TAKEN, DoseStatus.SKIPPED, DoseStatus.MISSED), start))
    }

    @Test fun `skipped beats taken`() {
        assertEquals(DayAdherence.SKIPPED, dayAdherence(start, listOf(DoseStatus.TAKEN, DoseStatus.SKIPPED), start))
    }

    @Test fun `only pending reads as not scheduled`() {
        assertEquals(DayAdherence.NOT_SCHEDULED, dayAdherence(start, listOf(DoseStatus.PENDING), start))
    }

    @Test fun `null startDate never yields unknown`() {
        assertEquals(DayAdherence.TAKEN, dayAdherence(start.minusYears(1), listOf(DoseStatus.TAKEN), null))
    }
}
