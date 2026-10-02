package com.hrithikvish.nanobot.ui.appfunctions

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Provides application metadata (display name and icon) from the Android [PackageManager]. */
@Singleton
open class AppDetailsProvider @Inject constructor(
    @param:ApplicationContext private val context: Context?,
) {
    open fun getAppInfo(packageName: String): Pair<String, Drawable?> {
        val pm = context?.packageManager ?: return Pair(packageName, null)
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val name = pm.getApplicationLabel(appInfo).toString()
            val icon = pm.getApplicationIcon(appInfo)
            Pair(name, icon)
        } catch (e: Exception) {
            val fallbackName = packageName.substringAfterLast('.').replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
            Pair(fallbackName, null)
        }
    }
}
