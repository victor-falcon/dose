package com.victorfalcon.dose.ui.meds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorfalcon.dose.domain.model.Medication
import com.victorfalcon.dose.domain.repository.MedicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MedicationsViewModel @Inject constructor(
    repository: MedicationRepository,
) : ViewModel() {
    val medications: StateFlow<List<Medication>> =
        repository.observeActiveMedications()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
