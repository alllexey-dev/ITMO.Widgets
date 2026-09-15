package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsAuthPolicy
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.Response

/** Serializes period selection and reads; another in-app request cannot change context halfway through. */
@Singleton
class BarsClient @Inject constructor(
    val api: BarsApi,
    private val tokens: BarsTokenStore,
    private val currentUser: CurrentUserProvider,
    private val silentLogin: BarsSilentLogin
) {
    private val mutex = Mutex()

    suspend fun <T> account(block: suspend Account.() -> T): AppResult<T> = safe {
        mutex.withLock {
            val account = Account(currentUser.getCurrentUser()?.isu ?: fail(AppError.Unauthorized))
            account.open()
            account.block()
        }
    }

    /** Interactive ITMO.ID result; the silent path reuses the same exchange. */
    suspend fun login(code: String): AppResult<Unit> = safe {
        mutex.withLock { exchange(currentUser.getCurrentUser()?.isu ?: fail(AppError.Unauthorized), code) }
    }

    private suspend fun exchange(owner: Int, code: String) {
        val generation = tokens.generation
        val response = api.login(code, BarsAuthPolicy.CALLBACK)
        if (!response.isSuccessful) fail(httpError(response.code()))
        val header = response.headers()["authorization"]?.takeIf(BarsTokenStore::validHeader) ?: fail(AppError.Unauthorized)
        val userResponse = api.currentUser(header)
        if (!userResponse.isSuccessful) fail(httpError(userResponse.code()))
        if (userResponse.body()?.login != owner.toString()) fail(AppError.Forbidden)
        if (tokens.generation != generation || currentUser.getCurrentUser()?.isu != owner) fail(AppError.Unauthorized)
        tokens.install(owner, header)
    }

    /** Calls inside one account block may run concurrently; renewal happens once and the rest retry. */
    inner class Account(val owner: Int) {
        /** Server-side selection as of the last read; period changes update it. */
        lateinit var user: BarsUser
            private set
        private val renewal = Mutex()
        @Volatile private var generation = tokens.generation
        private var renewed = false

        internal suspend fun open() {
            if (tokens.load(owner) == null) renewedToken(rejected = null)
            user = call { api.currentUser(it) }
            if (user.login != owner.toString()) {
                tokens.clear()
                fail(AppError.Unauthorized)
            }
        }

        suspend fun <T> call(request: suspend (String) -> Response<T>): T {
            checkOwner()
            var token = tokens.load(owner) ?: fail(AppError.Unauthorized)
            var response = request(token)
            if (response.code() == 401) {
                // Access tokens last 30 minutes and are not extended by the server.
                token = renewedToken(rejected = token)
                response = request(token)
            }
            checkOwner()
            if (!response.isSuccessful) fail(httpError(response.code()))
            response.headers()["authorization"]?.takeIf { BarsTokenStore.validHeader(it) && it != token }?.let {
                renewal.withLock { tokens.rotate(owner, it, generation) }
            }
            return response.body() ?: fail(AppError.Unknown())
        }

        suspend fun selectPeriod(studyYear: String, autumn: Boolean) {
            require(studyYear.matches(Regex("\\d{4}/\\d{4}")))
            val term = if (autumn) 1 else 0
            if (user.year == studyYear && user.term == term) return
            if (user.year != studyYear) call { api.selectPeriod(it, BarsSetting("current_year", studyYear)) }
            if (user.term != term) call { api.selectPeriod(it, BarsSetting("current_term", term.toString())) }
            user = call { api.currentUser(it) }
            if (user.year != studyYear || user.term != term) fail(AppError.Unknown())
        }

        private suspend fun renewedToken(rejected: String?): String = renewal.withLock {
            val current = tokens.load(owner)
            if (current != null && current != rejected) return current
            if (renewed) fail(AppError.Unauthorized)
            renewed = true
            checkOwnerLocked()
            exchange(owner, silentLogin.authorizationCode() ?: fail(AppError.Unauthorized))
            generation = tokens.generation
            tokens.load(owner) ?: fail(AppError.Unauthorized)
        }

        private suspend fun checkOwner() = renewal.withLock { checkOwnerLocked() }

        private suspend fun checkOwnerLocked() {
            if (tokens.generation != generation || currentUser.getCurrentUser()?.isu != owner) fail(AppError.Unauthorized)
        }
    }

    private suspend fun <T> safe(block: suspend () -> T): AppResult<T> = withContext(Dispatchers.IO) {
        try { AppResult.Success(block()) }
        catch (cancel: CancellationException) { throw cancel }
        catch (failure: BarsFailure) { AppResult.Failure(failure.error) }
        catch (_: IOException) { AppResult.Failure(AppError.Network) }
        // Never retain exceptions containing URLs, OAuth codes or response bodies in diagnostics.
        catch (_: Exception) { AppResult.Failure(AppError.Unknown()) }
    }

    private class BarsFailure(val error: AppError) : RuntimeException()
    private fun fail(error: AppError): Nothing = throw BarsFailure(error)
    private fun httpError(code: Int): AppError = when (code) {
        401 -> AppError.Unauthorized
        403, 423 -> AppError.Forbidden
        404 -> AppError.NotFound
        else -> AppError.Unknown()
    }
}
