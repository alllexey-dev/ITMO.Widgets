package dev.alllexey.itmowidgets.core.ui

import android.content.Context
import dev.alllexey.itmowidgets.R

/** Backend sends an empty name until the owner's identity is published; never show it raw. */
fun Context.userDisplayName(name: String, isu: Int): String =
    name.trim().ifEmpty { getString(R.string.user_name_placeholder, isu) }

/**
 * «Фамилия Имя Отчество» as «Фамилия И. О.»; a single word stays as it is. The initials are joined by no-break
 * spaces so a line never ends between them.
 */
fun shortPersonName(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    if (parts.size < 2) return name.trim()
    return parts.first() + " " + parts.drop(1).joinToString("\u00A0") { "${it.first().uppercaseChar()}." }
}
