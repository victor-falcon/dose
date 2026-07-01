package com.victorfalcon.dose.reminder

import com.victorfalcon.dose.domain.model.DoseStatus
import java.time.Duration
import java.time.LocalDateTime

/** What to do when a reminder alarm fires for an occurrence. Pure -> unit-testable. */
sealed interface ReminderStep {
    /** Already acted on (taken/skipped/missed): clear any notification, stop. */
    data object Done : ReminderStep

    /** Past the grace window while still pending: mark MISSED, stop. */
    data object Miss : ReminderStep

    /** Still pending: (re)post the notification and, if [nextAt] != null, re-nag then. */
    data class Notify(val nextAt: LocalDateTime?) : ReminderStep
}

/**
 * Reminder timing: nag at the scheduled time, re-nag every [RENAG_HOURS] while still
 * pending, and mark MISSED once [GRACE_HOURS] have passed with no action.
 */
object ReminderPolicy {
    const val GRACE_HOURS = 3L
    const val RENAG_HOURS = 1L

    fun step(scheduledAt: LocalDateTime, now: LocalDateTime, status: DoseStatus): ReminderStep {
        if (status != DoseStatus.PENDING) return ReminderStep.Done
        val elapsed = Duration.between(scheduledAt, now)
        if (elapsed >= Duration.ofHours(GRACE_HOURS)) return ReminderStep.Miss
        val elapsedHours = maxOf(0L, elapsed.toHours())
        val nextHours = minOf(elapsedHours + RENAG_HOURS, GRACE_HOURS)
        return ReminderStep.Notify(nextAt = scheduledAt.plusHours(nextHours))
    }
}
