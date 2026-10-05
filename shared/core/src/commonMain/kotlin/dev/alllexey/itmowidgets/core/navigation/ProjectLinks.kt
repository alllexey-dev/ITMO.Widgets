package dev.alllexey.itmowidgets.core.navigation

/** Addresses of the project outside the app: configuration, not copy, so they stay out of the string catalog. */
object ProjectLinks {
    const val GITHUB_URL = "https://github.com/alllexey-dev/ITMO.Widgets"
    const val TELEGRAM_URL = "https://t.me/itmowidgets"

    /** Opens the channel in the native Telegram client; [TELEGRAM_URL] is the web fallback. */
    const val TELEGRAM_DEEPLINK = "tg://resolve?domain=itmowidgets"

    /** Source code of the custom services Backend, shown during onboarding. */
    const val SERVICES_SOURCE_URL = "https://github.com/alllexey-dev/itmo-widgets-backend"
}
