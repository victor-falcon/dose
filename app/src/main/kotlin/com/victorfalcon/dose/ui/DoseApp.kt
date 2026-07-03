package com.victorfalcon.dose.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.victorfalcon.dose.R
import com.victorfalcon.dose.ui.editor.MedEditorScreen
import com.victorfalcon.dose.ui.history.HistoryScreen
import com.victorfalcon.dose.ui.meds.MedDetailScreen
import com.victorfalcon.dose.ui.meds.MedicationsScreen
import com.victorfalcon.dose.ui.settings.SettingsScreen
import com.victorfalcon.dose.ui.today.TodayScreen
import kotlinx.serialization.Serializable

// Top-level destinations (the bottom-bar tabs). @Serializable so the back
// stack survives config changes and process death.
@Serializable private data object Today : NavKey
@Serializable private data object Medications : NavKey
@Serializable private data object History : NavKey

// Detail destinations.
@Serializable private data class MedEditor(val medicationId: Long? = null) : NavKey
@Serializable private data class MedDetail(val medicationId: Long) : NavKey
@Serializable private data object SettingsDest : NavKey

private enum class TopLevelTab(val key: NavKey, val icon: ImageVector, val labelRes: Int) {
    TODAY(Today, Icons.Filled.CheckCircle, R.string.nav_today),
    MEDICATIONS(Medications, Icons.AutoMirrored.Filled.List, R.string.nav_medications),
    HISTORY(History, Icons.Filled.DateRange, R.string.nav_history),
}

@Composable
fun DoseApp() {
    val backStack = rememberNavBackStack(Today)

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
        // Material shared-axis X: forward slides the new screen in from the right;
        // back always slides the current screen off to the right while the previous
        // one parallaxes in from the left with a quick fade — same direction for
        // both swipe edges. Predictive back seeks these by gesture progress, so the
        // same specs drive the live drag.
        transitionSpec = { forwardTransition() },
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
            entry<History> {
                TopLevelScaffold(History, backStack) { padding ->
                    Contained(padding) { HistoryScreen() }
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
            entry<MedDetail> { key ->
                DetailScaffold(titleRes = null, onBack = { backStack.removeLastOrNull() }) { padding ->
                    Contained(padding) {
                        MedDetailScreen(
                            medicationId = key.medicationId,
                            onEdit = { backStack.add(MedEditor(it)) },
                            onArchived = { backStack.removeLastOrNull() },
                        )
                    }
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopLevelScaffold(
    tab: NavKey,
    backStack: NavBackStack<NavKey>,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val titleRes = when (tab) {
        Medications -> R.string.nav_medications
        History -> R.string.nav_history
        else -> R.string.nav_today
    }
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(titleRes)) },
                actions = {
                    IconButton(onClick = { backStack.add(SettingsDest) }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
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
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.labelRes)) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == Today || tab == Medications) {
                FloatingActionButton(onClick = { backStack.add(MedEditor()) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_medication))
                }
            }
        },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
