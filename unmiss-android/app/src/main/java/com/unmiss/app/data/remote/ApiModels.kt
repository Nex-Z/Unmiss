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
data class CategoryWeightsDto(
    val work: Int = 3,
    val life: Int = 3,
    val finance: Int = 3,
    val health: Int = 3,
    val social: Int = 3,
    val entertainment: Int = 3,
    val other: Int = 3,
) {
    fun asMap(): Map<String, Int> = mapOf(
        "work" to work, "life" to life, "finance" to finance,
        "health" to health, "social" to social,
        "entertainment" to entertainment, "other" to other,
    )

    companion object {
        fun fromMap(values: Map<String, Int>) = CategoryWeightsDto(
            work = values["work"] ?: 3,
            life = values["life"] ?: 3,
            finance = values["finance"] ?: 3,
            health = values["health"] ?: 3,
            social = values["social"] ?: 3,
            entertainment = values["entertainment"] ?: 3,
            other = values["other"] ?: 3,
        )
    }
}

@Serializable
data class ReminderDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val reason: String? = null,
    val importance: Int? = null,
    val quadrant: String = "important_not_urgent",
    val category: String = "other",
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
data class QualityStatsDto(
    @SerialName("periodDays") val periodDays: Int,
    @SerialName("generatedAt") val generatedAt: String,
    val analysis: QualityAnalysisDto,
    val reminders: QualityReminderDto,
    val packages: List<QualityPackageDto> = emptyList(),
    val quadrants: List<QualityQuadrantDto> = emptyList(),
)

@Serializable
data class QualityAnalysisDto(
    val runs: Int,
    @SerialName("successfulRuns") val successfulRuns: Int,
    @SerialName("failedRuns") val failedRuns: Int,
    @SerialName("notificationsAnalyzed") val notificationsAnalyzed: Int,
)

@Serializable
data class QualityReminderDto(
    val created: Int,
    val active: Int,
    val completed: Int,
    val ignored: Int,
    val confirmed: Int,
    val snoozed: Int,
    val evaluated: Int,
    @SerialName("usefulRate") val usefulRate: Double? = null,
    @SerialName("ignoreRate") val ignoreRate: Double? = null,
)

@Serializable
data class QualityPackageDto(
    @SerialName("packageName") val packageName: String,
    val created: Int,
    val completed: Int,
    val ignored: Int,
)

@Serializable
data class QualityQuadrantDto(
    val quadrant: String,
    val count: Int,
)

@Serializable
data class SnoozeReminderRequest(
    @SerialName("remindAt") val remindAt: String,
)
