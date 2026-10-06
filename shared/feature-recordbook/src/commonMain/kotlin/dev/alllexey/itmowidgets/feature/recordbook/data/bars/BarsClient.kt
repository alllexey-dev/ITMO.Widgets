package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import dev.alllexey.itmoapi.bars.model.Term
import dev.alllexey.itmoapi.bars.model.User
import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.network.isCausedByNetworkFailure
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.appResultOf
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import dev.alllexey.itmoapi.bars.BarsClient as LibraryBarsClient

/**
 * App-side seam over the library client: binds the session to the signed-in ISU, serializes period selection, and
 * turns library failures into [AppError]. The library client is built over [OwnerBoundBarsStorage] and [BarsRenewal]
 * (`recordbookModule` holds the one instance of each per process, so there is one session lock): screens ([account]) renew through the headless WebView flow, the background
 * ([backgroundAccount]) through ITMO.ID cookies. Only one block runs at a time, so the renewal mode belongs to the
 * block that holds the lock. The library's locks are not reentrant: a block never nests period changes. Every
 * successful answer is reported to [BarsSessionListener] after the lock is released.
 */
class BarsClient internal constructor(
    private val bars: LibraryBarsClient,
    private val renewal: BarsRenewal,
    private val storage: OwnerBoundBarsStorage,
    private val currentUser: CurrentUserProvider,
    private val listener: BarsSessionListener,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers
) {
    /** The library client over the platform's BARS [engine] (no cookie jar, cache or redirects, ADR 0012). */
    constructor(
        engine: HttpClientEngine,
        renewal: BarsRenewal,
        storage: OwnerBoundBarsStorage,
        currentUser: CurrentUserProvider,
        listener: BarsSessionListener,
        demo: DemoMode,
        dispatchers: AppDispatchers
    ) : this(
        LibraryBarsClient(engine, storage = storage, codeSupplier = renewal),
        renewal, storage, currentUser, listener, demo, dispatchers
    )

    private val mutex = Mutex()

    suspend fun <T> account(block: suspend Account.() -> T): AppResult<T> = safe {
        mutex.withLock {
            val account = Account(owner())
            account.open()
            account.block()
        }
    }.also { if (it is AppResult.Success) listener.onBarsAnswered() }

    /**
     * The background form of [account]: the session is renewed through ITMO.ID cookies, and an ended ITMO.ID
     * session is [BarsBackground.SessionEnded] instead of a failure. Without a saved session for the current
     * ISU nothing is requested. A network failure before any answer, of BARS or of ITMO.ID, is
     * [BarsBackground.Failure] with [AppError.Network], never [BarsBackground.SessionEnded]; the saved header stays.
     */
    suspend fun <T> backgroundAccount(block: suspend Account.() -> T): BarsBackground<T> = runBackground(block)
        .also { if (it is BarsBackground.Success) listener.onBarsAnswered() }

    private suspend fun <T> runBackground(block: suspend Account.() -> T): BarsBackground<T> = try {
        mutex.withLock {
            val owner = owner()
            if (withContext(dispatchers.io) { storage.getAuthorization() } == null) {
                BarsBackground.NoSession
            } else {
                renewal.throughCookies {
                    val account = Account(owner)
                    account.open()
                    BarsBackground.Success(account.block())
                }
            }
        }
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: BarsSessionEnded) {
        BarsBackground.SessionEnded
    } catch (failure: BarsFailure) {
        BarsBackground.Failure(failure.error)
    } catch (failure: Exception) {
        BarsBackground.Failure(failure.unexpectedError())
    }

    /** Interactive ITMO.ID result; the session is kept only if it belongs to the app's user. */
    suspend fun login(code: String): AppResult<Unit> = safe {
        mutex.withLock {
            val owner = owner()
            io { bars.login(code) }
            val user = io { bars.getCurrentUser() }
            if (user.login != owner.toString()) {
                storage.clear()
                fail(AppError.Forbidden)
            }
        }
    }.also { if (it is AppResult.Success) listener.onBarsAnswered() }

    inner class Account(val owner: Int) {
        /** Server-side selection as of the last read; period changes update it. */
        lateinit var user: User
            private set

        internal suspend fun open() {
            user = execute { getCurrentUser() }
            if (user.login != owner.toString()) {
                storage.clear()
                fail(AppError.Unauthorized)
            }
        }

        /** Calls inside one account block may run concurrently; the library shares one renewal between them. */
        suspend fun <T> execute(call: suspend LibraryBarsClient.() -> T): T = io { bars.call() }.also { checkOwner() }

        suspend fun selectPeriod(studyYear: String, autumn: Boolean) {
            user = io { bars.selectPeriod(studyYear, if (autumn) Term.AUTUMN else Term.SPRING) }
            checkOwner()
        }

        private suspend fun checkOwner() {
            if (currentUser.getCurrentUser()?.isu != owner) fail(AppError.Unauthorized)
        }
    }

    /** Every BARS request starts here; the demo session has no BARS account. */
    private suspend fun owner(): Int {
        if (demo.isActive()) fail(AppError.DemoUnavailable)
        val owner = currentUser.getCurrentUser()?.isu ?: fail(AppError.Unauthorized)
        storage.owner = owner
        return owner
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(dispatchers.io) {
        try { block() } catch (failure: MyItmoException) { fail(failure.toAppError()) }
    }

    private suspend fun <T> safe(block: suspend () -> T): AppResult<T> =
        appResultOf({ failure -> (failure as? BarsFailure)?.error ?: failure.unexpectedError() }) { block() }

    private fun fail(error: AppError): Nothing = throw BarsFailure(error)

    private fun MyItmoException.toAppError(): AppError = when (this) {
        is MyItmoException.Auth -> if (status == 401) AppError.Unauthorized else AppError.Forbidden
        is MyItmoException.Http -> when (status) {
            403, 423 -> AppError.Forbidden
            404 -> AppError.NotFound
            else -> AppError.Unknown()
        }
        else -> unexpectedError()
    }

    // Never retain exceptions containing URLs, OAuth codes or response bodies in diagnostics.
    private fun Throwable.unexpectedError(): AppError =
        if (isCausedByNetworkFailure()) AppError.Network else AppError.Unknown()
}

/** Told about every successful BARS answer of the account, outside the client's lock. */
fun interface BarsSessionListener {
    suspend fun onBarsAnswered()
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
