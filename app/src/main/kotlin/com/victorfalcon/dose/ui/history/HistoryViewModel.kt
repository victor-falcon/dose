package com.victorfalcon.dose.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.DoseView
import com.victorfalcon.dose.domain.repository.MedicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import javax.inject.Inject
import kotlin.math.roundToInt

/** Per-day calendar indicator derived from that day's doses. Pure -> testable. */
enum class DayMark { NONE, SCHEDULED, ALL_TAKEN, PARTIAL, MISSED }

fun dayMark(doses: List<DoseView>): DayMark = when {
    doses.isEmpty() -> DayMark.NONE
    doses.any { it.status == DoseStatus.MISSED } -> DayMark.MISSED
    doses.all { it.status == DoseStatus.PENDING } -> DayMark.SCHEDULED
    doses.all { it.status == DoseStatus.TAKEN } -> DayMark.ALL_TAKEN
    else -> DayMark.PARTIAL
}

data class Adherence(val taken: Int, val total: Int) {
    val percent: Int? get() = if (total == 0) null else (100.0 * taken / total).roundToInt()
}

/** Adherence over the [days] before [now]: taken vs all past-due doses in that span. */
fun adherence(doses: List<DoseView>, now: LocalDateTime, days: Long): Adherence {
    val from = now.minusDays(days)
    val pastDue = doses.filter { !it.scheduledAt.isBefore(from) && !it.scheduledAt.isAfter(now) }
    return Adherence(taken = pastDue.count { it.status == DoseStatus.TAKEN }, total = pastDue.size)
}

data class HistoryUiState(
    val month: YearMonth,
    val selectedDate: LocalDate,
    val marks: Map<LocalDate, DayMark> = emptyMap(),
    val selectedDoses: List<DoseView> = emptyList(),
    val adherence7: Adherence = Adherence(0, 0),
    val adherence30: Adherence = Adherence(0, 0),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    repository: MedicationRepository,
) : ViewModel() {

    private val today = LocalDate.now()
    private val month = MutableStateFlow(YearMonth.from(today))
    private val selectedDate = MutableStateFlow(today)

    private val monthDoses = month.flatMapLatest { m ->
        repository.observeDosesBetween(m.atDay(1).atStartOfDay(), m.plusMonths(1).atDay(1).atStartOfDay())
    }
    private val recentDoses = repository.observeDosesBetween(
        today.minusDays(30).atStartOfDay(),
        today.plusDays(1).atStartOfDay(),
    )

    val uiState: StateFlow<HistoryUiState> =
        combine(month, selectedDate, monthDoses, recentDoses) { m, selected, doses, recent ->
            val now = LocalDateTime.now()
            HistoryUiState(
                month = m,
                selectedDate = selected,
                marks = doses.groupBy { it.scheduledAt.toLocalDate() }.mapValues { dayMark(it.value) },
                selectedDoses = doses.filter { it.scheduledAt.toLocalDate() == selected }
                    .sortedBy { it.scheduledAt },
                adherence7 = adherence(recent, now, days = 7),
                adherence30 = adherence(recent, now, days = 30),
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            HistoryUiState(month = YearMonth.from(today), selectedDate = today),
        )

    fun showPreviousMonth() = month.update { it.minusMonths(1) }
    fun showNextMonth() = month.update { it.plusMonths(1) }
    fun selectDate(date: LocalDate) { selectedDate.value = date }
}
