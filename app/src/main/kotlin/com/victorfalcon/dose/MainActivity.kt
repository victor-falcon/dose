package com.victorfalcon.dose

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victorfalcon.dose.data.Settings
import com.victorfalcon.dose.data.SettingsRepository
import com.victorfalcon.dose.data.ThemeMode
import com.victorfalcon.dose.ui.DoseApp
import com.victorfalcon.dose.reminder.Reminders
import com.victorfalcon.dose.ui.theme.DoseTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    // A reminder notification or a dose in the widget opens straight into that dose.
    private val focusOccurrenceId = mutableStateOf<Long?>(null)

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* Today reflects doses regardless */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        focusOccurrenceId.value = intent.focusOccurrenceId()
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = Settings())
            val darkTheme = when (settings.theme) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            DoseTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
                DoseApp(focusOccurrenceId = focusOccurrenceId.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        focusOccurrenceId.value = intent.focusOccurrenceId()
    }

    private fun Intent?.focusOccurrenceId(): Long? =
        this?.getLongExtra(Reminders.EXTRA_OCCURRENCE_ID, -1L)?.takeIf { it >= 0 }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return // pre-33: no runtime prompt
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
