package dev.alllexey.itmowidgets.feature.finances.data.demo

import dev.alllexey.itmowidgets.feature.finances.domain.TuitionAnswer
import kotlin.time.Instant

/** The demo session's tuition: a student on a budget place, no Договор об обучении, so Профиль has no row. */
internal object DemoTuition {
    fun answer(now: Instant) = TuitionAnswer(contracts = emptyList(), fetchedAt = now)
}
