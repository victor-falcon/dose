package com.victorfalcon.dose

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.victorfalcon.dose.reminder.ReminderWorker
import com.victorfalcon.dose.reminder.Reminders
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class DoseApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    // WorkManager on-demand init so @HiltWorker follow-up/missed-dose workers get injected.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Hourly backstop sweep (re-nag cadence stays alarm-driven; this is the safety net).
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            Reminders.UNIQUE_SWEEP_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.HOURS).build(),
        )
    }
}
