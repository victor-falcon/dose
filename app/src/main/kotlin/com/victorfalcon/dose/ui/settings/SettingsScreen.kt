@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.victorfalcon.dose.ui.settings

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victorfalcon.dose.BuildConfig
import com.victorfalcon.dose.R
import com.victorfalcon.dose.data.ThemeMode
import kotlinx.coroutines.launch

private val SNOOZE_PRESETS = listOf(5, 10, 15, 30, 60)
private const val BACKUP_FILENAME = "dose-backup.json"
private const val SPONSOR_URL = "https://github.com/sponsors/victor-falcon"
private const val FOLLOW_URL = "https://twitter.com/victoor"

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val json = viewModel.exportJson()
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (text != null) viewModel.import(text)
        }
    }
    var confirmImport by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.action_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Section(stringResource(R.string.settings_appearance)) {
                    // Connected segmented buttons: the expressive way to pick one of a few.
                    ChoiceRow(R.drawable.ic_contrast, stringResource(R.string.settings_theme)) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            ThemeMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = settings.theme == mode,
                                    onClick = { viewModel.setTheme(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                                    icon = { SegmentedButtonDefaults.Icon(active = settings.theme == mode) },
                                    label = { Text(stringResource(mode.labelRes)) },
                                )
                            }
                        }
                    }
                    RowDivider()
                    SettingsRow(
                        icon = R.drawable.ic_palette,
                        title = stringResource(R.string.settings_dynamic_color),
                        subtitle = stringResource(R.string.settings_dynamic_color_detail),
                        onClick = { viewModel.setDynamicColor(!settings.dynamicColor) },
                    ) {
                        Switch(
                            checked = settings.dynamicColor,
                            onCheckedChange = viewModel::setDynamicColor,
                            thumbContent = if (settings.dynamicColor) {
                                {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }

                Section(stringResource(R.string.settings_support)) {
                    // Tinted so the ask reads before the two rows that act on it.
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                stringResource(R.string.settings_support_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.settings_support_body),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    SettingsRow(
                        icon = R.drawable.ic_favorite_outlined,
                        title = stringResource(R.string.settings_sponsor),
                        subtitle = stringResource(R.string.settings_sponsor_detail),
                        onClick = { context.startActivitySafely(browse(SPONSOR_URL)) },
                        trailing = { ExternalLinkIcon() },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = R.drawable.ic_x,
                        title = stringResource(R.string.settings_follow),
                        subtitle = stringResource(R.string.settings_follow_detail),
                        onClick = { context.startActivitySafely(browse(FOLLOW_URL)) },
                        trailing = { ExternalLinkIcon() },
                    )
                }

                Section(stringResource(R.string.settings_reminders)) {
                    ChoiceRow(
                        icon = R.drawable.ic_schedule_outlined,
                        title = stringResource(R.string.settings_snooze),
                        subtitle = stringResource(R.string.settings_snooze_detail),
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SNOOZE_PRESETS.forEach { minutes ->
                                FilterChip(
                                    selected = settings.defaultSnoozeMinutes == minutes,
                                    onClick = { viewModel.setSnooze(minutes) },
                                    label = { Text(minutes.toString()) },
                                )
                            }
                        }
                    }
                    RowDivider()
                    SettingsRow(
                        icon = R.drawable.ic_notifications_outlined,
                        title = stringResource(R.string.settings_notifications),
                        subtitle = stringResource(R.string.settings_notifications_detail),
                        onClick = {
                            context.startActivitySafely(
                                Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        },
                    )
                    RowDivider()
                    ExactAlarmRow()
                }

                Section(stringResource(R.string.settings_data)) {
                    SettingsRow(
                        icon = R.drawable.ic_upload,
                        title = stringResource(R.string.settings_export),
                        subtitle = stringResource(R.string.settings_export_detail),
                        onClick = { exportLauncher.launch(BACKUP_FILENAME) },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = R.drawable.ic_download,
                        title = stringResource(R.string.settings_import),
                        subtitle = stringResource(R.string.settings_import_detail),
                        onClick = { confirmImport = true },
                    )
                }
            }

            Text(
                stringResource(R.string.settings_footer, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
            )
        }
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = false
                    importLauncher.launch(arrayOf("application/json"))
                }) { Text(stringResource(R.string.action_import)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmImport = false }) { Text(stringResource(R.string.action_cancel)) }
            },
            text = { Text(stringResource(R.string.import_confirm_message)) },
        )
    }
}

/**
 * Without the exact-alarm grant reminders still fire, just whenever the system feels like it —
 * useless for a dose due at 8:00. The row only acts while the grant is missing: once it's there,
 * the trailing text is state, not an affordance.
 */
@Composable
private fun ExactAlarmRow() {
    val context = LocalContext.current
    val alarmManager = remember { context.getSystemService(AlarmManager::class.java) }
    var canSchedule by remember { mutableStateOf(alarmManager.canScheduleExactAlarms()) }
    // The grant happens in a system screen, so nothing here recomposes on the way back.
    LifecycleResumeEffect(Unit) {
        canSchedule = alarmManager.canScheduleExactAlarms()
        onPauseOrDispose {}
    }
    val requestGrant: () -> Unit = {
        context.startActivitySafely(
            Intent(
                AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    }

    SettingsRow(
        icon = R.drawable.ic_alarm_outlined,
        title = stringResource(R.string.settings_exact_alarm),
        subtitle = stringResource(R.string.settings_exact_alarm_detail),
        onClick = if (canSchedule) null else requestGrant,
        trailing = {
            Text(
                stringResource(
                    if (canSchedule) {
                        R.string.settings_exact_alarm_allowed
                    } else {
                        R.string.settings_exact_alarm_allow
                    },
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        },
    )
}

/** A labelled group of rows: the label outside, the rows inside one rounded container. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp),
        )
        // Surface, not background(): it clips the children to the shape — which is what
        // rounds the Support header's top corners — and sets the right content color.
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

/** Icon, title, subtitle, and a trailing control — a chevron unless told otherwise. */
@Composable
private fun SettingsRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    },
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

/** A row whose control is too wide for the trailing slot, so it sits under the label. */
@Composable
private fun ChoiceRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String? = null,
    control: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(painterResource(icon), contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        control()
    }
}

@Composable
private fun RowDivider() = HorizontalDivider(Modifier.padding(horizontal = 16.dp))

@Composable
private fun ExternalLinkIcon() = Icon(
    painterResource(R.drawable.ic_open_in_new),
    contentDescription = null,
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
)

private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
        ThemeMode.SYSTEM -> R.string.theme_system
    }

private fun browse(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url))

// ponytail: some OEM builds don't resolve these settings screens; swallow rather than crash.
private fun android.content.Context.startActivitySafely(intent: Intent) {
    runCatching { startActivity(intent) }
}
