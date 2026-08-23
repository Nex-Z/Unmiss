package com.unmiss.app.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

data class InstalledApp(
    val packageName: String,
    val displayName: String,
)

class AppCatalog(private val context: Context) {

    fun loadUserVisibleApps(): List<InstalledApp> {
        val pm = context.packageManager
        val launchIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launchIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launchIntent, 0)
        }
        return resolveInfos.asSequence()
            .mapNotNull { it.activityInfo?.applicationInfo }
            .filter { it.packageName != context.packageName }
            .filter { it.packageName !in EXCLUDED_PACKAGES }
            .distinctBy { it.packageName }
            .map { appInfo ->
                InstalledApp(
                    packageName = appInfo.packageName,
                    displayName = pm.getApplicationLabel(appInfo).toString(),
                )
            }
            .sortedBy { it.displayName.lowercase() }
            .toList()
    }

    private companion object {
        val EXCLUDED_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
        )
    }
}
