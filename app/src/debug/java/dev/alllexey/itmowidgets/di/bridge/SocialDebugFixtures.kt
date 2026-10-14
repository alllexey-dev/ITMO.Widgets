package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dev.alllexey.itmowidgets.core.demo.DemoCurrentUserProvider
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.di.KoinStarter
import dev.alllexey.itmowidgets.feature.reviews.data.TeacherReviewsRepositoryImpl
import dev.alllexey.itmowidgets.feature.social.data.FriendRepositoryImpl
import dev.alllexey.itmowidgets.feature.social.data.PeopleSearchRepositoryImpl
import dev.alllexey.itmowidgets.feature.social.data.PersonRepositoryImpl
import dev.alllexey.itmowidgets.feature.social.data.SocialRepositoryImpl
import dev.alllexey.itmowidgets.feature.social.domain.PersonRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The social screens' and the friend picker's fixture in Koin: a debug host replaces the repositories its screens
 * read with its own while it lives, so the shell's profile, user friends and friend picker entries obtain their
 * ViewModels exactly as in release. A host passes only the fakes it has; the other types stay the
 * release ones (`socialModule` and `reviewsModule` construct the data, `sessionModule` the
 * current user).
 *
 * Koin is process-wide: a host calls [load] in `onCreate` before `super.onCreate()` and [unload] in `onDestroy`, or
 * later tests in the same process would get the fake. Main thread only, like the host callbacks.
 */
object SocialDebugFixtures {

    /**
     * Each provider is called for every new ViewModel, so it reads the fake its host holds at that moment. `null`
     * keeps the release one.
     */
    class Fakes(
        val social: (() -> SocialRepository)? = null,
        val people: (() -> PersonRepository)? = null,
        val reviews: (() -> TeacherReviewsRepository)? = null,
        val currentUser: (() -> CurrentUserProvider)? = null,
        val friends: (() -> FriendRepository)? = null,
        val search: (() -> PeopleSearchRepository)? = null,
    )

    private var current: Module? = null
    private var currentFakes: Fakes? = null

    /** Overrides the types [fakes] provides; returns the handle [unload] takes. */
    fun load(context: Context, fakes: Fakes): Module {
        val fixture = module {
            fakes.social?.let { fake -> factory<SocialRepository> { fake() } }
            fakes.people?.let { fake -> factory<PersonRepository> { fake() } }
            fakes.reviews?.let { fake -> factory<TeacherReviewsRepository> { fake() } }
            fakes.currentUser?.let { fake -> factory<CurrentUserProvider> { fake() } }
            fakes.friends?.let { fake -> factory<FriendRepository> { fake() } }
            fakes.search?.let { fake -> factory<PeopleSearchRepository> { fake() } }
        }
        KoinStarter.ensureStarted(context).loadModules(listOf(fixture), allowOverride = true)
        current = fixture
        currentFakes = fakes
        return fixture
    }

    /**
     * Restores the release bindings. Unloading a Koin module drops its keys instead of bringing back what it
     * overrode. Each overridden social or picker port points back at the single its module already holds:
     * reloading `socialModule` would build a second repository beside the one the home
     * card and the cleaners share, and so does the reviews contract (`reviewsModule`) and the current user
     * (`sessionModule`, which also holds the session's other singles). A fixture that a newer host already replaced is
     * left to that host.
     */
    fun unload(context: Context, fixture: Module) {
        if (current !== fixture) return
        val fakes = checkNotNull(currentFakes)
        val koin = KoinStarter.ensureStarted(context)
        koin.unloadModules(listOf(fixture))
        fakes.social?.let { koin.declare<SocialRepository>(koin.get<SocialRepositoryImpl>(), allowOverride = true) }
        fakes.people?.let { koin.declare<PersonRepository>(koin.get<PersonRepositoryImpl>(), allowOverride = true) }
        fakes.friends?.let { koin.declare<FriendRepository>(koin.get<FriendRepositoryImpl>(), allowOverride = true) }
        fakes.search?.let {
            koin.declare<PeopleSearchRepository>(koin.get<PeopleSearchRepositoryImpl>(), allowOverride = true)
        }
        fakes.reviews?.let {
            koin.declare<TeacherReviewsRepository>(koin.get<TeacherReviewsRepositoryImpl>(), allowOverride = true)
        }
        koin.declare<CurrentUserProvider>(koin.get<DemoCurrentUserProvider>(), allowOverride = true)
        current = null
        currentFakes = null
    }
}
