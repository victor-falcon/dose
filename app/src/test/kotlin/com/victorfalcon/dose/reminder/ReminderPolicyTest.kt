package com.victorfalcon.dose.reminder

import com.victorfalcon.dose.domain.model.DoseStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ReminderPolicyTest {

    private val scheduled = LocalDateTime.of(2026, 7, 1, 8, 0)

    private fun stepAt(minutesAfter: Long, status: DoseStatus = DoseStatus.PENDING) =
        ReminderPolicy.step(scheduled, scheduled.plusMinutes(minutesAfter), status)

    @Test fun `on time notifies and re-nags an hour later`() {
        assertEquals(ReminderStep.Notify(scheduled.plusHours(1)), stepAt(0))
    }

    @Test fun `one hour later re-nags at two hours`() {
        assertEquals(ReminderStep.Notify(scheduled.plusHours(2)), stepAt(60))
    }

    @Test fun `late in the window the next nag is capped at the grace point`() {
        assertEquals(ReminderStep.Notify(scheduled.plusHours(3)), stepAt(150)) // 2h30 -> next 3h
    }

    @Test fun `at the grace point it is missed`() {
        assertEquals(ReminderStep.Miss, stepAt(180)) // exactly 3h
        assertEquals(ReminderStep.Miss, stepAt(500))
    }

    @Test fun `already acted doses do nothing`() {
        assertEquals(ReminderStep.Done, stepAt(0, DoseStatus.TAKEN))
        assertEquals(ReminderStep.Done, stepAt(200, DoseStatus.SKIPPED))
    }
}
