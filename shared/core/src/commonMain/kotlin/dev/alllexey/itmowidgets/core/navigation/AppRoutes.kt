package dev.alllexey.itmowidgets.core.navigation

import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * The keys whose fields are core types. Feature-typed keys live in their feature's
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

    /** Opens only with [RecordbookSubjectArgs.validOrNull] arguments; on `Материалы` when [materials]. */
    @Serializable
    data class RecordbookSubject(val args: RecordbookSubjectArgs, val materials: Boolean = false) : AppRoute {
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

    /** A teacher's MyITMO timetable, from their person card: the feed of [UserSchedule] over other data. */
    @Serializable
    data class TeacherSchedule(val isu: Int, val name: String = "") : AppRoute {
        override val kind get() = RouteKind.SCREEN
    }

    /** [givenName] is ITMO.ID's, for the title; null without it. */
    @Serializable
    data class UserSport(val isu: Int, val name: String = "", val givenName: String? = null) : AppRoute {
        override val kind get() = RouteKind.SCREEN
    }

    /** `Откуда баллы`: the sport points' formula, the bonus cap and My ITMO's history of awards. */
    @Serializable
    data object SportPoints : AppRoute {
        override val kind get() = RouteKind.SCREEN
    }

    /** The bell's sheet over Сегодня: scores, schedule changes, queue results and friend requests. */
    @Serializable
    data object Bell : AppRoute {
        override val kind get() = RouteKind.SHEET
        override val sheetPolicy get() = SheetPolicy(height = SheetPolicy.Height.TALL)
    }

    /** The pass sheet over Сегодня; the Сегодня button, widgets, the tile and the shortcuts open it. */
    @Serializable
    data object QrPass : AppRoute {
        override val kind get() = RouteKind.SHEET
        override val sheetPolicy get() = SheetPolicy()
    }

    /** Refused in the demo session ([ShellGate.check]). */
    @Serializable
    data object MyItmoWeb : AppRoute {
        override val kind get() = RouteKind.SCREEN
    }

    /** The schedule's friend picker; [selectedIsu] is [FriendSelectionContract.NO_USER_ISU] for the own schedule. */
    @Serializable
    data class FriendSelector(val selectedIsu: Int = FriendSelectionContract.NO_USER_ISU) : AppRoute {
        override val kind get() = RouteKind.SHEET
        override val sheetPolicy get() = SheetPolicy()
    }

    /**
     * The schedule's month sheet behind `Сентябрь ▾`: the month of [date] (ISO, `2026-09-17`) with a dot per lesson and
     * `Открыть`; [userIsu] is whose lessons, [FriendSelectionContract.NO_USER_ISU] for the own schedule.
     */
    @Serializable
    data class ScheduleMonth(val date: String, val userIsu: Int = FriendSelectionContract.NO_USER_ISU) : AppRoute {
        override val kind get() = RouteKind.SHEET
        override val sheetPolicy get() = SheetPolicy()
    }

    @Serializable
    data class SheetScores(val args: SheetScoresArgs) : AppRoute {
        override val kind get() = RouteKind.SHEET
        override val sheetPolicy get() = SheetPolicy(textInput = true)
    }

    /**
     * «Подключить устройство»: pairs an agent, an MCP server or a browser; refused in the demo session. [link] is what
     * the sheet reads as a scan (a pairing App Link, `PairLink`), whose fragment holds the client's key, so [toString]
     * hides it.
     */
    @Serializable
    data class Pairing(val link: String? = null) : AppRoute {
        override val kind get() = RouteKind.SHEET
        override val sheetPolicy get() = SheetPolicy(textInput = true)

        override fun toString(): String = "Pairing(link=${if (link == null) "null" else "redacted"})"
    }

    /** «Устройства»: app installs, connected clients and other ITMO.ID sessions; refused in the demo session. */
    @Serializable
    data object Devices : AppRoute {
        override val kind get() = RouteKind.SCREEN
    }

    /** The Wi-Fi sheet of Профиль: ITMO-Corp, the login and the password from ISU, and the quick connect. */
    @Serializable
    data object Wifi : AppRoute {
        override val kind get() = RouteKind.SHEET
        override val sheetPolicy get() = SheetPolicy()
    }

    /** One connected client of «Устройства» by its connection [id]; refused in the demo session. */
    @Serializable
    data class Connection(val id: String) : AppRoute {
        override val kind get() = RouteKind.SCREEN
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

    /**
     * The lesson sheet (`Лист пары`) of one lesson occurrence, from any lesson list; the full height, so the friends on
     * the lesson arriving later never resize it.
     */
    @Serializable
    data class LessonDetails(val args: LessonDetailsArgs) : AppRoute {
        override val kind get() = RouteKind.SHEET
        override val sheetPolicy get() = SheetPolicy(height = SheetPolicy.Height.FULL)
    }

    /** The lesson sheet of an own sport queue, the full height as [LessonDetails]; exclusive with the sport tab's sheet. */
    @Serializable
    data class PendingSportDetails(val args: PendingSportDetailsArgs) : AppRoute {
        override val kind get() = RouteKind.SHEET
        override val sheetPolicy get() = SheetPolicy(height = SheetPolicy.Height.FULL)
        override val exclusiveGroup get() = SPORT_DETAILS_GROUP
    }

    /** The campus map (`Карта`, docs/features/map.md), full screen; «Маршрут» of a lesson or browsing. */
    @Serializable
    data class CampusMap(val args: CampusMapArgs = CampusMapArgs()) : AppRoute {
        override val kind get() = RouteKind.SCREEN
    }

    /** ISU messages (`Сообщения ИСУ`, docs/features/isu-messages.md): the inbox and the archive, read-only. */
    @Serializable
    data object IsuMessages : AppRoute {
        override val kind get() = RouteKind.SCREEN
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
        subclass(UserProfile::class)
        subclass(UserSchedule::class)
        subclass(TeacherSchedule::class)
        subclass(UserSport::class)
        subclass(SportPoints::class)
        subclass(Bell::class)
        subclass(QrPass::class)
        subclass(MyItmoWeb::class)
        subclass(FriendSelector::class)
        subclass(ScheduleMonth::class)
        subclass(SheetScores::class)
        subclass(Pairing::class)
        subclass(Devices::class)
        subclass(Connection::class)
        subclass(ReviewEditor::class)
        subclass(SubjectLinks::class)
        subclass(LinkEditor::class)
        subclass(IcsExport::class)
        subclass(LinkActions::class)
        subclass(LessonDetails::class)
        subclass(PendingSportDetails::class)
        subclass(CampusMap::class)
        subclass(IsuMessages::class)
        subclass(ReportReview::class)
        subclass(ReportLink::class)
        subclass(LinkUnavailable::class)
        subclass(CancelBookingConfirm::class)
        subclass(Wifi::class)
    }
}

/** A module's keys, registered for both bases [appRouteSerializersModule] declares. */
typealias AppRouteRegistration = PolymorphicModuleBuilder<AppRoute>.() -> Unit

/**
 * One module for every key: the core keys of [AppRoutes] plus each feature's [featureRoutes], registered under
 * [AppRoute] for the shell's own state ([ShellBackStack], [RouteQueue]). Nav3 takes the keys as `NavEntry<AppRoute>`,
 * so no Nav3 type reaches common code.
 */
fun appRouteSerializersModule(vararg featureRoutes: AppRouteRegistration): SerializersModule {
    val registrations = listOf(AppRoutes.registration) + featureRoutes
    return SerializersModule {
        polymorphic(AppRoute::class) { registrations.forEach { it() } }
    }
}
