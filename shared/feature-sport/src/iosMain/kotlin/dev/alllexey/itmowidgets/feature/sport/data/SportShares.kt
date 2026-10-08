package dev.alllexey.itmowidgets.feature.sport.data

import dev.alllexey.itmowidgets.core.navigation.ShareLinkFactory
import dev.alllexey.itmowidgets.core.platform.PlatformActions
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportCommonDetailsArgs
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportSessionTiming
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportShareTarget
import dev.alllexey.itmowidgets.feature.sport.ui.common.shareDate
import dev.alllexey.itmowidgets.shared.feature.sport.Res
import dev.alllexey.itmowidgets.shared.feature.sport.share_sport_text
import dev.alllexey.itmowidgets.shared.feature.sport.share_sport_title
import org.jetbrains.compose.resources.getString

/**
 * What the sport details sheet shares on iOS, through the system share sheet ([PlatformActions]): the lesson's
 * section, day and time with its app link, a real lesson's or the prediction's of its prototype, as Android's
 * `SportCommonDetailsBottomSheet` sends them. Called on the main thread; false when no sheet could be shown.
 */
class SportShares(
    private val actions: PlatformActions,
    private val links: ShareLinkFactory,
    private val time: AcademicTimeProvider,
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
            getString(Res.string.share_sport_title),
            getString(Res.string.share_sport_text, item.sectionName, timing.shareDate(), link),
        )
    }
}
