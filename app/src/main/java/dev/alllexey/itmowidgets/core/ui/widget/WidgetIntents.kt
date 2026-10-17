package dev.alllexey.itmowidgets.core.ui.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.alllexey.itmowidgets.core.navigation.AppEntryIntents
import dev.alllexey.itmowidgets.core.navigation.EntryRouteParser
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.ui.navigation.AppEntryIntentFactory

/**
 * What a widget's taps open, through the route queue of `MainActivity` (Recents never repeats them). Each target of a
 * widget gets its own data URI (`itmowidgets-widget://<widget>/<slot>`), since pending intents that differ only in
 * their extras are one intent to the system.
 */
object WidgetIntents {

    fun schedule(context: Context, widgetId: Int, slot: String = "schedule"): PendingIntent =
        pending(context, widgetId, slot, AppEntryIntents.ACTION_OPEN_SCHEDULE)

    fun sport(context: Context, widgetId: Int, slot: String = "sport", lessonId: Long? = null, predicted: Boolean = false) =
        pending(context, widgetId, slot, AppEntryIntents.ACTION_OPEN_SPORT) {
            if (lessonId != null) {
                putExtra(AppEntryIntents.EXTRA_SPORT_LESSON, lessonId)
                putExtra(AppEntryIntents.EXTRA_SPORT_PREDICTED, predicted)
            }
        }

    fun lesson(context: Context, widgetId: Int, slot: String, args: LessonDetailsArgs): PendingIntent =
        pending(context, widgetId, slot, AppEntryIntents.ACTION_OPEN_LESSON) {
            putExtra(AppEntryIntents.EXTRA_LESSON, EntryRouteParser.lessonJson(args))
        }

    fun qrPass(context: Context, widgetId: Int): PendingIntent =
        pending(context, widgetId, "qr", AppEntryIntents.ACTION_OPEN_QR_PASS)

    private fun pending(
        context: Context,
        widgetId: Int,
        slot: String,
        action: String,
        extras: Intent.() -> Unit = {},
    ): PendingIntent {
        val intent = AppEntryIntentFactory.open(context, action).apply {
            data = Uri.parse("$SCHEME://$widgetId/$slot")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            extras()
        }
        return PendingIntent.getActivity(
            context,
            widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val SCHEME = "itmowidgets-widget"
}
