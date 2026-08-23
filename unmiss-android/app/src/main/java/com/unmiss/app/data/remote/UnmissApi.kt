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

    @retrofit2.http.GET("reminders/pending")
    suspend fun pendingReminders(): List<ReminderDto>

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
