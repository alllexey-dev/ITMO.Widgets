package dev.alllexey.itmowidgets.feature.social.presentation

import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.primaryGroup
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.schedule.TeacherLessons
import dev.alllexey.itmowidgets.core.schedule.TeacherNowLesson
import dev.alllexey.itmowidgets.core.schedule.lessonTypeName
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.personInitials
import dev.alllexey.itmowidgets.feature.social.domain.model.Person
import dev.alllexey.itmowidgets.shared.feature.social.SocialStrings
import kotlinx.datetime.LocalTime

sealed interface ProfilePart<out T> {
    data object Loading : ProfilePart<Nothing>
    data class Ready<T>(val value: T) : ProfilePart<T>
    data object Absent : ProfilePart<Nothing>
    data class Failed(val error: AppError) : ProfilePart<Nothing>
}

data class SocialBlock(val profile: UserProfile, val isSelf: Boolean, val busy: Boolean)

/** Why Backend has no profile of the person: they do not use ITMO.Widgets, or the viewer is not connected. */
enum class SocialAbsence { NOT_REGISTERED, NOT_CONNECTED }

/** A button of the card's header; the first one shown is the filled primary. */
enum class CardAction(private val key: String) {
    WRITE_REVIEW(SocialStrings.user_profile_write_review),
    ADD_FRIEND(SocialStrings.user_profile_add_friend),
    ACCEPT_REQUEST(SocialStrings.user_profile_accept_request),
    REJECT_REQUEST(SocialStrings.user_profile_reject_request),
    CANCEL_REQUEST(SocialStrings.user_profile_cancel_request),
    INVITE(SocialStrings.user_profile_invite);

    val label: UiText get() = UiText.Res(key)
}

/** A pill under the name: a position, a study group, the friendship, or that the card is the viewer's own. */
/** Not `SELF`: Objective-C export would turn it into `+self`, which overrides `+[NSObject self]` on iOS. */
enum class ProfileRoleKind { POSITION, STUDY, FRIEND, OWN }

data class ProfileRole(val kind: ProfileRoleKind, val text: UiText)

/** One row with a title and a quieter line: a room in "Где найти", a study group in "Учёба". */
data class ProfileLine(val title: UiText, val detail: String?)

/**
 * "Сейчас ведёт": the teacher's lesson now, or else their next one today, from MyITMO's teacher timetable. [heading]
 * says which (`Сейчас ведёт пару до 13:10`), [place] is the room and building or `Онлайн`, [lesson] the subject with
 * the type and the groups (`Алгоритмы, практика у M3207`).
 */
data class TeacherNowLine(val heading: UiText, val place: UiText, val lesson: UiText, val isNow: Boolean)

/**
 * "Преподавание": the lesson they give now ([now]), what the viewer's own lessons say the person teaches them
 * ([subjects], newest first) and where to find them; the reviews follow under it on the screen
 * ([UserProfileUiState.Content.reviews]).
 */
data class TeachingBlock(val subjects: List<String>, val rooms: List<ProfileLine>, val now: TeacherNowLine? = null)

/** "Учёба": the groups, then the rows Backend lets the viewer open. */
data class StudyBlock(val groups: List<ProfileLine>, val schedule: Boolean, val sport: Boolean, val friends: Boolean)

sealed interface UserProfileUiState {
    data object Loading : UserProfileUiState
    data class Error(val error: AppError) : UserProfileUiState

    /**
     * The person card (docs/features/social.md § Person profile): the centred header with [roles] and up to two
     * [actions], then "Преподавание" and "Учёба", each only with data, in the order of [teachingFirst], and
     * `Удалить из друзей` last when [canRemoveFriend].
     */
    data class Content(
        val isu: Int,
        val name: String,
        val pictureUrl: String?,
        val roles: List<ProfileRole>,
        val actions: List<CardAction>,
        /** A friendship action is in flight; the buttons stay inert meanwhile. */
        val busy: Boolean,
        val teaching: TeachingBlock?,
        val study: StudyBlock?,
        val teachingFirst: Boolean,
        val canRemoveFriend: Boolean,
        val social: SocialBlock?,
        val reviews: ProfileReviews?,
        /** ITMO.ID's name parts from Backend for the initials; null without them. */
        val familyName: String? = null,
        val givenName: String? = null,
    ) : UserProfileUiState {
        /** [name] for the header, the share text and the remove confirmation; an empty one becomes the placeholder. */
        val displayName: UiText get() = userDisplayName(name, isu)
        val initials: String get() = personInitials(name, familyName, givenName)
    }
}

