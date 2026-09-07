@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.victorfalcon.dose.ui.today

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victorfalcon.dose.R
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.DoseView
import com.victorfalcon.dose.domain.model.Medication
import com.victorfalcon.dose.ui.common.DoseShapes
import com.victorfalcon.dose.ui.common.DoseTakeButton
import com.victorfalcon.dose.ui.common.MedIcon
import com.victorfalcon.dose.ui.theme.doseStateColors
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay

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
        onSkip = viewModel::skip,
        onSnooze = viewModel::snooze,
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
    onSkip: (Long) -> Unit,
    onSnooze: (Long) -> Unit,
    onOpenMedication: (Long) -> Unit,
    onLogNow: (Long) -> Unit,
    onAddMedication: () -> Unit,
) {
    if (state.isEmpty) {
        EmptyState(onAddMedication)
        return
    }
    // Hold a just-marked hero on screen for a beat: the shape morph and the turn are the
    // feedback that the dose landed, and swapping in the next dose immediately eats them.
    var shownHero by remember { mutableStateOf(state.hero) }
    LaunchedEffect(state.hero?.occurrenceId) {
        if (state.hero?.occurrenceId != shownHero?.occurrenceId) {
            if (shownHero != null) delay(HERO_LINGER_MS)
            shownHero = state.hero
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "exact-alarm") { ExactAlarmNag() }
        if (state.totalCount > 0) {
            item(key = "progress") { DayProgress(state) }
            item(key = "hero") {
                val motion = MaterialTheme.motionScheme
                // Swapping the dose due now is the screen's main event: the outgoing card
                // shrinks away while the next one grows in.
                AnimatedContent(
                    targetState = shownHero,
                    transitionSpec = {
                        (fadeIn(motion.defaultEffectsSpec()) +
                            scaleIn(motion.defaultSpatialSpec(), initialScale = 0.92f)) togetherWith
                            (fadeOut(motion.fastEffectsSpec()) +
                                scaleOut(motion.defaultSpatialSpec(), targetScale = 0.92f))
                    },
                    label = "hero",
                ) { hero ->
                    if (hero == null) {
                        DayClearCard(state.takenCount, state.totalCount)
                    } else {
                        HeroDoseCard(
                            dose = hero,
                            snoozeMinutes = state.snoozeMinutes,
                            onTaken = { onTaken(hero.occurrenceId) },
                            onSnooze = { onSnooze(hero.occurrenceId) },
                            onSkip = { onSkip(hero.occurrenceId) },
                            onOpen = { onOpenMedication(hero.medicationId) },
                        )
                    }
                }
            }
        }
        if (state.rest.isNotEmpty()) {
            item(key = "rest-header") { SectionHeader(stringResource(R.string.today_rest_of_day)) }
            items(state.rest, key = { it.time.toString() }) { group ->
                HourRow(
                    group = group,
                    onTaken = onTaken,
                    onUndo = onUndo,
                    onOpenMedication = onOpenMedication,
                    modifier = Modifier.animateItem(),
                )
            }
        }
        if (state.asNeeded.isNotEmpty()) {
            item(key = "prn-header") { SectionHeader(stringResource(R.string.today_as_needed)) }
            item(key = "prn") { AsNeededRow(state.asNeeded, onLogNow) }
        }
    }
}

@Composable
private fun DayProgress(state: TodayUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The wavy indicator animates its own amplitude as it fills — the day's heartbeat.
        LinearWavyProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.today_progress, state.takenCount, state.totalCount),
            style = MaterialTheme.typography.labelLarge,
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
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun HeroDoseCard(
    dose: DoseView,
    snoozeMinutes: Int,
    onTaken: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.today_now).uppercase() + " · " +
                        dose.scheduledAt.toLocalTime().format(timeFormatter),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.1.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    relativeLabel(dose.scheduledAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MedIcon(dose.image, size = 64.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        dose.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    dose.dosage?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                DoseTakeButton(
                    taken = dose.status == DoseStatus.TAKEN,
                    onToggle = { onTaken() },
                    size = 76.dp,
                    emphasized = true,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // shapes(): M3 Expressive squashes the container while the finger is down.
                FilledTonalButton(onClick = onSnooze, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.dose_snooze_minutes, snoozeMinutes))
                }
                OutlinedButton(onClick = onSkip, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.dose_skip))
                }
            }
        }
    }
}

/**
 * Without the exact-alarm grant reminders still fire, just whenever the system feels like it —
 * useless for a dose due at 8:00. USE_EXACT_ALARM would make it automatic but is reserved for
 * alarm-clock and calendar apps, so ask instead. Only shown while the grant is missing.
 */
