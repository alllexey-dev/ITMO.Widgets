package dev.alllexey.itmowidgets.core.text

import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.user_name_placeholder

/** Backend sends an empty name until the owner's identity is published; never show it raw. */
fun userDisplayName(name: String, isu: Int): UiText =
    name.trim().takeIf(String::isNotEmpty)?.let(UiText::Dynamic) ?: UiText.Res(Res.string.user_name_placeholder, listOf(isu))

/**
 * «Фамилия Имя Отчество» as «Фамилия И. О.»; a single word stays as it is. The initials are joined by no-break
 * spaces so a line never ends between them.
 */
fun shortPersonName(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    if (parts.size < 2) return name.trim()
    return parts.first() + " " + parts.drop(1).joinToString("\u00A0") { "${it.first().uppercaseChar()}." }
}
