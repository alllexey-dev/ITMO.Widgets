package dev.alllexey.itmowidgets.feature.onboarding.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import dev.alllexey.itmowidgets.core.navigation.WidgetProviders
import dev.alllexey.itmowidgets.feature.onboarding.presentation.WidgetKind

/**
 * Asks the launcher to pin a widget and reports the ones it accepted.
 *
 * The confirmation happens in the launcher, with this screen stopped, so the
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

    fun start(onPinned: (WidgetKind) -> Unit) {
        if (receiver != null) return
        val created = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val kind = WidgetKind.entries
                    .firstOrNull { it.name == intent.getStringExtra(EXTRA_KIND) }
                    ?: return
                onPinned(kind)
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
    fun request(kind: WidgetKind): Boolean {
        val provider = ComponentName(appContext, kind.providerClassName())
        return try {
            manager.requestPinAppWidget(provider, null, successCallback(kind))
        } catch (_: IllegalStateException) {
            false
        }
    }

    private fun successCallback(kind: WidgetKind): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        kind.ordinal,
        Intent(ACTION_PINNED)
            .setPackage(appContext.packageName)
            .putExtra(EXTRA_KIND, kind.name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun WidgetKind.providerClassName(): String = when (this) {
        WidgetKind.SINGLE_LESSON -> WidgetProviders.SINGLE_LESSON
        WidgetKind.DAY_SCHEDULE -> WidgetProviders.DAY_SCHEDULE
        WidgetKind.QR -> WidgetProviders.QR_CODE
    }

    private companion object {
        const val ACTION_PINNED = "dev.alllexey.itmowidgets.action.WIDGET_PINNED"
        const val EXTRA_KIND = "widget_kind"
    }
}
