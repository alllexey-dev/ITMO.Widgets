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

/** FLOW publishes to one schedule flow of the author (`flowId`) at once; ALL may be premoderated. */
enum class LinkVisibility { PRIVATE, FLOW, ALL }

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
    /** The schedule flow of a FLOW link; null otherwise. */
    val flowId: Long?,
    /** The schedule name of a FLOW link's flow, e.g. «ФИЗ ПИИКТ 3.2.1». */
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

/**
 * A schedule flow of the subject the viewer can publish to: [label] is its schedule name, [typeId] the
 * schedule lesson type and [depth] the nesting level of its number (`3` lectures, `3.2`, `3.2.1`).
 */
data class LinkAudience(val flowId: Long, val label: String, val typeId: Int, val depth: Int)

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
