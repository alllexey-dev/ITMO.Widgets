package dev.alllexey.itmowidgets.core.text

import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

/**
 * Text a ViewModel or a domain rule hands to the UI without a platform context. [Res] and [Plural] carry CMP
 * resources whose `key` is also the Android resource name and the `.xcstrings` key, so Compose, the Android
 * resolver (`core/ui/UiTextResolver.kt`) and Swift resolve the same text. An argument that is itself a [UiText] is
 * resolved first.
 */
sealed interface UiText {

    data class Res(
        val resource: StringResource,
        val arguments: List<Any> = emptyList()
    ) : UiText

    data class Plural(
        val resource: PluralStringResource,
        val count: Int,
        val arguments: List<Any> = emptyList()
    ) : UiText

    data class Dynamic(val value: String) : UiText

    /** [parts] resolved and joined by [separator], a symbol rather than a word: "1506 · Кронва". */
    data class Joined(val parts: List<UiText>, val separator: String) : UiText

    /** [text] with its first character lowercased, so a capitalized phrase can continue a sentence. */
    data class LowercaseFirst(val text: UiText) : UiText
}
