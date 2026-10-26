package dev.alllexey.itmowidgets.core.network

import dev.alllexey.itmowidgets.core.session.ItmoIdCookies
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.hostWithPort
import io.ktor.http.renderSetCookieHeader
import io.ktor.http.setCookie

/**
 * The on-device ISU client: it signs in to ISU (`isu.ifmo.ru`, Oracle APEX) with the phone's own ITMO.ID SSO cookies,
 * as a browser that is already signed in to ITMO.ID would, and reads pages of the user's own cabinet. Nothing leaves
 * the phone and nothing is written in ISU.
 *
 * Redirects are followed by hand: the ITMO.ID cookies ([ItmoIdCookies]) go only to `id.itmo.ru`, the ISU session
 * cookies live in a jar of one [IsuSession] and go only to ISU, and any other host or plain HTTP is refused.
 * `Set-Cookie` of ITMO.ID goes back to the WebView's store as the WebView would keep it. Cookies, session numbers and
 * page bodies are never logged or put into exceptions.
 */
class IsuWebClient(engine: HttpClientEngine, private val cookies: ItmoIdCookies) {
    private val http = HttpClient(engine) {
        followRedirects = false
        expectSuccess = false
    }

    /**
     * A fresh ISU session (the portal's start page, `f?p=2143:1`); null when ITMO.ID wants the user: the phone has no
     * SSO cookies (a refresh-token sign-in has none) or they no longer sign in.
     *
     * @throws IsuException when ISU answers something else; a network failure is thrown as it is.
     */
    suspend fun signIn(): IsuSession? {
        val jar = AcceptAllCookiesStorage()
        val page = fetch(jar, apexUrl("f?p=$PORTAL:1")) ?: return null
        val id = page.sessionOf(PORTAL) ?: throw IsuException("ISU sign-in ended without a session")
        return IsuSession(this, jar, id)
    }

    /** The page of [app]:[page] in [session]; null when ISU or ITMO.ID wants a new sign-in. */
    internal suspend fun page(session: IsuSession, app: Int, page: String): Fetched? {
        val fetched = fetch(session.jar, apexUrl("f?p=$app:$page:${session.id}")) ?: return null
        // With a live cookie APEX silently replaces an unknown session number and serves the page.
        return fetched.takeIf { it.sessionOf(app) != null }
    }

    /** Follows redirects from [start] to a page; null when the chain reaches ITMO.ID's sign-in. */
    private suspend fun fetch(jar: CookiesStorage, start: Url): Fetched? {
        var url = start
        repeat(MAX_REDIRECTS) {
            val response = request(jar, url) ?: return null
            val location = response.headers[HttpHeaders.Location]
            if (response.status.value in REDIRECTS && location != null) {
                url = url.resolve(location)
                return@repeat
            }
            val body = response.bodyAsText()
            if (url.host == ITMO_ID_HOST || LOGIN_FORM in body) return null
            if (response.status.value != OK || !url.isApex()) {
                throw IsuException("ISU answered ${response.status.value}")
            }
            return Fetched(url, body)
        }
        throw IsuException("ISU redirected more than $MAX_REDIRECTS times")
    }

    /** One request with the cookies of its host; null when ITMO.ID is asked but the phone has no SSO cookies. */
    private suspend fun request(jar: CookiesStorage, url: Url): HttpResponse? {
        if (url.protocol.name != "https") throw IsuException("ISU redirected to plain HTTP")
        return when (url.host) {
            ITMO_ID_HOST -> {
                val header = cookies.cookieHeader(url.toString())?.takeIf { it.isNotBlank() } ?: return null
                send(url, header).also { response ->
                    cookies.store(url.toString(), response.setCookie().map(::setCookieLine))
                }
            }
            in ISU_HOSTS -> {
                val header = jar.get(url).joinToString("; ") { "${it.name}=${it.value}" }
                send(url, header.ifEmpty { null }).also { response ->
                    response.setCookie().forEach { jar.addCookie(url, it) }
                }
            }
            else -> throw IsuException("ISU redirected to another host")
        }
    }

    private suspend fun send(url: Url, cookieHeader: String?): HttpResponse = http.get(url) {
        header(HttpHeaders.UserAgent, USER_AGENT)
        header(HttpHeaders.Accept, "text/html")
        cookieHeader?.let { header(HttpHeaders.Cookie, it) }
    }

    /** [location] against this address: absolute, host-relative (`/...`) or relative to its directory (`f?p=...`). */
    private fun Url.resolve(location: String): Url = when {
        "://" in location -> Url(location)
        location.startsWith("//") -> Url("${protocol.name}:$location")
        location.startsWith("/") -> Url("${protocol.name}://$hostWithPort$location")
        else -> Url("${protocol.name}://$hostWithPort${encodedPath.substringBeforeLast('/')}/$location")
    }

    /** A parsed cookie back as the `Set-Cookie` line a WebView stores, without Ktor's own `${'$'}x-enc` marker. */
    private fun setCookieLine(cookie: Cookie): String = with(cookie) {
        renderSetCookieHeader(name, value, encoding, maxAge, expires, domain, path, secure, httpOnly, extensions, false)
    }

    private fun Url.isApex(): Boolean = host in ISU_HOSTS && encodedPath.startsWith(APEX_PATH)

    /** A page ISU served: where the redirects ended and its HTML. */
    internal class Fetched(val url: Url, val body: String) {
        /** The APEX session number of `f?p=<app>:<page>:<session>` in the final address. */
        fun sessionOf(app: Int): String? =
            Regex("""(?:^|&)p=$app:[^:&]*:([0-9]+)""").find(url.encodedQuery)?.groupValues?.get(1)

        override fun toString(): String = "Fetched(redacted)"
    }

    private companion object {
        const val ITMO_ID_HOST = "id.itmo.ru"
        val ISU_HOSTS = setOf("isu.ifmo.ru", "isu.itmo.ru")
        const val APEX_PATH = "/pls/apex/"
        const val PORTAL = 2143
        const val MAX_REDIRECTS = 10
        const val OK = 200
        val REDIRECTS = 300..399
        const val LOGIN_FORM = "id=\"kc-form-login\""

        /** ISU serves its cabinet to browsers; the same one the backend's ISU client sends. */
        const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/129.0.0.0 Safari/537.36"

        fun apexUrl(query: String): Url = Url("https://isu.ifmo.ru$APEX_PATH$query")
    }
}

/**
 * One signed-in ISU session: the APEX session number and the ISU cookies of the sign-in, kept in memory for as long
 * as the caller holds it. Each [page] reads one page of the user's cabinet.
 */
class IsuSession internal constructor(
    private val client: IsuWebClient,
    internal val jar: CookiesStorage,
    id: String,
) {
    internal var id: String = id
        private set

    /** The HTML of `f?p=[app]:[page]`; null when ISU lost the session and wants a new sign-in. */
    suspend fun page(app: Int, page: String): String? {
        val fetched = client.page(this, app, page) ?: return null
        fetched.sessionOf(app)?.let { id = it }
        return fetched.body
    }

    override fun toString(): String = "IsuSession(redacted)"
}

/** ISU answered something the client does not expect; the message names no cookie, session or page content. */
class IsuException(message: String) : Exception(message)
