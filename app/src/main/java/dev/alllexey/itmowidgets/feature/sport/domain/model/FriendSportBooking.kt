package dev.alllexey.itmowidgets.feature.sport.domain.model

import dev.alllexey.itmowidgets.core.model.UserSummary

data class FriendSportBooking(
    val friend: UserSummary,
    val lessonId: Long,
    val entry: SportQueueEntry?
)
