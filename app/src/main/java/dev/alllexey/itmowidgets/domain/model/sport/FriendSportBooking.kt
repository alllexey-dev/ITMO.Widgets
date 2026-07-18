package dev.alllexey.itmowidgets.domain.model.sport

import dev.alllexey.itmowidgets.core.model.SportQueueEntry
import dev.alllexey.itmowidgets.core.model.UserData

data class FriendSportBooking(
    val friend: UserData,
    val lessonId: Long,
    val entry: SportQueueEntry?
)
