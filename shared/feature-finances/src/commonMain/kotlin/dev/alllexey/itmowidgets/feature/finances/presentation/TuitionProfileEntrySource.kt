package dev.alllexey.itmowidgets.feature.finances.presentation

import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.payments.ContractKind
import dev.alllexey.itmowidgets.core.profile.ProfileEntry
import dev.alllexey.itmowidgets.core.profile.ProfileEntrySource
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.PaymentTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.finances.domain.TuitionAnswer
import dev.alllexey.itmowidgets.feature.finances.domain.TuitionRepository
import dev.alllexey.itmowidgets.shared.feature.finances.FinancesStrings
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * `Оплата обучения` in Профиль: shown while the stored answer has a Договор об обучении, hidden on a budget place. A
 * Профиль that finds no answer or one older than [REFRESH_AFTER] asks my.itmo again, so new agreements show on their
 * own; a failure leaves the stored answer as it is.
 */
class TuitionProfileEntrySource(
    private val repository: TuitionRepository,
    private val clock: Clock,
) : ProfileEntrySource {

    override fun observe(): Flow<ProfileEntry?> = channelFlow {
        launch { if (isOld(repository.observe().first())) repository.refresh() }
        repository.observe().map(::entry).collect { send(it) }
    }.distinctUntilChanged()

    private fun isOld(answer: TuitionAnswer?): Boolean = answer == null || clock.now() - answer.fetchedAt >= REFRESH_AFTER

    private fun entry(answer: TuitionAnswer?): ProfileEntry? {
        if (answer == null || !answer.hasAnything) return null
        return ProfileEntry(
            id = ID,
            icon = AppIcon.PAYMENTS,
            title = UiText.Res(FinancesStrings.tuition_title),
            subtitle = PaymentTexts.kind(ContractKind.TUITION),
            route = AppRoutes.Tuition,
        )
    }

    private companion object {
        const val ID = "tuition"
        val REFRESH_AFTER = 3.hours
    }
}
