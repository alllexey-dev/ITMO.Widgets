package dev.alllexey.itmowidgets.feature.sport.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.settings.WidgetPalette
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.buildingShortTitle
import dev.alllexey.itmowidgets.core.ui.lessonTypeColorRes
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.core.ui.widget.WidgetColors
import dev.alllexey.itmowidgets.core.ui.widget.WidgetIntents
import dev.alllexey.itmowidgets.core.ui.withAppLocale
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetDay
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetItem
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetKind
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetSnapshot
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format

/**
 * The sport widget (W3, `SportWidgetProvider`): the medium one is the score ring with the nearest bookings or queues
 * in the words of Спорт, the small one the ring alone. The ring is three white bitmaps (track, visits, bonus) tinted
 * per render, so it follows the system's light and dark mode like the text.
 */
object SportWidgetRenderer {

    fun views(context: Context, snapshot: SportWidgetSnapshot, palette: WidgetPalette?, widgetId: Int? = null, widthDp: Float = MEDIUM_WIDTH_DP): RemoteViews {
        val localized = context.withAppLocale()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && widgetId != null) {
            RemoteViews(
                mapOf(
                    SizeF(SMALL_WIDTH_DP, MIN_HEIGHT_DP) to layout(localized, snapshot, palette, widgetId, medium = false),
                    SizeF(MEDIUM_WIDTH_DP, MIN_HEIGHT_DP) to layout(localized, snapshot, palette, widgetId, medium = true),
                )
            )
        } else {
            layout(localized, snapshot, palette, widgetId, medium = widthDp >= MEDIUM_WIDTH_DP)
        }
    }

    fun layout(context: Context, snapshot: SportWidgetSnapshot, palette: WidgetPalette?, widgetId: Int?, medium: Boolean): RemoteViews {
        val colors = WidgetColors(context, palette)
        val views = RemoteViews(context.packageName, if (medium) R.layout.widget_sport_medium else R.layout.widget_sport_small)
        colors.applyBackground(views)
        widgetId?.let { views.setOnClickPendingIntent(android.R.id.background, WidgetIntents.sport(context, it)) }
        val content = snapshot.kind == SportWidgetKind.CONTENT
        views.setViewVisibility(R.id.sport_content, if (content) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_message, if (content) View.GONE else View.VISIBLE)
        colors.apply(views, R.id.widget_message, "setTextColor", R.color.widget_on_surface_variant)
        if (!content) {
            views.setTextViewText(
                R.id.widget_message,
                context.getString(
                    when (snapshot.kind) {
                        SportWidgetKind.SIGNED_OUT -> R.string.schedule_widget_signed_out
                        SportWidgetKind.ERROR -> R.string.widget_sport_error
                        else -> R.string.widget_sport_loading
                    }
                )
            )
            return views
        }
        bindRing(context, views, colors, snapshot, big = !medium)
        if (medium) bindDetails(context, views, colors, snapshot, widgetId)
        return views
    }

    private fun bindRing(context: Context, views: RemoteViews, colors: WidgetColors, snapshot: SportWidgetSnapshot, big: Boolean) {
        val size = (RING_PX_DP * context.resources.displayMetrics.density).toInt()
        val stroke = size * (if (big) STROKE_BIG else STROKE)
        views.setImageViewBitmap(R.id.ring_track, ring(size, stroke, 0f, FULL))
        if (snapshot.done) {
            views.setImageViewBitmap(R.id.ring_visits, ring(size, stroke, 0f, FULL))
            views.setViewVisibility(R.id.ring_bonus, View.GONE)
            views.setInt(R.id.ring_visits, "setColorFilter", ContextCompat.getColor(context, R.color.widget_ok))
        } else {
            val visits = FULL * snapshot.visits / SportWidgetSnapshot.CREDIT
            val bonus = FULL * snapshot.bonus / SportWidgetSnapshot.CREDIT
            views.setImageViewBitmap(R.id.ring_visits, ring(size, stroke, 0f, visits - gap(visits)))
            views.setImageViewBitmap(R.id.ring_bonus, ring(size, stroke, visits, bonus - gap(bonus)))
            views.setViewVisibility(R.id.ring_bonus, if (snapshot.bonus > 0) View.VISIBLE else View.GONE)
            colors.tint(views, R.id.ring_visits, R.color.widget_primary)
            colors.tint(views, R.id.ring_bonus, R.color.widget_tertiary)
        }
        views.setViewVisibility(R.id.ring_visits, if (snapshot.visits > 0 || snapshot.done) View.VISIBLE else View.GONE)
        colors.tint(views, R.id.ring_track, R.color.widget_surface_container_high)
        views.setTextViewText(R.id.ring_total, snapshot.total.toString())
        views.setTextViewTextSize(R.id.ring_total, TypedValue.COMPLEX_UNIT_SP, if (big) TOTAL_BIG_SP else TOTAL_SP)
        colors.apply(views, R.id.ring_total, "setTextColor", R.color.widget_on_surface)
        colors.apply(views, R.id.ring_goal, "setTextColor", R.color.widget_on_surface_variant)
    }

    private fun bindDetails(context: Context, views: RemoteViews, colors: WidgetColors, snapshot: SportWidgetSnapshot, widgetId: Int?) {
        views.setTextViewText(
            R.id.sport_title,
            if (snapshot.done) context.getString(R.string.widget_sport_done)
            else context.resources.getQuantityString(R.plurals.widget_sport_remaining, snapshot.remaining, snapshot.remaining)
        )
        val visits = context.getString(R.string.widget_sport_visits, snapshot.visits)
        val bonus = context.getString(R.string.widget_sport_bonus, snapshot.bonus)
        // A closed ring has no arcs to name: the legend is one line, `посещения 65, бонусы 39`.
        views.setTextViewText(R.id.sport_visits, if (snapshot.done) "$visits, $bonus" else visits)
        views.setTextViewText(R.id.sport_bonus, bonus)
        views.setViewVisibility(R.id.sport_bonus, if (snapshot.done) View.GONE else View.VISIBLE)
        colors.apply(views, R.id.sport_title, "setTextColor", R.color.widget_on_surface)
        colors.apply(views, R.id.sport_visits, "setTextColor", R.color.widget_on_surface_variant)
        colors.apply(views, R.id.sport_bonus, "setTextColor", R.color.widget_on_surface_variant)
        val dots = if (snapshot.done) View.GONE else View.VISIBLE
        views.setViewVisibility(R.id.sport_visits_dot, dots)
        views.setViewVisibility(R.id.sport_bonus_dot, dots)
        colors.tint(views, R.id.sport_visits_dot, R.color.widget_primary)
        colors.tint(views, R.id.sport_bonus_dot, R.color.widget_tertiary)
        views.removeAllViews(R.id.sport_items)
        val items = snapshot.items ?: return
        if (items.isEmpty()) {
            views.addView(R.id.sport_items, empty(context, colors))
            return
        }
        items.forEachIndexed { index, item -> views.addView(R.id.sport_items, item(context, colors, item, lead = index == 0, widgetId, index)) }
    }

    private fun item(context: Context, colors: WidgetColors, item: SportWidgetItem, lead: Boolean, widgetId: Int?, index: Int): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_sport_item)
        row.setViewVisibility(R.id.item_background, if (lead) View.VISIBLE else View.INVISIBLE)
        colors.tint(row, R.id.item_background, R.color.widget_surface_container_high)
        if (!lead) row.setViewPadding(R.id.item_content, dp(context, 4), 0, dp(context, 4), 0)
        row.setInt(R.id.item_bar, "setColorFilter", ContextCompat.getColor(context, lessonTypeColorRes(SPORT_TYPE_ID)))
        row.setTextViewText(R.id.item_time, time(context, item))
        row.setTextViewText(R.id.item_place, listOfNotNull(item.section, item.building?.let { buildingShortTitle(it).resolve(context) }).joinToString(", "))
        val booked = item.status == SportWidgetStatus.BOOKED
        row.setTextViewText(
            R.id.item_status,
            when (item.status) {
                SportWidgetStatus.BOOKED -> context.getString(R.string.lesson_pill_booked)
                SportWidgetStatus.UNPUBLISHED -> context.getString(R.string.sport_queue_unpublished)
                SportWidgetStatus.QUEUE -> if (item.position > 0 && item.total > 0) {
                    context.getString(R.string.sport_queue_position, item.position, item.total)
                } else context.getString(R.string.sport_queue_pill)
            }
        )
        row.setInt(R.id.item_status, "setBackgroundResource", if (booked) R.drawable.widget_pill_ok else R.drawable.widget_pill_warn)
        row.setTextColor(R.id.item_status, ContextCompat.getColor(context, if (booked) R.color.widget_on_ok_container else R.color.widget_on_warn_container))
        colors.apply(row, R.id.item_time, "setTextColor", R.color.widget_on_surface_variant)
        colors.apply(row, R.id.item_place, "setTextColor", R.color.widget_on_surface)
        widgetId?.let { row.setOnClickPendingIntent(R.id.item_root, WidgetIntents.sport(context, it, "item$index", item.lessonId, item.predicted)) }
        return row
    }

    private fun empty(context: Context, colors: WidgetColors): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_sport_item).apply {
            colors.tint(this, R.id.item_background, R.color.widget_surface_container_high)
            setViewVisibility(R.id.item_bar, View.GONE)
            setViewVisibility(R.id.item_status, View.GONE)
            setViewVisibility(R.id.item_place, View.GONE)
            setTextViewText(R.id.item_time, context.getString(R.string.widget_sport_empty))
            colors.apply(this, R.id.item_time, "setTextColor", R.color.widget_on_surface_variant)
        }

    /** `Сегодня 17:00`, `Завтра 15:20`, `Чт 15:20`. */
    private fun time(context: Context, item: SportWidgetItem): String = when (item.day) {
        SportWidgetDay.TODAY -> context.getString(R.string.widget_sport_today, item.time)
        SportWidgetDay.TOMORROW -> context.getString(R.string.widget_sport_tomorrow, item.time)
        SportWidgetDay.LATER -> context.getString(
            R.string.widget_sport_later,
            LocalDate.parse(item.date).format(SHORT_WEEKDAY).replaceFirstChar(Char::uppercaseChar),
            item.time
        )
    }

    /** A white arc of [sweep] degrees from [start] (clockwise from the top) on a transparent square of [size] px. */
    private fun ring(size: Int, stroke: Float, start: Float, sweep: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8)
        if (sweep <= 0f) return bitmap
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = if (sweep >= FULL) Paint.Cap.BUTT else Paint.Cap.ROUND
            color = Color.WHITE
        }
        val inset = stroke / 2
        Canvas(bitmap).drawArc(RectF(inset, inset, size - inset, size - inset), start - QUARTER, sweep, false, paint)
        return bitmap
    }

    /** The space a round cap takes at both ends of a short arc, so neighbouring arcs do not touch. */
    private fun gap(sweep: Float): Float = if (sweep >= FULL) 0f else minOf(ARC_GAP, sweep / 2)

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private val SHORT_WEEKDAY = LocalDate.Format { dayOfWeek(DateTexts.Names.DAYS_SHORT) }

    const val SMALL_WIDTH_DP = 110f
    const val MEDIUM_WIDTH_DP = 250f
    private const val MIN_HEIGHT_DP = 110f
    private const val RING_PX_DP = 144
    private const val STROKE = 0.1f
    private const val STROKE_BIG = 0.085f
    private const val FULL = 360f
    private const val QUARTER = 90f
    private const val ARC_GAP = 14f
    private const val TOTAL_SP = 30f
    private const val TOTAL_BIG_SP = 40f
    private const val SPORT_TYPE_ID = 11
}
