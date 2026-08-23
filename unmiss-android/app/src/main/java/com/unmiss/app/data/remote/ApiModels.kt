package com.unmiss.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceRegisterRequest(
    val platform: String = "android",
    val name: String,
)

@Serializable
data class DeviceDto(
    val id: String,
    val name: String? = null,
    val platform: String? = null,
)

@Serializable
data class DeviceRegisterResponse(
    val token: String,
    val device: DeviceDto,
)

@Serializable
data class NotificationUploadRequest(
    @SerialName("deviceId") val deviceId: String,
    @SerialName("notificationKey") val notificationKey: String,
    @SerialName("packageName") val packageName: String,
    val title: String?,
    val body: String?,
    @SerialName("subText") val subText: String?,
    @SerialName("postedAt") val postedAt: String,
    val timezone: String,
)

@Serializable
data class DeleteDataResponse(val deleted: Boolean)

@Serializable
data class ReminderDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val reason: String? = null,
    val importance: Int? = null,
    val status: String,
    @SerialName("remindAt") val remindAt: String,
)

@Serializable
data class SnoozeReminderRequest(
    @SerialName("remindAt") val remindAt: String,
)
