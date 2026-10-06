package dev.alllexey.itmowidgets.feature.recordbook.ui.home.preview

import dev.alllexey.itmowidgets.core.home.HomeCard

/** Synthetic new marks home cards for the previews and host tests; no real subject list. */
internal object MarksHomePreviewSamples {

    const val LONG_SUBJECT = "Проектирование и разработка распределённых информационных систем реального времени"

    /** Three subjects, as in the debug host's `HomeFixture`. */
    fun card(): HomeCard.Marks =
        HomeCard.Marks(listOf("Базы данных", "Дискретная математика", "Алгоритмы и структуры данных"))

    /** Four subjects, the first with a long name. */
    fun longNameCard(): HomeCard.Marks =
        HomeCard.Marks(listOf(LONG_SUBJECT, "Теория вероятностей и математическая статистика", "Физика", "Химия"))
}
