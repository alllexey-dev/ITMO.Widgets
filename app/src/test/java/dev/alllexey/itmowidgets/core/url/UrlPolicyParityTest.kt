package dev.alllexey.itmowidgets.core.url

import dev.alllexey.itmowidgets.core.navigation.AppLink
import dev.alllexey.itmowidgets.core.navigation.AppLinks
import dev.alllexey.itmowidgets.core.resources.GoogleSheetUrl
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.feature.auth.domain.ItmoAuthUrlPolicy
import dev.alllexey.itmowidgets.feature.resources.domain.guessCategory
import dev.alllexey.itmowidgets.feature.web.domain.MyItmoWebPolicy
import dev.alllexey.itmowidgets.feature.weblogin.domain.WebLoginCode
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every policy that gates a WebView or a link decides on the corpus exactly as its `java.net.URI` version did
 * ([Released], copied from 2.2 unchanged).
 */
class UrlPolicyParityTest {

    private val inputs = UrlParityCorpus.policyUrls + UrlParityCorpus.combinations(20_000) +
        UrlParityCorpus.noise(20_000)

    @Test fun `https navigation`() =
        assertParity(Released::isNavigable, HttpsNavigationPolicy::isNavigable)

    @Test fun `ITMO ID token callback`() =
        assertParity(Released::isTokenCallback, ItmoAuthUrlPolicy::isTokenCallback)

    @Test fun `My ITMO WebView origins and navigation`() {
        assertParity(Released::isInternal, MyItmoWebPolicy::isInternal)
        for (mainFrame in listOf(true, false)) for (gesture in listOf(true, false)) {
            assertParity({ Released.navigation(it, mainFrame, gesture) }, { MyItmoWebPolicy.navigation(it, mainFrame, gesture) })
        }
    }

    @Test fun `Telegram deep links`() = assertParity(Released::deepLink, TelegramLinks::deepLink)

    @Test fun `Google Sheet addresses`() = assertParity(Released::googleSheet, GoogleSheetUrl::parse)

    @Test fun `web sign-in codes`() = assertParity(Released::webLoginCode, WebLoginCode::parse)

    @Test fun `link category guesses`() = assertParity(Released::guessCategory, ::guessCategory)

    @Test fun `app links`() = assertParity(Released::appLink, AppLinks::parse, inputs + appLinkInputs())

    private fun <T> assertParity(released: (String) -> T, common: (String) -> T, cases: List<String> = inputs) {
        for (input in cases) assertEquals(input, released(input), common(input))
    }

    /** Links under the app prefixes, which the shared corpus rarely reaches, with the corpus traps around them. */
    private fun appLinkInputs(): List<String> {
        val schemes = listOf("https://", "HTTPS://", "Https://", "http://", "https:/", "https:\\\\")
        val authorities = listOf(
            "widgets.alllexey.dev", "dev.widgets.alllexey.dev", "WIDGETS.ALLLEXEY.DEV", "Dev.Widgets.Alllexey.Dev",
            "widgets.alllexey.dev.", "user@widgets.alllexey.dev", "@widgets.alllexey.dev", "widgets.alllexey.dev:443",
            "widgets.alllexey.dev:", "widgets.alllexey.dev:99999999999", "widgets.alllexey.dev%2F@evil.invalid",
            "widgets.alllexey.dev\\@evil.invalid", "wіdgets.alllexey.dev", "widgets_alllexey.dev", "[::1]",
            "widgets.alllexey.dev.example.com", "example.com",
        )
        val paths = listOf(
            "/u/123456", "/u/123456/", "/u/123456//", "/u/0", "/u/012", "/u/-1", "/u/+1", "/u/2147483647",
            "/u/2147483648", "/u/%31", "/u/1%2F", "/u%2F1", "/U/1", "/u/１", "/u/1;x", "/u/ 1", "/u/1\\", "/u/",
            "/u", "/sport/9223372036854775807", "/sport/9223372036854775808", "/sport/1/", "/sport/0", "/sport/x",
            "/sport/p/1", "/sport/p/1/", "/sport/p/", "/sport/p", "/sport/P/1", "/sport/p/%31", "/sport//1",
            "/sport/p/1/2", "/sports/1", "//u/1", "/app/x", "/", "",
        )
        val tails = listOf("", "", "?utm=tg", "#top", "?a=1#b", "?", "#", "#a#b", "?a b", "?a=[b]")
        val grid = schemes.flatMap { scheme ->
            authorities.flatMap { authority -> paths.map { path -> scheme + authority + path } }
        }
        val random = Random(17)
        return grid.map { it + tails.random(random) } + grid.map { it + tails.random(random) }
    }

