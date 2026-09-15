package dev.alllexey.itmowidgets.feature.recordbook.domain

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** Only the official confidential web-client callback is exchanged; credentials stay on ITMO.ID. */
object BarsAuthPolicy {
    const val CALLBACK = "https://bars.itmo.ru/rest/login"
    const val ISSUER = "https://id.itmo.ru/auth/realms/itmo"

    fun loginUrl(state: String): String = "$ISSUER/protocol/openid-connect/auth?" +
        "response_type=code&scope=openid&client_id=bars&redirect_uri=" +
        URLEncoder.encode(CALLBACK, "UTF-8") + "&state=" + URLEncoder.encode(state, "UTF-8")

    fun isCallback(url: String): Boolean = parse(url)?.let {
        trusted(it, "bars.itmo.ru") && it.rawPath == "/rest/login"
    } == true

    fun isAllowedPage(url: String): Boolean = parse(url)?.let {
        trusted(it, "id.itmo.ru") || isCallback(url)
    } == true

    fun authorizationCode(url: String, expectedState: String): String? = runCatching {
        if (!isCallback(url) || expectedState.isBlank()) return null
        val uri = URI(url)
        if (uri.rawFragment != null) return null
        val pairs = uri.rawQuery.orEmpty().split("&").map { part ->
            val pair = part.split("=", limit = 2)
            URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
        }
        if (pairs.map { it.first }.distinct().size != pairs.size) return null
        val query = pairs.toMap()
        if (query["state"] != expectedState || query.containsKey("error")) return null
        if (query["iss"] != null && query["iss"] != ISSUER) return null
        query["code"]?.takeIf { it.isNotBlank() && it.length <= 4096 }
    }.getOrNull()

    private fun trusted(uri: URI, host: String) = uri.scheme == "https" &&
        uri.host.equals(host, ignoreCase = true) && uri.rawUserInfo == null && uri.port in setOf(-1, 443)

    private fun parse(url: String): URI? = runCatching { URI(url) }.getOrNull()
}
