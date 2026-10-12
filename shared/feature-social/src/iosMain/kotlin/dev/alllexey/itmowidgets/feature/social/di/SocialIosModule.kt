package dev.alllexey.itmowidgets.feature.social.di

import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.friends.FriendsApi
import dev.alllexey.itmowidgets.feature.social.data.SocialShares
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The iOS bindings of [socialModule], what the platform's Koin modules and share helpers give Android;
 * load it with that module. Core 2.0's friendships area comes from the one `BackendClient`; the teacher reviews the
 * profile reads are `reviewsModule`'s; [SocialShares] sends the profile links and the invitation with
 * [inviteUrl]. The rest (the Backend gate, the users API, MyItmoApi, the application
 * scope, the share links, `DemoMode`, the current user, the services opt-in) comes from the core, account and
 * settings modules; the text resolver from `iosBackgroundModule`.
 */
fun socialIosModule(inviteUrl: String): Module = module {
    single<FriendsApi> { get<BackendClient>().friends }
    single { SocialShares(get(), get(), inviteUrl, get()) }
}
