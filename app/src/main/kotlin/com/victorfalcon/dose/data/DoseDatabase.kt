package com.victorfalcon.dose.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.victorfalcon.dose.domain.model.DoseOccurrence
import com.victorfalcon.dose.domain.model.Medication
import com.victorfalcon.dose.domain.model.Schedule

// exportSchema = false: no schema JSON to diff against, so every version bump from here on
// needs its migration hand-written in DatabaseModule.
@Database(
    entities = [Medication::class, Schedule::class, DoseOccurrence::class],
    version = 3, // v2: Medication.image; v3: DoseOccurrence.snoozedUntil
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class DoseDatabase : RoomDatabase() {
    abstract fun doseDao(): DoseDao
}
