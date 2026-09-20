package dev.alllexey.itmowidgets.core.home

import dev.alllexey.itmowidgets.core.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * One feature's contribution to the home feed.
 *
 * [observe] answers from the cache right away and emits an empty list when
 * there is nothing worth a card. [refresh] is the only call that goes to the
 * network; a failure leaves the cached cards in place. [revalidate] is a cheap
 * local re-check when the screen returns to the foreground: an expired pass, a
 * permission the user just granted. Sources are contributed with `@IntoSet`.
 */
interface HomeCardSource {
    fun observe(): Flow<List<HomeCard>>

    suspend fun refresh(): AppResult<Unit>

    suspend fun revalidate() = Unit
}
