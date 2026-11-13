package dev.alllexey.itmowidgets.feature.recordbook.data.lms

import dev.alllexey.itmowidgets.core.url.StrictUri
import io.ktor.http.Url

/** The LMS issues the state in its authorization redirect; accept it only from the expected Tuxedo endpoint. */
object LmsAuthorization {
    const val LOGIN_URL = "https://api.tuxedo.se.ifmo.ru/api/v1/sso/authorize?redirectUri=https%3A%2F%2Flms.tuxedo.se.ifmo.ru%2Fsso%2Ftoken"
    const val REDIRECT_URI = "https://lms.tuxedo.se.ifmo.ru/sso/token"
    val ALLOWED_HOSTS = setOf("api.tuxedo.se.ifmo.ru", "sso.tuxedo.se.ifmo.ru", "lms.tuxedo.se.ifmo.ru", "id.itmo.ru")

    fun isAllowedBrowserUrl(url: String): Boolean {
        val uri = StrictUri.parse(url) ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.rawUserInfo == null &&
            uri.host in ALLOWED_HOSTS &&
            (uri.port == null || uri.port == 443)
    }

    fun stateFromAuthorization(url: String): String? {
        val strict = StrictUri.parse(url) ?: return null
        if (!strict.scheme.equals("https", ignoreCase = true) || strict.rawUserInfo != null ||
            strict.host != "sso.tuxedo.se.ifmo.ru" || (strict.port != null && strict.port != 443)
        ) return null
        val parsed = runCatching { Url(url) }.getOrNull() ?: return null
        if (parsed.encodedPath != "/realms/Newdex/protocol/openid-connect/auth" ||
            parsed.parameters.getAll("state")?.size != 1 || parsed.parameters["client_id"] != "newdex-backend" ||
            parsed.parameters["redirect_uri"] != REDIRECT_URI || parsed.parameters["response_type"] != "code"
        ) return null
        return parsed.parameters["state"]?.takeIf { it.isNotBlank() && it.length <= 2048 }
    }

    fun callbackCode(url: String, state: String): String? {
        val strict = StrictUri.parse(url) ?: return null
        if (!strict.scheme.equals("https", ignoreCase = true) || strict.rawUserInfo != null ||
            strict.host != "lms.tuxedo.se.ifmo.ru" || (strict.port != null && strict.port != 443)
        ) return null
        val parsed = runCatching { Url(url) }.getOrNull() ?: return null
        if (parsed.encodedPath != "/sso/token" || parsed.parameters.getAll("state")?.size != 1 ||
            parsed.parameters.getAll("code")?.size != 1 || parsed.parameters["state"] != state
        ) return null
        return parsed.parameters["code"]?.takeIf { it.isNotBlank() && it.length <= 4096 }
    }
}
