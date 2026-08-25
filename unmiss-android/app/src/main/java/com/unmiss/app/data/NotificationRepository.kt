package com.unmiss.app.data

import android.os.Build
import com.unmiss.app.data.db.PendingNotificationUpload
import com.unmiss.app.data.remote.DeviceRegisterRequest
import com.unmiss.app.data.remote.NotificationUploadRequest
import com.unmiss.app.data.remote.NotificationUploadBatchRequest
import com.unmiss.app.upload.PayloadCodec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

class NotificationRepository(private val container: AppContainer) {

    private val registerMutex = Mutex()

    suspend fun ensureRegistered(): Pair<String, String> {
        container.tokenStore.deviceId?.let { deviceId ->
            container.tokenStore.deviceToken?.let { token -> return deviceId to token }
        }
        return registerMutex.withLock {
            container.tokenStore.deviceId?.let { deviceId ->
                container.tokenStore.deviceToken?.let { token -> return deviceId to token }
            }
            val api = container.apiFactory.create(container.settingsDataStore.baseUrlOnce())
            val response = api.registerDevice(
                DeviceRegisterRequest(name = "${Build.MANUFACTURER} ${Build.MODEL}"),
            )
            container.tokenStore.deviceToken = response.token
            container.tokenStore.deviceId = response.device.id
            response.device.id to response.token
        }
    }

    suspend fun upload(entry: PendingNotificationUpload) {
        val payload = PayloadCodec.decode(entry.payloadJson)
        repeat(2) { attempt ->
            val (deviceId, _) = ensureRegistered()
            val api = container.apiFactory.create(container.settingsDataStore.baseUrlOnce())
            val response = api.uploadNotification(
                NotificationUploadRequest(
                    deviceId = deviceId,
                    notificationKey = payload.notificationKey,
                    packageName = payload.packageName,
                    title = payload.title,
                    body = payload.body,
                    subText = payload.subText,
                    postedAt = payload.postedAt,
                    timezone = ZoneId.systemDefault().id,
                ),
            )
            if (response.isSuccessful) return
            if (response.code() == 401 && attempt == 0) {
                container.tokenStore.clear()
            } else {
                throw IllegalStateException("upload failed: HTTP ${response.code()}")
            }
        }
        throw IllegalStateException("upload failed after re-registration")
    }

    suspend fun uploadBatch(entries: List<PendingNotificationUpload>) {
        if (entries.isEmpty()) return
        val payloads = entries.map { PayloadCodec.decode(it.payloadJson) }
        repeat(2) { attempt ->
            val (deviceId, _) = ensureRegistered()
            val api = container.apiFactory.create(container.settingsDataStore.baseUrlOnce())
            val response = api.uploadNotifications(
                NotificationUploadBatchRequest(
                    notifications = payloads.map { payload ->
                        NotificationUploadRequest(
                            deviceId = deviceId,
                            notificationKey = payload.notificationKey.take(512),
                            packageName = payload.packageName.take(256),
                            title = payload.title?.take(1024),
                            body = payload.body?.take(4096),
                            subText = payload.subText?.take(512),
                            postedAt = payload.postedAt,
                            timezone = ZoneId.systemDefault().id,
                        )
                    },
                ),
            )
            if (response.isSuccessful) return
            if (response.code() == 401 && attempt == 0) {
                container.tokenStore.clear()
            } else {
                throw IllegalStateException("batch upload failed: HTTP ${response.code()}")
            }
        }
        throw IllegalStateException("batch upload failed after re-registration")
    }
}
