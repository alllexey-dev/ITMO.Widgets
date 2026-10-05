package dev.alllexey.itmowidgets.core.reviews

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.teacher_level_description
import dev.alllexey.itmowidgets.shared.core.teacher_level_mixed
import dev.alllexey.itmowidgets.shared.core.teacher_level_negative
import dev.alllexey.itmowidgets.shared.core.teacher_level_positive
import dev.alllexey.itmowidgets.shared.core.teacher_level_very_negative
import dev.alllexey.itmowidgets.shared.core.teacher_level_very_positive
import org.jetbrains.compose.resources.StringResource

/**
 * The tone of a teacher's reviews in words; TalkBack reads them with the dot. The dot's colour is a design-system
 * token (DS-01a), not part of the tone here.
 */
enum class TeacherLevelTone(private val labelResource: StringResource) {
    VERY_NEGATIVE(Res.string.teacher_level_very_negative),
    NEGATIVE(Res.string.teacher_level_negative),
    MIXED(Res.string.teacher_level_mixed),
    POSITIVE(Res.string.teacher_level_positive),
    VERY_POSITIVE(Res.string.teacher_level_very_positive);

    val label: UiText get() = UiText.Res(labelResource)

    /**
     * «Тон отзывов: скорее положительные» for a row that carries the dot. The sentence takes the label lower-cased
     * and [UiText] has no case transform, so the caller passes [label] as it resolved it.
     */
    fun description(resolvedLabel: String): UiText =
        UiText.Res(Res.string.teacher_level_description, listOf(resolvedLabel.lowercase()))
}

fun TeacherLevel.tone(): TeacherLevelTone = when (this) {
    TeacherLevel.VERY_NEGATIVE -> TeacherLevelTone.VERY_NEGATIVE
    TeacherLevel.NEGATIVE -> TeacherLevelTone.NEGATIVE
    TeacherLevel.MIXED -> TeacherLevelTone.MIXED
    TeacherLevel.POSITIVE -> TeacherLevelTone.POSITIVE
    TeacherLevel.VERY_POSITIVE -> TeacherLevelTone.VERY_POSITIVE
}
