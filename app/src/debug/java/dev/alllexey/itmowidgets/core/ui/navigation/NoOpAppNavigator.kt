package dev.alllexey.itmowidgets.core.ui.navigation

import android.os.Bundle
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs

/** Debug hosts delegate to it (`AppNavigator by NoOpAppNavigator`) and override only what they record. */
object NoOpAppNavigator : AppNavigator {
    override fun openScreen(screen: AppScreen, arguments: Bundle?) = Unit
    override fun openRoot(root: AppRoot) = Unit
    override fun dismissOverlays() = Unit
    override fun openLessonDetails(args: LessonDetailsArgs) = Unit
    override fun openPendingSportDetails(args: PendingSportDetailsArgs) = Unit
    override fun openSubjectLinks(args: SubjectLinksArgs) = Unit
    override fun openLinkEditor(args: SubjectLinksArgs, linkId: String?) = Unit
    override fun openLinkActions(args: SubjectLinksArgs, linkId: String) = Unit
    override fun openSheetScores(args: SheetScoresArgs) = Unit
    override fun openReviewEditor(args: TeacherReviewArgs) = Unit
    override fun openReviewReport(args: TeacherReviewArgs, reviewId: String) = Unit
    override fun openWebLogin() = Unit
}