    /** The 2.2 policies on `java.net.URI`, `URLEncoder` and `URLDecoder`. */
    private object Released {

        private fun parse(url: String): URI? = runCatching { URI(url) }.getOrNull()

        fun isNavigable(url: String): Boolean {
            val uri = parse(url) ?: return false
            return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
        }

        fun isTokenCallback(url: String): Boolean {
            val uri = parse(url) ?: return false
            return uri.scheme.equals("https", ignoreCase = true) && uri.host.equals("my.itmo.ru", ignoreCase = true) &&
                uri.path == "/login/callback"
        }

        fun isInternal(url: String): Boolean {
            val uri = parse(url) ?: return false
            return uri.scheme.equals("https", ignoreCase = true) && uri.rawUserInfo == null &&
                uri.port in setOf(-1, 443) && uri.host?.lowercase(Locale.ROOT) in setOf("my.itmo.ru", "id.itmo.ru")
        }

        fun navigation(url: String, mainFrame: Boolean, userGesture: Boolean): MyItmoWebPolicy.Navigation {
            if (isInternal(url)) return MyItmoWebPolicy.Navigation.INTERNAL
            val uri = parse(url)
            return if (mainFrame && userGesture && uri?.scheme.equals("https", ignoreCase = true) &&
                uri?.host != null && uri.rawUserInfo == null) MyItmoWebPolicy.Navigation.EXTERNAL
            else MyItmoWebPolicy.Navigation.BLOCKED
        }

        private val telegramHosts = setOf("t.me", "telegram.me", "telegram.dog")
        private val telegramUsername = Regex("[A-Za-z][A-Za-z0-9_]{3,31}")

        fun deepLink(url: String): String? {
            val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
            if (uri.scheme?.lowercase() != "https" || uri.host?.lowercase()?.removePrefix("www.") !in telegramHosts) return null
            val parts = uri.rawPath.orEmpty().split('/').filter(String::isNotEmpty)
            val first = parts.firstOrNull() ?: return null
            fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
            return when {
                first.startsWith("+") && first.length > 1 -> "tg://join?invite=${encode(first.drop(1))}"
                first == "joinchat" && parts.size >= 2 -> "tg://join?invite=${encode(parts[1])}"
                first == "c" && parts.size >= 3 && parts[1].all(Char::isDigit) && parts[2].all(Char::isDigit) ->
                    "tg://privatepost?channel=${parts[1]}&post=${parts[2]}"
                telegramUsername.matches(first) && parts.size == 1 -> "tg://resolve?domain=$first"
                telegramUsername.matches(first) && parts.size == 2 && parts[1].all(Char::isDigit) ->
                    "tg://resolve?domain=$first&post=${parts[1]}"
                else -> null
            }
        }

        private val sheetPath = Regex("""^/spreadsheets(?:/u/\d+)?/d/([A-Za-z0-9_-]+)(?:/.*)?$""")
        private val sheetId = Regex("""[A-Za-z0-9_-]{20,}""")

