package dev.alllexey.itmowidgets.core.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import dev.alllexey.itmowidgets.core.navigation.WidgetProviders

/**
 * Asks the launcher to pin a widget and reports the ones it accepted, by
 * provider class name (see [WidgetProviders]).
 *
 * The confirmation happens in the launcher, with the screen stopped, so the
 * receiver is tied to the Fragment instance rather than to its view.
 */
class WidgetPinRequester(context: Context) {

    private val appContext = context.applicationContext
    private val manager = AppWidgetManager.getInstance(appContext)
    private var receiver: BroadcastReceiver? = null

    val isSupported: Boolean
        get() = try {
            manager.isRequestPinAppWidgetSupported
        } catch (_: IllegalStateException) {
            false
        }

    fun start(onPinned: (providerClassName: String) -> Unit) {
        if (receiver != null) return
        val created = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                onPinned(intent.getStringExtra(EXTRA_PROVIDER) ?: return)
            }
        }
        receiver = created
        ContextCompat.registerReceiver(
            appContext,
            created,
            IntentFilter(ACTION_PINNED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun stop() {
        receiver?.let(appContext::unregisterReceiver)
        receiver = null
    }

    /** Returns false when the launcher refused to show the dialog at all. */
    fun request(providerClassName: String): Boolean {
        val provider = ComponentName(appContext, providerClassName)
        return try {
            manager.requestPinAppWidget(provider, null, successCallback(providerClassName))
        } catch (_: IllegalStateException) {
            false
        }
    }

    private fun successCallback(providerClassName: String): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        providerClassName.hashCode(),
        Intent(ACTION_PINNED)
            .setPackage(appContext.packageName)
            .putExtra(EXTRA_PROVIDER, providerClassName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private companion object {
        const val ACTION_PINNED = "dev.alllexey.itmowidgets.action.WIDGET_PINNED"
        const val EXTRA_PROVIDER = "widget_provider"
    }
}
