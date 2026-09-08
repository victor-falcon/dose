@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.victorfalcon.dose.ui

import android.text.format.DateFormat
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.victorfalcon.dose.R
import com.victorfalcon.dose.ui.dose.DoseFocusScreen
import com.victorfalcon.dose.ui.editor.MedEditorScreen
import com.victorfalcon.dose.ui.meds.MedDetailScreen
import com.victorfalcon.dose.ui.meds.MedicationsScreen
import com.victorfalcon.dose.ui.settings.SettingsScreen
import com.victorfalcon.dose.ui.today.TodayScreen
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Top-level destinations (the bottom-bar tabs). @Serializable so the back stack survives
// config changes and process death.
@Serializable private data object Today : NavKey
@Serializable private data object Medications : NavKey

// Detail destinations.
@Serializable private data class MedEditor(val medicationId: Long? = null) : NavKey
@Serializable private data class MedDetail(val medicationId: Long) : NavKey
@Serializable private data class DoseFocus(val occurrenceId: Long) : NavKey
@Serializable private data object SettingsDest : NavKey

private enum class TopLevelTab(val key: NavKey, val labelRes: Int) {
    TODAY(Today, R.string.nav_today),
    MEDICATIONS(Medications, R.string.nav_medications),
}

/**
 * @param focusOccurrenceId a dose to open straight into the focus screen — set when the activity
 * was launched from a reminder notification or from a dose in the widget.
 */
@Composable
fun DoseApp(focusOccurrenceId: Long? = null) {
    val backStack = rememberNavBackStack(Today)

    LaunchedEffect(focusOccurrenceId) {
        val id = focusOccurrenceId ?: return@LaunchedEffect
        if (backStack.lastOrNull() != DoseFocus(id)) backStack.add(DoseFocus(id))
    }

    // NavDisplay is the root: each destination owns its Scaffold (bars + opaque
    // background), so the predictive-back scaleOut shrinks the WHOLE screen as one
    // rounded card instead of leaving the top bar behind and showing through.
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        // Scope a ViewModelStore per NavEntry so hiltViewModel() gives each
        // destination its own VM; without this they'd share the activity scope
        // and MedEditor would carry stale state across create/edit navigations.
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        // Material shared-axis X for push/pop: forward slides the new screen in from
        // the right; back always slides the current screen off to the right while the
        // previous one parallaxes in from the left with a quick fade — same direction
        // for both swipe edges. Predictive back seeks these by gesture progress, so
        // the same specs drive the live drag. Bottom-nav tab switches cross-dissolve in place
        // instead: a tab switch replaces the whole stack, so the new tab is the only entry left,
        // while every other forward navigation pushes on top of something. (The Scene's own key
        // can't be compared to a NavKey — it's the entry's contentKey, derived from toString.)
        transitionSpec = {
            if (backStack.size == 1) tabTransition() else forwardTransition()
        },
        popTransitionSpec = { backTransition() },
        predictivePopTransitionSpec = { backTransition() },
        entryProvider = entryProvider {
            entry<Today> {
                TopLevelScaffold(Today, backStack) { padding ->
                    Contained(padding) {
                        TodayScreen(
                            onOpenMedication = { backStack.add(MedDetail(it)) },
                            onAddMedication = { backStack.add(MedEditor()) },
                        )
                    }
                }
            }
            entry<Medications> {
                TopLevelScaffold(Medications, backStack) { padding ->
                    Contained(padding) {
                        MedicationsScreen(
                            onOpenMedication = { backStack.add(MedDetail(it)) },
                            onAddMedication = { backStack.add(MedEditor()) },
                        )
                    }
                }
            }
            entry<MedEditor> { key ->
                DetailScaffold(
                    titleRes = if (key.medicationId != null) R.string.editor_title_edit else R.string.action_add_medication,
                    onBack = { backStack.removeLastOrNull() },
                ) { padding ->
                    Contained(padding) {
                        MedEditorScreen(
                            medicationId = key.medicationId,
                            onDone = { backStack.removeLastOrNull() },
                        )
                    }
                }
            }
            // Detail and focus own their app bars: both carry actions of their own.
            entry<MedDetail> { key ->
                MedDetailScreen(
                    medicationId = key.medicationId,
                    onBack = { backStack.removeLastOrNull() },
                    onEdit = { backStack.add(MedEditor(it)) },
                    onArchived = { backStack.removeLastOrNull() },
                )
            }
            entry<DoseFocus> { key ->
                DoseFocusScreen(
                    occurrenceId = key.occurrenceId,
                    onClose = { backStack.removeLastOrNull() },
                    onOpenMedication = { backStack.add(MedDetail(it)) },
                )
            }
            entry<SettingsDest> {
                DetailScaffold(
                    titleRes = R.string.action_settings,
                    onBack = { backStack.removeLastOrNull() },
                ) { padding ->
                    Contained(padding) { SettingsScreen() }
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun TopLevelScaffold(
    tab: NavKey,
    backStack: NavBackStack<NavKey>,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val isToday = tab == Today
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // The expressive large flexible bar carries the date as a subtitle and collapses
            // into a small bar as the list scrolls.
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(if (isToday) R.string.nav_today else R.string.nav_medications))
                },
                subtitle = if (isToday) {
                    { Text(todayLabel()) }
                } else {
                    null
                },
                actions = {
                    IconButton(onClick = { backStack.add(SettingsDest) }) {
                        Icon(
                            painterResource(R.drawable.ic_tune),
                            contentDescription = stringResource(R.string.action_settings),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            NavigationBar {
                TopLevelTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t.key,
                        onClick = {
                            if (tab != t.key) {
                                backStack.clear()
                                backStack.add(t.key)
                            }
                        },
                        icon = {
                            when (t) {
                                TopLevelTab.TODAY -> Icon(Icons.Filled.CheckCircle, contentDescription = null)
                                TopLevelTab.MEDICATIONS -> Icon(
                                    painterResource(R.drawable.ic_pill_capsule),
                                    contentDescription = null,
                                )
                            }
                        },
                        label = { Text(stringResource(t.labelRes)) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == Medications) {
                MediumFloatingActionButton(
                    onClick = { backStack.add(MedEditor()) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.action_add_medication),
                    )
                }
            }
        },
        content = content,
    )
}

/** Today's date the way the platform writes it in the user's locale ("lunes, 7 de septiembre"). */
@Composable
private fun todayLabel(): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)
    }
    return remember(formatter) { LocalDate.now().format(formatter) }
}

