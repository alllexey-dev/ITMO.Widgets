package dev.alllexey.itmowidgets.feature.dormitory.data.demo

import dev.alllexey.itmowidgets.core.text.SafeHtml
import dev.alllexey.itmowidgets.feature.dormitory.domain.AssignedDormitory
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryAnswer
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryStage
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryStatus
import kotlin.time.Instant

/** The demo session's dormitory: a settled resident of a fictional dormitory, answered at [now]. */
internal object DemoDormitory {

    fun answer(now: Instant) = DormitoryAnswer(
        status = DormitoryStatus(
            stage = DormitoryStage.SETTLED,
            queuePlace = null,
            dormitory = AssignedDormitory(
                name = "Общежитие №1",
                address = "Санкт-Петербург, Примерный пер., 1",
                instructions = SafeHtml.parse(
                    "<p>Заселение — в будни с 10:00 до 17:00 у коменданта.</p>" +
                        "<p>С собой:</p><ul><li>паспорт;</li><li>договор найма;</li><li>фото 3×4.</li></ul>",
                ),
            ),
            documents = emptyList(),
        ),
        fetchedAt = now,
    )
}
