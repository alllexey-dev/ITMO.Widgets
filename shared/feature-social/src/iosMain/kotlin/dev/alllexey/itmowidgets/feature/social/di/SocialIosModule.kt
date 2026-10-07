package dev.alllexey.itmowidgets.feature.social.di

import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.friends.FriendsApi
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.feature.social.data.SocialShares
import dev.alllexey.itmowidgets.feature.social.data.UnofferedTeacherReviews
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The iOS ports of [socialModule], what `:app`'s `CoreBridge`, `ReviewsBridge` and its share helpers give Android;
 * load it with that module. Core 2.0's friendships area comes from the one `BackendClient`; the teacher reviews the
 * profile reads are [UnofferedTeacherReviews] until IO-09f loads `reviewsModule`; [SocialShares] sends the profile
 * links and the invitation with [inviteUrl]. The rest (the Backend gate, the users API, MyItmoApi, the application
 * scope, the share links, `DemoMode`, the current user, the services opt-in) comes from the core, account and
 * settings modules.
 */
fun socialIosModule(inviteUrl: String): Module = module {
    single<FriendsApi> { get<BackendClient>().friends }
    single<TeacherReviewsRepository> { UnofferedTeacherReviews }
    single { SocialShares(get(), get(), inviteUrl) }
}
