package dev.alllexey.itmowidgets.designsystem.locale

import platform.Foundation.NSArgumentDomain
import platform.Foundation.NSUserDefaults

/**
 * The iOS side of the Russian app locale (L05 AA-13 is the Android side; report 95 "Plural rules and app language").
 *
 * Compose Multiplatform picks plural rules from `NSLocale.preferredLanguages`: the composable resolvers through
 * Compose's `Locale.current`, `getString`/`getPluralString` through the system resource environment. On an English
 * iPhone that gives "5 пары". [install] puts [LANGUAGE] first in `AppleLanguages` for this process, which both read
 * at once (the CMP docs' app-locale pattern), so it must run in `App.init`, before the first composition or
 * suspend resolver. SwiftUI and the extensions need no override: the bundle has only the `ru` localization.
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
