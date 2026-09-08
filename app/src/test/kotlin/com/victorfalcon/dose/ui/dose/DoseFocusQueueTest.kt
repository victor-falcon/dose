package com.victorfalcon.dose.ui.dose

import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.DoseView
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The bug report this fixes: creatine at 11:00 and Adiro at 13:00. At 12:00 only the creatine is
 * owed, so the focus screen must show one dose, not two.
 */
class DoseFocusQueueTest {

    private val day = LocalDate.of(2026, 7, 1)
    private val creatine = 1L
    private val adiro = 2L

    private fun dose(id: Long, hour: Int, status: DoseStatus = DoseStatus.PENDING) = DoseView(
        occurrenceId = id,
        medicationId = id,
        name = "Med $id",
        dosage = null,
        scheduledAt = LocalDateTime.of(day, LocalTime.of(hour, 0)),
        status = status,
        takenAt = null,
    )

    private fun at(hour: Int) = LocalDateTime.of(day, LocalTime.of(hour, 0))

    @Test fun `a dose not due yet stays out of the queue`() {
        val queue = doseQueue(
            doses = listOf(dose(creatine, 11), dose(adiro, 13)),
            openedWith = creatine,
            now = at(12),
        )

        assertEquals(listOf(creatine), queue)
    }

    @Test fun `the dose it was opened with answers first, then the rest oldest-first`() {
        val queue = doseQueue(
            doses = listOf(dose(creatine, 11), dose(adiro, 13)),
            openedWith = adiro,
            now = at(13),
        )

        assertEquals(listOf(adiro, creatine), queue)
    }

    @Test fun `a snoozed dose stays out of the queue`() {
        val queue = doseQueue(
            doses = listOf(dose(creatine, 11), dose(adiro, 13)),
            openedWith = adiro,
            now = at(13),
            snoozed = setOf(creatine),
        )

        assertEquals(listOf(adiro), queue)
    }

    @Test fun `already resolved doses never enter the queue`() {
        val queue = doseQueue(
            doses = listOf(
                dose(creatine, 11, DoseStatus.TAKEN),
                dose(adiro, 13),
                dose(3, 9, DoseStatus.SKIPPED),
                dose(4, 8, DoseStatus.MISSED),
            ),
            openedWith = adiro,
            now = at(13),
        )

        assertEquals(listOf(adiro), queue)
    }

    @Test fun `the queue keeps its length as its doses get resolved`() {
        val doses = listOf(dose(creatine, 11), dose(adiro, 13))
        val queue = doseQueue(doses, openedWith = adiro, now = at(13))

        // The screen snapshots the ids once and then reads them back off the live dose list, so
        // answering the Adiro must not drop a dot.
        val afterTakingAdiro = listOf(dose(creatine, 11), dose(adiro, 13, DoseStatus.TAKEN))
        val stillShown = queue.mapNotNull { id ->
            afterTakingAdiro.firstOrNull { it.occurrenceId == id }
        }

        assertEquals(2, stillShown.size)
        assertEquals(listOf(adiro, creatine), stillShown.map { it.occurrenceId })
    }

    @Test fun `no dose is owed yet`() {
        val queue = doseQueue(
            doses = listOf(dose(creatine, 11), dose(adiro, 13)),
            openedWith = creatine,
            now = at(9),
        )

        assertEquals(emptyList<Long>(), queue)
    }
}
