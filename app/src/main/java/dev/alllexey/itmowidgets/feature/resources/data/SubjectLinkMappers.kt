package dev.alllexey.itmowidgets.feature.resources.data

import dev.alllexey.itmowidgets.client.common.ReportReason
import dev.alllexey.itmowidgets.client.links.SaveSubjectLinkRequest
import dev.alllexey.itmowidgets.client.links.SubjectLinksResponse
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.model.toUserSummary
import dev.alllexey.itmowidgets.core.resources.LinkAudience
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceReportReason
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.RestrictionCapability
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinkStatus
import dev.alllexey.itmowidgets.core.resources.UserRestriction
import dev.alllexey.itmowidgets.client.links.LinkAudience as WireAudience
import dev.alllexey.itmowidgets.client.links.LinkCategory as WireCategory
import dev.alllexey.itmowidgets.client.links.LinkVisibility as WireVisibility
import dev.alllexey.itmowidgets.client.links.RestrictionCapability as WireCapability
import dev.alllexey.itmowidgets.client.links.SubjectLink as WireLink
import dev.alllexey.itmowidgets.client.links.SubjectLinkStatus as WireStatus
import dev.alllexey.itmowidgets.client.links.UserRestriction as WireRestriction

// Core 2.0 answers into the stored rows: the one place that changes when the wire types do.

internal fun SubjectLinksResponse.toStored() = StoredLinksAnswer(
    mine = mine.map { it.toStored() },
    shared = shared.map { it.toStored() },
    previous = previous.map { it.toStored() },
    pinnedId = pinnedId?.toString(),
    audiences = audiences.map { it.toStored() },
    premoderation = premoderation,
)

internal fun WireLink.toStored() = StoredLink(
    id = id.toString(),
    subjectId = subjectId,
    subjectName = subjectName.trim(),
    periodKey = periodKey,
    category = category.toModel(),
    url = url,
    title = title?.trim()?.takeIf { it.isNotEmpty() },
    visibility = LinkVisibility.valueOf(visibility.name),
    flowId = flowId,
    audienceLabel = audienceLabel?.trim()?.takeIf { it.isNotEmpty() },
    status = status.toModel(),
    reviewNote = reviewNote?.trim()?.takeIf { it.isNotEmpty() },
    score = score,
    myVote = myVote,
    isMine = isMine,
    reportedByMe = reportedByMe,
    author = author?.toUserSummary()?.toStored(),
    updatedAt = updatedAt,
)

/** A category added by a newer Backend is shown as "other". */
private fun WireCategory.toModel(): LinkCategory = when (this) {
    WireCategory.UNKNOWN -> LinkCategory.OTHER
    else -> LinkCategory.valueOf(name)
}

/** A state added by a newer Backend shows no badge, as a published link does. */
private fun WireStatus.toModel(): SubjectLinkStatus = when (this) {
    WireStatus.UNKNOWN -> SubjectLinkStatus.PUBLISHED
    else -> SubjectLinkStatus.valueOf(name)
}

internal fun WireAudience.toStored() = StoredAudience(flowId, label.trim(), typeId, depth)

internal fun StoredLinkRequest.toWire() = SaveSubjectLinkRequest(
    subjectId, subjectName, periodKey, WireCategory.valueOf(category.name), url, title, WireVisibility.valueOf(visibility.name), flowId,
)

internal fun ResourceReportReason.toWire() = ReportReason.valueOf(name)

/** Core decodes a capability added by a newer Backend as ALL, so an unknown one blocks every action. */
internal fun WireRestriction.toModel() = UserRestriction(id.toString(), capability.toModel(), reason, expiresAt)

private fun WireCapability.toModel(): RestrictionCapability = RestrictionCapability.valueOf(name)

// Stored rows into the domain.

internal fun StoredLink.toModel() = SubjectLink(
    id = id,
    scope = ResourceScope(subjectId, subjectName, periodKey),
    category = category,
    url = url,
    title = title,
    visibility = visibility,
    flowId = flowId,
    audienceLabel = audienceLabel,
    status = status,
    reviewNote = reviewNote,
    score = score,
    myVote = myVote,
    isMine = isMine,
    reportedByMe = reportedByMe,
    author = author?.toModel(),
    updatedAt = updatedAt,
)

internal fun LocalLink.toModel() = SubjectLink(
    id = id,
    scope = scope,
    category = request.category,
    url = request.url,
    title = request.title,
    visibility = LinkVisibility.PRIVATE,
    flowId = null,
    audienceLabel = null,
    status = SubjectLinkStatus.PRIVATE,
    reviewNote = null,
    score = 0,
    myVote = 0,
    isMine = true,
    reportedByMe = false,
    author = null,
    updatedAt = updatedAt,
    local = true,
)

internal fun StoredAudience.toModel() = LinkAudience(flowId, label, typeId, depth)

private fun UserSummary.toStored() = StoredAuthor(
    isu = isu,
    name = name,
    pictureUrl = pictureUrl,
    groups = groups.map { StoredAuthorGroup(it.name, it.course, it.facultyShortName) },
    sharing = StoredAuthorSharing(sport = sharing.sport, schedule = sharing.schedule, friends = sharing.friends),
)

private fun StoredAuthor.toModel() = UserSummary(
    isu = isu,
    name = name,
    pictureUrl = pictureUrl,
    groups = groups.map { UserGroup(it.name, it.course, it.facultyShortName) },
    sharing = UserSharing(sport = sharing.sport, schedule = sharing.schedule, friends = sharing.friends),
)
