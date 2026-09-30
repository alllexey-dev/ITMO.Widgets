package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import api.bars.Bars
import api.bars.model.Term
import api.bars.model.User
import api.bars.utils.BarsApiException
import api.bars.utils.BarsCodeSupplier
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.Call

/**
 * App-side seam over the library client: binds the session to the signed-in ISU, serializes
 * period selection, and turns library failures into [AppError]. Silent renewal goes through the
 * library's [BarsCodeSupplier]: screens ([account]) renew through the headless WebView flow, the
 * background ([backgroundAccount]) through ITMO.ID cookies without a WebView. Only one block runs
 * at a time, so the supplier's mode belongs to the block that holds the lock.
 */
@Singleton
class BarsClient @Inject constructor(
    val bars: Bars,
    private val storage: OwnerBoundBarsStorage,
    private val currentUser: CurrentUserProvider,
    silentLogin: BarsSilentLogin,
    backgroundLogin: BarsBackgroundLogin
) {
    private val mutex = Mutex()

    @Volatile private var cookieRenewal = false

    init {
        bars.codeSupplier = BarsCodeSupplier { state ->
            if (cookieRenewal) {
                codeOf(runBlocking { backgroundLogin.renew(state) })
            } else {
                runBlocking { silentLogin.authorizationCode(state) }
            }
        }
    }

    suspend fun <T> account(block: suspend Account.() -> T): AppResult<T> = safe {
        mutex.withLock {
            val account = Account(owner())
            account.open()
            account.block()
        }
    }

    /**
     * The background form of [account]: the session is renewed through ITMO.ID cookies, and an ended ITMO.ID
     * session is [BarsBackground.SessionEnded] instead of a failure. Without a saved session for the current
     * ISU nothing is requested.
     */
    suspend fun <T> backgroundAccount(block: suspend Account.() -> T): BarsBackground<T> = try {
        mutex.withLock {
            val owner = owner()
            if (withContext(Dispatchers.IO) { storage.getAuthorization() } == null) {
                BarsBackground.NoSession
            } else {
                cookieRenewal = true
                try {
                    val account = Account(owner)
                    account.open()
                    BarsBackground.Success(account.block())
                } finally {
                    cookieRenewal = false
                }
            }
        }
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: SessionEndedSignal) {
        BarsBackground.SessionEnded
    } catch (failure: BarsFailure) {
        BarsBackground.Failure(failure.error)
    } catch (_: Exception) {
        BarsBackground.Failure(AppError.Unknown())
    }

    /** Interactive ITMO.ID result; the session is kept only if it belongs to the app's user. */
    suspend fun login(code: String): AppResult<Unit> = safe {
        mutex.withLock {
            val owner = owner()
            val header = io { bars.authHelper.exchange(code) }
            storage.setAuthorization(header)
            val user = io { bars.execute(bars.api.getCurrentUser()) }
            if (user.login != owner.toString()) {
                storage.clear()
                fail(AppError.Forbidden)
            }
        }
    }

    inner class Account(val owner: Int) {
        /** Server-side selection as of the last read; period changes update it. */
        lateinit var user: User
            private set

        internal suspend fun open() {
            user = execute { bars.api.getCurrentUser() }
            if (user.login != owner.toString()) {
                storage.clear()
                fail(AppError.Unauthorized)
            }
        }

        /** Calls inside one account block may run concurrently; the library shares one renewal between them. */
        suspend fun <T> execute(call: () -> Call<T>): T = io { bars.execute(call()) }.also { checkOwner() }

        suspend fun selectPeriod(studyYear: String, autumn: Boolean) {
            user = io { bars.selectPeriod(studyYear, if (autumn) Term.AUTUMN else Term.SPRING) }
            checkOwner()
        }

        private suspend fun checkOwner() {
            if (currentUser.getCurrentUser()?.isu != owner) fail(AppError.Unauthorized)
        }
    }

    private suspend fun owner(): Int {
        val owner = currentUser.getCurrentUser()?.isu ?: fail(AppError.Unauthorized)
        storage.owner = owner
        return owner
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) {
        try { block() } catch (failure: BarsApiException) { fail(failure.toAppError()) }
    }

    private suspend fun <T> safe(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (failure: BarsFailure) {
        AppResult.Failure(failure.error)
    } catch (_: Exception) {
        // Never retain exceptions containing URLs, OAuth codes or response bodies in diagnostics.
        AppResult.Failure(AppError.Unknown())
    }

    /** Leaves the library's renewal untouched: the saved header stays, and [backgroundAccount] reports the end. */
    private class SessionEndedSignal : RuntimeException()

    private fun codeOf(renewal: BarsCookieRenewal): String = when (renewal) {
        is BarsCookieRenewal.Code -> renewal.code
        BarsCookieRenewal.SessionEnded -> throw SessionEndedSignal()
        is BarsCookieRenewal.Failed -> throw BarsApiException(
            "Renewal failed", if (renewal.error == AppError.Network) IOException() else null
        )
    }

    private class BarsFailure(val error: AppError) : RuntimeException()
    private fun fail(error: AppError): Nothing = throw BarsFailure(error)
    private fun BarsApiException.toAppError(): AppError = when (httpCode) {
        401 -> AppError.Unauthorized
        403, 423 -> AppError.Forbidden
        404 -> AppError.NotFound
        null -> if (cause is IOException) AppError.Network else AppError.Unknown()
        else -> AppError.Unknown()
    }
}

/** Result of a [BarsClient.backgroundAccount] block. */
sealed interface BarsBackground<out T> {
    data class Success<T>(val value: T) : BarsBackground<T>

    /** No saved BARS session for the signed-in user: the user never signed in to BARS or signed out. */
    data object NoSession : BarsBackground<Nothing>

    /** The ITMO.ID session behind the cookies ended; only an interactive sign-in brings BARS back. */
    data object SessionEnded : BarsBackground<Nothing>

    data class Failure(val error: AppError) : BarsBackground<Nothing>
}
