package dev.alllexey.itmowidgets.feature.resources

import android.content.Intent
import android.os.Looper
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.app.SubjectLinksPreviewActivity
import dev.alllexey.itmowidgets.core.debug.MemorySubjectLinksRepository
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEditorViewModel
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksViewModel
import dev.alllexey.itmowidgets.feature.resources.reference.SubjectLinksReferenceFixtures.SCOPE
import dev.alllexey.itmowidgets.feature.resources.reference.SubjectLinksReferenceFixtures.fixture
import dev.alllexey.itmowidgets.feature.resources.ui.LinkActionsBottomSheet
import dev.alllexey.itmowidgets.feature.resources.ui.LinkEditorBottomSheet
import dev.alllexey.itmowidgets.feature.resources.ui.ReportLinkDialogFragment
import dev.alllexey.itmowidgets.feature.resources.ui.SubjectLinksBottomSheet
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Each of the four links hosts obtains its ViewModel from Koin: the instance behind the host's `viewModel` property is
 * the one Koin keeps for the Fragment, and it reads the repository [SubjectLinksPreviewActivity] hands Koin through
 * `ResourcesDebugFixtures`, which no Hilt factory could reach.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class ResourcesHostsKoinTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val stopKoin = StopKoinRule()

    private val repository = MemorySubjectLinksRepository().apply {
        servicesEnabled = true
        snapshots.value = mapOf(SCOPE.key to fixture())
    }

    @Before
    fun setUp() {
        SubjectLinksPreviewActivity.repository = repository
    }

    @After
    fun tearDown() {
        SubjectLinksPreviewActivity.repository = MemorySubjectLinksRepository()
    }

    @Test
    fun `the links sheet renders the Koin view model over the fixture`() {
        val activity = launch(SubjectLinksPreviewActivity.SCREEN_LINKS)
        val model = activity.host<SubjectLinksViewModel>(SubjectLinksBottomSheet.TAG)

        assertEquals(fixture(), model.uiState.value.content)
    }

    @Test
    fun `the link editor renders the Koin view model with the edited link`() {
        val activity = launch(SubjectLinksPreviewActivity.SCREEN_EDITOR, linkId = "own-scores")
        val model = activity.host<LinkEditorViewModel>(LinkEditorBottomSheet.TAG)

        assertTrue(model.uiState.value.editing)
        assertEquals("https://docs.google.com/spreadsheets/d/own", model.uiState.value.url)
    }

    @Test
    fun `the actions sheet and the report dialog each keep their own Koin view model`() {
        val activity = launch(SubjectLinksPreviewActivity.SCREEN_ACTIONS, linkId = "others-sheet")
        ReportLinkDialogFragment.newInstance(SubjectLinksPreviewActivity.ARGS, "others-sheet")
            .show(activity.supportFragmentManager, ReportLinkDialogFragment.TAG)
        shadowOf(Looper.getMainLooper()).idle()

        val actions = activity.host<SubjectLinksViewModel>(LinkActionsBottomSheet.TAG)
        val report = activity.host<SubjectLinksViewModel>(ReportLinkDialogFragment.TAG)

        assertEquals(fixture(), actions.uiState.value.content)
        assertEquals(fixture(), report.uiState.value.content)
        assertNotSame(actions, report)
    }

    private fun launch(screen: String, linkId: String? = null): SubjectLinksPreviewActivity {
        val intent = Intent(ApplicationProvider.getApplicationContext(), SubjectLinksPreviewActivity::class.java)
            .putExtra(SubjectLinksPreviewActivity.EXTRA_SCREEN, screen)
            .putExtra(SubjectLinksPreviewActivity.EXTRA_LINK_ID, linkId)
        val activity = Robolectric.buildActivity(SubjectLinksPreviewActivity::class.java, intent).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return activity
    }

    /** The host's own `viewModel` property, checked to be the instance Koin keeps for that Fragment. */
    private inline fun <reified VM : ViewModel> SubjectLinksPreviewActivity.host(tag: String): VM {
        val fragment = checkNotNull(supportFragmentManager.findFragmentByTag(tag)) { "no $tag" }
        val property = fragment.viewModelProperty() as VM
        assertSame(fragment.getViewModel<VM>(), property)
        return property
    }

    private fun Fragment.viewModelProperty(): Any? {
        val delegate = javaClass.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }.get(this)
        return (delegate as Lazy<*>).value
    }
}
