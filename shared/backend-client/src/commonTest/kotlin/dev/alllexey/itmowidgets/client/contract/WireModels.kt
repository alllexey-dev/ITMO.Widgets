package dev.alllexey.itmowidgets.client.contract

import dev.alllexey.itmowidgets.client.app.AppVersionInfo
import dev.alllexey.itmowidgets.client.common.GroupData
import dev.alllexey.itmowidgets.client.common.ModerationReportRequest
import dev.alllexey.itmowidgets.client.common.RelationshipState
import dev.alllexey.itmowidgets.client.common.RelationshipStateSerializer
import dev.alllexey.itmowidgets.client.common.ReportReason
import dev.alllexey.itmowidgets.client.common.ReportReasonSerializer
import dev.alllexey.itmowidgets.client.common.ResourceVoteRequest
import dev.alllexey.itmowidgets.client.common.UserCapabilities
import dev.alllexey.itmowidgets.client.common.UserData
import dev.alllexey.itmowidgets.client.common.UserProfile
import dev.alllexey.itmowidgets.client.device.DevicePlatform
import dev.alllexey.itmowidgets.client.device.DevicePlatformSerializer
import dev.alllexey.itmowidgets.client.device.RegisterDeviceRequest
import dev.alllexey.itmowidgets.client.device.UnregisterDeviceRequest
import dev.alllexey.itmowidgets.client.links.LinkAudience
import dev.alllexey.itmowidgets.client.links.LinkCategory
import dev.alllexey.itmowidgets.client.links.LinkCategorySerializer
import dev.alllexey.itmowidgets.client.links.LinkVisibility
import dev.alllexey.itmowidgets.client.links.LinkVisibilitySerializer
import dev.alllexey.itmowidgets.client.links.PinSubjectLinkRequest
import dev.alllexey.itmowidgets.client.links.RestrictionCapability
import dev.alllexey.itmowidgets.client.links.RestrictionCapabilitySerializer
import dev.alllexey.itmowidgets.client.links.SaveSubjectLinkRequest
import dev.alllexey.itmowidgets.client.links.SubjectLink
import dev.alllexey.itmowidgets.client.links.SubjectLinkStatus
import dev.alllexey.itmowidgets.client.links.SubjectLinkStatusSerializer
import dev.alllexey.itmowidgets.client.links.SubjectLinksResponse
import dev.alllexey.itmowidgets.client.links.UserRestriction
import dev.alllexey.itmowidgets.client.push.FriendshipEvent
import dev.alllexey.itmowidgets.client.push.FriendshipEventSerializer
import dev.alllexey.itmowidgets.client.reviews.OwnTeacherReview
import dev.alllexey.itmowidgets.client.reviews.SaveTeacherReviewRequest
import dev.alllexey.itmowidgets.client.reviews.SummaryConfidence
import dev.alllexey.itmowidgets.client.reviews.SummaryConfidenceSerializer
import dev.alllexey.itmowidgets.client.reviews.SummaryLevel
import dev.alllexey.itmowidgets.client.reviews.SummaryLevelSerializer
import dev.alllexey.itmowidgets.client.reviews.SummaryScaleKind
import dev.alllexey.itmowidgets.client.reviews.SummaryScaleKindSerializer
import dev.alllexey.itmowidgets.client.reviews.SummaryScaleValue
import dev.alllexey.itmowidgets.client.reviews.SummaryScaleValueSerializer
import dev.alllexey.itmowidgets.client.reviews.TeacherReview
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewKind
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewKindSerializer
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewStatus
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewStatusSerializer
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewsResponse
import dev.alllexey.itmowidgets.client.reviews.TeacherSummary
import dev.alllexey.itmowidgets.client.reviews.TeacherSummaryLevel
import dev.alllexey.itmowidgets.client.reviews.TeacherSummaryScale
import dev.alllexey.itmowidgets.client.schedule.LessonDto
import dev.alllexey.itmowidgets.client.schedule.LessonSyncRequest
import dev.alllexey.itmowidgets.client.sport.model.FriendSportBooking
import dev.alllexey.itmowidgets.client.sport.model.FriendsSportBookingsResponse
import dev.alllexey.itmowidgets.client.sport.model.QueueEntryStatus
import dev.alllexey.itmowidgets.client.sport.model.QueueEntryStatusSerializer
import dev.alllexey.itmowidgets.client.sport.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.client.sport.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.client.sport.model.SportAutoSignQueue
import dev.alllexey.itmowidgets.client.sport.model.SportAutoSignRequest
import dev.alllexey.itmowidgets.client.sport.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.client.sport.model.SportFreeSignQueue
import dev.alllexey.itmowidgets.client.sport.model.SportFreeSignRequest
import dev.alllexey.itmowidgets.client.sport.model.SportLessonDto
import dev.alllexey.itmowidgets.client.sport.model.SportQueue
import dev.alllexey.itmowidgets.client.sport.model.SportQueueEntry
import dev.alllexey.itmowidgets.client.sport.model.UserSportBookingsResponse
import dev.alllexey.itmowidgets.client.users.IdTokenRequest
import dev.alllexey.itmowidgets.client.users.SharingVisibility
import dev.alllexey.itmowidgets.client.users.SharingVisibilitySerializer
import dev.alllexey.itmowidgets.client.users.UserLookupRequest
import dev.alllexey.itmowidgets.client.users.UserLookupResponse
import dev.alllexey.itmowidgets.client.users.UserPrivacySettings
import dev.alllexey.itmowidgets.client.users.WebLoginPreview
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.enums.EnumEntries

