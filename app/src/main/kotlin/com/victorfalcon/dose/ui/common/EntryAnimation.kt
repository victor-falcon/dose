package com.victorfalcon.dose.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Entry animation for list items: the element fades in while sliding a short way down into its
 * place, staggered by [index] so the list assembles top-down instead of landing all at once. The
 * fade and the spring both come from the theme's motion scheme.
 *
 * Only the first [StaggeredItems] items animate. Those are the ones on screen when the list opens;
 * in a lazy list everything below composes later, as it is scrolled into view, and animating there
 * would make scrolling flicker instead of feel alive. It bounds the entry too — a long list is in
 * after five steps, not fifteen.
 *
 * Items past that bound start out settled rather than skipping the modifier, so an item whose
 * position shifts under it — a medication above it deleted, say — keeps its state instead of
 * replaying its entrance from invisible.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.entryFadeSlide(index: Int): Modifier {
    val motion = MaterialTheme.motionScheme
    var shown by remember { mutableStateOf(index >= StaggeredItems) }
    val fade by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = motion.defaultEffectsSpec(),
        label = "entryFade",
    )
    val slide by animateFloatAsState(
        targetValue = if (shown) 0f else 1f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "entrySlide",
    )
    LaunchedEffect(Unit) {
        delay(StaggerStep * index)
        shown = true
    }
    return graphicsLayer {
        alpha = fade
        translationY = slide * -EntrySlide.toPx()
    }
}

private const val StaggeredItems = 5
private const val StaggerStep = 40L
private val EntrySlide = 12.dp
