package dev.alllexey.itmowidgets.core.navigation

import dev.alllexey.itmowidgets.core.url.UrlEncoding

/** Addresses of the project outside the app: configuration, not copy, so they stay out of the string catalog. */
object ProjectLinks {
    const val GITHUB_URL = "https://github.com/alllexey-dev/ITMO.Widgets"
    const val TELEGRAM_URL = "https://t.me/itmowidgets"

    /** The feedback site: ideas and bug reports, linked from Профиль through [feedbackUrl]. */
    const val FEEDBACK_URL = "https://fb.widgets.alllexey.dev"

    /** Opens the channel in the native Telegram client; [TELEGRAM_URL] is the web fallback. */
    const val TELEGRAM_DEEPLINK = "tg://resolve?domain=itmowidgets"

    /** Source code of the custom services Backend, shown during onboarding. */
    const val SERVICES_SOURCE_URL = "https://github.com/alllexey-dev/itmo-widgets-backend"

    /** The agent that books sport and checks marks while the app is closed, linked from the iPhone beta notice. */
    const val AGENT_URL = "https://github.com/alllexey-dev/itmo-widgets-agent"

    /**
     * [FEEDBACK_URL] with the form prefilled: the [platform], the app [version] and the [device] model, trimmed to the
     * site's limits (40 and 120 characters); a blank value is left out. Nothing about the user goes in the link, and
     * no `kind`, so the site's default applies.
     */
    fun feedbackUrl(platform: FeedbackPlatform, version: String, device: String): String {
        val parameters = listOf(
            "platform" to platform.name.lowercase(),
            "version" to version.trim().take(FEEDBACK_VERSION_LIMIT),
            "device" to device.trim().take(FEEDBACK_DEVICE_LIMIT),
        ).filter { it.second.isNotEmpty() }
        return "$FEEDBACK_URL/?" + parameters.joinToString("&") { (name, value) -> "$name=${UrlEncoding.percentEncode(value)}" }
    }

    private const val FEEDBACK_VERSION_LIMIT = 40
    private const val FEEDBACK_DEVICE_LIMIT = 120
}

/** The app's platform as the feedback site names it. */
enum class FeedbackPlatform { ANDROID, IOS }
