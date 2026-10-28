package dev.alllexey.itmowidgets.core.ui.widget

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.settings.WidgetColorRoles
import dev.alllexey.itmowidgets.core.settings.WidgetPalette

/**
 * [this] in the system's light or dark mode, the one the launcher draws a placed widget in: an activity carries the
 * app's `Тема` (AppCompat's night mode), so a widget drawn in it for a preview would show the other mode. Resources
 * and the app theme's attributes resolve in the system's mode; locale, font scale and density stay [this]'s.
 */
fun Context.inSystemNightMode(): Context {
    val system = Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    if (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == system) return this
    return ContextThemeWrapper(this, R.style.AppTheme).apply {
        // A delta: only the night bits are set, every other field stays undefined and keeps this context's value.
        applyOverrideConfiguration(Configuration().apply { uiMode = system })
    }
}

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

    /** Calls [method] of [viewId] with [light] or [dark] as the launcher's mode is (below API 31: the render's). */
    fun applyPair(views: RemoteViews, viewId: Int, method: String, @ColorInt light: Int, @ColorInt dark: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) views.setColorInt(viewId, method, light, dark)
        else views.setInt(viewId, method, if (isDark()) dark else light)
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

    private fun isDark() =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
}
