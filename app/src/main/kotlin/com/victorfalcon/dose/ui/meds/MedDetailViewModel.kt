package com.victorfalcon.dose.ui.meds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.DoseView
import com.victorfalcon.dose.domain.model.Medication
import com.victorfalcon.dose.domain.model.Schedule
import com.victorfalcon.dose.domain.repository.MedicationRepository
import com.victorfalcon.dose.ui.common.DoseCellState
import com.victorfalcon.dose.ui.common.toCellState
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
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** How many days the detail screen's hour × day grid covers. Two weeks keeps cells tappable-size. */
const val GRID_DAYS = 14

/** How many scheduled hours the grid will show before it stops adding rows. */
private const val MAX_GRID_ROWS = 6

/** One row of the grid: a scheduled hour across the window. `null` = nothing due that day. */
data class HourHistory(val time: LocalTime, val cells: List<DoseCellState?>)

/**
 * The detail screen's adherence block. Counted per dose: [taken] out of [resolved], with
 * [pending] still open today, so a day with 2 of 3 taken is exactly that.
 */
data class AdherenceStats(
    val taken: Int = 0,
    val resolved: Int = 0,
    val pending: Int = 0,
) {
    val percent: Int get() = if (resolved == 0) 0 else Math.round(taken * 100f / resolved)
}

data class MedDetailUiState(
    val medication: Medication? = null,
    val schedule: Schedule? = null,
    val grid: List<HourHistory> = emptyList(),
    val days: List<LocalDate> = emptyList(),
    val stats: AdherenceStats = AdherenceStats(),
    val recent: List<DoseView> = emptyList(),
)

/**
 * Lays the medication's doses out as hour × day. Rows are the scheduled hours (plus any hour the
 * history actually contains, in case the pauta changed), columns are the last [days] days ending
 * today. Pure -> unit-testable.
 */
fun adherenceGrid(
    doses: List<DoseView>,
    schedule: Schedule?,
    today: LocalDate,
    days: Int = GRID_DAYS,
): Pair<List<LocalDate>, List<HourHistory>> {
    val window = (0 until days).map { today.minusDays((days - 1 - it).toLong()) }
    val inWindow = doses.filter { it.scheduledAt.toLocalDate() in window }
    val hours = (schedule?.times.orEmpty() + inWindow.map { it.scheduledAt.toLocalTime() })
        .distinct()
        .sorted()
        .take(MAX_GRID_ROWS)
    val byHourAndDay = inWindow.associateBy { it.scheduledAt.toLocalTime() to it.scheduledAt.toLocalDate() }
    val rows = hours.map { hour ->
        HourHistory(hour, window.map { day -> byHourAndDay[hour to day]?.status?.toCellState() })
    }
    return window to rows
}

/** Per-dose adherence over the window. Pure. */
fun adherenceStats(doses: List<DoseView>, today: LocalDate, days: Int = GRID_DAYS): AdherenceStats {
    val from = today.minusDays((days - 1).toLong())
    val window = doses.filter { it.scheduledAt.toLocalDate() >= from && it.scheduledAt.toLocalDate() <= today }
    val pending = window.count { it.status == DoseStatus.PENDING }
    return AdherenceStats(
        taken = window.count { it.status == DoseStatus.TAKEN },
        resolved = window.size - pending,
        pending = pending,
    )
}

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
            val today = LocalDate.now()
            val (days, grid) = adherenceGrid(history, schedule, today)
            MedDetailUiState(
                medication = medication,
                schedule = schedule,
                grid = grid,
                days = days,
                stats = adherenceStats(history, today),
                recent = history.filter { it.scheduledAt.toLocalDate() <= today }.take(6),
            )
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
