package com.unmiss.app.reminder

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.unmiss.app.data.ServiceLocator
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

class ReminderSyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val reminders = ServiceLocator.get().reminderRepository.sync()
        reminders.forEach { reminder ->
            ReminderDisplayWorker.schedule(
                applicationContext,
                reminder.id,
                Duration.between(Instant.now(), Instant.parse(reminder.remindAt))
                    .toMillis()
                    .coerceAtLeast(0),
            )
        }
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }

    companion object {
        private const val PERIODIC_WORK = "unmiss-reminder-sync-periodic"
        private const val IMMEDIATE_WORK = "unmiss-reminder-sync-now"
        private const val FOLLOW_UP_WORK = "unmiss-reminder-sync-follow-up"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val periodic = PeriodicWorkRequestBuilder<ReminderSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic,
            )
            enqueueNow(context, constraints)
        }

        fun enqueueNow(context: Context) {
            enqueueNow(
                context,
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
        }

        fun enqueueFollowUp(context: Context) {
            val request = OneTimeWorkRequestBuilder<ReminderSyncWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setInitialDelay(60, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                FOLLOW_UP_WORK,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        private fun enqueueNow(context: Context, constraints: Constraints) {
            val immediate = OneTimeWorkRequestBuilder<ReminderSyncWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK,
                ExistingWorkPolicy.REPLACE,
                immediate,
            )
        }
    }
}
