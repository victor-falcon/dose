package com.victorfalcon.dose.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victorfalcon.dose.R
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.DoseView
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AdherenceHeader(state.adherence7.percent, state.adherence30.percent)
        MonthHeader(state.month, viewModel::showPreviousMonth, viewModel::showNextMonth)
        WeekdayHeader()
        CalendarGrid(
            month = state.month,
            marks = state.marks,
            selectedDate = state.selectedDate,
            today = LocalDate.now(),
            onSelect = viewModel::selectDate,
        )
        HorizontalDivider()
        SelectedDayDoses(state.selectedDoses)
    }
}

@Composable
private fun AdherenceHeader(percent7: Int?, percent30: Int?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.history_adherence),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        AdherenceStat(stringResource(R.string.history_range_7d), percent7)
        AdherenceStat(stringResource(R.string.history_range_30d), percent30)
    }
}

@Composable
private fun AdherenceStat(label: String, percent: Int?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = percent?.let { "$it%" } ?: "—",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    val title = remember(month) {
        val formatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())
        month.atDay(1).format(formatter).replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.action_prev_month))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.action_next_month))
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(Modifier.fillMaxWidth()) {
        weekdays().forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CalendarGrid(
    month: YearMonth,
    marks: Map<LocalDate, DayMark>,
    selectedDate: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val leadingBlanks = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val cells = buildList<LocalDate?> {
        repeat(leadingBlanks) { add(null) }
        for (day in 1..month.lengthOfMonth()) add(month.atDay(day))
        while (size % 7 != 0) add(null)
    }
    Column {
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        DayCell(
                            date = date,
                            mark = marks[date] ?: DayMark.NONE,
                            selected = date == selectedDate,
                            isToday = date == today,
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    mark: DayMark,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val numberModifier = Modifier
            .size(32.dp)
            .then(
                if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                else if (isToday) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                else Modifier,
            )
        Box(numberModifier, contentAlignment = Alignment.Center) {
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium)
        }
        val dot = mark.dotColor()
        if (dot != null) {
            Box(Modifier.padding(top = 2.dp).size(6.dp).background(dot, CircleShape))
        } else {
            Spacer(Modifier.padding(top = 2.dp).size(6.dp))
        }
    }
}

@Composable
private fun SelectedDayDoses(doses: List<DoseView>) {
    if (doses.isEmpty()) {
        Text(
            stringResource(R.string.history_day_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        doses.forEach { dose ->
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dose.scheduledAt.format(timeFormatter), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.size(12.dp))
                Text(dose.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    dose.status.label(),
                    style = MaterialTheme.typography.labelLarge,
                    color = dose.status.labelColor(),
                )
            }
        }
    }
}

private fun weekdays(): List<DayOfWeek> {
    val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    return (0L until 7L).map { first.plus(it) }
}

@Composable
private fun DayMark.dotColor(): Color? = when (this) {
    DayMark.NONE -> null
    DayMark.SCHEDULED -> MaterialTheme.colorScheme.outlineVariant
    DayMark.ALL_TAKEN -> MaterialTheme.colorScheme.primary
    DayMark.PARTIAL -> MaterialTheme.colorScheme.tertiary
    DayMark.MISSED -> MaterialTheme.colorScheme.error
}

@Composable
private fun DoseStatus.label(): String = when (this) {
    DoseStatus.PENDING -> stringResource(R.string.dose_pending)
    DoseStatus.TAKEN -> stringResource(R.string.dose_taken)
    DoseStatus.SKIPPED -> stringResource(R.string.dose_skip)
    DoseStatus.MISSED -> stringResource(R.string.dose_missed)
}

@Composable
private fun DoseStatus.labelColor(): Color = when (this) {
    DoseStatus.TAKEN -> MaterialTheme.colorScheme.primary
    DoseStatus.MISSED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
