package dev.alllexey.itmowidgets.core.text

import androidx.compose.runtime.Composable
import dev.alllexey.itmowidgets.shared.compose.strings.Res
import dev.alllexey.itmowidgets.shared.compose.strings.allPluralStringResources
import dev.alllexey.itmowidgets.shared.compose.strings.allStringResources
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The text outside composition: SwiftUI hosts through the shared ViewModels, workers, suspend code. Plural forms
 * follow the process default locale, which the app keeps Russian.
 */
suspend fun UiText.resolve(): String = when (this) {
    is UiText.Res -> getString(stringResourceOf(key), *arguments.map { it.resolveArgument() }.toTypedArray())
    is UiText.Plural ->
        getPluralString(pluralResourceOf(key), count, *arguments.map { it.resolveArgument() }.toTypedArray())
    is UiText.Dynamic -> value
    is UiText.Joined -> parts.map { it.resolve() }.joinToString(separator)
    is UiText.LowercaseFirst -> text.resolve().replaceFirstChar(Char::lowercaseChar)
}

/** The text inside composition; it follows the composition's locale. */
@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Res -> stringResource(stringResourceOf(key), *arguments.map { it.asStringArgument() }.toTypedArray())
    is UiText.Plural ->
        pluralStringResource(pluralResourceOf(key), count, *arguments.map { it.asStringArgument() }.toTypedArray())
    is UiText.Dynamic -> value
    is UiText.Joined -> parts.map { it.asString() }.joinToString(separator)
    is UiText.LowercaseFirst -> text.asString().replaceFirstChar(Char::lowercaseChar)
}

private fun stringResourceOf(key: String): StringResource =
    Res.allStringResources[key] ?: throw IllegalArgumentException("No string \"$key\" in the catalog")

private fun pluralResourceOf(key: String): PluralStringResource =
    Res.allPluralStringResources[key] ?: throw IllegalArgumentException("No plural \"$key\" in the catalog")

private suspend fun Any.resolveArgument(): Any = if (this is UiText) resolve() else this

@Composable
private fun Any.asStringArgument(): Any = if (this is UiText) asString() else this
