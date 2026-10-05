package dev.alllexey.itmowidgets.feature.web.domain

import dev.alllexey.itmowidgets.core.url.StrictUri

/** Only official HTTPS origins remain inside the app; redirects never launch other apps. */
object MyItmoWebPolicy {
    const val HOME_URL = "https://my.itmo.ru/"
    enum class Navigation { INTERNAL, EXTERNAL, BLOCKED }

    fun isInternal(url: String): Boolean {
        val uri = StrictUri.parse(url) ?: return false
        return uri.scheme.equals("https", ignoreCase = true) && uri.rawUserInfo == null &&
            (uri.port == null || uri.port == 443) && uri.host?.lowercase() in setOf("my.itmo.ru", "id.itmo.ru")
    }

    fun navigation(url: String, mainFrame: Boolean, userGesture: Boolean): Navigation {
        if (isInternal(url)) return Navigation.INTERNAL
        val uri = StrictUri.parse(url)
        return if (mainFrame && userGesture && uri?.scheme.equals("https", ignoreCase = true) &&
            uri?.host != null && uri.rawUserInfo == null) Navigation.EXTERNAL else Navigation.BLOCKED
    }
}
