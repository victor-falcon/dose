package com.victorfalcon.dose.data.di

import android.content.Context
import androidx.room.Room
import com.victorfalcon.dose.data.DoseDao
import com.victorfalcon.dose.data.DoseDatabase
import com.victorfalcon.dose.data.RoomMedicationRepository
import com.victorfalcon.dose.domain.repository.MedicationRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DoseDatabase =
        // ponytail: destructive fallback is fine pre-v1 (schema not public yet).
        // Add real migrations before shipping v1.
        Room.databaseBuilder(context, DoseDatabase::class.java, "dose.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideDoseDao(database: DoseDatabase): DoseDao = database.doseDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindMedicationRepository(impl: RoomMedicationRepository): MedicationRepository
}
