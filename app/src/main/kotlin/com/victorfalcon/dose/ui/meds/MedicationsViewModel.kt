package com.victorfalcon.dose.ui.meds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.Medication
import com.victorfalcon.dose.domain.repository.MedicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** How many days of adherence history each medication row shows. */
const val HISTORY_DAYS = 7

/** One day's outcome for a medication, rendered as a dot in the history strip. */
enum class DayAdherence {
    TAKEN,         // every dose that day was taken
    SKIPPED,       // the user deliberately skipped
    MISSED,        // scheduled but never resolved
    NOT_SCHEDULED, // no dose due that day (or today's still pending)
    UNKNOWN,       // before the schedule's startDate: we have no record
}

data class MedRow(
    val medication: Medication,
    /** Oldest -> newest, always [HISTORY_DAYS] long. */
    val history: List<DayAdherence>,
)

@HiltViewModel
class MedicationsViewModel @Inject constructor(
    repository: MedicationRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val windowStart: LocalDate = today.minusDays((HISTORY_DAYS - 1).toLong())

    val medications: StateFlow<List<MedRow>> = combine(
        repository.observeActiveMedications(),
        repository.observeDosesBetween(windowStart.atStartOfDay(), today.plusDays(1).atStartOfDay(), activeOnly = true),
        repository.observeActiveSchedules(),
    ) { meds, doses, schedules ->
        val startDates = schedules.associate { it.medicationId to it.startDate }
        val statusesByMedAndDay = doses.groupBy { it.medicationId }
            .mapValues { (_, medDoses) -> medDoses.groupBy { it.scheduledAt.toLocalDate() } }
        meds.map { med ->
            val byDay = statusesByMedAndDay[med.id].orEmpty()
            val startDate = startDates[med.id]
            val history = (0 until HISTORY_DAYS).map { offset ->
                val day = windowStart.plusDays(offset.toLong())
                dayAdherence(day, byDay[day]?.map { it.status }.orEmpty(), startDate)
            }
            MedRow(med, history)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

// ponytail: "worst status wins" aggregation for days with multiple doses; PENDING
// (only today) reads as not-yet-resolved rather than a failure.
internal fun dayAdherence(day: LocalDate, statuses: List<DoseStatus>, startDate: LocalDate?): DayAdherence = when {
    startDate != null && day < startDate -> DayAdherence.UNKNOWN
    statuses.isEmpty() -> DayAdherence.NOT_SCHEDULED
    DoseStatus.MISSED in statuses -> DayAdherence.MISSED
    DoseStatus.SKIPPED in statuses -> DayAdherence.SKIPPED
    statuses.all { it == DoseStatus.TAKEN } -> DayAdherence.TAKEN
    else -> DayAdherence.NOT_SCHEDULED // only PENDING left (today, unresolved)
}
