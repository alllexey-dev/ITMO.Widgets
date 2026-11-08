package dev.alllexey.itmowidgets.feature.dormitory.presentation

import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.profile.ProfileEntry
import dev.alllexey.itmowidgets.core.profile.ProfileEntrySource
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryAnswer
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryRepository
import dev.alllexey.itmowidgets.shared.feature.dormitory.DormitoryStrings
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * `Общежитие` in Профиль: shown while the stored answer has an application, with its stage as the subtitle. A Профиль
 * that finds no answer or one older than [REFRESH_AFTER] asks my.itmo again, so a new application shows on its own;
 * a failure leaves the stored answer as it is.
 */
class DormitoryProfileEntrySource(
    private val repository: DormitoryRepository,
    private val clock: Clock,
) : ProfileEntrySource {

    override fun observe(): Flow<ProfileEntry?> = channelFlow {
        launch { if (isOld(repository.observe().first())) repository.refresh() }
        repository.observe().map(::entry).collect { send(it) }
    }.distinctUntilChanged()

    private fun isOld(answer: DormitoryAnswer?): Boolean = answer == null || clock.now() - answer.fetchedAt >= REFRESH_AFTER

    private fun entry(answer: DormitoryAnswer?): ProfileEntry? {
        val status = answer?.status ?: return null
        return ProfileEntry(
            id = ID,
            icon = AppIcon.APARTMENT,
            title = UiText.Res(DormitoryStrings.dormitory_title),
            subtitle = DormitoryPresenter.stageName(status.stage),
            route = AppRoutes.Dormitory,
        )
    }

    private companion object {
        const val ID = "dormitory"
        val REFRESH_AFTER = 3.hours
    }
}
