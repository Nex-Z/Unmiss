package com.unmiss.app.upload

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.unmiss.app.data.ServiceLocator
import com.unmiss.app.reminder.ReminderSyncWorker
import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit

@Serializable
data class NotificationPayload(
    val notificationKey: String,
    val packageName: String,
    val title: String?,
    val body: String?,
    val subText: String?,
    val postedAt: String,
)

object PayloadCodec {
    private val json = kotlinx.serialization.json.Json { encodeDefaults = true }

    fun encode(payload: NotificationPayload): String = json.encodeToString(payload)

    fun decode(raw: String): NotificationPayload =
        json.decodeFromString<NotificationPayload>(raw)
}

class UploadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dao = ServiceLocator.get().pendingDao
        val repository = ServiceLocator.get().notificationRepository
        return try {
            val pending = dao.pending(50)
            var uploadedAny = false
            for (entry in pending) {
                try {
                    repository.upload(entry)
                    dao.markUploaded(entry.id, System.currentTimeMillis())
                    uploadedAny = true
                } catch (e: Exception) {
                    dao.markFailed(entry.id, e.message ?: e.javaClass.simpleName)
                }
            }
            if (uploadedAny) {
                ReminderSyncWorker.enqueueNow(applicationContext)
                ReminderSyncWorker.enqueueFollowUp(applicationContext)
            }
            // Keep a useful local audit trail while bounding the database size.
            dao.deleteUploadedBefore(System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000)
            if (dao.pending(50).isEmpty()) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "unmiss-notification-upload"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
