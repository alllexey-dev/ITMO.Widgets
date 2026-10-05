package dev.alllexey.itmowidgets.client.contract

import dev.alllexey.itmowidgets.client.app.AppRouteCases
import dev.alllexey.itmowidgets.client.device.DeviceRouteCases
import dev.alllexey.itmowidgets.client.friends.FriendsRouteCases
import dev.alllexey.itmowidgets.client.links.SubjectLinksRouteCases
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewsRouteCases
import dev.alllexey.itmowidgets.client.schedule.ScheduleRouteCases
import dev.alllexey.itmowidgets.client.sport.SportRouteCases
import dev.alllexey.itmowidgets.client.support.RouteCase
import dev.alllexey.itmowidgets.client.users.UsersRouteCases

/** Every area's route cases, one per public function of `BackendClient`; a new area adds its `all` here. */
object AllRouteCases {
    val all: List<RouteCase> = UsersRouteCases.all +
        FriendsRouteCases.all +
        SubjectLinksRouteCases.all +
        TeacherReviewsRouteCases.all +
        ScheduleRouteCases.all +
        SportRouteCases.all +
        DeviceRouteCases.all +
        AppRouteCases.all
}
