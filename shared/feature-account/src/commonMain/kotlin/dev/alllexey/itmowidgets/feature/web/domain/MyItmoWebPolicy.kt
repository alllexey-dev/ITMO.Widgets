package dev.alllexey.itmowidgets.feature.web.domain

import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.url.StrictUri

/** Only official HTTPS origins remain inside the app; redirects never launch other apps. */
object MyItmoWebPolicy {
    const val HOME_URL = "${AppRoutes.MyItmoWeb.ORIGIN}/"
    enum class Navigation { INTERNAL, EXTERNAL, BLOCKED }

    fun isInternal(url: String): Boolean {
        val uri = StrictUri.parse(url) ?: return false
        return uri.scheme.equals("https", ignoreCase = true) && uri.rawUserInfo == null &&
            (uri.port == null || uri.port == 443) && uri.host?.lowercase() in setOf("my.itmo.ru", "id.itmo.ru")
    }

    /**
     * The page the browser starts at for the site [path] of `AppRoutes.MyItmoWeb`: [HOME_URL] with the path, or
     * [HOME_URL] itself for a path that is not a plain absolute one or would leave the official hosts.
     */
    fun startUrl(path: String): String {
        if (!path.startsWith("/") || path.startsWith("//") || path.any { it.isWhitespace() || it == '\\' }) return HOME_URL
        val url = HOME_URL.removeSuffix("/") + path
        return if (isInternal(url)) url else HOME_URL
    }

    fun navigation(url: String, mainFrame: Boolean, userGesture: Boolean): Navigation {
        if (isInternal(url)) return Navigation.INTERNAL
        val uri = StrictUri.parse(url)
        return if (mainFrame && userGesture && uri?.scheme.equals("https", ignoreCase = true) &&
            uri?.host != null && uri.rawUserInfo == null) Navigation.EXTERNAL else Navigation.BLOCKED
    }
}
