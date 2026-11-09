package io.github.ikaridevgit.unofficialmonsender.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

fun Context.appLabel(packageName: String): String? = try {
    packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
} catch (_: PackageManager.NameNotFoundException) {
    null
}

data class InstalledApp(val packageName: String, val label: String)

/** Launchable apps only: those are the ones a user copies links from. */
fun Context.launchableApps(): List<InstalledApp> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return packageManager.queryIntentActivities(intent, 0)
        .map { it.activityInfo.packageName }
        .distinct()
        .filter { it != packageName }
        .map { InstalledApp(it, appLabel(it) ?: it) }
        .sortedBy { it.label.lowercase() }
}
