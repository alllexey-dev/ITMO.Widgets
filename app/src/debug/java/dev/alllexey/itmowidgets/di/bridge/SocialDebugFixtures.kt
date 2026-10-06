package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.feature.friendselector.domain.FriendSelectionHistory
import dev.alllexey.itmowidgets.feature.social.domain.PersonRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The social screens' and the friend picker's fixture in Koin: a debug host replaces the repositories its screens
 * read with its own while it lives, so `UserProfileFragment`, `UserFriendsFragment` and `FriendSelectorDialogFragment`
 * obtain their ViewModels exactly as in release. A host passes only the fakes it has; the other types stay bridged.
 *
 * Koin is process-wide: a host calls [load] in `onCreate` before `super.onCreate()` and [unload] in `onDestroy`, or
 * later tests in the same process would get the fake. Main thread only, like the host callbacks.
 */
object SocialDebugFixtures {

    /**
     * Each provider is called for every new ViewModel, so it reads the host's fixture at that moment (a test swaps
     * `SettingsNavigationTestActivity.friendSelectorFixture` before it opens the picker). `null` keeps the release one.
     */
    class Fakes(
        val social: (() -> SocialRepository)? = null,
        val people: (() -> PersonRepository)? = null,
        val reviews: (() -> TeacherReviewsRepository)? = null,
        val currentUser: (() -> CurrentUserProvider)? = null,
        val friends: (() -> FriendRepository)? = null,
        val history: (() -> FriendSelectionHistory)? = null,
        val search: (() -> PeopleSearchRepository)? = null,
    )

    private var current: Module? = null

    /** Overrides the types [fakes] provides; returns the handle [unload] takes. */
    fun load(context: Context, fakes: Fakes): Module {
        val fixture = module {
            fakes.social?.let { fake -> factory<SocialRepository> { fake() } }
            fakes.people?.let { fake -> factory<PersonRepository> { fake() } }
            fakes.reviews?.let { fake -> factory<TeacherReviewsRepository> { fake() } }
            fakes.currentUser?.let { fake -> factory<CurrentUserProvider> { fake() } }
            fakes.friends?.let { fake -> factory<FriendRepository> { fake() } }
            fakes.history?.let { fake -> factory<FriendSelectionHistory> { fake() } }
            fakes.search?.let { fake -> factory<PeopleSearchRepository> { fake() } }
        }
        KoinStarter.ensureStarted(context).loadModules(listOf(fixture), allowOverride = true)
        current = fixture
        return fixture
    }

    /**
     * Restores the release bindings. Unloading a Koin module drops its keys instead of bringing back what it
     * overrode, so the social and reviews bridge modules load again: their lazy singles forward Hilt's instances on
     * first use, and the real data graph is not built on the host's main thread. The current user, a light object,
     * is declared directly instead of reloading `coreBridgeModule`, which also defines the wall clock that the QR and
     * home fixtures of the same host may still override. A fixture that a newer host already replaced is left to
     * that host.
     */
    fun unload(context: Context, fixture: Module) {
        if (current !== fixture) return
        val koin = KoinStarter.ensureStarted(context)
        koin.unloadModules(listOf(fixture))
        koin.loadModules(listOf(socialBridgeModule, reviewsBridgeModule), allowOverride = true)
        koin.declare<CurrentUserProvider>(CoreBridgeEntryPoint.from(context).currentUserProvider(), allowOverride = true)
        current = null
    }
}
