package com.victorfalcon.dose.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButtonShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.victorfalcon.dose.R
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.ui.theme.doseStateColors

/**
 * The app's shape vocabulary. A dose is a shape: scalloped while it's still owed, a clover once
 * it's done, a diamond when it wasn't. Shape carries the meaning alongside color, so the states
 * still read without it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object DoseShapes {
    /** Pending dose — the tappable one. */
    val pending: Shape @Composable get() = MaterialShapes.Cookie7Sided.toShape()

    /** Held down: tighter scallops, so the press is visible before the finger lifts. */
    val pressed: Shape @Composable get() = MaterialShapes.Cookie12Sided.toShape()

    /** Taken. */
    val taken: Shape @Composable get() = MaterialShapes.Clover8Leaf.toShape()

    /** Missed or skipped. */
    val unresolved: Shape @Composable get() = MaterialShapes.Cookie4Sided.toShape()
}

/** One cell in an adherence strip or grid: the outcome of a single dose, not of a whole day. */
enum class DoseCellState { TAKEN, MISSED, SKIPPED, PENDING, NONE }

fun DoseStatus.toCellState(): DoseCellState = when (this) {
    DoseStatus.TAKEN -> DoseCellState.TAKEN
    DoseStatus.MISSED -> DoseCellState.MISSED
    DoseStatus.SKIPPED -> DoseCellState.SKIPPED
    DoseStatus.PENDING -> DoseCellState.PENDING
}

/**
 * A dose's outcome as a small shape. Filled means resolved well, hollow means it wasn't taken,
 * and a soft square means nothing was due — no icons, so it stays legible down to 10 dp.
 */
@Composable
fun DoseStateCell(state: DoseCellState, size: Dp, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dose = doseStateColors
    val stroke = (size.value * 0.09f).coerceIn(1f, 2.5f).dp
    when (state) {
        DoseCellState.TAKEN ->
            Box(modifier.size(size).clip(DoseShapes.taken).background(dose.taken))
        DoseCellState.MISSED ->
            Box(modifier.size(size).clip(DoseShapes.unresolved).background(scheme.error))
        DoseCellState.SKIPPED ->
            Box(modifier.size(size).border(stroke, scheme.outline, DoseShapes.unresolved))
        DoseCellState.PENDING ->
            Box(modifier.size(size).border(stroke, scheme.outlineVariant, DoseShapes.pending))
        DoseCellState.NONE ->
            Box(
                modifier
                    .size(size * 0.72f)
                    .clip(RoundedCornerShape(size * 0.26f))
                    .background(scheme.surfaceContainerHighest),
            )
    }
}

/**
 * The take/undo control: an M3 Expressive icon toggle button that morphs between three shapes —
 * scalloped cookie at rest, tighter cookie while pressed, eight-leaf clover once taken — and
 * spins a full turn as the dose lands. The morph and the press spring come from the theme's
 * motion scheme; only the rotation is ours.
 *
 * [emphasized] is the hero/primary version (filled primary, invites the tap); the plain version
 * is tonal, for rows where the dose isn't the one due right now.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DoseTakeButton(
    taken: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    emphasized: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val dose = doseStateColors
    val motion = MaterialTheme.motionScheme
    // The tap flips a local state first, so the shape morph and the turn start on *this* button
    // instance. Otherwise the dose moves to another spot in the list (or the hero card swaps for
    // the next dose) before the animation has a chance to run, and marking a dose feels dead.
    var checked by remember(taken) { mutableStateOf(taken) }
    val spin by animateFloatAsState(
        targetValue = if (checked) 360f else 0f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "doseSpin",
    )
    val stateLabel = stringResource(if (checked) R.string.dose_taken else R.string.dose_pending)
    val ringColor = if (emphasized) scheme.onPrimary else scheme.onPrimaryContainer
    FilledIconToggleButton(
        checked = checked,
        onCheckedChange = {
            checked = it
            onToggle(it)
        },
        shapes = IconToggleButtonShapes(
            shape = DoseShapes.pending,
            pressedShape = DoseShapes.pressed,
            checkedShape = DoseShapes.taken,
        ),
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = if (emphasized) scheme.primary else scheme.primaryContainer,
            contentColor = if (emphasized) scheme.onPrimary else scheme.onPrimaryContainer,
            checkedContainerColor = dose.taken,
            checkedContentColor = dose.onTaken,
        ),
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = spin }
            .semantics { contentDescription = stateLabel },
    ) {
        // The mark grows in from small as it swaps, so marking a dose feels like a landing.
        AnimatedContent(
            targetState = checked,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) +
                    scaleIn(motion.defaultSpatialSpec(), initialScale = 0.2f)) togetherWith
                    (fadeOut(motion.fastEffectsSpec()) +
                        scaleOut(motion.defaultSpatialSpec(), targetScale = 0.2f))
            },
            label = "doseMark",
        ) { isTaken ->
            if (isTaken || emphasized) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(size * 0.42f),
                )
            } else {
                // Hollow ring: this dose is still owed.
                Box(
                    Modifier
                        .size(size * 0.34f)
                        .border((size.value * 0.045f).dp, ringColor, CircleRing),
                )
            }
        }
    }
}

private val CircleRing = RoundedCornerShape(50)

/**
 * Fallback cell for medications with four or more doses a day, where separate cells stop being
 * legible: one shape per day, filled bottom-up to the fraction taken. The empty part is
 * error-tinted once a dose has actually been failed rather than merely still pending.
 */
@Composable
fun DoseFractionCell(
    taken: Int,
    total: Int,
    failed: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val dose = doseStateColors
    if (total == 0) {
        DoseStateCell(DoseCellState.NONE, size, modifier)
        return
    }
    val fraction = (taken.toFloat() / total).coerceIn(0f, 1f)
    Box(
        modifier
            .size(size)
            .clip(DoseShapes.taken)
            .background(if (failed) scheme.errorContainer else scheme.surfaceContainerHighest),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction)
                    .background(dose.taken),
            )
        }
    }
}
