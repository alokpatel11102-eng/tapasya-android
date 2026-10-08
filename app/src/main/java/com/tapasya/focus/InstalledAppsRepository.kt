package com.tapasya.focus

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

data class InstalledLaunchableApp(
    val packageName: String,
    val label: String
)

object InstalledAppsRepository {
    private val suggestedPackages = setOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.facebook.katana"
    )
    private val protectedPackages = setOf(
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.emergency",
        "com.google.android.apps.safetyhub",
        "com.android.settings",
        "com.google.android.settings"
    )

    fun loadLaunchableApps(context: Context): List<InstalledLaunchableApp> {
        val packageManager = context.packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launchIntent,
                PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launchIntent, 0)
        }

        return resolved.asSequence()
            .mapNotNull { info ->
                val appInfo = info.activityInfo?.applicationInfo ?: return@mapNotNull null
                val packageName = appInfo.packageName
                if (packageName == context.packageName || packageName in protectedPackages) {
                    return@mapNotNull null
                }
                val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                if (label.isBlank()) return@mapNotNull null
                InstalledLaunchableApp(packageName, label)
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }

    fun defaultSuggestedSelection(apps: List<InstalledLaunchableApp>): Set<String> {
        val installedPackages = apps.asSequence().map { it.packageName }.toSet()
        return suggestedPackages.intersect(installedPackages)
    }
}
