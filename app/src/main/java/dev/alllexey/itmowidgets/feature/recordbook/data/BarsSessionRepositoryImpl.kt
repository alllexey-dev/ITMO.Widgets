package dev.alllexey.itmowidgets.feature.recordbook.data

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsClient
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSessionRepository
import javax.inject.Inject

class BarsSessionRepositoryImpl @Inject constructor(private val client: BarsClient) : BarsSessionRepository {
    override fun loginUrl(state: String): String = client.bars.authHelper.getLoginUrl(state)

    override fun isCallback(url: String): Boolean = client.bars.authHelper.isCallback(url)

    override suspend fun completeLogin(callbackUrl: String, expectedState: String): AppResult<Unit> {
        val code = client.bars.authHelper.extractCode(callbackUrl, expectedState)
            ?: return AppResult.Failure(AppError.Unauthorized)
        return client.login(code)
    }
}
