package dev.alllexey.itmowidgets.feature.social.ui.preview

import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.reviews.ReviewDate
import dev.alllexey.itmowidgets.core.reviews.ReviewOrigin
import dev.alllexey.itmowidgets.core.reviews.TeacherReview
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.schedule.TeacherLessons
import dev.alllexey.itmowidgets.core.schedule.TeacherNowLesson
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.social.domain.model.Person
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonEducation
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonPosition
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonRoom
import dev.alllexey.itmowidgets.feature.social.presentation.FriendLists
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsUiState
import dev.alllexey.itmowidgets.feature.social.presentation.PeopleSearchResults
import dev.alllexey.itmowidgets.feature.social.presentation.ProfilePart
import dev.alllexey.itmowidgets.feature.social.presentation.SocialAbsence
import dev.alllexey.itmowidgets.feature.social.presentation.SocialBlock
import dev.alllexey.itmowidgets.feature.social.presentation.UserAction
import dev.alllexey.itmowidgets.feature.social.presentation.UserFriendsUiState
import dev.alllexey.itmowidgets.feature.social.presentation.UserListItem
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileUiState
import dev.alllexey.itmowidgets.feature.social.presentation.UserRowUi
import dev.alllexey.itmowidgets.feature.social.presentation.subtitleText
import dev.alllexey.itmowidgets.feature.social.presentation.userProfileUiState
import dev.alllexey.itmowidgets.feature.social.ui.reviews.ReviewPreviewFixtures
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.social.SocialStrings
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth

/**
 * The design lab's people (synthetic) at its axes, for the Android previews and the iOS snapshot tests: Друзья with
 * and without anyone, the search, and the person card for each kind of person. The card states go through the real
 * [userProfileUiState], as the ViewModel's do.
 */
object SocialSamples {
    private val open = UserSharing(sport = true, schedule = true, friends = true)
    private val closed = UserSharing(sport = false, schedule = false, friends = false)

    private fun user(isu: Int, name: String, group: String, course: Int = 2, sharing: UserSharing = open) =
        UserSummary(isu, name, null, listOf(UserGroup(group, course, "ФИТиП")), sharing)

    private val me = user(284731, "Александрова Мария", "M3205")
    private val vera = user(297716, "Соколова Вера", "M3206")
    private val sofia = user(297717, "Лебедева Софья", "M3205")
    private val friends = listOf(
        user(288104, "Каримов Тимур", "M3206"),
        user(288110, "Егорова Полина", "M3205"),
        user(288121, "Козлов Иван", "M3207"),
    )

    private fun row(user: UserSummary, primary: UserAction? = null, secondary: UserAction? = null, status: UiText? = null) =
        UserRowUi(user.isu, user.name, null, user.subtitleText(), status, primary, secondary)

    /** Друзья: [state] `list` or `empty`. */
    /** A request's status with when it was sent, as `requestStatus` builds it. */
    private fun sent(status: String, moment: UiText) = UiText.Joined(listOf(UiText.Res(status), moment), ", ")

    fun friends(state: String = "list"): FriendsUiState = FriendsUiState(
        lists = if (state == "empty") {
            FriendLists.Content(emptyList(), emptyList(), emptyList())
        } else {
            FriendLists.Content(
                incoming = listOf(row(vera, UserAction.ACCEPT, UserAction.REJECT, sent(SocialStrings.user_status_incoming, UiText.Dynamic("12:05")))),
                outgoing = listOf(row(sofia, secondary = UserAction.CANCEL, status = sent(SocialStrings.user_status_outgoing, UiText.Res(CoreStrings.event_moment_yesterday)))),
                friends = friends.map { row(it) },
            )
        },
        viewer = me,
    )

    val friendsDisabled: FriendsUiState = FriendsUiState(lists = FriendLists.Disabled)

    /** A query that found a user of ITMO.Widgets and someone without it. */
    val search: FriendsUiState = friends().copy(
        query = "Соко",
        search = PeopleSearchResults.Content(
            registered = listOf(row(vera, UserAction.ACCEPT, status = UiText.Res(SocialStrings.user_status_incoming))),
            others = listOf(
                UserRowUi(
                    310402, "Соколов Андрей", null, UiText.Res(SocialStrings.user_subtitle_isu, listOf(310402)),
                    UiText.Res(SocialStrings.user_status_not_registered), UserAction.INVITE,
                ),
            ),
            offersMore = true,
            loadingMore = false,
        ),
    )

    /** Whose friends [userFriends] are. */
    const val USER_FRIENDS_OWNER = "Каримов Тимур"

    /** Another user's friends. */
    val userFriends: UserFriendsUiState = UserFriendsUiState.Content(friends.map { UserListItem.User(row(it)) })

    private val morozovReviews = listOf(
        review("m1", "Алгоритмы", 2026, Month.MAY, 7, true,
            "Сам недавно сдавал этот курс, поэтому объясняет на пальцах и без снобизма. На практике разбирает каждую задачу до конца."),
        review("m2", "Алгоритмы", 2025, Month.DECEMBER, 3, false,
            "Отвечает в чате даже вечером, дедлайны иногда двигает. Иногда сам путается в доказательствах, но честно это признаёт."),
    )

