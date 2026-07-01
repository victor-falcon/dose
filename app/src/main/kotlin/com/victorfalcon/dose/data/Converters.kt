package com.victorfalcon.dose.data

import androidx.room.TypeConverter
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Room converters for the java.time / enum / collection fields. LocalDateTime is
 * stored as UTC epoch-seconds purely so range queries order and compare as longs;
 * it carries no zone semantics. Room also applies these to query parameters.
 */
class Converters {
    @TypeConverter fun localDateToEpochDay(d: LocalDate?): Long? = d?.toEpochDay()
    @TypeConverter fun epochDayToLocalDate(v: Long?): LocalDate? = v?.let(LocalDate::ofEpochDay)

    @TypeConverter fun localDateTimeToEpoch(dt: LocalDateTime?): Long? = dt?.toEpochSecond(ZoneOffset.UTC)
    @TypeConverter fun epochToLocalDateTime(v: Long?): LocalDateTime? =
        v?.let { LocalDateTime.ofEpochSecond(it, 0, ZoneOffset.UTC) }

    @TypeConverter fun scheduleTypeToString(t: ScheduleType): String = t.name
    @TypeConverter fun stringToScheduleType(s: String): ScheduleType = ScheduleType.valueOf(s)

    @TypeConverter fun doseStatusToString(t: DoseStatus): String = t.name
    @TypeConverter fun stringToDoseStatus(s: String): DoseStatus = DoseStatus.valueOf(s)

    @TypeConverter fun timesToString(times: List<LocalTime>): String =
        times.joinToString(",") { it.toSecondOfDay().toString() }

    @TypeConverter fun stringToTimes(s: String): List<LocalTime> =
        if (s.isEmpty()) emptyList() else s.split(",").map { LocalTime.ofSecondOfDay(it.toLong()) }

    @TypeConverter fun daysToString(days: Set<DayOfWeek>): String =
        days.joinToString(",") { it.value.toString() }

    @TypeConverter fun stringToDays(s: String): Set<DayOfWeek> =
        if (s.isEmpty()) emptySet() else s.split(",").map { DayOfWeek.of(it.toInt()) }.toSet()
}
