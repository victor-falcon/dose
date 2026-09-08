package com.victorfalcon.dose.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
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

    /**
     * Snooze targets became a column, and real medication histories now live on real phones —
     * so this jump gets a hand-written migration. INTEGER because `Converters` stores a
     * `LocalDateTime` as UTC epoch-seconds; nullable, no default, matching `takenAt`.
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE dose_occurrences ADD COLUMN snoozedUntil INTEGER")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DoseDatabase =
        Room.databaseBuilder(context, DoseDatabase::class.java, "dose.db")
            .addMigrations(MIGRATION_2_3)
            // Only the net for version jumps nobody wrote a migration for. Anything reachable
            // from a released build must be listed above instead: dropping the tables here
            // would take the user's medication history with it.
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
