package dev.alllexey.itmowidgets.core.resources

import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import java.time.OffsetDateTime

data class ResourceScope(val subjectId: Long, val subjectName: String, val periodKey: String) {
    val key: String get() = "$subjectId-$periodKey"
    companion object {
        fun periodKey(studyYear: String, semesterInCourse: Int): String {
            require(semesterInCourse in 1..2)
            val year = studyYear.substringBefore('/').toInt()
            require(year in 1000..9999)
            return "$year-$semesterInCourse"
        }
    }
}

/** Declaration order is the display order of chips and sections. */
enum class LinkCategory { SCORES, QUEUE, MATERIALS, TASKS, RECORDINGS, NOTES, EXAM, CHAT, OTHER }

/** GROUP and FLOW publish to the author's schedule flows at once; ALL may be premoderated. */
enum class LinkVisibility { PRIVATE, GROUP, FLOW, ALL }

/** Owners see their own link's review state; other viewers always see PUBLISHED. */
enum class SubjectLinkStatus { PRIVATE, PENDING, PUBLISHED, REJECTED, HIDDEN }

enum class ResourceReportReason { BROKEN, WRONG_SUBJECT, SPAM, OTHER }
enum class RestrictionCapability { SUBMIT_RESOURCES, VOTE, REPORT, WRITE_REVIEWS, ALL }

/** A link as seen by the viewer; [local] links exist only on this device and are always PRIVATE. */
data class SubjectLink(
    val id: String,
    val scope: ResourceScope,
    val category: LinkCategory,
    val url: String,
    val title: String?,
    val visibility: LinkVisibility,
    val audienceLabel: String?,
    val status: SubjectLinkStatus,
    val reviewNote: String?,
    val score: Int,
    val myVote: Int,
    val isMine: Boolean,
    val isSaved: Boolean,
    val reportedByMe: Boolean,
    val author: UserSummary?,
    val updatedAt: OffsetDateTime,
    val local: Boolean = false,
)

/** A GROUP or FLOW audience the viewer can publish to, labelled with its schedule group names. */
data class LinkAudience(val visibility: LinkVisibility, val label: String)

/** Without [servicesEnabled] only [mine] is filled, with local links. */
data class SubjectLinksSnapshot(
    val mine: List<SubjectLink>,
    val shared: List<SubjectLink>,
    val previous: List<SubjectLink>,
    val pinnedId: String?,
    val audiences: List<LinkAudience>,
    val premoderation: Boolean,
    val servicesEnabled: Boolean,
)

sealed interface SubjectLinksState {
    data object Loading : SubjectLinksState
    data class Content(val snapshot: SubjectLinksSnapshot, val refreshing: Boolean = false) : SubjectLinksState
    data class Error(val error: AppError) : SubjectLinksState
}

data class UserRestriction(val id: String, val capability: RestrictionCapability, val reason: String, val expiresAt: OffsetDateTime?)
fun List<UserRestriction>.blocks(capability: RestrictionCapability): UserRestriction? =
    firstOrNull { it.capability == capability || it.capability == RestrictionCapability.ALL }
