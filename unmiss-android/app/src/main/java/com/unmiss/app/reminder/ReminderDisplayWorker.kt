package com.unmiss.app.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.unmiss.app.MainActivity
import com.unmiss.app.R
import com.unmiss.app.data.ServiceLocator
import java.util.concurrent.TimeUnit

class ReminderDisplayWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_REMINDER_ID) ?: return Result.failure()
        val dao = ServiceLocator.get().reminderDao
        val reminder = dao.find(id) ?: return Result.success()
        if (reminder.status != "pending" || reminder.displayedAt != null) return Result.success()
        if (
            android.os.Build.VERSION.SDK_INT >= 33 &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        ensureChannel(applicationContext)
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            id.hashCode(),
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(reminder.title)
            .setContentText(reminder.description ?: reminder.reason ?: "待处理事项")
            .setContentIntent(contentIntent)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "已完成", actionIntent(applicationContext, id, ReminderActionReceiver.ACTION_DONE))
            .addAction(0, "稍后提醒", actionIntent(applicationContext, id, ReminderActionReceiver.ACTION_SNOOZE))
            .addAction(0, "忽略", actionIntent(applicationContext, id, ReminderActionReceiver.ACTION_IGNORE))
            .build()
        NotificationManagerCompat.from(applicationContext).notify(id.hashCode(), notification)
        dao.markDisplayed(id, System.currentTimeMillis())
        return Result.success()
    }

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
        const val CHANNEL_ID = "unmiss_reminders"

        fun schedule(context: Context, reminderId: String, delayMillis: Long) {
            val request = OneTimeWorkRequestBuilder<ReminderDisplayWorker>()
                .setInputData(workDataOf(KEY_REMINDER_ID to reminderId))
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "unmiss-reminder-display-$reminderId",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Unmiss 提醒", NotificationManager.IMPORTANCE_HIGH),
            )
        }

        private fun actionIntent(context: Context, id: String, action: String): PendingIntent {
            val intent = Intent(context, ReminderActionReceiver::class.java)
                .setAction(action)
                .putExtra(KEY_REMINDER_ID, id)
            return PendingIntent.getBroadcast(
                context,
                31 * id.hashCode() + action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
