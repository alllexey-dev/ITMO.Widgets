package dev.alllexey.itmowidgets.client.moderation

import dev.alllexey.itmowidgets.client.support.RouteCase

/** One [RouteCase] per public function of [ModerationNoticesApi]. */
object ModerationNoticesRouteCases {
    val notices = RouteCase("moderationNotices") { moderationNotices.notices() }

    val all: List<RouteCase> = listOf(notices)
}
