package dev.alllexey.itmowidgets.feature.me.reference

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FakeSessionRepository
import dev.alllexey.itmowidgets.core.testing.FakeSocialRepository
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.ReferenceHostActivity
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.feature.me.presentation.MeViewModel
import dev.alllexey.itmowidgets.feature.me.ui.MeFragment
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Today's Me tab under the names of LA-3's `MeScreen` previews, in `shared/feature-account/screenshots/`. The
 * Fragment gets a [MeViewModel] over in-memory session, social and opt-in fixtures, the states of
 * `SettingsNavigationTestActivity`'s profile plus the friends summary it cannot reach (loading, error).
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class MeReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    private val references = XmlReferenceCapture(shots, module = "feature-account")

    @Test
    fun groupAndRequests() = me("MeScreen_group-requests") {
        friends.value = LoadState.Content(listOf(friend(200001), friend(200002), friend(200003)))
        requests.value = LoadState.Content(FriendRequests(listOf(friend(200004), friend(200005)), emptyList()))
        currentUser.value = summary(ISU, NAME)
    }

    @Test
    fun servicesOff() = me("MeScreen_services-off", servicesEnabled = false) {
        friends.value = LoadState.Disabled
        requests.value = LoadState.Disabled
    }

    @Test
    fun friendsLoading() = me("MeScreen_friends-loading") {
        friends.value = LoadState.Loading
        requests.value = LoadState.Loading
    }

    @Test
    fun friendsError() = me("MeScreen_friends-error") {
        friends.value = LoadState.Error(AppError.Network)
        requests.value = LoadState.Error(AppError.Network)
    }

    @Test
    fun longName() = me("MeScreen_long-name", user = CurrentUser(ISU, LONG_NAME, null)) {
        friends.value = LoadState.Content(emptyList())
        requests.value = LoadState.Content(FriendRequests.EMPTY)
        currentUser.value = summary(ISU, LONG_NAME)
    }

    private fun me(
        preview: String,
        user: CurrentUser = CurrentUser(ISU, NAME, null),
        servicesEnabled: Boolean = true,
        social: FakeSocialRepository.() -> Unit,
    ) = references.host(
        preview,
        ReferenceHostActivity::class.java,
        appearance = {},
        ready = { host ->
            host.supportFragmentManager.fragments.any { it is MeFragment } || run {
                val fragment = MeFragment()
                host.supportFragmentManager.preset(fragment) {
                    MeViewModel(
                        FakeSessionRepository(SessionState.SignedIn(user)),
                        FakeSocialRepository().apply(social),
                        FakeCustomServicesRepository(servicesEnabled),
                    )
                }
                host.show(fragment)
                true
            }
        },
        view = { it.content },
    )

    private fun friend(isu: Int) = UserProfile(summary(isu, "Пользователь $isu"), RelationshipState.FRIENDS)

    private fun summary(isu: Int, name: String) =
        UserSummary(isu, name, null, listOf(UserGroup("M3205", 2, "ФИТиП")), UserSharing(true, true, true))

    private companion object {
        const val ISU = 123456
        const val NAME = "Александрова Мария Александровна"
        const val LONG_NAME = "Александра Константиновна Константинопольская-Преображенская"
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
