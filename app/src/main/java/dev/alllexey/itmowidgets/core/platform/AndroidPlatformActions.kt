package dev.alllexey.itmowidgets.core.platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.net.toUri
import dev.alllexey.itmowidgets.core.location.MapDestination
import dev.alllexey.itmowidgets.core.ui.shareTextIntent
import dev.alllexey.itmowidgets.core.url.HttpsNavigationPolicy
import dev.alllexey.itmowidgets.core.url.TelegramLinks

/**
 * [PlatformActions] over activities, with the intents of the View screens: `ShareText.kt`, `LinkOpener.kt` and
 * `MapLauncher.kt`. [context] is the host's; outside an activity a new task is started.
 */
class AndroidPlatformActions(private val context: Context) : PlatformActions {

    override fun shareText(title: String, text: String): Boolean = start(shareTextIntent(title, text))

    override fun openLink(url: String): Boolean {
        if (!HttpsNavigationPolicy.isNavigable(url)) return false
        // Telegram links go straight to the client; without it the https page is the fallback.
        val telegram = TelegramLinks.deepLink(url)
        if (telegram != null && start(Intent(Intent.ACTION_VIEW, telegram.toUri()))) return true
        return start(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
    }

    override fun openMap(destination: MapDestination): Boolean =
        start(Intent(Intent.ACTION_VIEW, destination.geoUri().toUri()))

    override fun openAppSettings(): Boolean = start(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    )

    override fun openNotificationSettings(): Boolean = start(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    )

    private fun start(intent: Intent): Boolean {
        if (context.findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
