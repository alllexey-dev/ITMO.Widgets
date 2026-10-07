package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.storage.SecureStore
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.parseServerSetCookieHeader
import kotlin.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The iOS [ItmoIdCookies]: a copy of the `id.itmo.ru` cookies in one Keychain item, so the background replay
 * ([BarsCookieSilentLogin]) signs in to BARS without a WebView (11 row 14, SP-21). [replaceFromWebKit] copies WebKit's
 * cookies after every WebView session; [store] merges the `Set-Cookie` values of a replay answer, the last of a name
 * winning (`KC_RESTART` may come twice). [cookieHeader] matches domain, path, `Secure` and expiry as RFC 6265 does.
 *
 * Only `id.itmo.ru` cookies are kept: WebKit's domain match also picks up `.itmo.ru` analytics cookies. The item is
 * `{"version": 1, "cookies": [...]}`; a higher version or a value that does not parse reads as no cookies. Values are
 * never logged; sign-out removes the item with every other Keychain item of the app.
 */
class KeychainItmoIdCookies(
    private val store: SecureStore,
    private val clock: Clock,
    private val dispatchers: AppDispatchers,
) : ItmoIdCookies {

    private val lock = Mutex()

    override suspend fun cookieHeader(url: String): String? {
        val target = secureTarget(url) ?: return null
        val now = clock.now().toEpochMilliseconds()
        return locked { read() }
            .filter { it.matches(target, now) }
            // RFC 6265 5.4: longer paths first.
            .sortedByDescending { it.path.length }
            .joinToString("; ") { "${it.name}=${it.value}" }
            .ifEmpty { null }
    }

    override suspend fun store(url: String, setCookies: List<String>) {
        if (setCookies.isEmpty()) return
        val target = secureTarget(url) ?: return
        val now = clock.now().toEpochMilliseconds()
        locked {
            val cookies = read().associateByTo(LinkedHashMap()) { it.key }
            setCookies.forEach { header ->
                val parsed = parse(header, target, now) ?: return@forEach
                if (parsed.isExpired(now)) cookies.remove(parsed.key) else cookies[parsed.key] = parsed
            }
            write(cookies.values.toList(), now)
        }
    }

    /** Replaces the copy with WebKit's `id.itmo.ru` cookies; with none the item is removed. */
    suspend fun replaceFromWebKit(cookies: List<WebKitCookie>) {
        val now = clock.now().toEpochMilliseconds()
        val copied = cookies.mapNotNull { it.toStored() }
        locked { write(copied, now) }
    }

    private suspend fun <T> locked(block: () -> T): T = lock.withLock { withContext(dispatchers.io) { block() } }

    private fun read(): List<StoredCookie> {
        val text = store.read(ITEM) ?: return emptyList()
        val item = runCatching { json.decodeFromString(StoredCookies.serializer(), text) }.getOrNull()
        return if (item == null || item.version > VERSION) emptyList() else item.cookies
    }

    private fun write(cookies: List<StoredCookie>, now: Long) {
        val kept = cookies.filter { it.domain == ITMO_ID_HOST && it.name.isNotEmpty() && !it.isExpired(now) }
        if (kept.isEmpty()) {
            store.delete(ITEM)
        } else {
            store.write(ITEM, json.encodeToString(StoredCookies.serializer(), StoredCookies(VERSION, kept)))
        }
    }

    private fun parse(header: String, target: Url, now: Long): StoredCookie? {
        val cookie = runCatching { parseServerSetCookieHeader(header) }.getOrNull() ?: return null
        if (cookie.name.isEmpty()) return null
        val host = target.host.lowercase()
        val domain = cookie.domain?.trim()?.trimStart('.')?.lowercase()?.takeIf { it.isNotEmpty() }
        // RFC 6265 5.3 step 6: a Domain the request host does not domain-match is ignored with the cookie.
        if (domain != null && host != domain && !host.endsWith(".$domain")) return null
        val expiresAt = when (val maxAge = cookie.maxAge) {
            null -> cookie.expires?.timestamp
            else -> if (maxAge <= 0) Long.MIN_VALUE else now + maxAge * 1_000L
        }
        return StoredCookie(
            name = cookie.name,
            value = cookie.value,
            domain = domain ?: host,
            hostOnly = domain == null,
            path = cookie.path?.takeIf { it.startsWith("/") } ?: defaultPath(target.encodedPath),
            secure = cookie.secure,
            expiresAt = expiresAt,
        )
    }

    private fun WebKitCookie.toStored(): StoredCookie? {
        val normalized = domain.trim().lowercase()
        return StoredCookie(
            name = name,
            value = value,
            domain = normalized.trimStart('.'),
            hostOnly = !normalized.startsWith("."),
            path = path.takeIf { it.startsWith("/") } ?: "/",
            secure = secure,
            expiresAt = expiresAtEpochMillis,
        ).takeIf { it.domain == ITMO_ID_HOST && name.isNotEmpty() }
    }

    private fun secureTarget(url: String): Url? =
        runCatching { Url(url) }.getOrNull()?.takeIf { it.protocol == URLProtocol.HTTPS && it.host.isNotEmpty() }

    companion object {
        /** The Keychain item (`SecureStore` name); never renamed, or every BARS user signs in to ITMO.ID again. */
        const val ITEM = "itmo_id_cookies"

        private const val VERSION = 1
        private const val ITMO_ID_HOST = "id.itmo.ru"

        private val json = Json { ignoreUnknownKeys = true }

        /** RFC 6265 5.1.4: the request path up to its last `/`, or `/`. */
        private fun defaultPath(requestPath: String): String {
            if (!requestPath.startsWith("/")) return "/"
            val end = requestPath.lastIndexOf('/')
            return if (end <= 0) "/" else requestPath.substring(0, end)
        }
    }
}

@Serializable
private class StoredCookies(val version: Int, val cookies: List<StoredCookie>)

@Serializable
private class StoredCookie(
    val name: String,
    val value: String,
    /** Lower case, without a leading dot. */
    val domain: String,
    val hostOnly: Boolean,
    val path: String,
    val secure: Boolean,
    /** Epoch milliseconds; null for a session cookie. */
    val expiresAt: Long?,
) {
    /** RFC 6265 5.3: one cookie per name, domain and path. */
    val key: String get() = "$name\n$domain\n$path"

    fun isExpired(now: Long): Boolean = expiresAt != null && expiresAt <= now

    fun matches(target: Url, now: Long): Boolean {
        val host = target.host.lowercase()
        val domainMatches = if (hostOnly) host == domain else host == domain || host.endsWith(".$domain")
        return domainMatches && pathMatches(target.encodedPath.ifEmpty { "/" }) &&
            (!secure || target.protocol == URLProtocol.HTTPS) && !isExpired(now)
    }

    /** RFC 6265 5.1.4. */
    private fun pathMatches(requestPath: String): Boolean = requestPath == path ||
        requestPath.startsWith(path) && (path.endsWith("/") || requestPath[path.length] == '/')

    override fun toString(): String = "StoredCookie($name @$domain$path)"
}
