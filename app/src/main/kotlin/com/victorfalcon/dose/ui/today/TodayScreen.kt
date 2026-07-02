package com.victorfalcon.dose.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    // The next pending dose, chronologically — the one to take right now.
    val featuredId = state.groups
        .flatMap { it.doses }
        .firstOrNull { it.status == DoseStatus.PENDING }
        ?.occurrenceId
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        state.groups.forEach { group ->
            items(group.doses, key = { it.occurrenceId }) { dose ->
                if (dose.occurrenceId == featuredId) DoseRow(dose, true, onTaken, onSkip, onUndo)
                else DoseRow(dose, false, onTaken, onSkip, onUndo)
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

/** Scheduled time shown as a row's third line. Keeps the old header's text format. */
@Composable
private fun DoseTime(dose: DoseView) {
    Text(
        dose.scheduledAt.toLocalTime().format(timeFormatter),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun DoseRow(
    dose: DoseView,
    highlight: Boolean,
    onTaken: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onUndo: (Long) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (highlight) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(16.dp).height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MedIcon(dose.image, modifier = Modifier.fillMaxHeight().aspectRatio(1f), size = null)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    dose.name,
                    style = if (dose.status == DoseStatus.PENDING) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    dose.dosage ?: " ",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DoseTime(dose)
            }

            if (dose.status == DoseStatus.PENDING) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    FilledTonalButton(
                        onClick = { onSkip(dose.occurrenceId) },
                        modifier = Modifier.widthIn(min = 48.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(
                            topStart = CornerSize(8.dp), bottomStart = CornerSize(8.dp),
                            topEnd = CornerSize(8.dp), bottomEnd = CornerSize(8.dp),
                        ),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.dose_skip), modifier = Modifier.size(18.dp))
                    }
                    Button(
                        onClick = { onTaken(dose.occurrenceId) },
                        shape = RoundedCornerShape(
                            topStart = CornerSize(50), bottomStart = CornerSize(50),
                            topEnd = CornerSize(50), bottomEnd = CornerSize(50),
                        ),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.dose_taken))
                    }
                }
            } else {
                TextButton(onClick = { onUndo(dose.occurrenceId) }) { Text(stringResource(R.string.action_undo)) }
            }
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
