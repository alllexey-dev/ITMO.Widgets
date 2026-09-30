package dev.alllexey.itmowidgets.feature.recordbook.work

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import api.bars.Bars
import api.bars.utils.BarsApiException
import api.bars.utils.BarsAuthHelper
import api.bars.utils.BarsSessionCode
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.core.debug.BarsSessionProbe
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.ItmoIdCookies
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Workers are built by WorkManager; see `QrWidgetEntryPoint` for why this is not `@HiltWorker`. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BarsCookieProbeEntryPoint {
    fun probeCookies(): ItmoIdCookies
    fun probeBars(): Bars
}

/**
 * Read-only probe of the cookie renewal: one ITMO.ID request with the WebView's cookies and, on a code, one
 * exchange. `Set-Cookie` is not written back, the BARS header is not saved, and the log line holds only the
 * outcome, counts and the process age.
 */
class BarsCookieProbeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!BuildConfig.DEBUG) return Result.success()
        val dependencies = EntryPointAccessors.fromApplication(applicationContext, BarsCookieProbeEntryPoint::class.java)
        val line = try {
            probe(dependencies.probeBars(), dependencies.probeCookies())
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: Exception) {
            val http = (failure as? BarsApiException)?.httpCode
            "outcome=ERROR step=$step type=${failure.javaClass.simpleName} http=$http cause=${failure.cause?.javaClass?.simpleName}"
        }
        Log.i(TAG, line)
        return Result.success()
    }

    /** The last step started, so an error line says where the probe stopped without echoing any value. */
    private var step = "start"

    private suspend fun probe(bars: Bars, cookies: ItmoIdCookies): String {
        val state = BarsAuthHelper.newState()
        step = "loginUrl"
        val url = bars.authHelper.getLoginUrl(state)
        step = "cookies"
        val cookie = cookies.cookieHeader(url)
        step = "request"
        val answer = withContext(Dispatchers.IO) { bars.authHelper.requestCodeWithCookies(state, cookie) }
        step = "exchange"
        val exchange = if (answer.outcome == BarsSessionCode.Outcome.CODE) {
            val valid = try {
                Bars.isValidAuthorization(withContext(Dispatchers.IO) { bars.authHelper.exchange(answer.code) })
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                false
            }
            if (valid) "OK" else "FAIL"
        } else {
            "SKIP"
        }
        val pairs = cookie?.split(';')?.count { it.isNotBlank() } ?: 0
        val processAgeSec = (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()) / 1000
        return "outcome=${answer.outcome} http=${answer.httpCode} exchange=$exchange cookies=$pairs " +
            "setCookies=${answer.setCookies.size} processAgeSec=$processAgeSec"
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
