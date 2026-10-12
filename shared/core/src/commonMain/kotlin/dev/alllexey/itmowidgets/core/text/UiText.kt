package dev.alllexey.itmowidgets.core.text

/**
 * Text a ViewModel or a domain rule hands to the UI without a platform context. [Res] and [Plural] carry a catalog
 * key (a `const val` of the module's generated `<Area>Strings`/`<Area>Plurals`), which is also the Android resource
 * name and the `.xcstrings` key, so Compose (`:shared:compose-strings`), the Android resolver
 * (`core/ui/UiTextResolver.kt`) and Swift resolve the same text. An argument that is itself a [UiText] is resolved
 * first.
 */
sealed interface UiText {

    data class Res(
        val key: String,
        val arguments: List<Any> = emptyList()
    ) : UiText

    data class Plural(
        val key: String,
        val count: Int,
        val arguments: List<Any> = emptyList()
    ) : UiText

    data class Dynamic(val value: String) : UiText

    /** [parts] resolved and joined by [separator], a symbol rather than a word: "1506 · Кронва". */
    data class Joined(val parts: List<UiText>, val separator: String) : UiText

    /** [text] with its first character lowercased, so a capitalized phrase can continue a sentence. */
    data class LowercaseFirst(val text: UiText) : UiText
}

/** Resolves [UiText] outside composition where no platform context is at hand (iOS notifications). */
fun interface UiTextResolver {
    suspend fun resolve(text: UiText): String
}
