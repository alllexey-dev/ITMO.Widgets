package dev.alllexey.itmowidgets.feature.sport.ui.common

import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportCommon
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntry
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
    val unavailableReasons: List<String>,
    val comment: String?,
    val intersectsSchedule: Boolean,
    val isLesson: Boolean
) : Serializable

data class SportQueueEntryArgs(
    val position: Int,
    val total: Int,
    val status: String,
    val satisfiedAt: String?,
    val expiredAt: String?,
    val notificationAttempts: Int,
    val maxNotificationAttempts: Int,
    val isAutoSign: Boolean
) : Serializable

data class SportFriendDetailsArgs(
    val name: String,
    val pictureUrl: String?,
    val entry: SportQueueEntryArgs?
) : Serializable

fun SportCommon.toDetailsArgs(): SportCommonDetailsArgs {
    val lesson = this as? SportLesson
    return SportCommonDetailsArgs(
        sectionName = sectionName.shorten(),
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
                entry = booking.entry?.toDetailsArgs()
            )
        },
        unavailableReasons = lesson
            ?.unavailableReasons
            .orEmpty()
            .map { it.shortDescription },
        comment = lesson?.comment,
        intersectsSchedule = lesson?.intersection == true,
        isLesson = lesson != null
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
        isAutoSign = this is SportAutoSignEntry
    )
}
