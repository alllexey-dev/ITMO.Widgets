package dev.alllexey.itmowidgets.feature.settings.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.feature.settings.domain.BackgroundWorkScreen
import dev.alllexey.itmowidgets.feature.settings.domain.BackgroundWorkScreens

/**
 * Opens the first system page of this device where the user can let the app work in the background; false when none
 * opens. The app does not hold `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (Google Play policy), so it only shows the page.
 */
fun Context.openBackgroundWorkSettings(): Boolean =
    BackgroundWorkScreens.forDevice(Build.MANUFACTURER, Build.BRAND).any { screen ->
        try {
            startActivity(intentFor(screen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            // MIUI builds that do not export the page throw this instead.
            false
        }
    }

private fun Context.intentFor(screen: BackgroundWorkScreen): Intent = when (screen) {
    BackgroundWorkScreen.MIUI_POWER_KEEPER -> Intent()
        .setComponent(ComponentName(MIUI_POWER_KEEPER, "$MIUI_POWER_KEEPER.ui.HiddenAppsConfigActivity"))
        .putExtra("package_name", packageName)
        .putExtra("package_label", getString(R.string.app_name))
    BackgroundWorkScreen.BATTERY_OPTIMIZATION_LIST -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    BackgroundWorkScreen.APP_DETAILS ->
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
}

private const val MIUI_POWER_KEEPER = "com.miui.powerkeeper"
