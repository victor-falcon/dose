@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.victorfalcon.dose.ui.dose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victorfalcon.dose.R
import com.victorfalcon.dose.domain.model.DoseView
import com.victorfalcon.dose.ui.common.DoseShapes
import com.victorfalcon.dose.ui.common.MedIcon
import com.victorfalcon.dose.ui.theme.doseStateColors
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * "Tomar dosis": the focused screen a notification or a widget dose opens into. One dose, one
 * huge button, no navigation bar — and when the hour holds several doses it becomes a queue with
 * a bulk "take all N".
 */
@Composable
fun DoseFocusScreen(
    occurrenceId: Long,
    onClose: () -> Unit,
    onOpenMedication: (Long) -> Unit,
    viewModel: DoseFocusViewModel = hiltViewModel(),
) {
    LaunchedEffect(occurrenceId) { viewModel.bind(occurrenceId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (state.time != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                state.time!!.format(timeFormatter),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (state.isQueue) {
                                Text(
                                    stringResource(R.string.focus_position, state.position, state.total),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                    }
                },
                actions = {
                    state.current?.let { dose ->
                        TextButton(onClick = { onOpenMedication(dose.medicationId) }) {
                            Text(stringResource(R.string.focus_open_medication))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            if (state.isQueue) {
                QueueProgress(total = state.total, resolved = state.resolved)
                Spacer(Modifier.height(8.dp))
            }
            val current = state.current
            if (current == null) {
                HourFinished(state, onClose)
                return@Column
            }
            DoseBody(
                dose = current,
                notes = state.notes,
                modifier = Modifier.weight(1f),
            )
            Actions(
                state = state,
                dose = current,
                onTake = { viewModel.take(current.occurrenceId) },
                onTakeAll = viewModel::takeAll,
                onSnooze = { viewModel.snooze(current.occurrenceId) },
                onSkip = { viewModel.skip(current.occurrenceId) },
            )
        }
    }
}

/** One segment per dose of the hour, so the queue's length is visible up front. */
@Composable
private fun QueueProgress(total: Int, resolved: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(total) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .background(
                        color = if (index < resolved.coerceAtLeast(1)) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(3.dp),
                    ),
            )
        }
    }
}

@Composable
private fun DoseBody(dose: DoseView, notes: String?, modifier: Modifier = Modifier) {
    val motion = MaterialTheme.motionScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Walking to the next dose of the hour swaps the pill and the name together.
        AnimatedContent(
            targetState = dose,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) +
                    scaleIn(motion.defaultSpatialSpec(), initialScale = 0.9f)) togetherWith
                    (fadeOut(motion.fastEffectsSpec()) +
                        scaleOut(motion.defaultSpatialSpec(), targetScale = 0.9f))
            },
            label = "focusDose",
        ) { current ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MedIcon(
                    current.image,
                    size = 208.dp,
                    shape = DoseShapes.pending,
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    current.name,
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                )
                current.dosage?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                notes?.let {
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Actions(
    state: DoseFocusUiState,
    dose: DoseView,
    onTake: () -> Unit,
    onTakeAll: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
) {
    // 88 dp: the design's own step between the medium and extra-large tokens. shapesFor()
    // still gives it the expressive press morph for that size.
    val takeHeight = 88.dp
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // The app's biggest button: extra-large expressive container, and its shape squashes
        // under the finger via shapesFor().
        Button(
            onClick = onTake,
            shapes = ButtonDefaults.shapesFor(takeHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(takeHeight),
            modifier = Modifier
                .fillMaxWidth()
                .height(takeHeight),
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                stringResource(R.string.dose_taken),
                style = ButtonDefaults.textStyleFor(takeHeight),
            )
        }
        if (state.remaining.size > 1) {
            FilledTonalButton(
                onClick = onTakeAll,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(stringResource(R.string.focus_take_all, state.remaining.size))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onSnooze,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Text(stringResource(R.string.dose_snooze_minutes, state.snoozeMinutes))
            }
            OutlinedButton(
                onClick = onSkip,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Text(stringResource(R.string.dose_skip))
            }
        }
        DayDots(taken = state.dayTaken, total = state.dayTotal, current = dose)
    }
}

/** The day at a glance under the actions: how much is done, how much is left. */
@Composable
private fun DayDots(taken: Int, total: Int, current: DoseView) {
    if (total == 0) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        repeat(total.coerceAtMost(12)) { index ->
            Box(
                Modifier
                    .size(14.dp)
                    .background(
                        color = when {
                            index < taken -> doseStateColors.taken
                            index == taken -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        shape = RoundedCornerShape(50),
                    ),
            )
        }
    }
}

@Composable
private fun HourFinished(state: DoseFocusUiState, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = DoseShapes.taken,
            color = doseStateColors.taken,
            modifier = Modifier.size(120.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = doseStateColors.onTaken,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.focus_hour_done),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.today_all_done_detail, state.dayTaken, state.dayTotal),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 28.dp),
        )
        Button(onClick = onClose, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.action_close))
        }
    }
}

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