sealed interface UserProfileEvent {
    data class ActionFailed(val error: AppError) : UserProfileEvent
    data class ConfirmRemove(val name: UiText) : UserProfileEvent
    data object LoadFailed : UserProfileEvent
    data object ConfirmDeleteReview : UserProfileEvent
}

/**
 * The page from its parts. It waits while any part loads; it exists when either identity (My ITMO's [person] or
 * Backend's [social]) is ready. [teaches] is what the viewer's own lessons say about the person, [absence] why
 * [social] is absent, [now] the teacher's lesson now or next today.
 */
fun userProfileUiState(
    isu: Int,
    person: ProfilePart<Person>,
    social: ProfilePart<SocialBlock>,
    reviews: ProfilePart<TeacherReviews>,
    teaches: ProfilePart<TeacherLessons> = ProfilePart.Absent,
    absence: SocialAbsence? = null,
    now: TeacherNowLesson? = null,
    busyId: String? = null,
    summaryExpanded: Boolean = false,
): UserProfileUiState {
    val parts = listOf(person, social, reviews, teaches)
    if (parts.any { it is ProfilePart.Loading }) return UserProfileUiState.Loading
    val personality = (person as? ProfilePart.Ready)?.value
    val block = (social as? ProfilePart.Ready)?.value
    if (personality == null && block == null) {
        val error = listOfNotNull((person as? ProfilePart.Failed)?.error, (social as? ProfilePart.Failed)?.error)
            .firstOrNull { it != AppError.NotFound } ?: AppError.NotFound
        return UserProfileUiState.Error(error)
    }
    val user = block?.profile?.user
    val section = profileReviews((reviews as? ProfilePart.Ready)?.value, personality, busyId, summaryExpanded)
    val subjects = (teaches as? ProfilePart.Ready)?.value?.subjects.orEmpty()
    val teaching = teachingBlock(personality, subjects, section, now)
    val study = studyBlock(personality, block)
    val isSelf = block?.isSelf == true
    val friends = block?.profile?.relationship == RelationshipState.FRIENDS && !isSelf
    return UserProfileUiState.Content(
        isu = isu,
        name = personality?.name ?: checkNotNull(user).name,
        pictureUrl = if (personality != null) personality.photoUrl else user?.pictureUrl,
        roles = roles(personality, block, isSelf, friends),
        actions = cardActions(block, absence, isStudent = study?.groups?.isNotEmpty() == true, section, subjects),
        busy = block?.busy == true,
        teaching = teaching,
        study = study,
        teachingFirst = teaching != null && subjects.isNotEmpty(),
        canRemoveFriend = friends,
        social = block,
        reviews = section,
        familyName = user?.familyName,
        givenName = user?.givenName,
    )
}

private fun roles(person: Person?, block: SocialBlock?, isSelf: Boolean, friends: Boolean): List<ProfileRole> = buildList {
    person?.positions?.forEach { position ->
        val role = position.title?.let(::shortRole)?.takeIf(String::isNotEmpty)
        val department = position.department?.let(::shortDepartment)
        val text = listOfNotNull(role, department).joinToString(", ")
        if (text.isNotEmpty()) add(ProfileRole(ProfileRoleKind.POSITION, UiText.Dynamic(text)))
    }
    val group = person?.education?.firstNotNullOfOrNull { it.group } ?: block?.profile?.user?.primaryGroup()?.name
    if (group != null) add(ProfileRole(ProfileRoleKind.STUDY, UiText.Res(SocialStrings.person_role_student, listOf(group))))
    if (isSelf) add(ProfileRole(ProfileRoleKind.OWN, UiText.Res(SocialStrings.user_profile_self)))
    if (friends) add(ProfileRole(ProfileRoleKind.FRIEND, UiText.Res(SocialStrings.user_profile_friend_badge)))
}.distinct()

/**
 * The header's buttons: writing a review when the viewer may (first when the person teaches them), the friendship's
 * next step for a user of ITMO.Widgets, or the invitation for a student without it. A pure teacher has no friendship;
 * the viewer's own card has none either. At most two.
 */
