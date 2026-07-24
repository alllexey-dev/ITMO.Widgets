package dev.alllexey.itmowidgets.feature.qr.data.repository

import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.qr.data.local.QrCodeLocalDataSource
import dev.alllexey.itmowidgets.feature.qr.data.remote.QrCodeRemoteDataSource
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class QrCodeRepositoryImpl @Inject constructor(
    private val local: QrCodeLocalDataSource,
    private val remote: QrCodeRemoteDataSource
) : QrCodeRepository, SessionDataCleaner {

    override fun observeQrHex(): Flow<String> {
        return local.observe()
    }

    override suspend fun refreshQrHex(force: Boolean): AppResult<Unit> {
        val cached = local.get()
        if (!force && cached != null) return AppResult.Success(Unit)

        return try {
            local.save(remote.getQrHex())
            AppResult.Success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }

    override fun clearCache() {
        local.clear()
    }

    override fun clearSessionData() {
        clearCache()
    }
}
