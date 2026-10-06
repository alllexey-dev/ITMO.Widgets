package dev.alllexey.itmowidgets.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * The keys whose fields are core types (route map SH-0, sections 3-7). Feature-typed keys live in their feature's
 * `navigation` package and join through [appRouteSerializersModule]. A key's fields are the arguments its screen reads
 * today under the names of the `*Args` holders.
 */
object AppRoutes {

    /** [exclusiveGroup] of the two sport details sheets: the pending booking's and the sport tab's. */
    const val SPORT_DETAILS_GROUP = "sport_details"

    @Serializable
    data object Auth : AppRoute {
        override val kind get() = RouteKind.GATE
    }

    @Serializable
    data object Onboarding : AppRoute {
        override val kind get() = RouteKind.GATE
    }

    /** The only key of a tab's own stack: contextual screens go to the overlay stack, never into a tab's history. */
    @Serializable
    data class TabRoot(val tab: AppTab) : AppRoute {
        override val kind get() = RouteKind.TAB_ROOT
    }

    /** [page] is a `SettingsPage` name; a sub-page pushes another [Settings]. */
    @Serializable
    data class Settings(val page: String = ROOT_PAGE) : AppRoute {
        override val kind get() = RouteKind.SCREEN

        companion object {
            const val ROOT_PAGE = "ROOT"
        }
    }

    @Serializable
    data object Diagnostics : AppRoute {
        override val kind get() = RouteKind.SCREEN
    }

    /** Registered only in debug builds. */
    @Serializable
    data object DebugTools : AppRoute {
        override val kind get() = RouteKind.SCREEN
    }

    /** Opens only with [RecordbookSubjectArgs.validOrNull] arguments. */
    @Serializable
    data class RecordbookSubject(val args: RecordbookSubjectArgs) : AppRoute {
        override val kind get() = RouteKind.SCREEN
    }

    @Serializable
    data object Friends : AppRoute {
        override val kind get() = RouteKind.SCREEN
    }

    @Serializable
    data class UserFriends(val isu: Int, val name: String = "") : AppRoute {
        override val kind get() = RouteKind.SCREEN
    }

    @Serializable
    data object UserSearch : AppRoute {
        override val kind get() = RouteKind.SCREEN
    }

    /** Built only from [UserScreenArgs.profileIsu] or another positive ISU. */
    @Serializable
    data class UserProfile(val isu: Int) : AppRoute {
        override val kind get() = RouteKind.SCREEN
    }

    /** Another user's schedule: it never consumes [TabRequest.ScheduleToday] and never opens the friend picker. */
    @Serializable
    data class UserSchedule(val isu: Int, val name: String = "") : AppRoute {
        override val kind get() = RouteKind.SCREEN
    }

    @Serializable
    data class UserSport(val isu: Int, val name: String = "") : AppRoute {
        override val kind get() = RouteKind.SCREEN
    }

    @Serializable
    data object ScheduleChanges : AppRoute {
        override val kind get() = RouteKind.SCREEN
    }

    @Serializable
    data object QrPass : AppRoute {
        override val kind get() = RouteKind.SCREEN
    }

    /** Refused in the demo session ([ShellGate.check]). */
    @Serializable
    data object MyItmoWeb : AppRoute {
        override val kind get() = RouteKind.SCREEN
    }

    /** The period picker of the recordbook; the lists are parallel, one element per period. */
    @Serializable
    data class RecordbookPeriod(
        val programName: String,
        val programNames: List<String>,
        val programIds: List<Long>,
        val semesters: List<Int>,
        val courses: List<Int>,
        val years: List<String>,
        val actual: List<Boolean>,
        val selectedProgram: Long,
        val selectedSemester: Int,
    ) : AppRoute {
        override val kind get() = RouteKind.SHEET
        override val sheetPolicy get() = SheetPolicy()
    }

    /** The schedule's friend picker; [selectedIsu] is [FriendSelectionContract.NO_USER_ISU] for the own schedule. */
    @Serializable
    data class FriendSelector(val selectedIsu: Int = FriendSelectionContract.NO_USER_ISU) : AppRoute {
        override val kind get() = RouteKind.SHEET
        override val sheetPolicy get() = SheetPolicy(height = SheetPolicy.Height.TALL)
    }

    @Serializable
    data class SheetScores(val args: SheetScoresArgs) : AppRoute {
        override val kind get() = RouteKind.SHEET
        override val sheetPolicy get() = SheetPolicy(textInput = true)
    }

    /**
     * Approves a browser's sign-in to the web version; refused in the demo session. [code] is the iOS Universal Link's
     * `/app/login?code=`; Android always opens it without one.
     */
    @Serializable
    data class WebLogin(val code: String? = null) : AppRoute {
        override val kind get() = RouteKind.SHEET
        override val sheetPolicy get() = SheetPolicy(textInput = true)
    }

