package dev.alllexey.itmowidgets.feature.social.reference

import android.os.Bundle
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.SavedStateHandle
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
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeSocialRepository
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.ReferenceHostActivity
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.feature.social.presentation.UserFriendsViewModel
import dev.alllexey.itmowidgets.feature.social.ui.UserFriendsFragment
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Another user's friends under the names of LC-3a's `UserFriendsScreen` previews, in
 * `shared/feature-social/screenshots/`: the Fragment over a [UserFriendsViewModel] on the answers
 * `SettingsNavigationTestActivity`'s `friendsResult` gives (a list, an empty list, 403), for a person with a long name.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class UserFriendsReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    private val references = XmlReferenceCapture(shots, module = "feature-social")

    @Test
    fun content() = userFriends("UserFriendsScreen_content", AppResult.Success(FRIENDS), rows = FRIENDS.size)

    @Test
    fun empty() = userFriends(
        "UserFriendsScreen_empty",
        AppResult.Success(emptyList()),
        title = R.string.user_friends_empty_title,
    )

    /** The owner does not share their friends with the viewer: Backend answers 403. */
    @Test
    fun denied() = userFriends(
        "UserFriendsScreen_denied",
        AppResult.Failure(AppError.Forbidden),
        title = R.string.user_friends_hidden_title,
    )

    /** Waits for [rows] list rows (the list diffs off the main thread) or, with none, for the state titled [title]. */
    private fun userFriends(
        preview: String,
        answer: AppResult<List<UserProfile>>,
        rows: Int = 0,
        @StringRes title: Int = 0,
    ) = references.host(
        preview,
        ReferenceHostActivity::class.java,
        appearance = {},
        ready = ready@{ host ->
            val shown = host.supportFragmentManager.fragments.filterIsInstance<UserFriendsFragment>().firstOrNull()
                ?: UserFriendsFragment().also { fragment ->
                    fragment.arguments = bundleOf(UserScreenArgs.ISU to OWNER_ISU, UserScreenArgs.NAME to OWNER_NAME)
                    host.supportFragmentManager.preset(fragment) {
                        UserFriendsViewModel(
                            SavedStateHandle(mapOf(UserScreenArgs.ISU to OWNER_ISU, UserScreenArgs.NAME to OWNER_NAME)),
                            FakeSocialRepository().apply { userFriendsResult = answer },
                        )
                    }
                    host.show(fragment)
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
        const val OWNER_ISU = 100001
        const val OWNER_NAME = "Александра Константиновна Константинопольская"

        val FRIENDS = listOf(
            friend(200001, "Преображенская Евгения Владиславовна", RelationshipState.FRIENDS),
            friend(200002, "Соколов Артём Игоревич", RelationshipState.NONE),
            friend(200003, "Григорьев Евгений Владиславович", RelationshipState.OUTGOING),
            friend(200004, "Иванова Дарья Сергеевна", RelationshipState.INCOMING),
        )

        fun friend(isu: Int, name: String, relationship: RelationshipState) = UserProfile(
            UserSummary(isu, name, null, listOf(UserGroup("M3234", 2, "ФИТиП")), UserSharing(true, true, true)),
            relationship,
        )
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
