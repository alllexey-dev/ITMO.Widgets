package dev.alllexey.itmowidgets.feature.recordbook.work

import android.content.Context
import android.os.Process
import android.os.SystemClock
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alllexey.itmoapi.bars.RuntimeBarsStorage
import dev.alllexey.itmoapi.bars.auth.BarsLogin
import dev.alllexey.itmoapi.bars.auth.BarsSessionCode
import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.core.debug.BarsSessionProbe
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.ItmoIdCookies
import dev.alllexey.itmowidgets.feature.recordbook.di.barsEngineQualifier
import io.ktor.client.engine.HttpClientEngine
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import dev.alllexey.itmoapi.bars.BarsClient as LibraryBarsClient

/**
 * Read-only probe of the cookie renewal: one ITMO.ID request with the WebView's cookies and, on a code, one
 * exchange. `Set-Cookie` is not written back, the BARS header is not saved, and the log line holds only the
 * outcome, counts and the process age.
 */
class BarsCookieProbeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {

    /** WorkManager can run a worker before `Application.onCreate()` has started Koin. */
    override fun getKoin(): Koin = KoinStarter.ensureStarted(applicationContext)

    override suspend fun doWork(): Result {
        if (!BuildConfig.DEBUG) return Result.success()
        val line = try {
            probe(get<BarsLogin>(), get<HttpClientEngine>(barsEngineQualifier), get<ItmoIdCookies>())
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: Exception) {
            val http = when (failure) {
                is MyItmoException.Http -> failure.status
                is MyItmoException.Auth -> failure.status
                else -> null
            }
            "outcome=ERROR step=$step type=${failure.javaClass.simpleName} http=$http cause=${failure.cause?.javaClass?.simpleName}"
        }
        get<AppLog>().info(TAG, line)
        return Result.success()
    }

    /** The last step started, so an error line says where the probe stopped without echoing any value. */
    private var step = "start"

    private suspend fun probe(login: BarsLogin, engine: HttpClientEngine, cookies: ItmoIdCookies): String {
        val state = login.newState()
        step = "loginUrl"
        val url = login.loginUrl(state)
        step = "cookies"
        val cookie = cookies.cookieHeader(url)
        step = "request"
        val answer = login.requestCodeWithCookies(state, cookie)
        step = "exchange"
        val code = answer.code
        val exchange = if (answer.outcome == BarsSessionCode.Outcome.CODE && code != null) {
            if (exchanges(engine, code)) "OK" else "FAIL"
        } else {
            "SKIP"
        }
        val pairs = cookie?.split(';')?.count { it.isNotBlank() } ?: 0
        val processAgeSec = (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()) / 1000
        return "outcome=${answer.outcome} http=${answer.httpCode} exchange=$exchange cookies=$pairs " +
            "setCookies=${answer.setCookies.size} processAgeSec=$processAgeSec"
    }

    /** Exchanges [code] into a throwaway in-memory session, so the app's saved BARS header stays untouched. */
    private suspend fun exchanges(engine: HttpClientEngine, code: String): Boolean {
        val storage = RuntimeBarsStorage()
        val client = LibraryBarsClient(engine, storage = storage)
        return try {
            client.login(code)
            LibraryBarsClient.isValidAuthorization(storage.getAuthorization())
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            false
        } finally {
            client.close()
        }
    }

    private companion object {
        const val TAG = "BarsCookieProbe"
    }
}

/** The delay leaves time to send the app to the background and kill its process, so the probe starts cold. */
class WorkManagerBarsSessionProbe @Inject constructor(
    @param:ApplicationContext private val context: Context
) : BarsSessionProbe {
    override fun start() {
        val request = OneTimeWorkRequestBuilder<BarsCookieProbeWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(DELAY_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val WORK = "bars-cookie-probe"
        const val DELAY_SECONDS = 120L
    }
}
