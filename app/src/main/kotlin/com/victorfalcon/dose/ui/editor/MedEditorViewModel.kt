package com.victorfalcon.dose.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorfalcon.dose.data.OccurrenceMaterializer
import com.victorfalcon.dose.domain.model.Medication
import com.victorfalcon.dose.domain.model.Schedule
import com.victorfalcon.dose.domain.model.ScheduleType
import com.victorfalcon.dose.domain.repository.MedicationRepository
import com.victorfalcon.dose.reminder.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

/** Editable form state. Validation and mapping are pure so they're unit-testable. */
data class MedEditorUiState(
    val name: String = "",
    val dosage: String = "",
    val notes: String = "",
    val image: String? = null,
    val startDate: LocalDate = LocalDate.now(),
    val type: ScheduleType = ScheduleType.DAILY_TIMES,
    val times: List<LocalTime> = listOf(LocalTime.of(8, 0)),
    val daysOfWeek: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val intervalDays: Int = 2,
    val cycleActiveDays: Int = 21,
    val cycleRestDays: Int = 7,
    val saving: Boolean = false,
) {
    val needsTimes: Boolean get() = type != ScheduleType.AS_NEEDED

    val isValid: Boolean
        get() = name.isNotBlank() &&
            (!needsTimes || times.isNotEmpty()) &&
            (type != ScheduleType.WEEKLY || daysOfWeek.isNotEmpty()) &&
            (type != ScheduleType.INTERVAL || intervalDays >= 1) &&
            (type != ScheduleType.CYCLIC || (cycleActiveDays >= 1 && cycleRestDays >= 1))
}

fun MedEditorUiState.toMedication(id: Long = 0): Medication = Medication(
    id = id,
    name = name.trim(),
    dosage = dosage.trim().ifBlank { null },
    notes = notes.trim().ifBlank { null },
    image = image,
)

fun MedEditorUiState.toSchedule(medicationId: Long = 0, id: Long = 0): Schedule = Schedule(
    id = id,
    medicationId = medicationId,
    type = type,
    startDate = startDate,
    times = if (needsTimes) times.sorted() else emptyList(),
    daysOfWeek = if (type == ScheduleType.WEEKLY) daysOfWeek else emptySet(),
    intervalDays = intervalDays,
    cycleActiveDays = cycleActiveDays,
    cycleRestDays = cycleRestDays,
)

@HiltViewModel
class MedEditorViewModel @Inject constructor(
    private val repository: MedicationRepository,
    private val materializer: OccurrenceMaterializer,
    private val alarmScheduler: AlarmScheduler,
) : ViewModel() {

    var uiState by mutableStateOf(MedEditorUiState())
        private set

    private var medicationId: Long = 0
    private var scheduleId: Long = 0

    /** Load an existing medication into the form (edit mode). No-op for create. */
    fun load(id: Long) {
        if (id == 0L || id == medicationId) return
        viewModelScope.launch {
            val med = repository.getMedication(id) ?: return@launch
            val schedule = repository.getSchedule(id)
            medicationId = med.id
            scheduleId = schedule?.id ?: 0
            val defaults = MedEditorUiState()
            uiState = defaults.copy(
                name = med.name,
                dosage = med.dosage.orEmpty(),
                notes = med.notes.orEmpty(),
                image = med.image,
                startDate = schedule?.startDate ?: defaults.startDate,
                type = schedule?.type ?: defaults.type,
                times = schedule?.times?.ifEmpty { defaults.times } ?: defaults.times,
                daysOfWeek = schedule?.daysOfWeek?.ifEmpty { defaults.daysOfWeek } ?: defaults.daysOfWeek,
                intervalDays = schedule?.intervalDays?.takeIf { it > 0 } ?: defaults.intervalDays,
                cycleActiveDays = schedule?.cycleActiveDays?.takeIf { it > 0 } ?: defaults.cycleActiveDays,
                cycleRestDays = schedule?.cycleRestDays?.takeIf { it > 0 } ?: defaults.cycleRestDays,
            )
        }
    }

    fun update(transform: (MedEditorUiState) -> MedEditorUiState) {
        uiState = transform(uiState)
    }

    fun save(onSaved: () -> Unit) {
        val state = uiState
        if (!state.isValid || state.saving) return
        uiState = state.copy(saving = true)
        viewModelScope.launch {
            val savedMedId = repository.saveMedicationWithSchedule(
                state.toMedication(medicationId),
                state.toSchedule(medicationId, scheduleId),
            )
            // Regenerate future only: drop future pending, re-materialize; past stays frozen.
            // No-op delete on create; on edit it swaps the old schedule's future doses.
            repository.deleteFuturePendingOccurrences(savedMedId, LocalDateTime.now())
            repository.getSchedule(savedMedId)?.let { materializer.materialize(it) }
            alarmScheduler.syncUpcoming() // arm exact alarms for the new/updated occurrences
            onSaved()
        }
    }
}
