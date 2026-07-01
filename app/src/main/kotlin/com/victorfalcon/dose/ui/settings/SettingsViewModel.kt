package com.victorfalcon.dose.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorfalcon.dose.data.Settings
import com.victorfalcon.dose.data.SettingsRepository
import com.victorfalcon.dose.data.ThemeMode
import com.victorfalcon.dose.data.backup.BackupManager
import com.victorfalcon.dose.reminder.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val backupManager: BackupManager,
    private val alarmScheduler: AlarmScheduler,
) : ViewModel() {

    val settings: StateFlow<Settings> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    fun setTheme(theme: ThemeMode) = viewModelScope.launch { repository.setTheme(theme) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { repository.setDynamicColor(enabled) }
    fun setSnooze(minutes: Int) = viewModelScope.launch { repository.setDefaultSnoozeMinutes(minutes) }

    suspend fun exportJson(): String = backupManager.export()

    /** Replace all data with [json], then re-arm alarms for the restored occurrences. */
    fun import(json: String) = viewModelScope.launch {
        backupManager.import(json)
        alarmScheduler.syncUpcoming()
    }
}
