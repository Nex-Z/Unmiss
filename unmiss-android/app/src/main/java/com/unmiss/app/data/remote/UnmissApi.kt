package com.unmiss.app.data.remote

import com.unmiss.app.data.token.TokenStore
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

interface UnmissApi {

    @retrofit2.http.POST("devices/register")
    suspend fun registerDevice(
        @retrofit2.http.Body body: DeviceRegisterRequest,
    ): DeviceRegisterResponse

    @retrofit2.http.POST("notifications")
    suspend fun uploadNotification(
        @retrofit2.http.Body body: NotificationUploadRequest,
    ): retrofit2.Response<Unit>

    @retrofit2.http.POST("notifications/batch")
    suspend fun uploadNotifications(
        @retrofit2.http.Body body: NotificationUploadBatchRequest,
    ): retrofit2.Response<NotificationUploadBatchResponse>

    @retrofit2.http.GET("devices/me/analysis-schedule")
    suspend fun analysisSchedule(): AnalysisScheduleDto

    @retrofit2.http.PUT("devices/me/analysis-schedule")
    suspend fun updateAnalysisSchedule(
        @retrofit2.http.Body body: UpdateAnalysisScheduleRequest,
    ): AnalysisScheduleDto

    @retrofit2.http.GET("devices/me/category-weights")
    suspend fun categoryWeights(): CategoryWeightsDto

    @retrofit2.http.PUT("devices/me/category-weights")
    suspend fun updateCategoryWeights(
        @retrofit2.http.Body body: CategoryWeightsDto,
    ): CategoryWeightsDto

    @retrofit2.http.GET("reminders/pending")
    suspend fun pendingReminders(): List<ReminderDto>

    @retrofit2.http.GET("reminders/inbox")
    suspend fun reminderInbox(): List<ReminderDto>

    @retrofit2.http.GET("reminders/history")
    suspend fun reminderHistory(): List<ReminderDto>

    @retrofit2.http.GET("analysis/runs")
    suspend fun analysisRuns(): List<AnalysisRunDto>

    @retrofit2.http.GET("analysis/quality")
    suspend fun qualityStats(): QualityStatsDto

    @retrofit2.http.POST("reminders/{id}/done")
    suspend fun completeReminder(
        @retrofit2.http.Path("id") id: String,
    ): ReminderDto

    @retrofit2.http.POST("reminders/{id}/ignore")
    suspend fun ignoreReminder(
        @retrofit2.http.Path("id") id: String,
    ): ReminderDto

    @retrofit2.http.POST("reminders/{id}/snooze")
    suspend fun snoozeReminder(
        @retrofit2.http.Path("id") id: String,
        @retrofit2.http.Body body: SnoozeReminderRequest,
    ): ReminderDto

    @retrofit2.http.POST("reminders/{id}/confirm")
    suspend fun confirmReminder(
        @retrofit2.http.Path("id") id: String,
        @retrofit2.http.Body body: SnoozeReminderRequest,
    ): ReminderDto

    @retrofit2.http.DELETE("devices/me/data")
    suspend fun deleteMyData(): DeleteDataResponse
}

class UnmissApiFactory(private val tokenStore: TokenStore) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val token = tokenStore.deviceToken
                val request = if (token.isNullOrBlank()) {
                    chain.request()
                } else {
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                }
                chain.proceed(request)
            }
            .build()
    }

    fun create(baseUrl: String): UnmissApi =
        Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(UnmissApi::class.java)
}
