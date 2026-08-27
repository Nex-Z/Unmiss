package com.unmiss.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "unmiss_settings")

class SettingsDataStore(private val context: Context) {

    val captureEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[CAPTURE_ENABLED_KEY] ?: true
    }

    suspend fun captureEnabledOnce(): Boolean = captureEnabled.first()

    suspend fun setCaptureEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[CAPTURE_ENABLED_KEY] = enabled }
    }

    val liquidGlassEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[LIQUID_GLASS_ENABLED_KEY] ?: true
    }

    suspend fun liquidGlassEnabledOnce(): Boolean = liquidGlassEnabled.first()

    suspend fun setLiquidGlassEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[LIQUID_GLASS_ENABLED_KEY] = enabled }
    }

    val liquidGlassIntensity: Flow<Float> = context.dataStore.data.map { prefs ->
        (prefs[LIQUID_GLASS_INTENSITY_KEY] ?: DEFAULT_LIQUID_GLASS_INTENSITY)
            .coerceIn(0f, 1f)
    }

    suspend fun liquidGlassIntensityOnce(): Float = liquidGlassIntensity.first()

    suspend fun setLiquidGlassIntensity(intensity: Float) {
        context.dataStore.edit { prefs ->
            prefs[LIQUID_GLASS_INTENSITY_KEY] = intensity.coerceIn(0f, 1f)
        }
    }

    val enabledPackages: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[ENABLED_PACKAGES_KEY] ?: emptySet()
    }

    suspend fun enabledPackagesOnce(): Set<String> = enabledPackages.first()

    suspend fun ensureDefaultPackages(installedPackages: Set<String>): Set<String> {
        var result = emptySet<String>()
        context.dataStore.edit { prefs ->
            val current = prefs[ENABLED_PACKAGES_KEY] ?: emptySet()
            result = if (prefs[ALLOWLIST_INITIALIZED_KEY] == true) {
                current
            } else {
                (current + DEFAULT_COMMUNICATION_PACKAGES.intersect(installedPackages)).also {
                    prefs[ENABLED_PACKAGES_KEY] = it
                    prefs[ALLOWLIST_INITIALIZED_KEY] = true
                }
            }
        }
        return result
    }

    suspend fun setPackageEnabled(packageName: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[ENABLED_PACKAGES_KEY] ?: emptySet()
            prefs[ENABLED_PACKAGES_KEY] =
                if (enabled) current + packageName else current - packageName
        }
    }

    val baseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[BASE_URL_KEY] ?: DEFAULT_BASE_URL
    }

    suspend fun baseUrlOnce(): String = baseUrl.first()

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[BASE_URL_KEY] = url.trim().trimEnd('/')
        }
    }

    val analysisTimes: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[ANALYSIS_TIMES_KEY] ?: setOf(DEFAULT_ANALYSIS_TIME)
    }

    suspend fun analysisTimesOnce(): Set<String> = analysisTimes.first()

    suspend fun setAnalysisTimes(times: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[ANALYSIS_TIMES_KEY] = times
        }
    }

    val categoryWeights: Flow<Map<String, Int>> = context.dataStore.data.map { prefs ->
        CATEGORY_IDS.associateWith { id ->
            (prefs[intPreferencesKey("category_weight_$id")] ?: DEFAULT_CATEGORY_WEIGHT)
                .coerceIn(0, 5)
        }
    }

    suspend fun categoryWeightsOnce(): Map<String, Int> = categoryWeights.first()

    suspend fun setCategoryWeights(weights: Map<String, Int>) {
        context.dataStore.edit { prefs ->
            CATEGORY_IDS.forEach { id ->
                prefs[intPreferencesKey("category_weight_$id")] =
                    (weights[id] ?: DEFAULT_CATEGORY_WEIGHT).coerceIn(0, 5)
            }
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "http://10.0.2.2:3000/api/v1"

        private val ENABLED_PACKAGES_KEY = stringSetPreferencesKey("enabled_packages")
        private val BASE_URL_KEY = stringPreferencesKey("base_url")
        private val CAPTURE_ENABLED_KEY = booleanPreferencesKey("capture_enabled")
        private val LIQUID_GLASS_ENABLED_KEY = booleanPreferencesKey("liquid_glass_enabled")
        private val LIQUID_GLASS_INTENSITY_KEY = floatPreferencesKey("liquid_glass_intensity")
        private val ANALYSIS_TIMES_KEY = stringSetPreferencesKey("analysis_times")
        private val ALLOWLIST_INITIALIZED_KEY = booleanPreferencesKey("allowlist_initialized")
        const val DEFAULT_ANALYSIS_TIME = "22:00"
        const val DEFAULT_LIQUID_GLASS_INTENSITY = 1f
        const val DEFAULT_CATEGORY_WEIGHT = 3
        val CATEGORY_IDS = listOf(
            "work", "life", "finance", "health", "social", "entertainment", "other",
        )

        private val DEFAULT_COMMUNICATION_PACKAGES = setOf(
            "com.tencent.mm",
            "com.tencent.mobileqq",
            "com.tencent.wework",
            "com.alibaba.android.rimet",
            "com.ss.android.lark",
            "com.whatsapp",
            "org.telegram.messenger",
            "com.google.android.apps.messaging",
            "com.android.mms",
        )
    }
}
