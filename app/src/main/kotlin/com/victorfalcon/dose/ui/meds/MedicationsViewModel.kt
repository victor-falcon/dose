package com.victorfalcon.dose.ui.meds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorfalcon.dose.domain.model.DoseView
import com.victorfalcon.dose.domain.model.Medication
import com.victorfalcon.dose.domain.model.Schedule
import com.victorfalcon.dose.domain.repository.MedicationRepository
import com.victorfalcon.dose.ui.common.DoseCellState
import com.victorfalcon.dose.ui.common.toCellState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** How many days of adherence history each medication row shows. */
const val HISTORY_DAYS = 7

/**
 * One day of one medication: the outcome of every dose due that day, in time order. An empty
 * list means nothing was due — which is why the strip can't be one check per day.
 */
data class DayDoses(val date: LocalDate, val cells: List<DoseCellState>)

data class MedRow(
    val medication: Medication,
    val schedule: Schedule?,
    /** Oldest -> newest, always [HISTORY_DAYS] long. */
    val week: List<DayDoses>,
    val takenThisWeek: Int,
    val dosesThisWeek: Int,
) {
    /** The busiest day: above a single dose, the strip spells out each day's taken/total. */
    val dosesPerDay: Int get() = week.maxOfOrNull { it.cells.size } ?: 0
}

/**
 * Builds the per-dose week strip for each medication. Pure -> unit-testable. Doses are counted
 * one by one (a day with 2 of 3 taken counts as 2 of 3), never collapsed into a day verdict.
 */
fun weekRows(
    medications: List<Medication>,
    doses: List<DoseView>,
    schedules: List<Schedule>,
    windowStart: LocalDate,
    days: Int = HISTORY_DAYS,
): List<MedRow> {
    val scheduleByMed = schedules.associateBy { it.medicationId }
    val dosesByMed = doses.groupBy { it.medicationId }
    return medications.map { med ->
        val byDay = dosesByMed[med.id].orEmpty().groupBy { it.scheduledAt.toLocalDate() }
        val week = (0 until days).map { offset ->
            val day = windowStart.plusDays(offset.toLong())
            val cells = byDay[day].orEmpty()
                .sortedBy { it.scheduledAt }
                .map { it.status.toCellState() }
            DayDoses(day, cells)
        }
        val all = week.flatMap { it.cells }
        MedRow(
            medication = med,
            schedule = scheduleByMed[med.id],
            week = week,
            takenThisWeek = all.count { it == DoseCellState.TAKEN },
            dosesThisWeek = all.size,
        )
    }
}

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
        weekRows(meds, doses, schedules, windowStart)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
