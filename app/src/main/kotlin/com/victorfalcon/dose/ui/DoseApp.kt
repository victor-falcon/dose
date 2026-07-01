package com.victorfalcon.dose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import kotlinx.serialization.Serializable

// Top-level destinations (the two bottom-bar tabs). @Serializable so the back
// stack survives config changes and process death.
@Serializable private data object Today : NavKey
@Serializable private data object History : NavKey

private enum class TopLevelTab(val key: NavKey, val icon: ImageVector, val labelRes: Int) {
    TODAY(Today, Icons.Filled.CheckCircle, R.string.nav_today),
    HISTORY(History, Icons.Filled.DateRange, R.string.nav_history),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoseApp() {
    val backStack = rememberNavBackStack(Today)
    val current = backStack.lastOrNull()

    Scaffold(
        topBar = {
            val titleRes = if (current == History) R.string.nav_history else R.string.nav_today
            TopAppBar(title = { Text(stringResource(titleRes)) })
        },
        bottomBar = {
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
        },
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<Today> { PlaceholderScreen("Today") }
                entry<History> { PlaceholderScreen("History") }
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