        fun googleSheet(url: String): GoogleSheetUrl? {
            val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
            if (!uri.scheme.equals("https", ignoreCase = true)) return null
            if (!uri.host.equals("docs.google.com", ignoreCase = true)) return null
            val id = sheetPath.matchEntire(uri.rawPath.orEmpty())?.groupValues?.get(1) ?: return null
            if (id == "e" || !sheetId.matches(id)) return null
            fun parameter(text: String?, name: String): String? = text?.split('&')
                ?.firstOrNull { it.substringBefore('=') == name && '=' in it }
                ?.substringAfter('=')
            val gid = parameter(uri.rawFragment, "gid") ?: parameter(uri.rawQuery, "gid")
            return GoogleSheetUrl(id, gid?.toLongOrNull())
        }

        private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

        fun webLoginCode(raw: String): String? {
            fun normalize(text: String): String? {
                val code = text.filterNot { it.isWhitespace() || it == '-' }.uppercase()
                return code.takeIf { it.length == 8 && it.all(CODE_ALPHABET::contains) }
            }
            val text = raw.trim()
            if (text.startsWith("https://", ignoreCase = true)) {
                val uri = runCatching { URI(text) }.getOrNull() ?: return null
                if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() || uri.rawUserInfo != null) return null
                if (uri.path?.trimEnd('/') != "/app/login") return null
                val value = uri.rawQuery.orEmpty().split('&')
                    .map { it.substringBefore('=') to it.substringAfter('=', "") }
                    .singleOrNull { it.first == "code" }?.second ?: return null
                val decoded = runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrNull() ?: return null
                return normalize(decoded)
            }
            if (text.contains("://")) return null
            return normalize(text)
        }

        private val isu = Regex("[1-9][0-9]{0,9}")
        private val lessonId = Regex("[1-9][0-9]{0,18}")

        fun appLink(url: String): AppLink? {
            val uri = parse(url) ?: return null
            if (!uri.scheme.equals("https", ignoreCase = true)) return null
            if (uri.host?.lowercase() !in setOf("widgets.alllexey.dev", "dev.widgets.alllexey.dev")) return null
            val path = uri.rawPath.orEmpty()
            fun identifier(prefix: String, format: Regex): String? =
                path.removePrefix(prefix).removeSuffix("/").takeIf(format::matches)
            return when {
                path.startsWith("/u/") -> identifier("/u/", isu)
                    ?.toIntOrNull()?.let(AppLink::Profile) ?: AppLink.Malformed
                path.startsWith("/sport/p/") -> identifier("/sport/p/", lessonId)
                    ?.toLongOrNull()?.let(AppLink::PredictedSportLesson) ?: AppLink.Malformed
                path.startsWith("/sport/") -> identifier("/sport/", lessonId)
                    ?.toLongOrNull()?.let(AppLink::SportLesson) ?: AppLink.Malformed
                else -> null
            }
        }

        fun guessCategory(url: String): LinkCategory? {
            val text = url.trim().let { if ("://" in it) it else "https://$it" }
            val uri = runCatching { URI(text) }.getOrNull() ?: return null
            val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return null
            val path = uri.path.orEmpty()
            fun on(domain: String) = host == domain || host.endsWith(".$domain")
            return when {
                host == "docs.google.com" && path.startsWith("/spreadsheets") -> LinkCategory.SCORES
                host == "docs.google.com" && path.startsWith("/forms") -> LinkCategory.QUEUE
                on("github.com") -> LinkCategory.TASKS
                on("youtube.com") || on("youtu.be") || on("vkvideo.ru") -> LinkCategory.RECORDINGS
                on("vk.com") && path.startsWith("/video") -> LinkCategory.RECORDINGS
                on("notion.so") || host.endsWith(".notion.site") -> LinkCategory.NOTES
                on("lms.itmo.ru") -> LinkCategory.MATERIALS
                on("t.me") || on("vk.me") || host == "chat.whatsapp.com" -> LinkCategory.CHAT
                else -> null
            }
        }
    }
}
