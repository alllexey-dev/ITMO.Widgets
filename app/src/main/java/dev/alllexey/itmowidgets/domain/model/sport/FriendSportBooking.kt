package dev.alllexey.itmowidgets.domain.model.sport

import dev.alllexey.itmowidgets.domain.model.user.UserSummary

data class FriendSportBooking(
    val friend: UserSummary,
    val lessonId: Long,
    val entry: SportQueueEntry?
)
