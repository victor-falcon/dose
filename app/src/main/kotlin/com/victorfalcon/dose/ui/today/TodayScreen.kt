package com.victorfalcon.dose.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victorfalcon.dose.R
import com.victorfalcon.dose.ui.common.MedIcon
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.DoseView
import com.victorfalcon.dose.domain.model.Medication
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TodayScreen(
    onAddMedication: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TodayContent(
        state = state,
        onTaken = viewModel::markTaken,
        onSkip = viewModel::skip,
        onUndo = viewModel::undo,
        onLogNow = viewModel::logNow,
        onAddMedication = onAddMedication,
    )
}

@Composable
private fun TodayContent(
    state: TodayUiState,
    onTaken: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onUndo: (Long) -> Unit,
    onLogNow: (Long) -> Unit,
    onAddMedication: () -> Unit,
) {
    if (state.isEmpty) {
        EmptyState(onAddMedication)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        state.groups.forEach { group ->
            item(key = "time-${group.time}") { SectionHeader(group.time.format(timeFormatter)) }
            items(group.doses, key = { it.occurrenceId }) { dose ->
                DoseRow(dose, onTaken, onSkip, onUndo)
            }
        }
        if (state.asNeeded.isNotEmpty()) {
            item(key = "prn-header") { SectionHeader(stringResource(R.string.today_as_needed)) }
            items(state.asNeeded, key = { "prn-${it.id}" }) { med ->
                AsNeededRow(med, onLogNow)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun DoseRow(
    dose: DoseView,
    onTaken: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onUndo: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MedIcon(dose.image)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(dose.name, style = MaterialTheme.typography.titleMedium)
            val subtitle = listOfNotNull(dose.dosage, dose.status.label()).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (dose.status == DoseStatus.PENDING) {
            TextButton(onClick = { onSkip(dose.occurrenceId) }) { Text(stringResource(R.string.dose_skip)) }
            FilledTonalButton(onClick = { onTaken(dose.occurrenceId) }) { Text(stringResource(R.string.dose_taken)) }
        } else {
            TextButton(onClick = { onUndo(dose.occurrenceId) }) { Text(stringResource(R.string.action_undo)) }
        }
    }
}

@Composable
private fun AsNeededRow(medication: Medication, onLogNow: (Long) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MedIcon(medication.image)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(medication.name, style = MaterialTheme.typography.titleMedium)
            medication.dosage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FilledTonalButton(onClick = { onLogNow(medication.id) }) {
            Text(stringResource(R.string.action_log_now))
        }
    }
}

@Composable
private fun EmptyState(onAddMedication: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.today_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.today_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        Button(onClick = onAddMedication) { Text(stringResource(R.string.action_add_medication)) }
    }
}

@Composable
private fun DoseStatus.label(): String? = when (this) {
    DoseStatus.PENDING -> null
    DoseStatus.TAKEN -> stringResource(R.string.dose_taken)
    DoseStatus.SKIPPED -> stringResource(R.string.dose_skip)
    DoseStatus.MISSED -> stringResource(R.string.dose_missed)
}

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
