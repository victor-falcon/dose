package com.victorfalcon.dose.widget

import com.victorfalcon.dose.domain.repository.MedicationRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Glance widgets aren't Hilt-injected; grab dependencies through this entry point. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun repository(): MedicationRepository
}
