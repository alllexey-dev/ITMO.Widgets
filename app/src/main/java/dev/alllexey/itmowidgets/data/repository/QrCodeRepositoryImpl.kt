package dev.alllexey.itmowidgets.data.repository

import dev.alllexey.itmowidgets.data.local.QrCodeLocalDataSource
import dev.alllexey.itmowidgets.data.remote.QrCodeRemoteDataSource
import dev.alllexey.itmowidgets.domain.repository.QrCodeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class QrCodeRepositoryImpl @Inject constructor(
    private val local: QrCodeLocalDataSource,
    private val remote: QrCodeRemoteDataSource
) : QrCodeRepository {

    override fun observeQrHex(): Flow<String> {
        return local.observe()
    }

    override suspend fun refreshQrHex(force: Boolean) {
        val cached = local.get()

        val shouldRefresh = when {
            force -> true
            cached == null -> true
            else -> false
        }

        if (!shouldRefresh) return

        val remoteHex = remote.getQrHex()
            ?: throw RuntimeException("QR API returned null")

        local.save(remoteHex)
    }

    override fun clearCaches() {
        local.clear()
    }
}
