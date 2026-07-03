package com.victorfalcon.dose.ui.meds

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victorfalcon.dose.R
import com.victorfalcon.dose.ui.common.MedIcon
import com.victorfalcon.dose.ui.theme.DoseTakenFill
import com.victorfalcon.dose.ui.theme.OnDoseTaken
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
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        items(medications, key = { it.medication.id }) { row ->
            MedicationRow(row) { onOpenMedication(row.medication.id) }
        }
    }
}

@Composable
private fun MedicationRow(row: MedRow, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                MedIcon(row.medication.image, size = 56.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        row.medication.name,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        row.medication.dosage ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AdherenceHistory(row.history)
        }
    }
}

@Composable
private fun AdherenceHistory(history: List<DayAdherence>) {
    // history is oldest -> newest, always HISTORY_DAYS long, so the last cell is today.
    val today = remember { LocalDate.now() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        history.forEachIndexed { index, adherence ->
            DayCell(
                date = today.minusDays((history.lastIndex - index).toLong()),
                adherence = adherence,
                isToday = index == history.lastIndex,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// Each day's outcome as an M3 MaterialShapes shape: SoftBoom (taken), 4-sided cookie (not
// taken — skipped or missed), 7-sided cookie (not scheduled). Colour + a check/✕ reinforce it.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DayCell(date: LocalDate, adherence: DayAdherence, isToday: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = when (adherence) {
        DayAdherence.TAKEN -> MaterialShapes.SoftBoom
        DayAdherence.SKIPPED, DayAdherence.MISSED -> MaterialShapes.Cookie4Sided
        else -> MaterialShapes.Cookie7Sided // NOT_SCHEDULED, UNKNOWN
    }.toShape()
    val fill = when (adherence) {
        DayAdherence.TAKEN -> DoseTakenFill
        DayAdherence.SKIPPED, DayAdherence.MISSED -> scheme.error
        else -> scheme.surfaceContainerHighest // NOT_SCHEDULED, UNKNOWN
    }
    val content = when (adherence) {
        DayAdherence.TAKEN -> OnDoseTaken
        DayAdherence.SKIPPED, DayAdherence.MISSED -> scheme.onError
        else -> scheme.onSurfaceVariant
    }
    val icon = when (adherence) {
        DayAdherence.TAKEN -> Icons.Filled.Check
        DayAdherence.SKIPPED, DayAdherence.MISSED -> Icons.Filled.Close
        else -> null
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) scheme.onSurface else scheme.onSurfaceVariant,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (isToday) scheme.onSurface else scheme.onSurfaceVariant,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )
        Box(
            modifier = Modifier
                .size(34.dp)
                .let { m ->
                    // UNKNOWN (before the schedule started) reads as a faint outline, no fill.
                    if (adherence == DayAdherence.UNKNOWN) m.border(1.dp, scheme.outlineVariant, shape)
                    else m.clip(shape).background(fill)
                },
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            }
        }
    }
}

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
        Button(onClick = onAddMedication) { Text(stringResource(R.string.action_add_medication)) }
    }
}