/** A client wire model and the Backend schema it mirrors; [name] is the Kotlin class name, equal to Backend's. */
class WireModel(val name: String, val serializer: KSerializer<*>) {
    override fun toString(): String = name
}

/** A wire enum: its serializer (whose descriptor is a string) and its constants, `UNKNOWN` included. */
class WireEnum(val serializer: KSerializer<*>, val constants: List<String>) {
    val name: String get() = serializer.descriptor.serialName.substringAfterLast('.')
}

/**
 * Every HTTP wire model of the client, sealed subtypes included, and every wire enum. The model check fails when a
 * listed model reaches an unlisted one, so a nested type cannot be forgotten; a new top-level request or answer
 * type joins here. The FCM payloads are not here: OpenAPI does not describe them, their fixtures do.
 */
object WireModels {
    val all: List<WireModel> = listOf(
        model<AppVersionInfo>(),
        model<RegisterDeviceRequest>(),
        model<UnregisterDeviceRequest>(),
        model<UserData>(),
        model<GroupData>(),
        model<UserCapabilities>(),
        model<UserProfile>(),
        model<IdTokenRequest>(),
        model<UserLookupRequest>(),
        model<UserLookupResponse>(),
        model<UserPrivacySettings>(),
        model<WebLoginPreview>(),
        model<ResourceVoteRequest>(),
        model<ModerationReportRequest>(),
        model<SubjectLink>(),
        model<SubjectLinksResponse>(),
        model<LinkAudience>(),
        model<SaveSubjectLinkRequest>(),
        model<PinSubjectLinkRequest>(),
        model<UserRestriction>(),
        model<TeacherReviewsResponse>(),
        model<TeacherReview>(),
        model<OwnTeacherReview>(),
        model<TeacherSummary>(),
        model<TeacherSummaryScale>(),
        model<TeacherSummaryLevel>(),
        model<SaveTeacherReviewRequest>(),
        model<LessonSyncRequest>(),
        model<LessonDto>(),
        model<SportLessonDto>(),
        model<SportQueueEntry>(),
        model<SportFreeSignEntry>(),
        model<SportAutoSignEntry>(),
        model<SportQueue>(),
        model<SportFreeSignQueue>(),
        model<SportAutoSignQueue>(),
        model<SportFreeSignRequest>(),
        model<SportAutoSignRequest>(),
        model<SportAutoSignLimits>(),
        model<FriendSportBooking>(),
        model<FriendsSportBookingsResponse>(),
        model<UserSportBookingsResponse>(),
    )

    val enums: List<WireEnum> = listOf(
        wireEnum(RelationshipStateSerializer, RelationshipState.entries),
        wireEnum(SharingVisibilitySerializer, SharingVisibility.entries),
        wireEnum(LinkVisibilitySerializer, LinkVisibility.entries),
        wireEnum(FriendshipEventSerializer, FriendshipEvent.entries),
        wireEnum(ReportReasonSerializer, ReportReason.entries),
        wireEnum(DevicePlatformSerializer, DevicePlatform.entries),
        wireEnum(RestrictionCapabilitySerializer, RestrictionCapability.entries),
        wireEnum(QueueEntryStatusSerializer, QueueEntryStatus.entries),
        wireEnum(LinkCategorySerializer, LinkCategory.entries),
        wireEnum(SubjectLinkStatusSerializer, SubjectLinkStatus.entries),
        wireEnum(TeacherReviewKindSerializer, TeacherReviewKind.entries),
        wireEnum(TeacherReviewStatusSerializer, TeacherReviewStatus.entries),
        wireEnum(SummaryLevelSerializer, SummaryLevel.entries),
        wireEnum(SummaryConfidenceSerializer, SummaryConfidence.entries),
        wireEnum(SummaryScaleKindSerializer, SummaryScaleKind.entries),
        wireEnum(SummaryScaleValueSerializer, SummaryScaleValue.entries),
    )

    private inline fun <reified T : Any> model(): WireModel = WireModel(T::class.simpleName!!, serializer<T>())

    private fun <E : Enum<E>> wireEnum(serializer: KSerializer<E>, entries: EnumEntries<E>): WireEnum =
        WireEnum(serializer, entries.map { it.name })
}
