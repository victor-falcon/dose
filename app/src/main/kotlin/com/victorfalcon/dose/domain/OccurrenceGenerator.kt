package com.victorfalcon.dose.domain

import com.victorfalcon.dose.domain.model.DoseOccurrence
import com.victorfalcon.dose.domain.model.Schedule
import com.victorfalcon.dose.domain.model.ScheduleType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Turns a [Schedule] rule into concrete [DoseOccurrence]s over a date range.
 * Pure and deterministic (no clock, no I/O) so it's unit-testable and safe to
 * call from the alarm scheduler, the editor, and the missed-dose worker.
 */
class OccurrenceGenerator @Inject constructor() {

    /** Occurrences with [scheduledAt] in [from]..[to] (inclusive), all PENDING. */
    fun generate(schedule: Schedule, from: LocalDate, to: LocalDate): List<DoseOccurrence> {
        if (schedule.type == ScheduleType.AS_NEEDED || schedule.times.isEmpty()) return emptyList()
        val out = mutableListOf<DoseOccurrence>()
        var date = maxOf(from, schedule.startDate)
        while (!date.isAfter(to)) {
            if (isActiveOn(schedule, date)) {
                for (time in schedule.times) {
                    out += DoseOccurrence(
                        medicationId = schedule.medicationId,
                        scheduledAt = LocalDateTime.of(date, time),
                    )
                }
            }
            date = date.plusDays(1)
        }
        return out
    }

    /** Whether the medication is due (at all) on [date] per its rule. */
    fun isActiveOn(schedule: Schedule, date: LocalDate): Boolean {
        if (date.isBefore(schedule.startDate)) return false
        val daysSinceStart = ChronoUnit.DAYS.between(schedule.startDate, date)
        return when (schedule.type) {
            ScheduleType.DAILY_TIMES -> true
            ScheduleType.WEEKLY -> date.dayOfWeek in schedule.daysOfWeek
            ScheduleType.INTERVAL ->
                schedule.intervalDays > 0 && daysSinceStart % schedule.intervalDays == 0L
            ScheduleType.CYCLIC -> {
                val period = schedule.cycleActiveDays + schedule.cycleRestDays
                period > 0 && (daysSinceStart % period) < schedule.cycleActiveDays
            }
            ScheduleType.AS_NEEDED -> false
        }
    }
}
