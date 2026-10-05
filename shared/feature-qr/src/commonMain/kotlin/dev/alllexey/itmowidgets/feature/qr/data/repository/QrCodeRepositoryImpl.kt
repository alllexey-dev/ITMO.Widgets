package dev.alllexey.itmowidgets.feature.qr.data.repository

import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.network.asAppError
import dev.alllexey.itmowidgets.core.network.isCausedByNetworkFailure
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.appResultOf
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.qr.data.local.QrCodeLocalDataSource
import dev.alllexey.itmowidgets.feature.qr.data.remote.QrCodeRemoteDataSource
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * The pass of the signed-in user. Koin holds one instance for the screen, the widget, the worker and sign-out (the
 * `qrModule` single): a second one would read its own copy of the cached pass and drift from the first.
 */
class QrCodeRepositoryImpl(
    private val local: QrCodeLocalDataSource,
    private val remote: QrCodeRemoteDataSource,
    private val dispatchers: AppDispatchers
) : QrCodeRepository, SessionDataCleaner {

    override suspend fun currentQr() = withContext(dispatchers.io) { local.snapshot() }

    override fun observeQrHex(): Flow<String> {
        return local.observe()
    }

    override suspend fun currentQrHex(allowExpired: Boolean): String? =
        withContext(dispatchers.io) {
            local.get(allowExpired)
        }

    override suspend fun refreshQrHex(force: Boolean): AppResult<Unit> {
        val cached = local.get()
        if (!force && cached != null) return AppResult.Success(Unit)

        return appResultOf(::toAppError) { local.save(remote.getQrHex()) }
    }

    override fun clearCache() {
        local.clear()
    }

    override suspend fun clearSessionData() {
        withContext(dispatchers.io) { clearCache() }
    }

    /**
     * The app's released mapping for a MyItmoApi 2.x source: a failure before any answer is [AppError.Network], then
     * the first [MyItmoException] in the cause chain maps in common code, anything else is a retriable unknown.
     */
    private fun toAppError(error: Exception): AppError {
        if (error.isCausedByNetworkFailure()) return AppError.Network
        val seen = mutableSetOf<Throwable>()
        var current: Throwable? = error
        while (current != null && seen.add(current)) {
            if (current is MyItmoException) return current.asAppError()
            current = current.cause
        }
        return AppError.Unknown(error)
    }
}
