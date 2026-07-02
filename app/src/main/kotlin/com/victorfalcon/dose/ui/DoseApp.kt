package com.victorfalcon.dose.ui

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
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoseApp() {
    val backStack = rememberNavBackStack(Today)
    val current = backStack.lastOrNull()
    val isTopLevel = current is Today || current is Medications || current is History
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = if (isTopLevel) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier,
        topBar = {
            when (current) {
                is MedEditor -> DetailTopBar(
                    titleRes = if (current.medicationId != null) R.string.editor_title_edit else R.string.action_add_medication,
                    onBack = { backStack.removeLastOrNull() },
                )
                is MedDetail -> DetailTopBar(titleRes = null, onBack = { backStack.removeLastOrNull() })
                is SettingsDest -> DetailTopBar(
                    titleRes = R.string.action_settings,
                    onBack = { backStack.removeLastOrNull() },
                )
                else -> {
                    val titleRes = when (current) {
                        is History -> R.string.nav_history
                        is Medications -> R.string.nav_medications
                        else -> R.string.nav_today
                    }
                    LargeTopAppBar(
                        title = { Text(stringResource(titleRes)) },
                        actions = {
                            IconButton(onClick = { backStack.add(SettingsDest) }) {
                                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                }
            }
        },
        bottomBar = {
            if (isTopLevel) {
                NavigationBar {
                    TopLevelTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.key,
                            onClick = {
                                if (current != tab.key) {
                                    backStack.clear()
                                    backStack.add(tab.key)
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (current == Today || current == Medications) {
                FloatingActionButton(onClick = { backStack.add(MedEditor()) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_medication))
                }
            }
        },
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<Today> {
                    TodayScreen(onAddMedication = { backStack.add(MedEditor()) })
                }
                entry<Medications> {
                    MedicationsScreen(
                        onOpenMedication = { backStack.add(MedDetail(it)) },
                        onAddMedication = { backStack.add(MedEditor()) },
                    )
                }
                entry<History> { HistoryScreen() }
                entry<MedEditor> { key ->
                    MedEditorScreen(
                        medicationId = key.medicationId,
                        onDone = { backStack.removeLastOrNull() },
                    )
                }
                entry<MedDetail> { key ->
                    MedDetailScreen(
                        medicationId = key.medicationId,
                        onEdit = { backStack.add(MedEditor(it)) },
                        onArchived = { backStack.removeLastOrNull() },
                    )
                }
                entry<SettingsDest> { SettingsScreen() }
            },
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(titleRes: Int?, onBack: () -> Unit) {
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
}
