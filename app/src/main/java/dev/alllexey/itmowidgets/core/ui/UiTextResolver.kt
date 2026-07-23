package dev.alllexey.itmowidgets.core.ui

import android.content.Context
import dev.alllexey.itmowidgets.core.text.UiText

fun UiText.resolve(context: Context): String {
    return when (this) {
        is UiText.Resource -> context.getString(resourceId, *arguments.toTypedArray())
        is UiText.Dynamic -> value
    }
}
