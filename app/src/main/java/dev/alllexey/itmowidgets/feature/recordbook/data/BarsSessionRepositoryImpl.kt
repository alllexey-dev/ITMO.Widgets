package dev.alllexey.itmowidgets.feature.recordbook.data

import dev.alllexey.itmoapi.bars.auth.BarsLogin
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsClient
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSessionRepository
import javax.inject.Inject

/** The interactive sign-in: URL and callback checks of [BarsLogin], the code exchange through [BarsClient]. */
class BarsSessionRepositoryImpl @Inject constructor(
    private val login: BarsLogin,
    private val client: BarsClient
) : BarsSessionRepository {
    override fun loginUrl(state: String): String = login.loginUrl(state)

    override fun isCallback(url: String): Boolean = login.isCallback(url)

    override suspend fun completeLogin(callbackUrl: String, expectedState: String): AppResult<Unit> {
        val code = login.extractCode(callbackUrl, expectedState)
            ?: return AppResult.Failure(AppError.Unauthorized)
        return client.login(code)
    }
}
