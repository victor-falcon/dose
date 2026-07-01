package com.victorfalcon.dose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.victorfalcon.dose.R
import com.victorfalcon.dose.ui.editor.MedEditorScreen
import kotlinx.serialization.Serializable

// Top-level destinations (the two bottom-bar tabs). @Serializable so the back
// stack survives config changes and process death.
@Serializable private data object Today : NavKey
@Serializable private data object History : NavKey

// Detail destination: null id = create, non-null = edit (reused later for edit).
@Serializable private data class MedEditor(val medicationId: Long? = null) : NavKey

private enum class TopLevelTab(val key: NavKey, val icon: ImageVector, val labelRes: Int) {
    TODAY(Today, Icons.Filled.CheckCircle, R.string.nav_today),
    HISTORY(History, Icons.Filled.DateRange, R.string.nav_history),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoseApp() {
    val backStack = rememberNavBackStack(Today)
    val current = backStack.lastOrNull()
    val isTopLevel = current is Today || current is History

    Scaffold(
        topBar = {
            when (current) {
                is MedEditor -> {
                    val titleRes = if (current.medicationId != null) {
                        R.string.editor_title_edit
                    } else {
                        R.string.action_add_medication
                    }
                    TopAppBar(
                        title = { Text(stringResource(titleRes)) },
                        navigationIcon = {
                            IconButton(onClick = { backStack.removeLastOrNull() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back),
                                )
                            }
                        },
                    )
                }
                else -> {
                    val titleRes = if (current == History) R.string.nav_history else R.string.nav_today
                    TopAppBar(title = { Text(stringResource(titleRes)) })
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
            if (current == Today) {
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
                entry<Today> { PlaceholderScreen("Today") }
                entry<History> { PlaceholderScreen("History") }
                entry<MedEditor> { key ->
                    MedEditorScreen(
                        medicationId = key.medicationId,
                        onDone = { backStack.removeLastOrNull() },
                    )
                }
            },
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun PlaceholderScreen(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(name, style = MaterialTheme.typography.headlineMedium)
    }
}