    /** A form: it asks before a draft is discarded. */
    @Serializable
    data class ReviewEditor(val args: TeacherReviewArgs) : AppRoute {
        override val kind get() = RouteKind.SHEET
        override val sheetPolicy get() = SheetPolicy(dismissal = SheetPolicy.Dismissal.FORM, textInput = true)
    }

    @Serializable
    data class SubjectLinks(val args: SubjectLinksArgs) : AppRoute {
        override val kind get() = RouteKind.SHEET
        override val sheetPolicy get() = SheetPolicy()
    }

    /** Adds a link, or edits the viewer's own link [linkId]. */
    @Serializable
    data class LinkEditor(val args: SubjectLinksArgs, val linkId: String? = null) : AppRoute {
        override val kind get() = RouteKind.SHEET
        override val sheetPolicy get() = SheetPolicy(textInput = true)
    }

    @Serializable
    data object IcsExport : AppRoute {
        override val kind get() = RouteKind.SHEET
        override val sheetPolicy get() = SheetPolicy()
    }

    @Serializable
    data class LinkActions(val args: SubjectLinksArgs, val linkId: String) : AppRoute {
        override val kind get() = RouteKind.SHEET
        override val sheetPolicy get() = SheetPolicy()
    }

    @Serializable
    data class LessonDetails(val args: LessonDetailsArgs) : AppRoute {
        override val kind get() = RouteKind.SHEET
        override val sheetPolicy get() = SheetPolicy(height = SheetPolicy.Height.TALL)
    }

    /** The schedule's own sheet for a queue the sport data does not know yet; exclusive with the sport tab's sheet. */
    @Serializable
    data class PendingSportDetails(val args: PendingSportDetailsArgs) : AppRoute {
        override val kind get() = RouteKind.SHEET
        override val sheetPolicy get() = SheetPolicy(height = SheetPolicy.Height.TALL)
        override val exclusiveGroup get() = SPORT_DETAILS_GROUP
    }

    @Serializable
    data class ReportReview(val args: TeacherReviewArgs, val reviewId: String) : AppRoute {
        override val kind get() = RouteKind.DIALOG
    }

    @Serializable
    data class ReportLink(val args: SubjectLinksArgs, val linkId: String) : AppRoute {
        override val kind get() = RouteKind.DIALOG
    }

    /** A malformed app link: `app_link_unavailable_title`, `app_link_unavailable_text`, `common_got_it`. */
    @Serializable
    data object LinkUnavailable : AppRoute {
        override val kind get() = RouteKind.DIALOG
    }

    /** Asks before a booking is cancelled from a sheet the shell opened: `sport_cancel_booking_question`. */
    @Serializable
    data class CancelBookingConfirm(val lessonId: Long) : AppRoute {
        override val kind get() = RouteKind.DIALOG
    }

    /** Every key of this object, in the order of the route map. */
    val registration: AppRouteRegistration = {
        subclass(Auth::class)
        subclass(Onboarding::class)
        subclass(TabRoot::class)
        subclass(Settings::class)
        subclass(Diagnostics::class)
        subclass(DebugTools::class)
        subclass(RecordbookSubject::class)
        subclass(Friends::class)
        subclass(UserFriends::class)
        subclass(UserSearch::class)
        subclass(UserProfile::class)
        subclass(UserSchedule::class)
        subclass(UserSport::class)
        subclass(ScheduleChanges::class)
        subclass(QrPass::class)
        subclass(MyItmoWeb::class)
        subclass(RecordbookPeriod::class)
        subclass(FriendSelector::class)
        subclass(SheetScores::class)
        subclass(WebLogin::class)
        subclass(ReviewEditor::class)
        subclass(SubjectLinks::class)
        subclass(LinkEditor::class)
        subclass(IcsExport::class)
        subclass(LinkActions::class)
        subclass(LessonDetails::class)
        subclass(PendingSportDetails::class)
        subclass(ReportReview::class)
        subclass(ReportLink::class)
        subclass(LinkUnavailable::class)
        subclass(CancelBookingConfirm::class)
    }
}

/** A module's keys, registered for both bases [appRouteSerializersModule] declares. */
typealias AppRouteRegistration = PolymorphicModuleBuilder<AppRoute>.() -> Unit

/**
 * One module for every key: the core keys of [AppRoutes] plus each feature's [featureRoutes]. Registered under
 * [NavKey] (Nav3's back stacks) and [AppRoute] (the shell's own state: [ShellBackStack], [RouteQueue]).
 */
fun appRouteSerializersModule(vararg featureRoutes: AppRouteRegistration): SerializersModule {
    val registrations = listOf(AppRoutes.registration) + featureRoutes
    return SerializersModule {
        polymorphic(NavKey::class) { registrations.forEach { it() } }
        polymorphic(AppRoute::class) { registrations.forEach { it() } }
    }
}
