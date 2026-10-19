package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.UiTextResolver

/**
 * Catalog text as its key, for tests without string tables (Kotlin/Native tests have none, ADR 0035): `Res` and
 * `Plural` give `key` or `key: argument, argument`, nested texts first; the rest resolves as on a device.
 */
object KeyTextResolver : UiTextResolver {

    override suspend fun resolve(text: UiText): String = when (text) {
        is UiText.Res -> keyed(text.key, text.arguments)
        is UiText.Plural -> keyed(text.key, text.arguments)
        is UiText.Dynamic -> text.value
        is UiText.Joined -> text.parts.map { resolve(it) }.joinToString(text.separator)
        is UiText.LowercaseFirst -> resolve(text.text).replaceFirstChar(Char::lowercaseChar)
    }

    private suspend fun keyed(key: String, arguments: List<Any>): String =
        if (arguments.isEmpty()) key else "$key: " + arguments.map { if (it is UiText) resolve(it) else it }.joinToString()
}
