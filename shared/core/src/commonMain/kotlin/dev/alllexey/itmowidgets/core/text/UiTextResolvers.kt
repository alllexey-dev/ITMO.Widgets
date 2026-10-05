package dev.alllexey.itmowidgets.core.text

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The text outside composition: SwiftUI hosts through the shared ViewModels, workers, suspend code. Plural forms
 * follow the process default locale (SP-13a), which the app keeps Russian.
 */
@Suppress("DEPRECATION")
suspend fun UiText.resolve(): String = when (this) {
    is UiText.Res -> getString(resource, *arguments.map { it.resolveArgument() }.toTypedArray())
    is UiText.Plural -> getPluralString(resource, count, *arguments.map { it.resolveArgument() }.toTypedArray())
    is UiText.Dynamic -> value
    is UiText.Joined -> parts.map { it.resolve() }.joinToString(separator)
    is UiText.LowercaseFirst -> text.resolve().replaceFirstChar(Char::lowercaseChar)
    is UiText.Resource -> throw IllegalStateException(ANDROID_ONLY)
}

/** The text inside composition; it follows the composition's locale. */
@Suppress("DEPRECATION")
@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Res -> stringResource(resource, *arguments.map { it.asStringArgument() }.toTypedArray())
    is UiText.Plural -> pluralStringResource(resource, count, *arguments.map { it.asStringArgument() }.toTypedArray())
    is UiText.Dynamic -> value
    is UiText.Joined -> parts.map { it.asString() }.joinToString(separator)
    is UiText.LowercaseFirst -> text.asString().replaceFirstChar(Char::lowercaseChar)
    is UiText.Resource -> throw IllegalStateException(ANDROID_ONLY)
}

private suspend fun Any.resolveArgument(): Any = if (this is UiText) resolve() else this

@Composable
private fun Any.asStringArgument(): Any = if (this is UiText) asString() else this

private const val ANDROID_ONLY = "UiText.Resource holds an Android id; resolve it with core.ui resolve(context)"
