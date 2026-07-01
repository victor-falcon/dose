package com.victorfalcon.dose.data.backup

import com.victorfalcon.dose.data.DoseDao
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Serializes the whole database to/from JSON. File IO and alarm re-arm live in the caller. */
class BackupManager @Inject constructor(
    private val dao: DoseDao,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun export(): String {
        val backup = Backup(
            medications = dao.getAllMedications().map { it.toBackup() },
            schedules = dao.getAllSchedules().map { it.toBackup() },
            occurrences = dao.getAllOccurrences().map { it.toBackup() },
        )
        return json.encodeToString(backup)
    }

    /** Replace the database with the backup's contents. */
    suspend fun import(text: String) {
        val backup = json.decodeFromString<Backup>(text)
        dao.replaceAll(
            medications = backup.medications.map { it.toDomain() },
            schedules = backup.schedules.map { it.toDomain() },
            occurrences = backup.occurrences.map { it.toDomain() },
        )
    }
}
