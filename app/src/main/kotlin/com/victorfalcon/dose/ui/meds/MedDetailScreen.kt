package com.victorfalcon.dose.ui.meds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victorfalcon.dose.R
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.DoseView
import com.victorfalcon.dose.domain.model.Schedule
import com.victorfalcon.dose.domain.model.ScheduleType
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MedDetailScreen(
    medicationId: Long,
    onEdit: (Long) -> Unit,
    onArchived: () -> Unit,
    viewModel: MedDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(medicationId) { viewModel.bind(medicationId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val medication = state.medication ?: return

    var confirmArchive by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(medication.name, style = MaterialTheme.typography.headlineSmall)
        medication.dosage?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        medication.notes?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.schedule?.let { Text(scheduleSummary(it), style = MaterialTheme.typography.bodyMedium) }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onEdit(medicationId) }) { Text(stringResource(R.string.action_edit)) }
            OutlinedButton(onClick = { confirmArchive = true }) { Text(stringResource(R.string.action_archive)) }
        }

        HorizontalDivider()
        Text(stringResource(R.string.nav_history), style = MaterialTheme.typography.titleMedium)
        if (state.history.isEmpty()) {
            Text(
                stringResource(R.string.history_day_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.history.forEach { HistoryRow(it) }
        }
    }

    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            confirmButton = {
                TextButton(onClick = {
                    confirmArchive = false
                    viewModel.archive(onArchived)
                }) { Text(stringResource(R.string.action_archive)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmArchive = false }) { Text(stringResource(R.string.action_cancel)) }
            },
            text = { Text(stringResource(R.string.archive_confirm_message)) },
        )
    }
}

@Composable
private fun HistoryRow(dose: DoseView) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(dose.scheduledAt.format(dateTimeFormatter), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(dose.status.label(), style = MaterialTheme.typography.labelLarge, color = dose.status.labelColor())
    }
}

@Composable
private fun scheduleSummary(schedule: Schedule): String {
    val times = schedule.times.joinToString(", ") { it.format(timeFormatter) }
    return when (schedule.type) {
        ScheduleType.DAILY_TIMES -> "${stringResource(R.string.schedule_daily_times)} · $times"
        ScheduleType.WEEKLY -> {
            val days = schedule.daysOfWeek.sortedBy { it.value }
                .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
            "${stringResource(R.string.schedule_weekly)} ($days) · $times"
        }
        ScheduleType.INTERVAL -> "${stringResource(R.string.schedule_interval)} (${schedule.intervalDays}) · $times"
        ScheduleType.CYCLIC ->
            "${stringResource(R.string.schedule_cyclic)} ${schedule.cycleActiveDays}/${schedule.cycleRestDays} · $times"
        ScheduleType.AS_NEEDED -> stringResource(R.string.schedule_as_needed)
    }
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
private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
