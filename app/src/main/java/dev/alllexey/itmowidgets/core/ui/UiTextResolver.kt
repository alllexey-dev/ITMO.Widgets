package dev.alllexey.itmowidgets.core.ui

import android.content.Context
import dev.alllexey.itmowidgets.core.text.UiText

/**
 * [UiText] for Views, widgets and notifications. `Res` and `Plural` go through the generated key -> `R` table of the
 * exported strings ([ExportedStringIds]); arguments that are themselves [UiText] are resolved first, so a format can
 * take localized names.
 */
@Suppress("DEPRECATION")
fun UiText.resolve(context: Context): String {
    return when (this) {
        is UiText.Res -> context.getString(ExportedStringIds.string(resource.key), *arguments.resolved(context))
        is UiText.Plural -> context.resources.getQuantityString(
            ExportedStringIds.plural(resource.key),
            count,
            *arguments.resolved(context)
        )
        is UiText.Resource -> context.getString(resourceId, *arguments.resolved(context))
        is UiText.Dynamic -> value
    }
}

private fun List<Any>.resolved(context: Context): Array<Any> =
    map { if (it is UiText) it.resolve(context) else it }.toTypedArray()
