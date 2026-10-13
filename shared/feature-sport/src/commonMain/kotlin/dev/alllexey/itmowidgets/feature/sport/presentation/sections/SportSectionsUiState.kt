package dev.alllexey.itmowidgets.feature.sport.presentation.sections

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportKindTone
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportPrompt
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportOption
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportRowAction

/**
 * «Мои секции» (DECISIONS.md, Спорт): the pinned sections with their next lessons and «Очередь через неделю»; the
 * queue limit in the [subtitle]; the [limitGate] when it is spent; the [picker] of sections to pin (null while closed);
 * the queue's [prompt].
 */
data class SportSectionsUiState(
    val subtitle: UiText? = null,
    val body: SportSectionsBody = SportSectionsBody.Loading,
    val limitGate: SportLimitGate? = null,
    val picker: List<SportOption>? = null,
    val prompt: SportPrompt? = null,
)

sealed interface SportSectionsBody {
    data object Loading : SportSectionsBody

    data class Failed(val error: AppError) : SportSectionsBody

    /** Nothing pinned yet: `Закрепите свои секции` with the picker. */
    data object Empty : SportSectionsBody

    /** [note] explains the repeat when the feature is on. */
    data class Content(val sections: List<SportSectionCard>, val note: UiText?, val refreshing: Boolean = false) : SportSectionsBody
}

/** «Новая очередь не включится»: why ([reason]) and the way to free one. */
data class SportLimitGate(val title: UiText, val reason: UiText, val fix: UiText)

/**
 * One pinned section: [title] (`Плавание`), the [teacher] of its next lesson, its [kind] and [place], the next
 * [lessons] and «Очередь через неделю» ([repeat], null while Backend has no rules). [name] is My ITMO's own, for unpin.
 */
data class SportSectionCard(
    val name: String,
    val title: String,
    val teacher: String?,
    val kind: UiText?,
    val kindTone: SportKindTone,
    val place: UiText?,
    val lessons: List<SportSectionLesson>,
    val repeat: SportRepeatUi?,
)

/** A next lesson: [day] `Вт, 8 сент.`, [time] `15:20`, its status or one [action]. */
data class SportSectionLesson(val lessonId: Long, val day: UiText, val time: String, val action: SportRowAction)

/**
 * «Очередь через неделю»: [on], the slot in [subtitle] (`Вторник, 15:20, через неделю`), [enabled] while the limit
 * allows, the next [dates] it queues for and the one-line [clash] with a lesson.
 */
data class SportRepeatUi(
    val on: Boolean,
    val subtitle: UiText,
    val enabled: Boolean,
    val dates: List<UiText>,
    val clash: UiText?,
)
