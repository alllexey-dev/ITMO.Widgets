package dev.alllexey.itmowidgets.feature.social.reference

import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.social.PeopleSearchPage
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.core.social.PersonSearchResult
import dev.alllexey.itmowidgets.core.testing.FakeSocialRepository
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.ReferenceHostActivity
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import dev.alllexey.itmowidgets.feature.social.presentation.UserSearchViewModel
import dev.alllexey.itmowidgets.feature.social.ui.UserSearchFragment
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Today's people search under the names of LC-3c's `UserSearchScreen` previews, in
 * `shared/feature-social/screenshots/`. The screen has no debug host (only the nav graph), so the Fragment gets a
 * [UserSearchViewModel] over a synthetic [PeopleSearchRepository] before Koin could create one, and the query is
 * typed into the real field. The cursor is hidden: its blink would make two runs differ.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class UserSearchReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    /** The Fragment's `by viewModel()` asks Koin, which the test application does not start. */
    @get:Rule
    val stopKoin = StopKoinRule()

    private val references = XmlReferenceCapture(shots, module = "feature-social")

    @Test
    fun idle() = search("UserSearchScreen_idle", query = null, title = R.string.user_search_idle_title) {
        error("The idle screen searches nothing")
    }

    /** Registered people (no relationship, a sent request, a friend), then the others, then «load more». */
    @Test
    fun results() = search("UserSearchScreen_results", rows = REGISTERED.size + OTHERS.size + 3) {
        AppResult.Success(PeopleSearchPage(REGISTERED + OTHERS, total = 40, nextOffset = REGISTERED.size + OTHERS.size))
    }

    @Test
    fun empty() = search("UserSearchScreen_empty", title = R.string.user_search_empty_title) {
        AppResult.Success(PeopleSearchPage(emptyList(), total = 0, nextOffset = null))
    }

    @Test
    fun error() = search("UserSearchScreen_error", title = R.string.common_load_error_title) {
        AppResult.Failure(AppError.Network)
    }

    /**
     * Shows the screen once per launch, types [query] and waits for [rows] list rows (headers and «load more»
     * included; the list diffs off the main thread) or for the state titled [title].
     */
    private fun search(
        preview: String,
        query: String? = QUERY,
        rows: Int = 0,
        @StringRes title: Int = 0,
        answer: () -> AppResult<PeopleSearchPage>,
    ) = references.host(
        preview,
        ReferenceHostActivity::class.java,
        appearance = {},
        ready = ready@{ host ->
            val shown = host.supportFragmentManager.fragments.filterIsInstance<UserSearchFragment>().firstOrNull()
                ?: UserSearchFragment().also { fragment ->
                    val people = object : PeopleSearchRepository {
                        override suspend fun search(query: String, offset: Int) = answer()
                    }
                    host.supportFragmentManager.preset(fragment) { UserSearchViewModel(people, FakeSocialRepository()) }
                    host.show(fragment)
                    fragment.requireView().findViewById<EditText>(R.id.search_input).apply {
                        isCursorVisible = false
                        query?.let(::setText)
                    }
                }
            val view = shown.view ?: return@ready false
            if (rows == 0) {
                val state = view.findViewById<TextView>(R.id.state_title)
                return@ready state.isShown && state.text == host.getString(title)
            }
            val list = view.findViewById<RecyclerView>(R.id.recycler_view)
            list.isShown && list.adapter?.itemCount == rows && list.childCount > 0 && !list.hasPendingAdapterUpdates() &&
                !list.isAnimating
        },
        view = { it.content },
    )

    private companion object {
        const val QUERY = "Соколов"

        val REGISTERED = listOf(
            registered(200011, "Соколов Артём Игоревич", RelationshipState.NONE),
            registered(200012, "Соколова Александра Константиновна Константинопольская", RelationshipState.OUTGOING),
            registered(200013, "Соколов Евгений Владиславович", RelationshipState.FRIENDS),
        )
        val OTHERS = listOf(
            PersonSearchResult(200014, "Соколов Борис Константинович", null, null),
            PersonSearchResult(200015, "Соколова Дарья Сергеевна", null, null),
        )

        fun registered(isu: Int, name: String, relationship: RelationshipState) = PersonSearchResult(
            isu,
            name,
            null,
            UserProfile(
                UserSummary(isu, name, null, listOf(UserGroup("M3205", 2, "ФИТиП")), UserSharing(true, true, true)),
                relationship,
            ),
        )
    }
}

/** Hands [fragment] the view model [create] makes before Koin could create one, under the key Koin reads. */
private fun FragmentManager.preset(fragment: Fragment, create: () -> ViewModel) =
    registerFragmentLifecycleCallbacks(
        object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentPreCreated(fm: FragmentManager, f: Fragment, savedInstanceState: Bundle?) {
                if (f !== fragment) return
                KoinStarter.ensureStarted(f.requireContext())
                val model = create()
                ViewModelProvider(
                    f,
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = model as T
                    },
                )[model.javaClass]
            }
        },
        false,
    )
