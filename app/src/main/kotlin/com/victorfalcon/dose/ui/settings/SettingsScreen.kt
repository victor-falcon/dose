package com.victorfalcon.dose.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victorfalcon.dose.R
import com.victorfalcon.dose.data.ThemeMode

private val SNOOZE_PRESETS = listOf(5, 10, 15, 30, 60)

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle(stringResource(R.string.settings_appearance))

        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.theme == mode,
                    onClick = { viewModel.setTheme(mode) },
                    label = { Text(stringResource(mode.labelRes)) },
                )
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.settings_dynamic_color),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = settings.dynamicColor, onCheckedChange = viewModel::setDynamicColor)
        }

        Text(stringResource(R.string.settings_snooze), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SNOOZE_PRESETS.forEach { minutes ->
                FilterChip(
                    selected = settings.defaultSnoozeMinutes == minutes,
                    onClick = { viewModel.setSnooze(minutes) },
                    label = { Text(stringResource(R.string.unit_minutes, minutes)) },
                )
            }
        }

        HorizontalDivider()
        SectionTitle(stringResource(R.string.settings_permissions))
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_notification_settings)) },
            modifier = Modifier.clickable {
                context.startActivitySafely(
                    Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
                )
            },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_exact_alarm)) },
            modifier = Modifier.clickable {
                context.startActivitySafely(
                    Intent(
                        AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
        ThemeMode.SYSTEM -> R.string.theme_system
    }

// ponytail: some OEM builds don't resolve these settings screens; swallow rather than crash.
private fun android.content.Context.startActivitySafely(intent: Intent) {
    runCatching { startActivity(intent) }
}
