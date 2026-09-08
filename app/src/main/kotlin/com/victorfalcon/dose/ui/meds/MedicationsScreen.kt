@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.victorfalcon.dose.ui.meds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victorfalcon.dose.R
import com.victorfalcon.dose.ui.common.DoseCellState
import com.victorfalcon.dose.ui.common.DoseFractionCell
import com.victorfalcon.dose.ui.common.DoseStateCell
import com.victorfalcon.dose.ui.common.MedIcon
import com.victorfalcon.dose.ui.common.entryFadeSlide
import com.victorfalcon.dose.ui.common.scheduleSummaryShort
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MedicationsScreen(
    onOpenMedication: (Long) -> Unit,
    onAddMedication: () -> Unit,
    viewModel: MedicationsViewModel = hiltViewModel(),
) {
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    if (medications.isEmpty()) {
        EmptyState(onAddMedication)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
    ) {
        itemsIndexed(medications, key = { _, row -> row.medication.id }) { index, row ->
            MedicationCard(row, Modifier.animateItem().entryFadeSlide(index)) {
                onOpenMedication(row.medication.id)
            }
        }
    }
}

@Composable
private fun MedicationCard(row: MedRow, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MedIcon(row.medication.image, size = 56.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        row.medication.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = listOfNotNull(
                        row.medication.dosage?.takeIf { it.isNotBlank() },
                        scheduleSummaryShort(row.schedule),
                    ).joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (row.dosesThisWeek > 0) {
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            stringResource(R.string.med_week_count, row.takenThisWeek, row.dosesThisWeek),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.med_week_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (row.dosesThisWeek > 0) {
                Spacer(Modifier.height(14.dp))
                WeekStrip(row)
            }
        }
    }
}

/**
 * The week, one column per day at one fixed cell size. A day with a single dose shows that dose's
 * shape; a day with several shows one shape filled to the fraction taken, with the count spelled
 * out underneath — so "2 of 3 taken" reads without shrinking the cells, and every card ends up the
 * same height whatever the schedule.
 */
@Composable
private fun WeekStrip(row: MedRow) {
    val today = remember { LocalDate.now() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        row.week.forEach { day ->
            val isToday = day.date == today
            val taken = day.cells.count { it == DoseCellState.TAKEN }
            val failed = day.cells.any { it == DoseCellState.MISSED || it == DoseCellState.SKIPPED }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                )
                when (day.cells.size) {
                    0 -> DoseStateCell(DoseCellState.NONE, CellSize)
                    1 -> DoseStateCell(day.cells.first(), CellSize)
                    else -> DoseFractionCell(taken, day.cells.size, failed, CellSize)
                }
                if (row.dosesPerDay > 1) {
                    Text(
                        if (day.cells.isEmpty()) "·"
                        else stringResource(R.string.med_week_count, taken, day.cells.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (failed) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** One size for every medication, so cells stay legible and cards stay comparable. */
private val CellSize = 30.dp

@Composable
private fun EmptyState(onAddMedication: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
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
        Button(onClick = onAddMedication, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.action_add_medication))
        }
    }
}
