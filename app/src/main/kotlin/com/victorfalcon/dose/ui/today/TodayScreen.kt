package com.victorfalcon.dose.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victorfalcon.dose.R
import com.victorfalcon.dose.ui.common.MedIcon
import com.victorfalcon.dose.ui.theme.DoseTakenFill
import com.victorfalcon.dose.ui.theme.OnDoseTaken
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.DoseView
import com.victorfalcon.dose.domain.model.Medication
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TodayScreen(
    onOpenMedication: (Long) -> Unit,
    onAddMedication: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TodayContent(
        state = state,
        onTaken = viewModel::markTaken,
        onUndo = viewModel::undo,
        onOpenMedication = onOpenMedication,
        onLogNow = viewModel::logNow,
        onAddMedication = onAddMedication,
    )
}

@Composable
private fun TodayContent(
    state: TodayUiState,
    onTaken: (Long) -> Unit,
    onUndo: (Long) -> Unit,
    onOpenMedication: (Long) -> Unit,
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
                val highlight = dose.occurrenceId == featuredId
                DoseRow(
                    dose = dose,
                    highlight = highlight,
                    onTaken = { onTaken(dose.occurrenceId) },
                    onUndo = { onUndo(dose.occurrenceId) },
                    onOpen = { onOpenMedication(dose.medicationId) },
                )
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
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun DoseRow(
    dose: DoseView,
    highlight: Boolean,
    onTaken: () -> Unit,
    onUndo: () -> Unit,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        color = if (highlight) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Column 1 — medication image.
            MedIcon(dose.image, modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f), size = null)
            Spacer(Modifier.width(16.dp))
            // Column 2 — name / dosage / time.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    dose.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    dose.dosage ?: " ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DoseTime(dose)
            }
            Spacer(Modifier.width(16.dp))
            // Column 3 — the single take/undo toggle.
            DoseTakeButton(
                taken = dose.status == DoseStatus.TAKEN,
                onToggle = { if (dose.status == DoseStatus.TAKEN) onUndo() else onTaken() },
            )
        }
    }
}

/**
 * The per-dose action, third column: an M3 [MaterialShapes] shape button. Not-taken shows a
 * 7-sided cookie with a circle mark; tapping marks the dose taken and the shape spins a full
 * turn into a SoftBoom while a check grows in from small. Tapping again undoes it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DoseTakeButton(taken: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val spin by animateFloatAsState(
        targetValue = if (taken) 360f else 0f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "doseSpin",
    )
    val fill by animateColorAsState(
        targetValue = if (taken) DoseTakenFill else scheme.surfaceContainerHighest,
        animationSpec = motion.defaultEffectsSpec(),
        label = "doseFill",
    )
    val shape: Shape = (if (taken) MaterialShapes.SoftBoom else MaterialShapes.Cookie7Sided).toShape()
    val actionLabel = stringResource(if (taken) R.string.action_undo else R.string.dose_taken)
    val stateLabel = stringResource(if (taken) R.string.dose_taken else R.string.dose_pending)
    Box(
        modifier = Modifier
            .size(56.dp)
            .graphicsLayer { rotationZ = spin }
            .clip(shape)
            .background(fill)
            .clickable(onClickLabel = actionLabel, onClick = onToggle)
            .semantics { contentDescription = stateLabel },
        contentAlignment = Alignment.Center,
    ) {
        // Cross-fade the mark and grow it in from small to full size.
        AnimatedContent(
            targetState = taken,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) +
                    scaleIn(motion.defaultSpatialSpec(), initialScale = 0.2f)) togetherWith
                    (fadeOut(motion.defaultEffectsSpec()) +
                        scaleOut(motion.defaultSpatialSpec(), targetScale = 0.2f))
            },
            label = "doseMark",
        ) { isTaken ->
            if (isTaken) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = OnDoseTaken, modifier = Modifier.size(26.dp))
            } else {
                Box(Modifier.size(18.dp).border(2.dp, scheme.onSurfaceVariant, CircleShape))
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
