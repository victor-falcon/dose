package com.victorfalcon.dose.domain

import com.victorfalcon.dose.domain.model.Schedule
import com.victorfalcon.dose.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class OccurrenceGeneratorTest {

    private val gen = OccurrenceGenerator()
    private val at8 = listOf(LocalTime.of(8, 0))
    private val wed = LocalDate.of(2026, 7, 1) // a Wednesday
    private fun mon(week: Int = 0) = LocalDate.of(2026, 7, 6).plusWeeks(week.toLong()) // a Monday

    @Test fun `two times a day yields two occurrences per active day`() {
        val s = Schedule(
            medicationId = 1, type = ScheduleType.DAILY_TIMES, startDate = wed,
            times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
        )
        val out = gen.generate(s, wed, wed)
        assertEquals(2, out.size)
    }

    @Test fun `weekly fires only on selected days`() {
        val s = Schedule(
            medicationId = 1, type = ScheduleType.WEEKLY, startDate = mon(), times = at8,
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        )
        val out = gen.generate(s, mon(), mon().plusDays(6)) // Mon..Sun
        assertEquals(3, out.size)
        assertTrue(out.all { it.scheduledAt.dayOfWeek in s.daysOfWeek })
    }

    @Test fun `interval every 3 days from the anchor`() {
        val s = Schedule(
            medicationId = 1, type = ScheduleType.INTERVAL, startDate = wed, times = at8,
            intervalDays = 3,
        )
        val out = gen.generate(s, wed, wed.plusDays(9)) // 07-01,04,07,10
        assertEquals(listOf(1, 4, 7, 10), out.map { it.scheduledAt.dayOfMonth })
    }

    @Test fun `cyclic 21 on 7 off`() {
        val s = Schedule(
            medicationId = 1, type = ScheduleType.CYCLIC, startDate = wed, times = at8,
            cycleActiveDays = 21, cycleRestDays = 7,
        )
        assertTrue(gen.isActiveOn(s, wed.plusDays(20)))  // last active day
        assertFalse(gen.isActiveOn(s, wed.plusDays(21))) // first rest day
        assertFalse(gen.isActiveOn(s, wed.plusDays(27))) // last rest day
        assertTrue(gen.isActiveOn(s, wed.plusDays(28)))  // cycle restarts
    }

    @Test fun `as needed and pre-start produce nothing`() {
        val prn = Schedule(medicationId = 1, type = ScheduleType.AS_NEEDED, startDate = wed, times = at8)
        assertTrue(gen.generate(prn, wed, wed.plusDays(30)).isEmpty())

        val daily = Schedule(medicationId = 1, type = ScheduleType.DAILY_TIMES, startDate = wed, times = at8)
        val out = gen.generate(daily, wed.minusDays(5), wed) // range starts before anchor
        assertEquals(1, out.size)
        assertEquals(wed, out.single().scheduledAt.toLocalDate())
    }
}
