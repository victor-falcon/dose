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

/**
 * Today splits into the dose due now and the rest of the day. Doses already resolved stay in the
 * list — an hour with three pills has to keep showing all three.
 */
class TodayStateTest {

    private val day = java.time.LocalDate.of(2026, 7, 1)

    private fun dose(id: Long, hour: Int, status: DoseStatus) = DoseView(
        occurrenceId = id,
        medicationId = id,
        name = "Med $id",
        dosage = null,
        scheduledAt = LocalDateTime.of(day, LocalTime.of(hour, 0)),
        status = status,
        takenAt = null,
    )

    @Test fun `hero is the first pending dose and the rest keeps everything else`() {
        val state = todayState(
            doses = listOf(
                dose(1, 7, DoseStatus.TAKEN),
                dose(2, 8, DoseStatus.PENDING),
                dose(3, 14, DoseStatus.PENDING),
                dose(4, 14, DoseStatus.PENDING),
            ),
            asNeeded = emptyList(),
        )

        assertEquals(2L, state.hero?.occurrenceId)
        assertEquals(listOf(LocalTime.of(7, 0), LocalTime.of(14, 0)), state.rest.map { it.time })
        assertEquals(2, state.rest.last().doses.size) // both 14:00 doses stay together
        assertEquals(1, state.takenCount)
        assertEquals(4, state.totalCount)
        assertEquals(0.25f, state.progress, 0.001f)
    }

    @Test fun `a finished day has no hero`() {
        val state = todayState(
            doses = listOf(dose(1, 8, DoseStatus.TAKEN), dose(2, 20, DoseStatus.SKIPPED)),
            asNeeded = emptyList(),
        )

        assertEquals(null, state.hero)
        assertEquals(true, state.allDone)
        assertEquals(2, state.rest.sumOf { it.doses.size })
    }

    @Test fun `no doses and no PRN medications is empty`() {
        assertEquals(true, todayState(emptyList(), emptyList()).isEmpty)
    }
}

/**
 * What the hero card says beside "NOW · 3:30 PM". While the dose is actually due it says nothing:
 * the time is already there, and a second label repeating it is noise.
 */
class DoseTimingTest {

    private val due = LocalDateTime.of(2026, 7, 1, 15, 30)

    /** Positive [minutesEarly] = the dose is still that far ahead; negative = it is that late. */
    private fun timing(minutesEarly: Long) = doseTiming(due, due.minusMinutes(minutesEarly))

    @Test fun `a dose due right now says nothing`() {
        assertEquals(null, timing(0))
    }

    @Test fun `the minute either side of due still says nothing`() {
        assertEquals(null, timing(1))
        assertEquals(null, timing(-1))
    }

    @Test fun `two minutes out is the first thing worth saying`() {
        assertEquals(DoseTiming(DoseTimingLabel.IN_MINUTES, 2), timing(2))
        assertEquals(DoseTiming(DoseTimingLabel.MINUTES_AGO, 2), timing(-2))
    }

    @Test fun `still due counts up in minutes then hours`() {
        assertEquals(DoseTiming(DoseTimingLabel.IN_MINUTES, 40), timing(40))
        assertEquals(DoseTiming(DoseTimingLabel.IN_HOURS, 3), timing(180))
    }

    @Test fun `late counts up in minutes then hours`() {
        assertEquals(DoseTiming(DoseTimingLabel.MINUTES_AGO, 12), timing(-12))
        assertEquals(DoseTiming(DoseTimingLabel.HOURS_AGO, 2), timing(-120))
    }

    @Test fun `only the past tense wordings count as late`() {
        assertEquals(false, timing(40)!!.label.late)
        assertEquals(false, timing(180)!!.label.late)
        assertEquals(true, timing(-12)!!.label.late)
        assertEquals(true, timing(-120)!!.label.late)
    }
}
