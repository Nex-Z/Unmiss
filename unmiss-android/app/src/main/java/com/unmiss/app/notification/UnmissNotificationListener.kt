package com.unmiss.app.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.unmiss.app.data.ServiceLocator
import com.unmiss.app.data.db.PendingNotificationUpload
import com.unmiss.app.upload.NotificationPayload
import com.unmiss.app.upload.PayloadCodec
import com.unmiss.app.upload.UploadWorker
import kotlinx.coroutines.launch
import java.time.Instant

class UnmissNotificationListener : NotificationListenerService() {

    private val container by lazy { ServiceLocator.get() }

    override fun onListenerConnected() {
        super.onListenerConnected()
        UploadWorker.enqueueNow(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return
        if (packageName == this.packageName) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        if (text.isNullOrBlank()) return

        val notificationKey = sbn.key ?: return

        container.applicationScope.launch {
            try {
                if (!container.settingsDataStore.captureEnabledOnce()) return@launch
                val enabled = container.settingsDataStore.enabledPackagesOnce()
                if (packageName !in enabled) return@launch

                val payload = NotificationPayload(
                    notificationKey = notificationKey,
                    packageName = packageName,
                    title = title,
                    body = text,
                    subText = subText,
                    postedAt = Instant.ofEpochMilli(sbn.postTime).toString(),
                )
                container.pendingDao.insert(
                    PendingNotificationUpload(
                        notificationKey = notificationKey,
                        payloadJson = PayloadCodec.encode(payload),
                        createdAt = System.currentTimeMillis(),
                        packageName = packageName,
                        title = title,
                        body = text,
                        postedAt = sbn.postTime,
                    ),
                )
                UploadWorker.enqueue(this@UnmissNotificationListener)
            } catch (error: Exception) {
                Log.w("UnmissListener", "Failed to queue notification from $packageName", error)
            }
        }
    }
}
