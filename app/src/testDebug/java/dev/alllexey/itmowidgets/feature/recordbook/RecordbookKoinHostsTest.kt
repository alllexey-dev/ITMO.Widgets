package dev.alllexey.itmowidgets.feature.recordbook

import android.os.Looper
import androidx.fragment.app.Fragment
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.core.navigation.toBundle
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSessionRepository
import dev.alllexey.itmowidgets.feature.recordbook.presentation.BarsLoginViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresViewModel
import dev.alllexey.itmowidgets.feature.recordbook.ui.BarsLoginActivity
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPreviewActivity
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPreviewFixtures
import dev.alllexey.itmowidgets.feature.recordbook.ui.sheets.SheetScoresBottomSheet
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The four recordbook hosts obtain their ViewModels from Koin (LR-2a): the list, the subject page and the «Мои баллы»
 * sheet in the debug host, which overrides the bridged data through `RecordbookDebugFixtures`, and the BARS sign-in
 * on the release graph with a fake session port. Hilt can no longer build these ViewModels, so a host that resolves
 * one proves the Koin path; the arguments reaching the handle prove `CreationExtras` carry them.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class RecordbookKoinHostsTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val stopKoin = StopKoinRule()

    @Before
    fun setUp() {
        RecordbookPreviewFixtures.reset()
    }

    @After
    fun tearDown() {
        RecordbookPreviewFixtures.reset()
    }

    @Test
    fun `the list, the subject page and the sheet obtain their ViewModels from Koin`() {
        RecordbookPreviewFixtures.install(RecordbookPreviewFixtures.Phase.MIDDLE)
        val controller = Robolectric.buildActivity(RecordbookPreviewActivity::class.java).setup()
        try {
            val activity = controller.get()
            val list = activity.fragment(RecordbookPreviewActivity.ROOT_TAG)
            val listModel = list.getViewModel<RecordbookViewModel>()
            settle { listModel.uiState.value is RecordbookUiState.Content }
            assertSame(listModel, list.getViewModel<RecordbookViewModel>())

            activity.openScreen(AppScreen.RECORDBOOK_SUBJECT, RecordbookSubjectArgs(2, 1, 2, "2025/2026").toBundle())
            settle { activity.supportFragmentManager.findFragmentByTag(SUBJECT_TAG) != null }
            val page = activity.fragment(SUBJECT_TAG)
            assertSame(page.getViewModel<RecordbookSubjectViewModel>(), page.getViewModel<RecordbookSubjectViewModel>())

            // The connect step stays open on the fixture's failed inspection; the total step closes without a connection.
            val args = SheetScoresArgs(2, "Алгоритмы", "2025-2", RecordbookPreviewFixtures.SHEET_URL, SheetScoresArgs.Step.CONNECT)
            activity.openSheetScores(args)
            settle { activity.supportFragmentManager.findFragmentByTag(SheetScoresBottomSheet.TAG) != null }
            val sheet = activity.fragment(SheetScoresBottomSheet.TAG).getViewModel<SheetScoresViewModel>()
            assertEquals(SheetScoresArgs.Step.CONNECT, sheet.step)
            assertEquals(args.subjectId, sheet.scope.subjectId)
            assertEquals(args.periodKey, sheet.scope.periodKey)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `the BARS sign-in obtains its ViewModel from Koin`() {
        val koin = KoinStarter.ensureStarted(ApplicationProvider.getApplicationContext())
        koin.loadModules(listOf(module { single<BarsSessionRepository> { FakeSession } }), allowOverride = true)
        val controller = Robolectric.buildActivity(BarsLoginActivity::class.java).setup()
        try {
            val model = controller.get().getViewModel<BarsLoginViewModel>()
            assertTrue(model.loginUrl, model.loginUrl.startsWith("https://bars.example/login?state="))
            assertSame(model, controller.get().getViewModel<BarsLoginViewModel>())
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun RecordbookPreviewActivity.fragment(tag: String): Fragment =
        checkNotNull(supportFragmentManager.findFragmentByTag(tag)) { "no fragment $tag" }

    private fun settle(ready: () -> Boolean) {
        val looper = shadowOf(Looper.getMainLooper())
        val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
        looper.idle()
        while (!ready()) {
            check(System.nanoTime() < deadline) { "The host was not ready in time" }
            looper.idleFor(Duration.ofMillis(20))
            Thread.sleep(20)
        }
        looper.idle()
    }

    private object FakeSession : BarsSessionRepository {
        override fun loginUrl(state: String) = "https://bars.example/login?state=$state"
        override fun isCallback(url: String) = false
        override suspend fun completeLogin(callbackUrl: String, expectedState: String): AppResult<Unit> =
            AppResult.Success(Unit)
    }

    private companion object {
        /** The tag the debug host gives the subject page. */
        const val SUBJECT_TAG = "detail"
    }
}
