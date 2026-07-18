package dev.alllexey.itmowidgets.core.util

import android.content.res.Resources

inline fun <reified T : Enum<T>> safeEnumOf(value: String?): T? {
    if (value == null) return null
    return enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) }
}

inline fun <reified T : Enum<T>> safeEnumOf(value: String?, default: T): T {
    if (value == null) return default
    return enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: default
}

val Int.dp: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()
