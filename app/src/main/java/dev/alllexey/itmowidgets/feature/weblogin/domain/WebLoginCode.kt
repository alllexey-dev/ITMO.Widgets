package dev.alllexey.itmowidgets.feature.weblogin.domain

import java.net.URI
import java.net.URLDecoder

/**
 * The sign-in code a browser shows: eight characters without the look-alikes `0 O 1 I L`.
 * The QR next to it holds `https://<host>/app/login?code=<code>`; any HTTPS host is accepted
 * so the production and the dev web versions both work.
 */
object WebLoginCode {
    const val LENGTH = 8
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private const val LOGIN_PATH = "/app/login"

    /** The normalized code from a typed code or a scanned link, or null when [raw] holds neither. */
    fun parse(raw: String): String? {
        val text = raw.trim()
        if (text.startsWith("https://", ignoreCase = true)) return fromLink(text)
        if (text.contains("://")) return null
        return normalize(text)
    }

    /** Case, spaces and dashes are ignored, so `abcd-efgh` and `ABCD EFGH` are the same code. */
    private fun normalize(text: String): String? {
        val code = text.filterNot { it.isWhitespace() || it == '-' }.uppercase()
        return code.takeIf { it.length == LENGTH && it.all(ALPHABET::contains) }
    }

    private fun fromLink(text: String): String? {
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() || uri.rawUserInfo != null) return null
        if (uri.path?.trimEnd('/') != LOGIN_PATH) return null
        val value = uri.rawQuery.orEmpty().split('&')
            .map { it.substringBefore('=') to it.substringAfter('=', "") }
            .singleOrNull { it.first == "code" }?.second ?: return null
        val decoded = runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrNull() ?: return null
        return normalize(decoded)
    }
}
