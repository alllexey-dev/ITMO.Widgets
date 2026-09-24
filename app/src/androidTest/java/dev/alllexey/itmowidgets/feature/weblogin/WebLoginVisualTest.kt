package dev.alllexey.itmowidgets.feature.weblogin

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.IndeterminateDrawable
import com.google.android.material.textfield.TextInputLayout
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.SettingsNavigationTestActivity
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.weblogin.ui.WebLoginBottomSheet
import dev.alllexey.itmowidgets.feature.weblogin.ui.WebLoginPreviewActivity
import dev.alllexey.itmowidgets.feature.weblogin.ui.WebLoginPreviewActivity.Companion.SYNTHETIC_PREVIEW
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.Appearances.toWebLogin
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import dev.alllexey.itmowidgets.testing.ViewChecks.assertTextFits
import dev.alllexey.itmowidgets.testing.ViewChecks.assertTouchTargets
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebLoginVisualTest {

    @Test fun codeIsCheckedThenApprovedThenDone() {
        Appearances.default.forEachIndexed { index, spec ->
            val repository = WebLoginPreviewActivity.FixtureRepository().apply { previews[CODE] = AppResult.Success(SYNTHETIC_PREVIEW) }
            withPreview(spec.toWebLogin(), repository) { scenario ->
                settle()
                scenario.onActivity { activity ->
                    val sheet = sheet(activity)
                    assertShown(sheet, R.id.input_group)
                    assertFalse(sheet.findViewById<View>(R.id.continue_button).isEnabled)
                    assertTextFits(sheet)
                    assertTouchTargets(sheet.findViewById(R.id.input_group))
                }
                screenshot("input-$index")

                val gate = CompletableDeferred<Unit>().also { repository.gate = it }
                scenario.onActivity { activity ->
                    val sheet = sheet(activity)
                    sheet.findViewById<TextView>(R.id.code).text = "abcd-2345"
                    sheet.findViewById<View>(R.id.continue_button).performClick()
                }
                settle()
                scenario.onActivity { activity ->
                    val sheet = sheet(activity)
                    val button = sheet.findViewById<MaterialButton>(R.id.continue_button)
                    assertTrue(button.icon is IndeterminateDrawable<*>)
                    assertFalse(button.isEnabled)
                    assertFalse(sheet.findViewById<View>(R.id.code).isEnabled)
                    assertEquals("ABCD2345", sheet.findViewById<TextView>(R.id.code).text.toString())
                }
                screenshot("checking-$index")
                repository.gate = null
                gate.complete(Unit)

                TestUi.eventually(idleBetween = true) {
                    scenario.onActivity { assertShown(sheet(it), R.id.confirm_group) }
                }
                settle()
                scenario.onActivity { activity ->
                    val sheet = sheet(activity)
                    assertEquals("Chrome на macOS", sheet.findViewById<TextView>(R.id.browser).text.toString())
                    assertEquals("Запрошен в 12:04", sheet.findViewById<TextView>(R.id.requested_at).text.toString())
                    assertTextFits(sheet)
                    assertTouchTargets(sheet.findViewById(R.id.confirm_group))
                }
                screenshot("confirm-$index")

                scenario.onActivity { sheet(it).findViewById<View>(R.id.approve_button).performClick() }
                TestUi.eventually(idleBetween = true) {
                    scenario.onActivity { assertShown(sheet(it), R.id.result_group) }
                }
                settle()
                scenario.onActivity { activity ->
                    val sheet = sheet(activity)
                    assertEquals(activity.getString(R.string.web_login_done), sheet.findViewById<TextView>(R.id.result_text).text.toString())
                    assertEquals(activity.getString(R.string.common_close),
                        sheet.findViewById<TextView>(R.id.result_button).text.toString())
                    assertTextFits(sheet)
                    assertTouchTargets(sheet.findViewById(R.id.result_group), requireWidth = false)
                }
                screenshot("done-$index")
                assertEquals(listOf(SYNTHETIC_PREVIEW.challengeId), repository.approved)

                scenario.onActivity { sheet(it).findViewById<View>(R.id.result_button).performClick() }
                settle()
                scenario.onActivity { assertNull(it.supportFragmentManager.findFragmentByTag(WebLoginBottomSheet.TAG)) }
            }
        }
    }

    @Test fun wrongAndExpiredCodesSayWhatHappened() {
        val repository = WebLoginPreviewActivity.FixtureRepository().apply {
            previews[CODE] = AppResult.Success(SYNTHETIC_PREVIEW)
            approval = AppResult.Failure(AppError.NotFound)
        }
        withPreview(Appearances.light.toWebLogin(), repository) { scenario ->
            settle()
            fun submit(code: String) = scenario.onActivity { activity ->
                val sheet = sheet(activity)
                sheet.findViewById<TextView>(R.id.code).text = code
                sheet.findViewById<View>(R.id.continue_button).performClick()
            }
            fun assertFieldError(text: Int) = TestUi.eventually(idleBetween = true) {
                scenario.onActivity { activity ->
                    val layout = sheet(activity).findViewById<TextInputLayout>(R.id.code_layout)
                    assertEquals(activity.getString(text), layout.error?.toString())
                }
            }

            submit("ABC")
            assertFieldError(R.string.web_login_code_invalid)
            submit("ZZZZ2345")
            assertFieldError(R.string.web_login_not_found)
            settle()
            scenario.onActivity { assertTextFits(sheet(it)) }
            screenshot("not-found")

            submit(CODE)
            TestUi.eventually(idleBetween = true) { scenario.onActivity { assertShown(sheet(it), R.id.confirm_group) } }
            scenario.onActivity { sheet(it).findViewById<View>(R.id.approve_button).performClick() }
            TestUi.eventually(idleBetween = true) { scenario.onActivity { assertShown(sheet(it), R.id.result_group) } }
            settle()
            scenario.onActivity { activity ->
                val sheet = sheet(activity)
                assertEquals(activity.getString(R.string.web_login_not_found), sheet.findViewById<TextView>(R.id.result_text).text.toString())
                assertEquals(activity.getString(R.string.common_retry), sheet.findViewById<TextView>(R.id.result_button).text.toString())
                assertTextFits(sheet)
            }
            screenshot("expired")

            scenario.onActivity { sheet(it).findViewById<View>(R.id.result_button).performClick() }
            settle()
            scenario.onActivity { activity ->
                val sheet = sheet(activity)
                assertShown(sheet, R.id.input_group)
                assertEquals("", sheet.findViewById<TextView>(R.id.code).text.toString())
            }
        }
    }

    /** Always runs, matrix or not: font scale 1.3 on a 320 dp window with the longest browser name. */
    @Test fun largeFontOnANarrowScreenKeepsEveryStepReadable() {
        listOf(Appearances.all[2], Appearances.all[3]).forEach { spec ->
            val preview = SYNTHETIC_PREVIEW.copy(userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 YaBrowser/24.7.0.0 Safari/537.36")
            val repository = WebLoginPreviewActivity.FixtureRepository().apply { previews[CODE] = AppResult.Success(preview) }
            withPreview(spec.toWebLogin(), repository) { scenario ->
                settle()
                scenario.onActivity { activity ->
                    val sheet = sheet(activity)
                    assertTextFits(sheet)
                    assertTouchTargets(sheet.findViewById(R.id.input_group))
                }
                screenshot("large-input-${spec.name}")
                scenario.onActivity { activity ->
                    val sheet = sheet(activity)
                    sheet.findViewById<TextView>(R.id.code).text = CODE
                    sheet.findViewById<View>(R.id.continue_button).performClick()
                }
                TestUi.eventually(idleBetween = true) { scenario.onActivity { assertShown(sheet(it), R.id.confirm_group) } }
                settle()
                scenario.onActivity { activity ->
                    val sheet = sheet(activity)
                    assertEquals("Яндекс Браузер на Windows", sheet.findViewById<TextView>(R.id.browser).text.toString())
                    assertTextFits(sheet)
                    assertTouchTargets(sheet.findViewById(R.id.confirm_group))
                }
                screenshot("large-confirm-${spec.name}")
            }
        }
    }

    @Test fun profileOffersWebSignInOnlyWithTheConnection() {
        try {
            for (enabled in listOf(false, true)) {
                SettingsNavigationTestActivity.profileServicesEnabled = enabled
                ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
                    scenario.onActivity { it.host.navController.navigate(R.id.navigation_me) }
                    settle()
                    scenario.onActivity { activity ->
                        val row = activity.findViewById<View>(R.id.web_login_row)
                        assertEquals(enabled, row.isShown)
                        assertEquals(enabled, activity.findViewById<View>(R.id.web_login_divider).isShown)
                        if (enabled) {
                            assertTouchTargets(row, requireWidth = false)
                            assertTextFits(row)
                            row.performClick()
                            assertEquals(1, activity.webLoginOpened.size)
                        }
                    }
                    screenshot("profile-${if (enabled) "connected" else "offline"}")
                }
            }
        } finally {
            SettingsNavigationTestActivity.profileServicesEnabled = true
        }
    }

    private fun withPreview(
        appearance: WebLoginPreviewActivity.Appearance,
        repository: WebLoginPreviewActivity.FixtureRepository,
        block: (ActivityScenario<WebLoginPreviewActivity>) -> Unit,
    ) {
        WebLoginPreviewActivity.appearance = appearance
        WebLoginPreviewActivity.repository = repository
        val intent = Intent(ApplicationProvider.getApplicationContext(), WebLoginPreviewActivity::class.java)
        try {
            ActivityScenario.launch<WebLoginPreviewActivity>(intent).use(block)
        } finally {
            WebLoginPreviewActivity.appearance = WebLoginPreviewActivity.Appearance()
            WebLoginPreviewActivity.repository = WebLoginPreviewActivity.FixtureRepository()
        }
    }

    private fun sheet(activity: WebLoginPreviewActivity): View =
        checkNotNull(activity.supportFragmentManager.findFragmentByTag(WebLoginBottomSheet.TAG)?.view)

    /** Exactly one step of the sheet is on screen. */
    private fun assertShown(sheet: View, group: Int) {
        listOf(R.id.input_group, R.id.confirm_group, R.id.result_group).forEach {
            assertEquals("Group ${sheet.resources.getResourceEntryName(it)}", it == group, sheet.findViewById<View>(it).isShown)
        }
    }

    private fun settle() = TestUi.settle(600)

    private fun screenshot(name: String) = Screenshots.capture("web-login-screenshots", name)

    private companion object {
        const val CODE = "ABCD2345"
    }
}
