package dev.alllexey.itmowidgets.feature.resources

import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.core.debug.MemorySubjectLinksRepository
import dev.alllexey.itmowidgets.designsystem.ReferenceHostActivity
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import dev.alllexey.itmowidgets.feature.resources.LinksHostFixtures.ARGS
import dev.alllexey.itmowidgets.feature.resources.LinksHostFixtures.SCOPE
import dev.alllexey.itmowidgets.feature.resources.LinksHostFixtures.fixture
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEditorViewModel
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksViewModel
import dev.alllexey.itmowidgets.feature.resources.ui.LinkActionsBottomSheet
import dev.alllexey.itmowidgets.feature.resources.ui.LinkEditorBottomSheet
import dev.alllexey.itmowidgets.feature.resources.ui.ReportLinkDialogFragment
import dev.alllexey.itmowidgets.feature.resources.ui.SubjectLinksBottomSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
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
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * The four links hosts over Koin (LX-2b) with the Compose bodies of LX-3b to LX-3d, in a plain host activity: the
 * instance behind each host's `viewModel` property is the one Koin keeps for the Fragment, and it reads the in-memory
 * repository this test hands Koin. The actions sheet stays open after a vote, closes after a pin and once its link is
 * deleted here (after the confirmation) or elsewhere; the report dialog sends through its own view model, stays with
 * the error of a failure and keeps its form across a recreation. What the bodies draw is covered by
 * `LinkActionsSheetTest` and `ReportLinkDialogTest` of `:shared:feature-resources`; the navigation the actions start
 * (profile, sheet scores, editor, report) needs the app's navigator and is the Fragment's own code.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class ResourcesHostsKoinTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val stopKoin = StopKoinRule()

    /** Drives the Compose bodies' frames and finds their nodes, the dialogs' windows included. */
    @get:Rule(order = 2)
    val compose = createEmptyComposeRule()

    private lateinit var repository: MemorySubjectLinksRepository

    @Before
    fun setUp() {
        repository = LinksHostFixtures.install()
    }

    @Test
    fun `the links sheet renders the Koin view model over the fixture`() = withHost({ openLinks() }) { activity ->
        val model = activity.host<SubjectLinksViewModel>(SubjectLinksBottomSheet.TAG)

        assertEquals(fixture(), model.uiState.value.content)
    }

    @Test
    fun `the link editor renders the Koin view model with the edited link`() =
        withHost({ openEditor("own-scores") }) { activity ->
            val model = activity.host<LinkEditorViewModel>(LinkEditorBottomSheet.TAG)

            assertTrue(model.uiState.value.editing)
            assertEquals("https://docs.google.com/spreadsheets/d/own", model.uiState.value.url)
        }

    @Test
    fun `the actions sheet and the report dialog each keep their own Koin view model`() =
        withHost({
            openActions("others-sheet")
            openReport("others-sheet")
        }) { activity ->
            val actions = activity.host<SubjectLinksViewModel>(LinkActionsBottomSheet.TAG)
            val report = activity.host<SubjectLinksViewModel>(ReportLinkDialogFragment.TAG)

            assertEquals(fixture(), actions.uiState.value.content)
            assertEquals(fixture(), report.uiState.value.content)
            assertNotSame(actions, report)
        }

    @Test
    fun `a vote keeps the actions sheet open and a pin closes it`() = withHost({ openActions("others-sheet") }) { activity ->
        compose.onNodeWithText("Баллы по таблице преподавателя").assertIsDisplayed()

        activity.host<SubjectLinksViewModel>(LinkActionsBottomSheet.TAG).vote("others-sheet", up = false)
        idle()

        assertEquals(-1, repository.peek(SCOPE).shared.single().myVote)
        assertNotNull(activity.supportFragmentManager.findFragmentByTag(LinkActionsBottomSheet.TAG))

        compose.onNodeWithText("Закрепить").performClick()
        idle()

        assertEquals("others-sheet", repository.peek(SCOPE).pinnedId)
        assertNull(activity.supportFragmentManager.findFragmentByTag(LinkActionsBottomSheet.TAG))
    }

    @Test
    fun `deleting asks first and the deleted link closes the sheet`() = withHost({ openActions("own-scores") }) { activity ->
        compose.onNodeWithText("Удалить").performClick()
        idle()
        compose.onNodeWithText("Удалить ссылку?").assertIsDisplayed()
        assertEquals(2, repository.peek(SCOPE).mine.size)

        compose.onNode(hasText("Удалить") and hasAnyAncestor(isDialog())).performClick()
        idle()

        assertEquals(listOf("own-other"), repository.peek(SCOPE).mine.map { it.id })
        assertNull(activity.supportFragmentManager.findFragmentByTag(LinkActionsBottomSheet.TAG))
    }

    @Test
    fun `a link deleted elsewhere closes the actions sheet`() = withHost({ openActions("own-other") }) { activity ->
        assertNotNull(activity.supportFragmentManager.findFragmentByTag(LinkActionsBottomSheet.TAG))

        repository.snapshots.value = mapOf(SCOPE.key to fixture().let { it.copy(mine = it.mine.take(1)) })
        idle()

        assertNull(activity.supportFragmentManager.findFragmentByTag(LinkActionsBottomSheet.TAG))
    }

    @Test
    fun `the report dialog sends through its view model and closes once accepted`() =
        withHost({ openReport("others-sheet") }) { activity ->
            compose.onNodeWithText("Жалоба на ссылку").assertIsDisplayed()

            compose.onNodeWithText("Не открывается").performClick()
            compose.onNodeWithText("Отправить").performClick()
            idle()

            assertTrue(repository.peek(SCOPE).shared.single().reportedByMe)
            assertNull(activity.supportFragmentManager.findFragmentByTag(ReportLinkDialogFragment.TAG))
        }

    @Test
    fun `a failed report keeps the dialog with its error`() = withHost({ openReport("others-sheet") }) { activity ->
        val fragment = activity.supportFragmentManager.findFragmentByTag(ReportLinkDialogFragment.TAG)
        repository.servicesEnabled = false

        compose.onNodeWithText("Спам").performClick()
        compose.onNodeWithText("Отправить").performClick()
        idle()

        assertSame(fragment, activity.supportFragmentManager.findFragmentByTag(ReportLinkDialogFragment.TAG))
        compose.onNodeWithText("Чтобы смотреть расписание друзей, подключитесь к ITMO.Widgets").assertIsDisplayed()
        compose.onNodeWithText("Жалоба на ссылку").assertIsDisplayed()
    }

    @Test
    fun `the report dialog keeps the chosen reason and comment across a recreation`() {
        val controller = launch { openReport("others-sheet") }
        try {
            compose.onNodeWithText("Другой предмет").performClick()
            compose.onNode(hasSetTextAction()).performTextInput(COMMENT)
            idle()

            controller.recreate()
            idle()

            compose.onNodeWithText("Другой предмет").assertIsSelected()
            compose.onNodeWithText(COMMENT).assertIsDisplayed()
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun withHost(open: ReferenceHostActivity.() -> Unit, block: (ReferenceHostActivity) -> Unit) {
        val controller = launch(open)
        try {
            block(controller.get())
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun launch(open: ReferenceHostActivity.() -> Unit): ActivityController<ReferenceHostActivity> {
        val controller = Robolectric.buildActivity(ReferenceHostActivity::class.java).setup()
        controller.get().open()
        idle()
        return controller
    }

    private fun ReferenceHostActivity.openLinks() =
        SubjectLinksBottomSheet.newInstance(ARGS).show(supportFragmentManager, SubjectLinksBottomSheet.TAG)

    private fun ReferenceHostActivity.openEditor(linkId: String) =
        LinkEditorBottomSheet.newInstance(ARGS, linkId).show(supportFragmentManager, LinkEditorBottomSheet.TAG)

    private fun ReferenceHostActivity.openActions(linkId: String) =
        LinkActionsBottomSheet.newInstance(ARGS, linkId).show(supportFragmentManager, LinkActionsBottomSheet.TAG)

    private fun ReferenceHostActivity.openReport(linkId: String) =
        ReportLinkDialogFragment.newInstance(ARGS, linkId).show(supportFragmentManager, ReportLinkDialogFragment.TAG)

    /** The host's own `viewModel` property, checked to be the instance Koin keeps for that Fragment. */
    private inline fun <reified VM : ViewModel> ReferenceHostActivity.host(tag: String): VM {
        val fragment = checkNotNull(supportFragmentManager.findFragmentByTag(tag)) { "no $tag" }
        val property = fragment.viewModelProperty() as VM
        assertSame(fragment.getViewModel<VM>(), property)
        return property
    }

    private fun Fragment.viewModelProperty(): Any? {
        val delegate = javaClass.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }.get(this)
        return (delegate as Lazy<*>).value
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    private companion object {
        const val COMMENT = "Ссылка на курс другого семестра"
    }
}
