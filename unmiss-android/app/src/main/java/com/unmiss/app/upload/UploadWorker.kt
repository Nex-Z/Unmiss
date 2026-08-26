package com.unmiss.app.upload

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
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
        var uploadedAny = false
        return try {
            var processedBatches = 0
            while (processedBatches < MAX_BATCHES_PER_RUN) {
                val entries = dao.pending(BATCH_SIZE)
                if (entries.isEmpty()) break
                processedBatches += 1
                try {
                    repository.uploadBatch(entries)
                    dao.markUploaded(entries.map { it.id }, System.currentTimeMillis())
                    uploadedAny = true
                } catch (error: Exception) {
                    dao.markFailed(
                        entries.map { it.id },
                        error.message ?: error.javaClass.simpleName,
                    )
                    return Result.retry()
                }
                if (entries.size < BATCH_SIZE) break
            }
            if (uploadedAny) {
                ReminderSyncWorker.enqueueNow(applicationContext)
                ReminderSyncWorker.enqueueFollowUp(applicationContext)
            }
            dao.deleteUploadedBefore(System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000)
            if (dao.pending(1).isNotEmpty()) enqueueContinuation(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "unmiss-notification-upload"
        private const val BATCH_SIZE = 100
        private const val MAX_BATCHES_PER_RUN = 5
        private const val PERIODIC_WORK_NAME = "unmiss-notification-upload-safety"

        fun schedule(context: Context) {
            val periodic = PeriodicWorkRequestBuilder<UploadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic,
            )
            enqueue(context)
        }

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request(initialDelaySeconds = 0),
            )
        }

        fun enqueueNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request(initialDelaySeconds = 0),
            )
        }

        private fun enqueueContinuation(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request(initialDelaySeconds = 0),
            )
        }

        private fun request(initialDelaySeconds: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(
                    networkConstraints(),
                )
                .setInitialDelay(initialDelaySeconds, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                .build()

        private fun networkConstraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
