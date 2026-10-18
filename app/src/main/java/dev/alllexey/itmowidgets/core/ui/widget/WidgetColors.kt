package dev.alllexey.itmowidgets.core.ui.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.settings.WidgetColorRoles
import dev.alllexey.itmowidgets.core.settings.WidgetPalette
/** The roles of the mode [context] renders in. */
fun WidgetPalette.rolesFor(context: Context): WidgetColorRoles = roles(
    dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
)

/**
 * The colours of one widget render: the layouts' own `widget_*` colours, or the app's [palette] while the widgets
 * follow the theme (`widgets_follow_app_theme`). Every colour is set on every render, so a view the launcher reuses
 * drops the colours of its previous render. From API 31 a palette colour is a day and night pair and a layout colour
 * a resource, both resolved by the launcher, so the widget follows the system's mode; below API 31 the colour of the
 * render's own mode is set.
 */
class WidgetColors(private val context: Context, private val palette: WidgetPalette?) {

    /** [res] in the render's mode: the palette's role, or the resource. */
    @ColorInt
    fun resolve(@ColorRes res: Int): Int =
        palette?.rolesFor(context)?.role(res)?.let(::opaque) ?: ContextCompat.getColor(context, res)

    /** Calls [method] of [viewId] (an `int` colour setter such as `setTextColor`) with [res]. */
    fun apply(views: RemoteViews, viewId: Int, method: String, @ColorRes res: Int) {
        val light = palette?.light?.role(res)
        val dark = palette?.dark?.role(res)
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> views.setInt(viewId, method, resolve(res))
            light == null || dark == null -> views.setColor(viewId, method, res)
            else -> views.setColorInt(viewId, method, opaque(light), opaque(dark))
        }
    }

    /** [apply] `setColorFilter` of an image, the tint of a shape drawn as a view's image. */
    fun tint(views: RemoteViews, viewId: Int, @ColorRes res: Int) = apply(views, viewId, "setColorFilter", res)

    /** [apply] `setTextColor` with each label's layout colour of [textColors] (view id to colour resource). */
    fun applyText(views: RemoteViews, textColors: Map<Int, Int>) {
        textColors.forEach { (id, res) -> apply(views, id, "setTextColor", res) }
    }

    /**
     * The `widget_theme_background` layers over the layout's own background: shown and tinted with a palette, gone
     * without one, so the widget keeps its own background exactly.
     */
    fun applyBackground(views: RemoteViews) {
        val visibility = if (palette == null) View.GONE else View.VISIBLE
        views.setViewVisibility(R.id.widget_theme_outline, visibility)
        views.setViewVisibility(R.id.widget_theme_surface, visibility)
        if (palette == null) return
        apply(views, R.id.widget_theme_outline, "setColorFilter", R.color.widget_outline_variant)
        apply(views, R.id.widget_theme_surface, "setColorFilter", R.color.widget_surface)
    }

    /** The palette's role of a layout colour; null for the colours that stay the same in every theme (status tones). */
    private fun WidgetColorRoles.role(@ColorRes res: Int): Int? = when (res) {
        R.color.widget_surface -> surface
        R.color.widget_on_surface -> onSurface
        R.color.widget_on_surface_variant -> onSurfaceVariant
        R.color.widget_outline_variant -> outlineVariant
        R.color.widget_primary -> primary
        R.color.widget_primary_container -> primaryContainer
        R.color.widget_on_primary_container -> onPrimaryContainer
        R.color.widget_surface_container_high -> surfaceContainerHigh
        R.color.widget_tertiary -> tertiary
        else -> null
    }

    private fun opaque(rgb: Int): Int = Color.BLACK or rgb
}