@Composable
private fun DetailScaffold(
    titleRes: Int?,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { titleRes?.let { Text(stringResource(it)) } },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        content = content,
    )
}

// Applies the Scaffold's inner padding around a screen that manages its own
// fillMaxSize layout, so screens don't each need a padding parameter.
@Composable
private fun Contained(padding: PaddingValues, screen: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding)) { screen() }
}

private const val SLIDE_MS = 350
private const val FADE_MS = 180
private const val PARALLAX = 4 // the reveal-side screen moves width / PARALLAX
private const val TAB_FADE_MS = 200

// Bottom-nav tab switch: a straight cross-dissolve. Tabs are siblings, so nothing slides or
// scales — both screens carry the same bars in the same place, so only the title, the list and
// the nav indicator dissolve and the chrome reads as if it never moved.
private fun tabTransition(): ContentTransform =
    ContentTransform(
        targetContentEnter = fadeIn(tween(TAB_FADE_MS)),
        initialContentExit = fadeOut(tween(TAB_FADE_MS)),
    )

// Forward: the new screen slides fully in from the right; the current one
// parallaxes left and fades out beneath it.
private fun forwardTransition(): ContentTransform =
    ContentTransform(
        targetContentEnter = slideInHorizontally(tween(SLIDE_MS)) { it },
        initialContentExit = slideOutHorizontally(tween(SLIDE_MS)) { -it / PARALLAX } +
            fadeOut(tween(FADE_MS)),
    )

// Back: the current screen slides fully out to the right on top, revealing the
// previous one, which parallaxes in from the left with a quick fade.
private fun backTransition(): ContentTransform =
    ContentTransform(
        targetContentEnter = slideInHorizontally(tween(SLIDE_MS)) { -it / PARALLAX } +
            fadeIn(tween(FADE_MS)),
        initialContentExit = slideOutHorizontally(tween(SLIDE_MS)) { it },
        targetContentZIndex = -1f,
    )
