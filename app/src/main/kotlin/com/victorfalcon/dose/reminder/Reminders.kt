package com.victorfalcon.dose.reminder

/** Shared constants for the reminder engine (channel, intent actions, extras). */
object Reminders {
    const val CHANNEL_ID = "dose_reminders"

    const val ACTION_FIRE = "com.victorfalcon.dose.action.FIRE"
    const val ACTION_TAKEN = "com.victorfalcon.dose.action.TAKEN"
    const val ACTION_SKIP = "com.victorfalcon.dose.action.SKIP"
    const val ACTION_SNOOZE = "com.victorfalcon.dose.action.SNOOZE"

    const val EXTRA_OCCURRENCE_ID = "occurrenceId"

    const val UNIQUE_SWEEP_WORK = "reminder-sweep"

    // How far ahead to arm exact alarms; the sweep worker tops this up.
    const val WINDOW_HOURS = 48L

    // ponytail: request codes pack the occurrence id with a per-action offset so the
    // four PendingIntents of one dose stay distinct. Assumes id * 10 fits in an Int
    // (~214M doses); revisit only if that ever becomes plausible.
    fun requestCode(occurrenceId: Long, actionOffset: Int): Int = (occurrenceId * 10 + actionOffset).toInt()
}