private fun cardActions(
    block: SocialBlock?,
    absence: SocialAbsence?,
    isStudent: Boolean,
    reviews: ProfileReviews?,
    subjects: List<String>,
): List<CardAction> {
    val review = CardAction.WRITE_REVIEW.takeIf { reviews?.canWrite == true }
    val friendship = when {
        block == null -> listOfNotNull(CardAction.INVITE.takeIf { isStudent && absence == SocialAbsence.NOT_REGISTERED })
        block.isSelf -> emptyList()
        else -> when (block.profile.relationship) {
            RelationshipState.NONE -> listOf(CardAction.ADD_FRIEND)
            RelationshipState.OUTGOING -> listOf(CardAction.CANCEL_REQUEST)
            RelationshipState.INCOMING -> listOf(CardAction.ACCEPT_REQUEST, CardAction.REJECT_REQUEST)
            RelationshipState.FRIENDS, RelationshipState.BLOCKED -> emptyList()
        }
    }
    val ordered = if (subjects.isNotEmpty()) listOfNotNull(review) + friendship else {
        // The answer to a request comes as one pair; a review button joins only where it leaves room.
        if (friendship.size > 1) friendship else friendship + listOfNotNull(review)
    }
    return ordered.take(MAX_ACTIONS)
}

private fun teachingBlock(person: Person?, subjects: List<String>, reviews: ProfileReviews?, now: TeacherNowLesson?): TeachingBlock? {
    val teaches = person?.positions?.isNotEmpty() == true || subjects.isNotEmpty() || reviews != null || now != null
    if (!teaches) return null
    val rooms = person?.rooms.orEmpty().map {
        ProfileLine(UiText.Res(SocialStrings.person_room, listOf(it.number)), it.building)
    }
    return TeachingBlock(subjects, rooms, now?.let(::teacherNowLine))
}

internal fun teacherNowLine(lesson: TeacherNowLesson): TeacherNowLine {
    val heading = if (lesson.isNow) {
        UiText.Res(SocialStrings.person_now_teaching, listOf(clock(lesson.end)))
    } else {
        UiText.Res(SocialStrings.person_next_lesson, listOf(clock(lesson.start)))
    }
    val room = lesson.room?.trim()?.takeIf { it.isNotEmpty() }
    val place = when {
        lesson.online || room == null && lesson.building.isNullOrBlank() -> UiText.Res(SocialStrings.person_lesson_online)
        else -> UiText.Joined(
            listOfNotNull(
                room?.let { UiText.Res(SocialStrings.person_room, listOf(it)) },
                lesson.building?.trim()?.takeIf { it.isNotEmpty() }?.let(UiText::Dynamic),
            ),
            ", ",
        )
    }
    val type = UiText.LowercaseFirst(lessonTypeName(lesson.typeId))
    val kind = if (lesson.groups.isEmpty()) type else {
        UiText.Res(SocialStrings.person_lesson_for, listOf(type, lesson.groups.joinToString(", ")))
    }
    return TeacherNowLine(heading, place, UiText.Joined(listOf(UiText.Dynamic(lesson.subject), kind), ", "), lesson.isNow)
}

/** `9:05` as `09:05`. */
private fun clock(time: LocalTime): String =
    "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"

/** Groups from My ITMO, else Backend's current one; the rows follow Backend's viewer capabilities, all open to self. */
private fun studyBlock(person: Person?, block: SocialBlock?): StudyBlock? {
    val groups = if (person != null) {
        person.education.mapNotNull { education ->
            val title = when {
                education.group != null && education.course != null ->
                    UiText.Res(SocialStrings.person_group_course, listOf(education.group, education.course))
                education.group != null -> UiText.Dynamic(education.group)
                else -> null
            }
            title?.let { ProfileLine(it, education.faculty) }
        }
    } else {
        listOfNotNull(block?.profile?.user?.primaryGroup()?.let {
            ProfileLine(UiText.Res(SocialStrings.person_group_course, listOf(it.name, it.course)), it.facultyShortName)
        })
    }
    val sharing = block?.profile?.user?.sharing
    val self = block?.isSelf == true
    val schedule = block != null && (self || sharing?.schedule == true)
    val sport = block != null && (self || sharing?.sport == true)
    val friends = block != null && (self || sharing?.friends == true)
    if (groups.isEmpty() && !schedule && !sport && !friends) return null
    return StudyBlock(groups, schedule, sport, friends)
}

private const val MAX_ACTIONS = 2
