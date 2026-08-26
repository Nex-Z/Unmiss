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
data class NotificationUploadBatchRequest(
    val notifications: List<NotificationUploadRequest>,
)

@Serializable
data class NotificationUploadBatchResponse(
    val accepted: Int,
    val created: Int,
)

@Serializable
data class DeleteDataResponse(val deleted: Boolean)

@Serializable
data class AnalysisScheduleDto(
    val times: List<String>,
    val timezone: String,
    @SerialName("lastRunAt") val lastRunAt: String? = null,
)

@Serializable
data class UpdateAnalysisScheduleRequest(
    val times: List<String>,
    val timezone: String,
)

@Serializable
data class ReminderDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val reason: String? = null,
    val importance: Int? = null,
    val quadrant: String = "important_not_urgent",
    val status: String,
    @SerialName("remindAt") val remindAt: String,
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("updatedAt") val updatedAt: String? = null,
    @SerialName("completedAt") val completedAt: String? = null,
)

@Serializable
data class AnalysisRunDto(
    val id: String,
    val status: String,
    @SerialName("notificationCount") val notificationCount: Int,
    @SerialName("reminderCount") val reminderCount: Int,
    @SerialName("updateCount") val updateCount: Int,
    val error: String? = null,
    val result: AnalysisRunResultDto? = null,
    @SerialName("startedAt") val startedAt: String,
    @SerialName("completedAt") val completedAt: String? = null,
)

@Serializable
data class AnalysisRunResultDto(
    val reminders: List<AnalysisRunReminderDto> = emptyList(),
    val updates: List<AnalysisRunUpdateDto> = emptyList(),
)

@Serializable
data class AnalysisRunReminderDto(
    val title: String,
    val reason: String? = null,
    val quadrant: String? = null,
)

@Serializable
data class AnalysisRunUpdateDto(
    val action: String,
    val title: String? = null,
    val reason: String? = null,
)

@Serializable
data class SnoozeReminderRequest(
    @SerialName("remindAt") val remindAt: String,
)
