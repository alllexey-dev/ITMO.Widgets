package dev.alllexey.itmowidgets.feature.sport.ui.common

import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportCommon
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLessonKind
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntry
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingConditions
import dev.alllexey.itmowidgets.feature.sport.presentation.common.bookingConditions
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportRegistrationStatus
import java.io.Serializable

data class SportCommonDetailsArgs(
    val sectionName: String,
    val start: String,
    val end: String,
    val teacherFio: String,
    val roomName: String,
    val signed: Boolean,
    val signEntry: SportQueueEntryArgs?,
    val friends: List<SportFriendDetailsArgs>,
    val bookingConditions: SportBookingConditions?,
    val comment: String?,
    val intersectsSchedule: Boolean,
    val isLesson: Boolean,
    val isReal: Boolean,
    val kind: SportLessonKind?,
    val available: Int?,
    val limit: Int?,
    val mapAddress: String?,
    val registrationStatus: SportRegistrationStatus
) : Serializable

data class SportQueueEntryArgs(
    val position: Int,
    val total: Int,
    val status: String,
    val satisfiedAt: String?,
    val expiredAt: String?,
    val notificationAttempts: Int,
    val maxNotificationAttempts: Int,
    val isAutoSign: Boolean,
    val createdAt: String,
    val lastNotifiedAt: String?,
    val cancelledAt: String?
) : Serializable

data class SportFriendDetailsArgs(
    val name: String,
    val pictureUrl: String?,
    val entry: SportQueueEntryArgs?,
    val registrationStatus: SportRegistrationStatus
) : Serializable

fun SportCommon.toDetailsArgs(): SportCommonDetailsArgs {
    val lesson = this as? SportLesson
    return SportCommonDetailsArgs(
        sectionName = sectionName.raw,
        start = start.toString(),
        end = end.toString(),
        teacherFio = teacherFio,
        roomName = roomName,
        signed = signed,
        signEntry = signEntry?.toDetailsArgs(),
        friends = friendsBookings.map { booking ->
            SportFriendDetailsArgs(
                name = booking.friend.name,
                pictureUrl = booking.friend.pictureUrl,
                entry = booking.entry?.toDetailsArgs(),
                registrationStatus = SportRegistrationStatus.from(booking.entry == null, booking.entry)
            )
        },
        bookingConditions = lesson?.bookingConditions(),
        comment = lesson?.comment,
        intersectsSchedule = lesson?.intersection == true,
        isLesson = lesson != null,
        isReal = isLessonReal,
        kind = lesson?.kind ?: when (lessonLevel) {
            2 -> SportLessonKind.TRAINING_SECTION
            3 -> SportLessonKind.INTERMEDIATE_SECTION
            4 -> SportLessonKind.TEAM_SECTION
            else -> null
        },
        available = lesson?.available,
        limit = lesson?.limit,
        mapAddress = extractBuildingAddress(),
        registrationStatus = SportRegistrationStatus.from(signed, signEntry)
    )
}

private fun SportQueueEntry.toDetailsArgs(): SportQueueEntryArgs {
    return SportQueueEntryArgs(
        position = position,
        total = total,
        status = status.name,
        satisfiedAt = satisfiedAt?.toString(),
        expiredAt = expiredAt?.toString(),
        notificationAttempts = notificationAttempts,
        maxNotificationAttempts = maxNotificationAttempts,
        isAutoSign = this is SportAutoSignEntry,
        createdAt = createdAt.toString(),
        lastNotifiedAt = lastNotifiedAt?.toString(),
        cancelledAt = cancelledAt?.toString()
    )
}
