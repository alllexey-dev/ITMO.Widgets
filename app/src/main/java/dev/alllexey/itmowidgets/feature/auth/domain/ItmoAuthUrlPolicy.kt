package dev.alllexey.itmowidgets.feature.auth.domain

import java.net.URI

object ItmoAuthUrlPolicy {

    const val LOGIN_URL = "https://my.itmo.ru/"

    fun isAllowed(url: String): Boolean {
        val uri = parse(url) ?: return false
        return uri.scheme.equals(HTTPS_SCHEME, ignoreCase = true) &&
            uri.host?.lowercase() in ALLOWED_HOSTS
    }

    fun isTokenCallback(url: String): Boolean {
        val uri = parse(url) ?: return false
        return uri.scheme.equals(HTTPS_SCHEME, ignoreCase = true) &&
            uri.host.equals(MY_ITMO_HOST, ignoreCase = true) &&
            uri.path == CALLBACK_PATH
    }

    private fun parse(url: String): URI? {
        return runCatching { URI(url) }.getOrNull()
    }

    private const val HTTPS_SCHEME = "https"
    private const val MY_ITMO_HOST = "my.itmo.ru"
    private const val ITMO_ID_HOST = "id.itmo.ru"
    private const val CALLBACK_PATH = "/login/callback"
    private val ALLOWED_HOSTS = setOf(MY_ITMO_HOST, ITMO_ID_HOST)
}
