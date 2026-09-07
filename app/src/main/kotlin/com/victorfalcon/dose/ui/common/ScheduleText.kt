package com.victorfalcon.dose.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.victorfalcon.dose.R
import com.victorfalcon.dose.domain.model.Schedule
import com.victorfalcon.dose.domain.model.ScheduleType
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * One-line pauta for a list row: short enough to sit next to the name without wrapping. A
 * medication with several daily doses says how many rather than listing every hour — the hours
 * belong on the detail screen.
 */
@Composable
fun scheduleSummaryShort(schedule: Schedule?): String? {
    if (schedule == null) return null
    return when (schedule.type) {
        ScheduleType.DAILY_TIMES -> when (schedule.times.size) {
            0 -> stringResource(R.string.sched_daily)
            1 -> stringResource(R.string.sched_daily_at, schedule.times.first().format(timeFormatter))
            else -> stringResource(R.string.sched_times_per_day, schedule.times.size)
        }
        ScheduleType.WEEKLY -> {
            val days = schedule.daysOfWeek.sortedBy { it.value }
                .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
            val time = schedule.times.firstOrNull()?.format(timeFormatter)
            listOfNotNull(days.takeIf { it.isNotBlank() }, time).joinToString(", ")
        }
        ScheduleType.INTERVAL -> stringResource(R.string.sched_every_n_days, schedule.intervalDays)
        ScheduleType.CYCLIC -> stringResource(R.string.sched_cycle, schedule.cycleActiveDays, schedule.cycleRestDays)
        ScheduleType.AS_NEEDED -> stringResource(R.string.schedule_as_needed)
    }
}

/** The full pauta for the detail screen: the rule plus every scheduled hour. */
@Composable
fun scheduleSummaryFull(schedule: Schedule?): String? {
    if (schedule == null) return null
    val times = schedule.times.joinToString(", ") { it.format(timeFormatter) }
    val rule = when (schedule.type) {
        ScheduleType.DAILY_TIMES -> stringResource(R.string.sched_daily)
        ScheduleType.WEEKLY -> schedule.daysOfWeek.sortedBy { it.value }
            .joinToString(", ") { it.getDisplayName(TextStyle.FULL, Locale.getDefault()) }
        ScheduleType.INTERVAL -> stringResource(R.string.sched_every_n_days, schedule.intervalDays)
        ScheduleType.CYCLIC -> stringResource(R.string.sched_cycle, schedule.cycleActiveDays, schedule.cycleRestDays)
        ScheduleType.AS_NEEDED -> return stringResource(R.string.schedule_as_needed)
    }
    return listOf(rule, times).filter { it.isNotBlank() }.joinToString(" · ")
}

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
