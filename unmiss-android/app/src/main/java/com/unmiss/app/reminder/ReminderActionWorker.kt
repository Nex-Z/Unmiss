package com.unmiss.app.reminder

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.unmiss.app.data.ServiceLocator
import java.time.Instant
import java.time.temporal.ChronoUnit

class ReminderActionWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_REMINDER_ID) ?: return Result.failure()
        val action = inputData.getString(KEY_ACTION) ?: return Result.failure()
        return try {
            val repository = ServiceLocator.get().reminderRepository
            when (action) {
                ReminderActionReceiver.ACTION_DONE -> repository.done(id)
                ReminderActionReceiver.ACTION_IGNORE -> repository.ignore(id)
                ReminderActionReceiver.ACTION_SNOOZE -> {
                    val remindAt = Instant.now().plus(1, ChronoUnit.HOURS)
                    repository.snooze(id, remindAt)
                    ReminderDisplayWorker.schedule(applicationContext, id, 3_600_000)
                }
                else -> return Result.failure()
            }
            NotificationManagerCompat.from(applicationContext).cancel(id.hashCode())
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
        const val KEY_ACTION = "action"
    }
}
