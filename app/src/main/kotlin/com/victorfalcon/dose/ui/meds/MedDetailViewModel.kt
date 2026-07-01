package com.victorfalcon.dose.ui.meds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorfalcon.dose.domain.model.DoseView
import com.victorfalcon.dose.domain.model.Medication
import com.victorfalcon.dose.domain.model.Schedule
import com.victorfalcon.dose.domain.repository.MedicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MedDetailUiState(
    val medication: Medication? = null,
    val schedule: Schedule? = null,
    val history: List<DoseView> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MedDetailViewModel @Inject constructor(
    private val repository: MedicationRepository,
) : ViewModel() {

    private val medicationId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<MedDetailUiState> = medicationId.filterNotNull().flatMapLatest { id ->
        combine(
            repository.observeMedication(id),
            repository.observeSchedule(id),
            repository.observeMedicationDoses(id),
        ) { medication, schedule, history ->
            MedDetailUiState(medication, schedule, history)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MedDetailUiState())

    fun bind(id: Long) { medicationId.value = id }

    fun archive(onArchived: () -> Unit) {
        val id = medicationId.value ?: return
        viewModelScope.launch {
            repository.archiveMedication(id)
            onArchived()
        }
    }
}
