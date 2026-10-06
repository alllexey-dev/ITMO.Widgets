package dev.alllexey.itmowidgets.feature.recordbook.data.sheets

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.CsvGrid
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetGrid
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTab
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.request
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.charset
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.charsets.decode
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.io.IOException

/** What one download from a public sheet gave. */
sealed interface SheetFetch<out T> {
    data class Loaded<T>(val value: T) : SheetFetch<T> {
        override fun toString(): String = "Loaded"
    }

    /** The sheet asks to sign in: it is not public. */
    data object Closed : SheetFetch<Nothing>

    /** The tab is gone. */
    data object Missing : SheetFetch<Nothing>

    /** The answer is larger than [PublicSheetClient.MAX_BYTES]. */
    data object TooLarge : SheetFetch<Nothing>
    data class Failed(val error: AppError) : SheetFetch<Nothing>
}

/**
 * The HTTP client for public pages: no cookies (no `HttpCookies`) and no credentials of any account; redirects are
 * followed, but never from HTTPS to HTTP (the engine follows none itself); 15 s to connect, 30 s between bytes and 90 s
 * per request.
 */
internal fun publicSheetHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    expectSuccess = false
    followRedirects = true
    install(HttpRedirect) {
        allowHttpsDowngrade = false
    }
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 30_000
        requestTimeoutMillis = 90_000
    }
}

/**
 * The platform engine of [publicSheetHttpClient]: OkHttp on Android, URLSession on iOS, each without cookie storage,
 * cache or redirects of its own.
 */
internal expect fun publicSheetEngine(): HttpClientEngine

/**
 * Downloads public Google Sheets without any account: a tab as CSV, then as the HTML view when the export is
 * forbidden, and the list of tabs from the HTML view. Addresses and bodies never reach the log or an exception.
 */
class PublicSheetClient internal constructor(
    private val client: HttpClient,
    private val base: Url,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers
) {

    constructor(demo: DemoMode, dispatchers: AppDispatchers) :
        this(publicSheetHttpClient(publicSheetEngine()), Url("https://docs.google.com/"), demo, dispatchers)

    suspend fun tabs(spreadsheetId: String): SheetFetch<List<SheetTab>> =
        exchange(sheetUrl(spreadsheetId, "htmlview")) { response ->
            when {
                signIn(response) || response.status.value == 404 -> SheetFetch.Closed
                !response.status.isSuccess() -> failure(response.status.value)
                else -> text(response).map(SheetTabsParser::parse)
            }
        }

    suspend fun grid(spreadsheetId: String, gid: Long): SheetFetch<SheetGrid> {
        val csvUrl = sheetUrl(spreadsheetId, "export", "format" to "csv", "gid" to gid.toString())
        var html = false
        val csv = exchange(csvUrl) { response ->
            val code = response.status.value
            when {
                signIn(response) -> SheetFetch.Closed
                code == 400 || code == 404 -> SheetFetch.Missing
                code == 401 || code == 403 -> { html = true; SheetFetch.Closed }
                !response.status.isSuccess() -> failure(code)
                !isCsv(response) -> { html = true; SheetFetch.Closed }
                else -> text(response).map(CsvGrid::parse)
            }
        }
        if (!html) return csv
        val htmlUrl = sheetUrl(spreadsheetId, "htmlview/sheet", "headers" to "false", "gid" to gid.toString())
        return exchange(htmlUrl) { response ->
            val code = response.status.value
            when {
                signIn(response) || code == 401 || code == 403 -> SheetFetch.Closed
                code == 400 || code == 404 -> SheetFetch.Missing
                !response.status.isSuccess() -> failure(code)
                else -> text(response).map(SheetHtmlGrid::parse)
            }
        }
    }

    /** `spreadsheets/d/<id>/<path>` on [base]; the id is one path segment, whatever it holds. */
    private fun sheetUrl(spreadsheetId: String, path: String, vararg query: Pair<String, String>): Url =
        URLBuilder(base).apply {
            pathSegments = listOf("spreadsheets", "d", spreadsheetId) + path.split("/")
            query.forEach { (name, value) -> parameters.append(name, value) }
        }.build()

    /**
     * Runs one request on the IO dispatcher and reads its body there, streaming; cancelling the coroutine cancels the
     * call.
     */
    private suspend fun <T> exchange(url: Url, handle: suspend (HttpResponse) -> SheetFetch<T>): SheetFetch<T> =
        if (demo.isActive()) {
            SheetFetch.Failed(AppError.DemoUnavailable)
        } else withContext(dispatchers.io) {
            try {
                client.prepareGet(url).execute { response -> handle(response) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: IOException) {
                SheetFetch.Failed(AppError.Network)
            } catch (_: RuntimeException) {
                SheetFetch.Failed(AppError.Unknown())
            }
        }

    private fun signIn(response: HttpResponse): Boolean {
        val url = response.request.url
        return url.host == "accounts.google.com" ||
            url.encodedPath.startsWith("/ServiceLogin") ||
            url.encodedPath.startsWith("/v3/signin")
    }

    private fun isCsv(response: HttpResponse): Boolean = response.contentType()?.let {
        it.contentType.equals("text", ignoreCase = true) && it.contentSubtype.equals("csv", ignoreCase = true)
    } == true

    private fun failure(code: Int): SheetFetch<Nothing> =
        SheetFetch.Failed(if (code >= 500 || code == 429) AppError.Network else AppError.Unknown())

    /** The body as text, or [SheetFetch.TooLarge] without reading past [MAX_BYTES]. */
    private suspend fun text(response: HttpResponse): SheetFetch<String> {
        val declared = response.contentLength()
        if (declared != null && declared > MAX_BYTES) return SheetFetch.TooLarge
        val body = response.bodyAsChannel().readBuffer(MAX_BYTES + 1L)
        if (body.size > MAX_BYTES) return SheetFetch.TooLarge
        val charset = response.charset() ?: Charsets.UTF_8
        return SheetFetch.Loaded(charset.newDecoder().decode(body))
    }

    private inline fun <T, R> SheetFetch<T>.map(transform: (T) -> R): SheetFetch<R> = when (this) {
        is SheetFetch.Loaded -> SheetFetch.Loaded(transform(value))
        SheetFetch.Closed -> SheetFetch.Closed
        SheetFetch.Missing -> SheetFetch.Missing
        SheetFetch.TooLarge -> SheetFetch.TooLarge
        is SheetFetch.Failed -> this
    }

    companion object {
        const val MAX_BYTES = 5 * 1024 * 1024
    }
}
