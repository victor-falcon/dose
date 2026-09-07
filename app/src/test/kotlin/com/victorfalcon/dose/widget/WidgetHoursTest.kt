package com.victorfalcon.dose.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The widget shows the next scheduled *hour*, not the next pill: an hour with three doses has to
 * arrive as one group so it can offer "take all 3".
 */
class WidgetHoursTest {

    private fun dose(id: Long, minutes: Int, taken: Boolean = false) =
        WidgetDose(id = id, name = "Med $id", time = "$minutes", minutes = minutes, taken = taken)

    @Test fun `doses of one time arrive as a single group, in order`() {
        val hours = doseHours(
            listOf(
                dose(3, 14 * 60),
                dose(1, 8 * 60),
                dose(2, 14 * 60),
            ),
        )

        assertEquals(listOf(8 * 60, 14 * 60), hours.map { it.minutes })
        assertEquals(listOf(2L, 3L), hours.last().doses.map { it.id })
    }

    @Test fun `the leading hour is the first one that still owes a dose`() {
        val hours = doseHours(
            listOf(
                dose(1, 8 * 60, taken = true),
                dose(2, 14 * 60, taken = true),
                dose(3, 14 * 60),
                dose(4, 22 * 60),
            ),
        )
        val focus = focusHour(hours)

        assertEquals(14 * 60, focus?.minutes)
        assertEquals(listOf(3L), focus?.pending?.map { it.id }) // the taken sibling stays visible
        assertEquals(2, focus?.doses?.size)
    }

    @Test fun `a finished day has no leading hour`() {
        val hours = doseHours(listOf(dose(1, 8 * 60, taken = true), dose(2, 22 * 60, taken = true)))

        assertNull(focusHour(hours))
    }

    @Test fun `an empty day has no hours`() {
        assertEquals(emptyList<DoseHour>(), doseHours(emptyList()))
        assertNull(focusHour(emptyList()))
    }
}
