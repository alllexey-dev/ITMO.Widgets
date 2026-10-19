package dev.alllexey.itmowidgets.core.platform

import platform.Foundation.NSArgumentDomain
import platform.Foundation.NSUserDefaults

/**
 * The iOS side of the Russian app locale (:app's `AppLocale` is the Android side): puts [LANGUAGE] first in
 * `AppleLanguages` for this process, so whatever reads the preferred languages (Foundation and SwiftUI formatting on
 * the current locale, system controls) is Russian on any device, as Android's per-app locale makes it. Text needs no
 * override: every bundle has only the `ru` localization and Kotlin resolves through the Swift tables (ADR 0035).
 * `App.init` calls [install] first, before the Kotlin graph starts.
 */
object AppLocale {

    const val LANGUAGE = "ru"

    fun install() {
        val defaults = NSUserDefaults.standardUserDefaults
        defaults.setObject(listOf(LANGUAGE), forKey = APPLE_LANGUAGES)
        // A launch argument `-AppleLanguages (en)` (XCUITest, scheme options) outranks the app domain.
        val arguments = defaults.volatileDomainForName(NSArgumentDomain)
        if (arguments[APPLE_LANGUAGES] != null) {
            defaults.removeVolatileDomainForName(NSArgumentDomain)
            defaults.setVolatileDomain(arguments + (APPLE_LANGUAGES to listOf(LANGUAGE)), forName = NSArgumentDomain)
        }
    }

    private const val APPLE_LANGUAGES = "AppleLanguages"
}
