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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victorfalcon.dose.R
import com.victorfalcon.dose.ui.common.MedIcon

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                MedIcon(row.medication.image, size = 48.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        row.medication.name,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    row.medication.dosage?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            AdherenceHistory(row.history)
        }
    }
}

@Composable
private fun AdherenceHistory(history: List<DayAdherence>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        history.forEach { DayDot(it) }
    }
}

@Composable
private fun DayDot(state: DayAdherence) {
    val scheme = MaterialTheme.colorScheme
    val fill: Color
    val content: Color
    val icon: ImageVector?
    when (state) {
        DayAdherence.TAKEN -> { fill = scheme.primary; content = scheme.onPrimary; icon = Icons.Filled.Check }
        DayAdherence.SKIPPED -> { fill = scheme.secondaryContainer; content = scheme.onSecondaryContainer; icon = Icons.Filled.Close }
        DayAdherence.MISSED -> { fill = scheme.errorContainer; content = scheme.onErrorContainer; icon = Icons.Filled.Close }
        DayAdherence.NOT_SCHEDULED -> { fill = scheme.surfaceContainerHighest; content = Color.Unspecified; icon = null }
        DayAdherence.UNKNOWN -> { fill = Color.Transparent; content = Color.Unspecified; icon = null }
    }
    Box(
        modifier = Modifier
            .size(20.dp)
            .let { m ->
                if (state == DayAdherence.UNKNOWN) m.border(1.dp, scheme.outlineVariant, CircleShape)
                else m.background(fill, CircleShape)
            },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(12.dp))
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
