package dev.alllexey.itmowidgets.feature.resources.data

import dev.alllexey.itmowidgets.core.model.resources.ReportReason
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
import dev.alllexey.itmowidgets.core.model.resources.LinkAudience as WireAudience
import dev.alllexey.itmowidgets.core.model.resources.LinkCategory as WireCategory
import dev.alllexey.itmowidgets.core.model.resources.LinkVisibility as WireVisibility
import dev.alllexey.itmowidgets.core.model.resources.SubjectLink as WireLink
import dev.alllexey.itmowidgets.core.model.resources.UserRestriction as WireRestriction

internal fun WireLink.toModel() = SubjectLink(
    id = id.toString(),
    scope = ResourceScope(subjectId, subjectName.trim(), periodKey),
    category = LinkCategory.valueOf(category.name),
    url = url,
    title = title?.trim()?.takeIf { it.isNotEmpty() },
    visibility = visibility.toModel(),
    flowId = flowId,
    audienceLabel = audienceLabel?.trim()?.takeIf { it.isNotEmpty() },
    status = SubjectLinkStatus.valueOf(status.name),
    reviewNote = reviewNote?.trim()?.takeIf { it.isNotEmpty() },
    score = score,
    myVote = myVote,
    isMine = isMine,
    isSaved = isSaved,
    reportedByMe = reportedByMe,
    author = author?.toUserSummary(),
    updatedAt = updatedAt,
)

internal fun LocalLink.toModel() = SubjectLink(
    id = id,
    scope = scope,
    category = LinkCategory.valueOf(request.category.name),
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
    isSaved = false,
    reportedByMe = false,
    author = null,
    updatedAt = updatedAt,
    local = true,
)

internal fun WireAudience.toModel() = LinkAudience(flowId, label.trim(), typeId, depth)
internal fun WireVisibility.toModel() = LinkVisibility.valueOf(name)
internal fun LinkVisibility.toWire() = WireVisibility.valueOf(name)
internal fun LinkCategory.toWire() = WireCategory.valueOf(name)
internal fun ResourceReportReason.toWire() = ReportReason.valueOf(name)
internal fun WireRestriction.toModel() = UserRestriction(id.toString(), RestrictionCapability.valueOf(capability.name), reason, expiresAt)
