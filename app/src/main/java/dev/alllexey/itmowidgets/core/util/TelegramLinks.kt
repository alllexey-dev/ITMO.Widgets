package dev.alllexey.itmowidgets.core.util

import java.net.URI
import java.net.URLEncoder

/**
 * Translates a public t.me link into the tg:// scheme the Telegram client handles itself;
 * an https t.me link is often claimed by the browser instead. `null` means "open as is".
 */
object TelegramLinks {
    private val hosts = setOf("t.me", "telegram.me", "telegram.dog")
    private val username = Regex("[A-Za-z][A-Za-z0-9_]{3,31}")

    fun deepLink(url: String): String? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() != "https" || uri.host?.lowercase()?.removePrefix("www.") !in hosts) return null
        val parts = uri.rawPath.orEmpty().split('/').filter(String::isNotEmpty)
        val first = parts.firstOrNull() ?: return null
        return when {
            first.startsWith("+") && first.length > 1 -> "tg://join?invite=${encode(first.drop(1))}"
            first == "joinchat" && parts.size >= 2 -> "tg://join?invite=${encode(parts[1])}"
            first == "c" && parts.size >= 3 && parts[1].all(Char::isDigit) && parts[2].all(Char::isDigit) ->
                "tg://privatepost?channel=${parts[1]}&post=${parts[2]}"
            username.matches(first) && parts.size == 1 -> "tg://resolve?domain=$first"
            username.matches(first) && parts.size == 2 && parts[1].all(Char::isDigit) ->
                "tg://resolve?domain=$first&post=${parts[1]}"
            else -> null
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}
