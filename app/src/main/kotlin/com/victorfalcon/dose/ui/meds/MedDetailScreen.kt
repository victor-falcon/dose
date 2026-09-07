@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.victorfalcon.dose.ui.meds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victorfalcon.dose.R
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.DoseView
import com.victorfalcon.dose.ui.common.DoseCellState
import com.victorfalcon.dose.ui.common.DoseStateCell
import com.victorfalcon.dose.ui.common.MedIcon
import com.victorfalcon.dose.ui.common.scheduleSummaryFull
import com.victorfalcon.dose.ui.theme.doseStateColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun MedDetailScreen(
    medicationId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onArchived: () -> Unit,
    viewModel: MedDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(medicationId) { viewModel.bind(medicationId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmArchive by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(medicationId) }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                    }
                    IconButton(onClick = { confirmArchive = true }) {
                        Icon(
                            painterResource(R.drawable.ic_archive),
                            contentDescription = stringResource(R.string.action_archive),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val medication = state.medication
        if (medication == null) {
            Spacer(Modifier.fillMaxSize())
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MedIcon(medication.image, size = 80.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(medication.name, style = MaterialTheme.typography.headlineMedium)
                    medication.dosage?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { onEdit(medicationId) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.action_edit))
                }
                OutlinedButton(
                    onClick = { confirmArchive = true },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_archive),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.action_archive))
                }
            }

            Spacer(Modifier.height(18.dp))
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DetailCard(stringResource(R.string.detail_schedule)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_schedule),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            scheduleSummaryFull(state.schedule).orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    state.schedule?.startDate?.let {
                        Text(
                            stringResource(R.string.detail_since, it.format(dateFormatter)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                medication.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    DetailCard(stringResource(R.string.detail_notes)) {
                        Text(notes, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                if (state.grid.isNotEmpty()) {
                    AdherenceCard(state)
                }
            }

            if (state.recent.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                SectionHeader(stringResource(R.string.detail_recent))
                Column(Modifier.padding(horizontal = 20.dp)) {
                    state.recent.forEach { RecentRow(it) }
                }
            }
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
private fun DetailCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.1.sp,
            )
            content()
        }
    }
}

/**
 * Adherence as hour × day: one row per scheduled hour, one column per day. This is the shape of
 * the data — a dose is a day *and* an hour — and it's the only view that shows "the 10 pm one is
 * the one I keep missing".
 */
@Composable
private fun AdherenceCard(state: MedDetailUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.detail_adherence).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.1.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.detail_adherence_percent, state.stats.percent),
                    style = MaterialTheme.typography.titleLarge,
                    color = doseStateColors.taken,
                )
            }
            Text(
                buildString {
                    append(
                        stringResource(
                            R.string.detail_adherence_detail,
                            state.stats.taken,
                            state.stats.resolved,
                        ),
                    )
                    if (state.stats.pending > 0) {
                        append(" · ")
                        append(stringResource(R.string.detail_adherence_pending, state.stats.pending))
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            state.grid.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        row.time.format(timeFormatter),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.width(58.dp),
                    )
                    row.cells.forEach { cell ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            DoseStateCell(cell ?: DoseCellState.NONE, 16.dp)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(58.dp))
                Text(
                    stringResource(R.string.detail_axis_start),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.detail_axis_end),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LegendItem(DoseCellState.TAKEN, stringResource(R.string.legend_taken))
                LegendItem(DoseCellState.MISSED, stringResource(R.string.legend_missed))
                LegendItem(DoseCellState.SKIPPED, stringResource(R.string.legend_skipped))
                LegendItem(DoseCellState.PENDING, stringResource(R.string.legend_pending))
            }
        }
    }
}

@Composable
private fun LegendItem(state: DoseCellState, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DoseStateCell(state, 14.dp)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.1.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun RecentRow(dose: DoseView) {
    Row(
        modifier = Modifier.fillMaxWidth().height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            relativeDay(dose.scheduledAt.toLocalDate()) + " · " +
                dose.scheduledAt.toLocalTime().format(timeFormatter),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            dose.statusLabel(),
            style = MaterialTheme.typography.labelLarge,
            color = dose.statusColor(),
        )
    }
}

@Composable
private fun relativeDay(date: LocalDate): String {
    val today = remember { LocalDate.now() }
    return when (date) {
        today -> stringResource(R.string.date_today)
        today.minusDays(1) -> stringResource(R.string.date_yesterday)
        else -> date.format(dateFormatter)
    }
}

@Composable
private fun DoseView.statusLabel(): String = when (status) {
    DoseStatus.TAKEN -> takenAt?.let {
        stringResource(R.string.dose_taken_at, it.toLocalTime().format(timeFormatter))
    } ?: stringResource(R.string.dose_taken)
    DoseStatus.PENDING -> stringResource(R.string.dose_pending)
    DoseStatus.SKIPPED -> stringResource(R.string.dose_skipped)
    DoseStatus.MISSED -> stringResource(R.string.dose_missed)
}

@Composable
private fun DoseView.statusColor(): Color = when (status) {
    DoseStatus.TAKEN -> doseStateColors.taken
    DoseStatus.MISSED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
