package dev.alllexey.itmowidgets.feature.qr.data.home

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.home.QrPass
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.time.WallClock
import dev.alllexey.itmowidgets.feature.qr.domain.QrAppearancePreferences
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/** The card is always there; without a valid code it only leads to the full screen. */
@Singleton
class QrHomeCardSource @Inject constructor(
    private val repository: QrCodeRepository,
    private val preferences: QrAppearancePreferences,
    @param:WallClock private val clock: Clock
) : HomeCardSource {

    private val revalidations = MutableStateFlow(0)

    override fun observe(): Flow<List<HomeCard>> =
        combine(repository.observeQrHex(), revalidations) { _, _ -> listOf(HomeCard.Qr(currentPass())) }

    override suspend fun refresh(): AppResult<Unit> = repository.refreshQrHex(force = false)

    /** The deadline passes without a cache change, so the screen asks on every return. */
    override suspend fun revalidate() {
        revalidations.update { it + 1 }
        if (currentPass() == null) refresh()
    }

    private suspend fun currentPass(): QrPass? {
        val code = repository.currentQr() ?: return null
        if (code.expiresAtMillis <= clock.millis()) return null
        return QrPass(code.hex, code.expiresAtMillis, preferences.isSpoilerEnabled())
    }
}
