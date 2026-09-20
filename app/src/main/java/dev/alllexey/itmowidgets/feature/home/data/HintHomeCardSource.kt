package dev.alllexey.itmowidgets.feature.home.data

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.feature.home.domain.HomeHintStatus
import dev.alllexey.itmowidgets.feature.home.domain.HomeHintStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/** Local only: device state and the opt-in, minus what the user already closed. */
@Singleton
class HintHomeCardSource @Inject constructor(
    private val status: HomeHintStatus,
    private val store: HomeHintStore,
    private val services: CustomServicesRepository
) : HomeCardSource {

    private val revalidations = MutableStateFlow(0)

    override fun observe(): Flow<List<HomeCard>> =
        combine(store.observeDismissed(), services.observeEnabled(), revalidations) { dismissed, enabled, _ ->
            buildList {
                if (!status.anyWidgetPlaced()) add(HomeHint.WIDGETS)
                if (!status.notificationsEnabled()) add(HomeHint.NOTIFICATIONS)
                if (!enabled) add(HomeHint.SERVICES)
            }.filterNot { it in dismissed }.map(HomeCard::Hint)
        }

    override suspend fun refresh(): AppResult<Unit> {
        revalidate()
        return AppResult.Success(Unit)
    }

    override suspend fun revalidate() = revalidations.update { it + 1 }
}
