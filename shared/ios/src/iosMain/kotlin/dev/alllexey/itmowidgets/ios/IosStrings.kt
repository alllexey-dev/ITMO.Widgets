package dev.alllexey.itmowidgets.ios

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.ComposeUIViewController
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.resolve
import dev.alllexey.itmowidgets.designsystem.locale.AppLocale
import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.allPluralStringResources
import dev.alllexey.itmowidgets.shared.core.allStringResources
import platform.UIKit.UIViewController

/**
 * The catalog as Swift reaches it before IO-05's bridge (L18 IO-20): the app locale for Compose Multiplatform and
 * the CMP resolvers that `ITMOWidgetsTests/StringsTests` holds against the Swift one (`UiText+Resolve.swift`).
 */
object IosStrings {

    /** The first call of `App.init`, before any composition or suspend resolver ([AppLocale]). */
    fun installAppLocale() = AppLocale.install()

    /** A `:shared:core` string as [UiText], or null for a key it does not have. */
    fun coreString(key: String, arguments: List<Any>): UiText? =
        Res.allStringResources[key]?.let { UiText.Res(it, arguments) }

    /** A `:shared:core` plural for [count], which is also its argument 1, or null for a key it does not have. */
    fun corePlural(key: String, count: Int): UiText? =
        Res.allPluralStringResources[key]?.let { UiText.Plural(it, count, listOf(count)) }

    /** [text] as CMP resolves it outside composition. */
    suspend fun resolve(text: UiText): String = text.resolve()

    /** A Compose host that hands [onText] every value composition resolves for [text]. */
    fun compositionProbe(text: UiText, onText: (String) -> Unit): UIViewController = ComposeUIViewController {
        val value = text.asString()
        LaunchedEffect(value) { onText(value) }
    }
}
