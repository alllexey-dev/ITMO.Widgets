package dev.alllexey.itmowidgets.core.ui

import android.content.Context
import dev.alllexey.itmowidgets.core.text.UiText

/** Arguments that are themselves [UiText] are resolved first, so a format can take localized names. */
fun UiText.resolve(context: Context): String {
    return when (this) {
        is UiText.Resource -> context.getString(resourceId, *arguments.map { if (it is UiText) it.resolve(context) else it }.toTypedArray())
        is UiText.Dynamic -> value
    }
}
