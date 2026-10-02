package dev.alllexey.itmowidgets.feature.recordbook.data.sheets

import dev.alllexey.itmowidgets.core.network.PublicWebClient
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.CsvGrid
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetGrid
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTab
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

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
 * Downloads public Google Sheets without any account: a tab as CSV, then as the HTML view when the export is
 * forbidden, and the list of tabs from the HTML view. Addresses and bodies never reach the log or an exception.
 */
class PublicSheetClient internal constructor(
    private val client: OkHttpClient,
    private val base: HttpUrl,
    private val demo: DemoMode
) {

    @Inject constructor(@PublicWebClient client: OkHttpClient, demo: DemoMode) :
        this(client, "https://docs.google.com/".toHttpUrl(), demo)

    suspend fun tabs(spreadsheetId: String): SheetFetch<List<SheetTab>> =
        exchange(sheetUrl(spreadsheetId).addPathSegment("htmlview").build()) { response ->
            when {
                signIn(response) || response.code == 404 -> SheetFetch.Closed
                !response.isSuccessful -> failure(response.code)
                else -> text(response).map(SheetTabsParser::parse)
            }
        }

    suspend fun grid(spreadsheetId: String, gid: Long): SheetFetch<SheetGrid> {
        val csvUrl = sheetUrl(spreadsheetId).addPathSegment("export")
            .addQueryParameter("format", "csv").addQueryParameter("gid", gid.toString()).build()
        var html = false
        val csv = exchange(csvUrl) { response ->
            when {
                signIn(response) -> SheetFetch.Closed
                response.code == 400 || response.code == 404 -> SheetFetch.Missing
                response.code == 401 || response.code == 403 -> { html = true; SheetFetch.Closed }
                !response.isSuccessful -> failure(response.code)
                !isCsv(response) -> { html = true; SheetFetch.Closed }
                else -> text(response).map(CsvGrid::parse)
            }
        }
        if (!html) return csv
        val htmlUrl = sheetUrl(spreadsheetId).addPathSegments("htmlview/sheet")
            .addQueryParameter("headers", "false").addQueryParameter("gid", gid.toString()).build()
        return exchange(htmlUrl) { response ->
            when {
                signIn(response) || response.code == 401 || response.code == 403 -> SheetFetch.Closed
                response.code == 400 || response.code == 404 -> SheetFetch.Missing
                !response.isSuccessful -> failure(response.code)
                else -> text(response).map(SheetHtmlGrid::parse)
            }
        }
    }

    private fun sheetUrl(spreadsheetId: String): HttpUrl.Builder =
        base.newBuilder().addPathSegment("spreadsheets").addPathSegment("d").addPathSegment(spreadsheetId)

    /** Runs one request on the IO dispatcher; cancelling the coroutine cancels the call. */
    private suspend fun <T> exchange(url: HttpUrl, handle: (Response) -> SheetFetch<T>): SheetFetch<T> = if (demo.isActive()) {
        SheetFetch.Failed(AppError.DemoUnavailable)
    } else coroutineScope {
        val call = client.newCall(Request.Builder().url(url).get().build())
        val finished = AtomicBoolean(false)
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                if (!finished.get()) call.cancel()
            }
        }
        try {
            withContext(Dispatchers.IO) {
                try {
                    call.execute().use(handle)
                } catch (_: IOException) {
                    SheetFetch.Failed(AppError.Network)
                } catch (_: RuntimeException) {
                    SheetFetch.Failed(AppError.Unknown())
                }
            }
        } finally {
            finished.set(true)
            watcher.cancel()
        }
    }

    private fun signIn(response: Response): Boolean {
        val url = response.request.url
        return url.host == "accounts.google.com" ||
            url.encodedPath.startsWith("/ServiceLogin") ||
            url.encodedPath.startsWith("/v3/signin")
    }

    private fun isCsv(response: Response): Boolean = response.body?.contentType()
        ?.let { it.type == "text" && it.subtype == "csv" } == true

    private fun failure(code: Int): SheetFetch<Nothing> =
        SheetFetch.Failed(if (code >= 500 || code == 429) AppError.Network else AppError.Unknown())

    /** The body as text, or [SheetFetch.TooLarge] without reading past [MAX_BYTES]. */
    private fun text(response: Response): SheetFetch<String> {
        val body = response.body ?: return SheetFetch.Loaded("")
        if (body.contentLength() > MAX_BYTES) return SheetFetch.TooLarge
        val source = body.source()
        val buffer = Buffer()
        while (buffer.size <= MAX_BYTES) {
            if (source.read(buffer, READ_STEP) == -1L) break
        }
        if (buffer.size > MAX_BYTES) return SheetFetch.TooLarge
        val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        return SheetFetch.Loaded(buffer.readString(charset))
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
        private const val READ_STEP = 64L * 1024
    }
}
