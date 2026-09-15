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
 * period selection, and turns library failures into [AppError]. Silent renewal is delegated to
 * the WebView flow through the library's [BarsCodeSupplier].
 */
@Singleton
class BarsClient @Inject constructor(
    val bars: Bars,
    private val storage: OwnerBoundBarsStorage,
    private val currentUser: CurrentUserProvider,
    silentLogin: BarsSilentLogin
) {
    private val mutex = Mutex()

    init {
        bars.codeSupplier = BarsCodeSupplier { state -> runBlocking { silentLogin.authorizationCode(state) } }
    }

    suspend fun <T> account(block: suspend Account.() -> T): AppResult<T> = safe {
        mutex.withLock {
            val account = Account(owner())
            account.open()
            account.block()
        }
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
