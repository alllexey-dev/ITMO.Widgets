package dev.alllexey.itmowidgets.feature.friendselector.reference

import android.content.Context
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R as MaterialR
import com.google.android.material.button.MaterialButtonToggleGroup
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.FriendSelectionContract
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.social.PeopleSearchPage
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.core.social.PersonSearchResult
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.ReferenceHostActivity
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import dev.alllexey.itmowidgets.feature.friendselector.domain.FriendSelectionHistory
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorViewModel
import dev.alllexey.itmowidgets.feature.friendselector.ui.FriendSelectorDialogFragment
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Today's friend picker under the names of LC-5's `FriendSelectorSheetContent` previews, in
 * `shared/feature-social/screenshots/`: the real sheet over a [FriendSelectorViewModel] on the friends, history and
 * search of the debug `FriendSelectorFixture`, captured without the scrim. The scope and the query go through the
 * real toggle and field; the cursor is hidden, since its blink would make two runs differ.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class FriendSelectorReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    /** The Fragment's `by viewModel()` asks Koin, which the test application does not start. */
    @get:Rule
    val stopKoin = StopKoinRule()

    private val references = XmlReferenceCapture(shots, module = "feature-social")

    @Test
    fun friends() = picker("FriendSelectorSheetContent_friends", FRIENDS, rows = FRIENDS.size)

    @Test
    fun people() = picker("FriendSelectorSheetContent_people", FRIENDS, rows = PEOPLE.size, scopeAll = true, query = "Соколов")

    @Test
    fun emptyFilter() = picker(
        "FriendSelectorSheetContent_empty-filter",
        FRIENDS,
        query = "Ъъъ",
        title = R.string.friend_picker_empty_title,
    )

    /** Friends whose schedule is closed show a lock instead of the selection and have no recent chip. */
    @Test
    fun locked() = picker("FriendSelectorSheetContent_locked", LOCKED, rows = LOCKED.size)

    /**
     * Shows the sheet once per launch over [friends]; once the list loaded, switches to the wide scope if
     * [scopeAll] and types [query]. Waits for [rows] list rows or, with none, for the state titled [title].
     */
    private fun picker(
        preview: String,
        friends: List<UserSummary>,
        rows: Int = 0,
        scopeAll: Boolean = false,
        query: String? = null,
        @StringRes title: Int = 0,
    ) {
        var sheet: FriendSelectorDialogFragment? = null
        var acted = false
        references.host(
            preview,
            ReferenceHostActivity::class.java,
            appearance = {},
            ready = ready@{ host ->
                if (host.supportFragmentManager.findFragmentByTag(FriendSelectorDialogFragment.TAG) == null) {
                    val fragment = FriendSelectorDialogFragment.newInstance()
                    host.supportFragmentManager.preset(fragment) {
                        FriendSelectorViewModel(
                            Friends(friends),
                            History(friends.take(RECENT).map(UserSummary::isu)),
                            People,
                            SavedStateHandle(mapOf(FriendSelectionContract.ARG_SELECTED_ISU to FriendSelectionContract.NO_USER_ISU)),
                        )
                    }
                    fragment.show(host.supportFragmentManager, FriendSelectorDialogFragment.TAG)
                    sheet = fragment
                    acted = false
                }
                val shown = sheet ?: return@ready false
                if (shown.dialog?.isShowing != true) return@ready false
                val view = shown.view ?: return@ready false
                val scope = view.findViewById<MaterialButtonToggleGroup>(R.id.scope_toggle)
                if (!acted) {
                    if (!scope.isShown) return@ready false
                    if (scopeAll) scope.check(R.id.scope_all)
                    view.findViewById<EditText>(R.id.search_input).apply {
                        isCursorVisible = false
                        query?.let(::setText)
                    }
                    acted = true
                    return@ready false
                }
                val recent = view.findViewById<RecyclerView>(R.id.recent_recycler_view)
                if (recent.hasPendingAdapterUpdates() || recent.isAnimating) return@ready false
                if (rows == 0) {
                    val state = view.findViewById<TextView>(R.id.state_title)
                    return@ready state.isShown && state.text == host.getString(title)
                }
                val list = view.findViewById<RecyclerView>(R.id.recycler_view)
                list.isShown && list.adapter?.itemCount == rows && list.childCount > 0 &&
                    !list.hasPendingAdapterUpdates() && !list.isAnimating
            },
            view = { host ->
                val surface = checkNotNull(sheet?.dialog?.findViewById<View>(MaterialR.id.design_bottom_sheet))
                host.mirror(surface)
            },
        )
    }

    private class Friends(friends: List<UserSummary>) : FriendRepository {
        private val state = MutableStateFlow<LoadState<List<UserSummary>>>(LoadState.Content(friends))
        override fun observeFriendList() = state
        override fun observeCurrentUser() = MutableStateFlow<UserSummary?>(null)
        override suspend fun refreshFriendList() = Unit
        override val currentFriends: List<UserSummary>? get() = (state.value as? LoadState.Content)?.value
    }

    private class History(private val recent: List<Int>) : FriendSelectionHistory {
        override suspend fun getRecentIsu() = recent
        override suspend fun record(isu: Int) = Unit
    }

    /** Every query finds [PEOPLE], all registered. */
    private object People : PeopleSearchRepository {
        override suspend fun search(query: String, offset: Int): AppResult<PeopleSearchPage> = AppResult.Success(
            PeopleSearchPage(
                PEOPLE.map { PersonSearchResult(it.isu, it.name, null, UserProfile(it, RelationshipState.NONE)) },
                PEOPLE.size,
                null,
            ),
        )
    }

    private companion object {
        const val RECENT = 5

        /** The names of `FriendSelectorFixture`: long, so rows and chips show their ellipsis. */
        val FRIENDS = listOf("Александра", "Борис", "Виктория", "Григорий", "Дарья", "Евгений", "Жанна", "Захар")
            .mapIndexed { index, name -> person(100001 + index, "$name Константинович Оченьдлиннаяфамилия") }

        /** Every other friend keeps their schedule closed. */
        val LOCKED = FRIENDS.mapIndexed { index, friend ->
            if (index % 2 == 1) friend.copy(sharing = UserSharing(sport = true, schedule = false)) else friend
        }

        val PEOPLE = listOf(
            person(200011, "Соколов Артём Игоревич"),
            person(200012, "Соколова Александра Константиновна Константинопольская"),
            person(200013, "Соколов Евгений Владиславович"),
        )

        fun person(isu: Int, name: String) =
            UserSummary(isu, name, null, listOf(UserGroup("M3205", 2, "ФИТиП")), UserSharing(true, true))
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

/**
 * Shows [source], a view of a dialog window, in place of the host's content: a capture finds only views of the
 * activity's window and includes what lies under a transparent view, so the screen behind the dialog is hidden.
 */
private fun ReferenceHostActivity.mirror(source: View): View {
    for (index in 0 until container.childCount) container.getChildAt(index).visibility = View.INVISIBLE
    return WindowMirror(this, source).also(::show)
}

/** Draws [source] at its own size. */
private class WindowMirror(context: Context, private val source: View) : View(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(source.width, source.height)

    override fun onDraw(canvas: Canvas) = source.draw(canvas)
}
