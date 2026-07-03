package com.victorfalcon.dose.widget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pushes a widget refresh after data changes. Injected into the repository so every
 * write updates the widget. ponytail: updateAll on each write is fine at personal scale;
 * batch/debounce only if a widget ever proves too chatty.
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // A widget update failure must never roll back / crash the DB write that triggered it.
    suspend fun refresh() {
        runCatching { refreshWidgetState(context) }
    }
}