    private val ershovReviews = listOf(
        review("e1", "Программирование", 2026, Month.MAY, 18, true,
            "Лабы сдаются строго по дедлайнам, но на защите всегда объясняет, что не так. Лекции понятные, примеры из реальных проектов."),
        review("e2", "Программирование", 2026, Month.JANUARY, 11, true,
            "Много лаб, к защите надо готовиться заранее: спрашивает по коду каждую строчку. Зато оценки честные и без сюрпризов."),
    )

    private fun review(id: String, subject: String, year: Int, month: Month, score: Int, verified: Boolean, text: String) =
        TeacherReview(
            id, subject, ReviewDate.Month(YearMonth(year, month)), text, score, myVote = 0,
            origin = ReviewOrigin.Community(verified, author = null, reportedByMe = false),
        )

    /**
     * The person card. [who] `app` (a student in ITMO.Widgets), `noapp` (a student without it), `teacher`, `both`
     * (a teacher who also studies); [relation] `none` or `friend`; [teaches] `no` or `yes` (they teach the viewer).
     */
    fun card(who: String = "app", relation: String = "none", teaches: String = "no"): UserProfileUiState {
        val friend = relation == "friend"
        val teaching = teaches == "yes"
        return when (who) {
            "noapp" -> state(
                Person(297715, "Соколова Вера", null, emptyList(), emptyList(), listOf(PersonEducation("M3206", 2, "ФИТиП"))),
                social = null,
                absence = SocialAbsence.NOT_REGISTERED,
            )
            "teacher" -> state(
                Person(
                    107834, "Ершов Никита Андреевич", null,
                    listOf(PersonPosition("доцент", "Факультет программной инженерии и компьютерной техники (ПИиКТ)")),
                    listOf(PersonRoom("1410", "Кронверкский пр., 49")),
                    emptyList(),
                ),
                social = null,
                absence = SocialAbsence.NOT_REGISTERED,
                reviews = TeacherReviews(107834, ershovReviews, null, canWrite = teaching, canVote = true,
                    canReport = true, knownTeacher = true, summary = ReviewPreviewFixtures.summary.copy(reviewCount = 24)),
                subjects = if (teaching) listOf("Программирование") else emptyList(),
                now = nowLesson("Программирование", 2, if (teaching) "1506" else "2337", if (teaching) "M3205" else "M3201"),
            )
            "both" -> state(
                Person(
                    312058, "Морозов Кирилл Олегович", null,
                    listOf(PersonPosition("ассистент", "Факультет программной инженерии и компьютерной техники (ПИиКТ)")),
                    emptyList(),
                    listOf(PersonEducation("M4150", 1, "ПИиКТ")),
                ),
                social = UserProfile(
                    user(312058, "Морозов Кирилл", "M4150", course = 1, sharing = if (friend) open else closed),
                    if (friend) RelationshipState.FRIENDS else RelationshipState.NONE,
                ),
                reviews = TeacherReviews(312058, morozovReviews, null, canWrite = teaching, canVote = true,
                    canReport = true, knownTeacher = true),
                subjects = if (teaching) listOf("Алгоритмы") else emptyList(),
                now = nowLesson("Алгоритмы", 3, "2337", "M3207"),
            )
            else -> state(
                Person(288104, "Каримов Тимур", null, emptyList(), emptyList(), listOf(PersonEducation("M3206", 2, "ФИТиП"))),
                social = UserProfile(
                    user(288104, "Каримов Тимур", "M3206", sharing = if (friend) open else closed),
                    if (friend) RelationshipState.FRIENDS else RelationshipState.NONE,
                ),
            )
        }
    }

    /** The viewer's own card: no friendship, every row open. */
    val ownCard: UserProfileUiState = state(
        Person(284731, "Александрова Мария", null, emptyList(), emptyList(), listOf(PersonEducation("M3205", 2, "ФИТиП"))),
        social = UserProfile(me, RelationshipState.NONE),
        self = true,
    )

    /** An incoming request: `Принять заявку` and `Отклонить`. */
    val incomingCard: UserProfileUiState = state(
        Person(297716, "Соколова Вера", null, emptyList(), emptyList(), listOf(PersonEducation("M3206", 2, "ФИТиП"))),
        social = UserProfile(vera.copy(sharing = closed), RelationshipState.INCOMING),
    )

    val notFound: UserProfileUiState = UserProfileUiState.Error(AppError.NotFound)
    val failed: UserProfileUiState = UserProfileUiState.Error(AppError.Network)

    private fun state(
        person: Person,
        social: UserProfile?,
        absence: SocialAbsence? = null,
        reviews: TeacherReviews? = null,
        subjects: List<String> = emptyList(),
        self: Boolean = false,
        now: TeacherNowLesson? = null,
    ): UserProfileUiState = userProfileUiState(
        isu = person.isu,
        person = ProfilePart.Ready(person),
        social = social?.let { ProfilePart.Ready(SocialBlock(it, self, busy = false)) } ?: ProfilePart.Absent,
        reviews = reviews?.let { ProfilePart.Ready(it) } ?: ProfilePart.Absent,
        teaches = ProfilePart.Ready(TeacherLessons(emptySet(), subjects)),
        absence = absence,
        now = now,
    )

    /** A lesson going on at noon of the samples' day, as MyITMO's teacher timetable gives it. */
    private fun nowLesson(subject: String, typeId: Int, room: String, group: String) = TeacherNowLesson(
        start = LocalTime(11, 40), end = LocalTime(13, 10), subject = subject, typeId = typeId, room = room,
        building = "Кронверкский пр., 49", online = false, groups = listOf(group), isNow = true,
    )
}
