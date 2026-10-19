package dev.alllexey.itmowidgets.feature.sport.ui

import dev.alllexey.itmowidgets.core.navigation.ShareLinkFactory
import dev.alllexey.itmowidgets.core.platform.PlatformActions
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.UiTextResolver
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportCommonDetailsArgs
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportSessionTiming
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportShareTarget
import dev.alllexey.itmowidgets.feature.sport.presentation.common.shareDate
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings

/**
 * What the sport details sheet shares on iOS, through the system share sheet ([PlatformActions]): the lesson's
 * section, day and time with its app link, a real lesson's or the prediction's of its prototype, as
 * Android's sport details entry sends them; [texts] resolves the catalog texts. Callable from any thread; false when no
 * sheet could be shown.
 */
class SportShares(
    private val actions: PlatformActions,
    private val links: ShareLinkFactory,
    private val time: AcademicTimeProvider,
    private val texts: UiTextResolver,
) {

    suspend fun lesson(item: SportCommonDetailsArgs, target: SportShareTarget): Boolean {
        val link = when (target) {
            is SportShareTarget.Lesson -> links.sportLesson(target.lessonId)
            is SportShareTarget.Prediction -> links.predictedSportLesson(target.prototypeLessonId)
        }
        val timing = SportSessionTiming(
            DateTexts.parseOffsetInstant(item.start),
            DateTexts.parseOffsetInstant(item.end),
            time,
        )
        return actions.shareText(
            texts.resolve(UiText.Res(SportStrings.share_sport_title)),
            texts.resolve(UiText.Res(SportStrings.share_sport_text, listOf(item.sectionName, timing.shareDate(), link))),
        )
    }
}