@Composable
private fun ExactAlarmNag() {
    val context = LocalContext.current
    val alarmManager = remember { context.getSystemService(AlarmManager::class.java) }
    var canSchedule by remember { mutableStateOf(alarmManager.canScheduleExactAlarms()) }
    // The grant happens in a system screen, so nothing here recomposes on the way back.
    LifecycleResumeEffect(Unit) {
        canSchedule = alarmManager.canScheduleExactAlarms()
        onPauseOrDispose {}
    }
    if (canSchedule) return

    Card(
        onClick = {
            // ponytail: some OEM builds don't resolve this screen; swallow rather than crash.
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_schedule),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.today_exact_alarm_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    stringResource(R.string.today_exact_alarm_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

/**
 * Nothing left to take. Only a day where every dose was actually taken earns the green
 * celebration — a day that merely ran out of pending doses (some missed or skipped) gets a
 * neutral card, because "all taken · 0 of 1" is a lie.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DayClearCard(taken: Int, total: Int) {
    val allTaken = total > 0 && taken == total
    val scheme = MaterialTheme.colorScheme
    val stateColors = doseStateColors
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (allTaken) stateColors.takenContainer else scheme.surfaceContainer,
        ),
    ) {
        val contentColor = if (allTaken) stateColors.onTakenContainer else scheme.onSurface
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = if (allTaken) DoseShapes.taken else DoseShapes.pending,
                color = if (allTaken) stateColors.taken else scheme.surfaceContainerHighest,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = if (allTaken) stateColors.onTaken else scheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Column {
                Text(
                    stringResource(
                        if (allTaken) R.string.today_all_done else R.string.today_nothing_pending,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                )
                Text(
                    stringResource(
                        if (allTaken) R.string.today_all_done_detail else R.string.today_nothing_pending_detail,
                        taken,
                        total,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                )
            }
        }
    }
}

/**
 * One scheduled time. A time can hold several doses (morning pills taken together), so the row is
 * a card with one line per dose rather than a single check — that's the whole point of the
 * per-dose model.
 */
@Composable
private fun HourRow(
    group: DoseGroup,
    onTaken: (Long) -> Unit,
    onUndo: (Long) -> Unit,
    onOpenMedication: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            group.time.format(timeFormatter),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier
                .width(64.dp)
                .padding(top = if (group.doses.size > 1) 14.dp else 20.dp),
        )
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                group.doses.forEach { dose ->
                    DoseLine(
                        dose = dose,
                        onToggle = { taken -> if (taken) onTaken(dose.occurrenceId) else onUndo(dose.occurrenceId) },
                        onOpen = { onOpenMedication(dose.medicationId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DoseLine(dose: DoseView, onToggle: (Boolean) -> Unit, onOpen: () -> Unit) {
    val taken = dose.status == DoseStatus.TAKEN
    val stateColors = doseStateColors
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = if (taken) stateColors.takenContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MedIcon(
                dose.image,
                size = 38.dp,
                container = if (taken) stateColors.takenContainer else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (taken) stateColors.onTakenContainer else MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    dose.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (taken) stateColors.onTakenContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The subtitle carries the outcome when there is one: a failed dose can still be
                // taken late, so it keeps its button — but it must not look merely pending.
                val subtitle = when {
                    taken && dose.takenAt != null ->
                        stringResource(R.string.dose_taken_at, dose.takenAt.toLocalTime().format(timeFormatter))
                    dose.status == DoseStatus.MISSED -> stringResource(R.string.dose_missed)
                    dose.status == DoseStatus.SKIPPED -> stringResource(R.string.dose_skipped)
                    else -> dose.dosage?.takeIf { it.isNotBlank() }
                }
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            taken -> stateColors.onTakenContainer
                            dose.status == DoseStatus.MISSED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            DoseTakeButton(
                taken = taken,
                onToggle = onToggle,
                size = 44.dp,
            )
        }
    }
}

@Composable
private fun AsNeededRow(medications: List<Medication>, onLogNow: (Long) -> Unit) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        medications.forEach { med ->
            AssistChip(
                onClick = { onLogNow(med.id) },
                label = { Text(med.name) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
            )
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
        Button(onClick = onAddMedication, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.action_add_medication))
        }
    }
}

@Composable
private fun relativeLabel(at: LocalDateTime): String {
    val now = remember { LocalDateTime.now() }
    val minutes = ChronoUnit.MINUTES.between(now, at)
    return when {
        minutes in -1..1 -> stringResource(R.string.today_right_now)
        minutes in 2..59 -> stringResource(R.string.today_in_minutes, minutes)
        minutes >= 60 -> stringResource(R.string.today_in_hours, minutes / 60)
        minutes in -59..-2 -> stringResource(R.string.today_ago_minutes, -minutes)
        else -> stringResource(R.string.today_ago_hours, -minutes / 60)
    }
}

/** Long enough for the take button's morph to read, short enough not to feel laggy. */
private const val HERO_LINGER_MS = 420L

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
