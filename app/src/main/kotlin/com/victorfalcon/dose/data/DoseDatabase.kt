package com.victorfalcon.dose.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.victorfalcon.dose.domain.model.DoseOccurrence
import com.victorfalcon.dose.domain.model.Medication
import com.victorfalcon.dose.domain.model.Schedule

// exportSchema = false: no migrations before v1 ships; revisit when the schema is public.
@Database(
    entities = [Medication::class, Schedule::class, DoseOccurrence::class],
    version = 2, // v2: Medication.image
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class DoseDatabase : RoomDatabase() {
    abstract fun doseDao(): DoseDao
}
