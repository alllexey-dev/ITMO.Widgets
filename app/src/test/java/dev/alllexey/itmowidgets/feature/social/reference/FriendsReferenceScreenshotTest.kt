package dev.alllexey.itmowidgets.feature.social.reference

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.testing.FakeSocialRepository
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.ReferenceHostActivity
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsViewModel
import dev.alllexey.itmowidgets.feature.social.ui.FriendsFragment
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Today's friends screen under the names of LC-3b's `FriendsScreen` previews, in `shared/feature-social/screenshots/`.
 * The screen has no debug host (only the nav graph), so the Fragment gets a [FriendsViewModel] over an in-memory
 * [FakeSocialRepository] before Hilt could create one.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class FriendsReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    private val references = XmlReferenceCapture(shots, module = "feature-social")

    @Test
    fun content() = friends("FriendsScreen_content", rows = FRIENDS.size) {
        friends.value = LoadState.Content(FRIENDS.map { profile(it, RelationshipState.FRIENDS) })
        requests.value = LoadState.Content(FriendRequests(listOf(profile(INCOMING[0], RelationshipState.INCOMING)), emptyList()))
    }

    @Test
    fun requests() = friends("FriendsScreen_requests", tab = REQUESTS_TAB, rows = INCOMING.size + OUTGOING.size + 2) {
        friends.value = LoadState.Content(FRIENDS.map { profile(it, RelationshipState.FRIENDS) })
        requests.value = LoadState.Content(
            FriendRequests(
                incoming = INCOMING.map { profile(it, RelationshipState.INCOMING) },
                outgoing = OUTGOING.map { profile(it, RelationshipState.OUTGOING) },
            ),
        )
    }

    @Test
    fun empty() = friends("FriendsScreen_empty") {
        friends.value = LoadState.Content(emptyList())
        requests.value = LoadState.Content(FriendRequests.EMPTY)
    }

    @Test
    fun disabled() = friends("FriendsScreen_disabled") {
        friends.value = LoadState.Disabled
        requests.value = LoadState.Disabled
    }

    /**
     * Shows the screen once per launch over [social], selects [tab] and waits for its [rows] (headers included; the
     * list diffs off the main thread, so a count tells the tab's rows from the previous tab's) or, with none, its state.
     */
    private fun friends(
        preview: String,
        tab: Int = 0,
        rows: Int = 0,
        social: FakeSocialRepository.() -> Unit,
    ) = references.host(
        preview,
        ReferenceHostActivity::class.java,
        appearance = {},
        ready = ready@{ host ->
            val shown = host.supportFragmentManager.fragments.filterIsInstance<FriendsFragment>().firstOrNull()
                ?: FriendsFragment().also { fragment ->
                    host.supportFragmentManager.preset(fragment) {
                        FriendsViewModel(FakeSocialRepository().apply(social))
                    }
                    host.show(fragment)
                }
            val view = shown.view ?: return@ready false
            val tabs = view.findViewById<TabLayout>(R.id.tabs)
            if (tabs.isShown && tabs.selectedTabPosition != tab) {
                tabs.getTabAt(tab)?.select()
                return@ready false
            }
            if (rows == 0) return@ready view.findViewById<View>(R.id.state_container).isShown
            val list = view.findViewById<RecyclerView>(R.id.recycler_view)
            list.isShown && list.adapter?.itemCount == rows && list.childCount > 0 && !list.hasPendingAdapterUpdates() &&
                !list.isAnimating
        },
        view = { it.content },
    )

    private fun profile(name: Named, relationship: RelationshipState) = UserProfile(
        UserSummary(name.isu, name.name, null, listOf(UserGroup(name.group, 2, "ФИТиП")), UserSharing(true, true, true)),
        relationship,
    )

    private class Named(val isu: Int, val name: String, val group: String)

    private companion object {
        const val REQUESTS_TAB = 1

        val FRIENDS = listOf(
            Named(200001, "Александра Константиновна Константинопольская-Преображенская", "M3205"),
            Named(200002, "Соколов Артём Игоревич", "M3234"),
            Named(200003, "Иванова Дарья Сергеевна", "M3205"),
        )
        val INCOMING = listOf(
            Named(200004, "Григорьев Евгений Владиславович", "R3135"),
            Named(200005, "Преображенская Евгения Владиславовна", "M3207"),
        )
        val OUTGOING = listOf(Named(200006, "Захаров Борис Константинович", "P3110"))
    }
}

/** Hands [fragment] the view model [create] makes before Hilt could create one. */
private fun FragmentManager.preset(fragment: Fragment, create: () -> ViewModel) =
    registerFragmentLifecycleCallbacks(
        object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentPreCreated(fm: FragmentManager, f: Fragment, savedInstanceState: Bundle?) {
                if (f !== fragment) return
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
