package dev.alllexey.itmowidgets.core.reviews

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.CoreStrings

/**
 * The tone of a teacher's reviews in words; TalkBack reads them with the dot. The dot's colour is a design-system
 * token, not part of the tone here.
 */
enum class TeacherLevelTone(private val labelKey: String) {
    VERY_NEGATIVE(CoreStrings.teacher_level_very_negative),
    NEGATIVE(CoreStrings.teacher_level_negative),
    MIXED(CoreStrings.teacher_level_mixed),
    POSITIVE(CoreStrings.teacher_level_positive),
    VERY_POSITIVE(CoreStrings.teacher_level_very_positive);

    val label: UiText get() = UiText.Res(labelKey)

    /**
     * «Тон отзывов: скорее положительные» for a row that carries the dot. The sentence takes the label lower-cased
     * and [UiText] has no case transform, so the caller passes [label] as it resolved it.
     */
    fun description(resolvedLabel: String): UiText =
        UiText.Res(CoreStrings.teacher_level_description, listOf(resolvedLabel.lowercase()))
}

fun TeacherLevel.tone(): TeacherLevelTone = when (this) {
    TeacherLevel.VERY_NEGATIVE -> TeacherLevelTone.VERY_NEGATIVE
    TeacherLevel.NEGATIVE -> TeacherLevelTone.NEGATIVE
    TeacherLevel.MIXED -> TeacherLevelTone.MIXED
    TeacherLevel.POSITIVE -> TeacherLevelTone.POSITIVE
    TeacherLevel.VERY_POSITIVE -> TeacherLevelTone.VERY_POSITIVE
}
