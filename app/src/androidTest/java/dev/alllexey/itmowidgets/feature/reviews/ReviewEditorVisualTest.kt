package dev.alllexey.itmowidgets.feature.reviews

import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import androidx.activity.ComponentDialog
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputLayout
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.debug.PreviewAppearance
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.OwnReviewStatus
import dev.alllexey.itmowidgets.core.reviews.OwnTeacherReview
import dev.alllexey.itmowidgets.core.reviews.ReviewDate
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewDraft
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.schedule.TeacherLessons
import dev.alllexey.itmowidgets.feature.reviews.ui.ReportReviewDialogFragment
import dev.alllexey.itmowidgets.feature.reviews.ui.ReviewEditorBottomSheet
import dev.alllexey.itmowidgets.feature.reviews.ui.ReviewEditorPreviewActivity
import dev.alllexey.itmowidgets.feature.reviews.ui.ReviewEditorPreviewActivity.Companion.REVIEW_ID
import dev.alllexey.itmowidgets.feature.reviews.ui.ReviewEditorPreviewActivity.Companion.TEACHER_ISU
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.toReviewEditor
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import dev.alllexey.itmowidgets.testing.ViewChecks
import dev.alllexey.itmowidgets.testing.ViewChecks.descendants
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewEditorVisualTest {

    @Test fun newReviewIsAnonymousAndSuggestsSubjectsOnceTheHistoryAnswers() = appearances { spec ->
        editor(spec, {
            ReviewEditorPreviewActivity.lessons = AppResult.Success(TeacherLessons(FLOWS, SUBJECTS))
            // Late enough to outlast a cold launch, so the first frame is always without suggestions.
            ReviewEditorPreviewActivity.lessonsDelayMs = 3_000
        }) { scenario ->
            scenario.onActivity { activity ->
                val sheet = sheet(activity)
                assertEquals("Новый отзыв", sheet.findViewById<TextView>(R.id.title).text.toString())
                assertEquals("Константинопольская А.\u00A0К.", sheet.findViewById<TextView>(R.id.teacher).text.toString())
                assertTrue(sheet.findViewById<View>(R.id.title).isAccessibilityHeading)
                assertEquals("Закрыть", sheet.findViewById<View>(R.id.close).contentDescription)
                assertTrue(sheet.findViewById<MaterialSwitch>(R.id.anonymous).isChecked)
                assertFalse(sheet.findViewById<View>(R.id.anonymous_hint).isShown)
                assertFalse(sheet.findViewById<View>(R.id.save_button).isEnabled)
                assertEquals("Отправить", sheet.findViewById<TextView>(R.id.save_button).text.toString())
                assertFalse(sheet.findViewById<View>(R.id.suggestions_scroll).isShown)
                assertEquals("Не короче 30 символов", sheet.findViewById<TextInputLayout>(R.id.text_layout).helperText.toString())
            }
            TestUi.eventually(attempts = 150) {
                scenario.onActivity { assertEquals(SUBJECTS, chips(sheet(it)).map { chip -> chip.text.toString() }) }
            }
            frame(scenario, "editor-empty-${spec.name}")
            scenario.onActivity { chips(sheet(it))[1].performClick() }
            type(scenario, R.id.text, REVIEW_TEXT)
            scenario.onActivity { activity ->
                val sheet = sheet(activity)
                assertEquals(SUBJECTS[1], sheet.findViewById<EditText>(R.id.subject).text.toString())
                assertTrue(sheet.findViewById<View>(R.id.save_button).isEnabled)
                assertNull(sheet.findViewById<TextInputLayout>(R.id.text_layout).helperText)
            }
            frame(scenario, "editor-filled-${spec.name}")
        }
    }

    @Test fun turningAnonymityOffWarnsThatTheNameIsPublic() = appearances { spec ->
        editor(spec) { scenario ->
            scenario.onActivity { sheet(it).findViewById<MaterialSwitch>(R.id.anonymous).performClick() }
            TestUi.eventually {
                scenario.onActivity {
                    val hint = sheet(it).findViewById<TextView>(R.id.anonymous_hint)
                    assertTrue(hint.isShown)
                    assertEquals("Имя будет видно всем", hint.text.toString())
                }
            }
            frame(scenario, "editor-named-${spec.name}")
        }
    }

    @Test fun shortTextIsAnErrorOnlyOnSendAndTheCounterStopsAtTheLimit() = appearances { spec ->
        editor(spec) { scenario ->
            type(scenario, R.id.text, "а".repeat(29))
            scenario.onActivity {
                val layout = sheet(it).findViewById<TextInputLayout>(R.id.text_layout)
                assertNull(layout.error)
                assertEquals("Не короче 30 символов", layout.helperText.toString())
                assertEquals(3000, layout.counterMaxLength)
                assertTrue(layout.isCounterEnabled)
                sheet(it).findViewById<View>(R.id.save_button).performClick()
            }
            TestUi.eventually {
                scenario.onActivity { assertEquals("Не короче 30 символов", sheet(it).findViewById<TextInputLayout>(R.id.text_layout).error.toString()) }
            }
            assertTrue(ReviewEditorPreviewActivity.drafts.isEmpty())
            frame(scenario, "editor-short-${spec.name}")
        }
    }

    @Test fun editingStartsFromTheOwnReview() = appearances { spec ->
        editor(spec, { ReviewEditorPreviewActivity.reviews = reviews(own()) }) { scenario ->
            scenario.onActivity { activity ->
                val sheet = sheet(activity)
                assertEquals("Изменить отзыв", sheet.findViewById<TextView>(R.id.title).text.toString())
                assertEquals("Математический анализ", sheet.findViewById<EditText>(R.id.subject).text.toString())
                assertEquals(REVIEW_TEXT, sheet.findViewById<EditText>(R.id.text).text.toString())
                assertFalse(sheet.findViewById<MaterialSwitch>(R.id.anonymous).isChecked)
                assertTrue(sheet.findViewById<View>(R.id.anonymous_hint).isShown)
                assertEquals("Сохранить", sheet.findViewById<TextView>(R.id.save_button).text.toString())
            }
            frame(scenario, "editor-edit-${spec.name}")
        }
    }

    @Test fun backWithChangesAsksAndWithoutChangesCloses() {
        editor(Appearances.light) { scenario ->
            type(scenario, R.id.text, "Черновик")
            back(scenario)
            onView(withText("Не сохранять отзыв?")).inRoot(isDialog()).check(matches(isDisplayed()))
            Screenshots.capture(SCREENSHOTS, "editor-discard", Screenshots.Location.FILES) { TestUi.settle(400) }
            onView(withText("Отмена")).inRoot(isDialog()).perform(click())
            TestUi.settle(300)
            scenario.onActivity {
                assertNotNull(it.supportFragmentManager.findFragmentByTag(ReviewEditorBottomSheet.TAG))
                assertEquals("Черновик", sheet(it).findViewById<EditText>(R.id.text).text.toString())
            }
            back(scenario)
            onView(withText("Не сохранять")).inRoot(isDialog()).perform(click())
            TestUi.eventually { scenario.onActivity { assertNull(it.supportFragmentManager.findFragmentByTag(ReviewEditorBottomSheet.TAG)) } }
        }
        editor(Appearances.light) { scenario ->
            back(scenario)
            TestUi.eventually { scenario.onActivity { assertNull(it.supportFragmentManager.findFragmentByTag(ReviewEditorBottomSheet.TAG)) } }
        }
    }

    @Test fun recreationKeepsTheTypedText() {
        editor(Appearances.light) { scenario ->
            type(scenario, R.id.text, REVIEW_TEXT)
            scenario.onActivity { sheet(it).findViewById<MaterialSwitch>(R.id.anonymous).performClick() }
            scenario.recreate()
            TestUi.settle(600)
            scenario.onActivity {
                assertEquals(REVIEW_TEXT, sheet(it).findViewById<EditText>(R.id.text).text.toString())
                assertFalse(sheet(it).findViewById<MaterialSwitch>(R.id.anonymous).isChecked)
            }
        }
    }

    @Test fun failedSaveKeepsTheSheetAndSuccessClosesIt() = appearances { spec ->
        editor(spec, {
            ReviewEditorPreviewActivity.lessons = AppResult.Success(TeacherLessons(FLOWS, SUBJECTS))
            ReviewEditorPreviewActivity.saveResult = AppResult.Failure(AppError.Network)
        }) { scenario ->
            type(scenario, R.id.text, REVIEW_TEXT)
            scenario.onActivity { sheet(it).findViewById<View>(R.id.save_button).performClick() }
            TestUi.eventually {
                scenario.onActivity {
                    val text = sheet(it).findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                    assertTrue(text?.isShown == true)
                }
            }
            frame(scenario, "editor-failed-${spec.name}")
            scenario.onActivity { assertNotNull(it.supportFragmentManager.findFragmentByTag(ReviewEditorBottomSheet.TAG)) }
            ReviewEditorPreviewActivity.saveResult = AppResult.Success(reviews(own()))
            scenario.onActivity { sheet(it).findViewById<View>(R.id.save_button).performClick() }
            TestUi.eventually { scenario.onActivity { assertNull(it.supportFragmentManager.findFragmentByTag(ReviewEditorBottomSheet.TAG)) } }
            assertEquals(listOf(TeacherReviewDraft(null, REVIEW_TEXT, anonymous = true, flowIds = FLOWS)),
                ReviewEditorPreviewActivity.drafts.toList())
        }
    }

    @Test fun sendStaysReachableAboveTheKeyboardOnANarrowScreen() {
        val narrow = Appearances.all.first { it.widthDp == 320 && !it.dark }
        editor(narrow, { ReviewEditorPreviewActivity.lessons = AppResult.Success(TeacherLessons(FLOWS, SUBJECTS)) }) { scenario ->
            type(scenario, R.id.text, LONG_TEXT)
            scenario.onActivity { activity ->
                val text = sheet(activity).findViewById<EditText>(R.id.text)
                text.requestFocus()
                WindowInsetsControllerCompat(checkNotNull(dialog(activity).window), text).show(WindowInsetsCompat.Type.ime())
            }
            TestUi.settle(1_200)
            scenario.onActivity { sheet(it).findViewById<NestedScrollView>(R.id.scroll).fullScroll(View.FOCUS_DOWN) }
            TestUi.settle(600)
            scenario.onActivity { activity ->
                val button = sheet(activity).findViewById<View>(R.id.save_button)
                val visible = Rect()
                assertTrue(button.getGlobalVisibleRect(visible))
                assertEquals(button.height, visible.height())
                val frame = Rect().also { dialog(activity).window!!.decorView.getWindowVisibleDisplayFrame(it) }
                assertTrue("Send ${visible.bottom} under the keyboard ${frame.bottom}", visible.bottom <= frame.bottom)
                ViewChecks.assertTextFits(sheet(activity))
            }
            Screenshots.capture(SCREENSHOTS, "editor-keyboard-narrow", Screenshots.Location.FILES) { TestUi.settle(300) }
        }
    }

    @Test fun reportNeedsAReasonAndStaysOpenOnFailure() = appearances { spec ->
        report(spec, { ReviewEditorPreviewActivity.reportResult = AppResult.Failure(AppError.Network) }) { scenario ->
            scenario.onActivity { activity ->
                val dialog = reportDialog(activity)
                assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
                val reasons = dialog.window!!.decorView.descendants().filterIsInstance<RadioButton>().map { it.text.toString() }.toList()
                assertEquals(listOf("Оскорбления", "Не тот преподаватель", "Спам", "Другое"), reasons)
            }
            frame(scenario, "report-${spec.name}", report = true)
            scenario.onActivity { activity ->
                val dialog = reportDialog(activity)
                dialog.findViewById<RadioButton>(R.id.reason_wrong_teacher)!!.performClick()
                dialog.findViewById<EditText>(R.id.comment)!!.setText("Ведёт другой предмет")
                assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            TestUi.eventually {
                scenario.onActivity {
                    val layout = reportDialog(it).findViewById<TextInputLayout>(R.id.comment_layout)!!
                    assertEquals("Нет связи. Проверьте интернет.", layout.error?.toString())
                }
            }
            frame(scenario, "report-failed-${spec.name}", report = true)
            ReviewEditorPreviewActivity.reportResult = AppResult.Success(reviews(null))
            scenario.onActivity { reportDialog(it).getButton(AlertDialog.BUTTON_POSITIVE).performClick() }
            TestUi.eventually { scenario.onActivity { assertNull(it.supportFragmentManager.findFragmentByTag(ReportReviewDialogFragment.TAG)) } }
            assertEquals(listOf("$REVIEW_ID:WRONG_TEACHER:Ведёт другой предмет"), ReviewEditorPreviewActivity.reports.toList())
        }
    }

    private fun appearances(block: (Appearances.Spec) -> Unit) = Appearances.default.forEach(block)

    private fun editor(spec: Appearances.Spec, configure: () -> Unit = {}, block: (ActivityScenario<ReviewEditorPreviewActivity>) -> Unit) =
        launch(spec, ReviewEditorPreviewActivity.SCREEN_EDITOR, configure, block)

    private fun report(spec: Appearances.Spec, configure: () -> Unit = {}, block: (ActivityScenario<ReviewEditorPreviewActivity>) -> Unit) =
        launch(spec, ReviewEditorPreviewActivity.SCREEN_REPORT, configure, block)

    private fun launch(
        spec: Appearances.Spec,
        screen: String,
        configure: () -> Unit,
        block: (ActivityScenario<ReviewEditorPreviewActivity>) -> Unit
    ) {
        reset()
        ReviewEditorPreviewActivity.appearance = spec.toReviewEditor()
        configure()
        val intent = Intent(ApplicationProvider.getApplicationContext(), ReviewEditorPreviewActivity::class.java)
            .putExtra(ReviewEditorPreviewActivity.EXTRA_SCREEN, screen)
        try {
            ActivityScenario.launch<ReviewEditorPreviewActivity>(intent).use { scenario ->
                TestUi.settle(600)
                block(scenario)
            }
        } finally {
            reset()
        }
    }

    private fun reset() {
        ReviewEditorPreviewActivity.appearance = PreviewAppearance()
        ReviewEditorPreviewActivity.reviews = null
        ReviewEditorPreviewActivity.lessons = AppResult.Success(TeacherLessons(emptySet(), emptyList()))
        ReviewEditorPreviewActivity.lessonsDelayMs = 0
        ReviewEditorPreviewActivity.saveResult = AppResult.Failure(AppError.Network)
        ReviewEditorPreviewActivity.saveDelayMs = 0
        ReviewEditorPreviewActivity.reportResult = AppResult.Failure(AppError.Network)
        ReviewEditorPreviewActivity.drafts.clear()
        ReviewEditorPreviewActivity.reports.clear()
    }

    private fun type(scenario: ActivityScenario<ReviewEditorPreviewActivity>, field: Int, value: String) {
        scenario.onActivity { sheet(it).findViewById<EditText>(field).setText(value) }
        TestUi.idle()
    }

    private fun back(scenario: ActivityScenario<ReviewEditorPreviewActivity>) {
        scenario.onActivity { (dialog(it) as ComponentDialog).onBackPressedDispatcher.onBackPressed() }
        TestUi.idle()
    }

    private fun frame(scenario: ActivityScenario<ReviewEditorPreviewActivity>, name: String, report: Boolean = false) {
        TestUi.settle(if (Screenshots.enabled) 600 else 100)
        lateinit var activity: ReviewEditorPreviewActivity
        scenario.onActivity {
            activity = it
            val root = if (report) reportDialog(it).window!!.decorView else sheet(it)
            ViewChecks.assertTextFits(root)
            ViewChecks.assertTouchTargets(root)
        }
        TestUi.awaitFrameCommit(activity)
        Screenshots.capture(SCREENSHOTS, name, Screenshots.Location.FILES)
    }

    private fun dialog(activity: ReviewEditorPreviewActivity) =
        checkNotNull((activity.supportFragmentManager.findFragmentByTag(ReviewEditorBottomSheet.TAG) as DialogFragment).dialog)

    private fun sheet(activity: ReviewEditorPreviewActivity): View = dialog(activity).window!!.decorView

    private fun chips(sheet: View): List<Chip> = sheet.findViewById<ChipGroup>(R.id.suggestions).descendants().filterIsInstance<Chip>().toList()

    private fun reportDialog(activity: ReviewEditorPreviewActivity) =
        (activity.supportFragmentManager.findFragmentByTag(ReportReviewDialogFragment.TAG) as DialogFragment).dialog as AlertDialog

    private fun reviews(mine: OwnTeacherReview?) = TeacherReviews(TEACHER_ISU, emptyList(), mine, canWrite = true, canVote = true,
        canReport = true, knownTeacher = true)

    private fun own() = OwnTeacherReview("own", "Математический анализ", REVIEW_TEXT, anonymous = false, status = OwnReviewStatus.PUBLISHED,
        reviewNote = null, score = 2, verified = true, written = ReviewDate.Month(YearMonth(2026, 9)))

    private companion object {
        const val SCREENSHOTS = "review-editor-screenshots"
        val FLOWS = setOf(7101L, 7102L)
        val SUBJECTS = listOf("Математический анализ", "Линейная алгебра", "Дискретная математика")
        const val REVIEW_TEXT = "Лекции понятные, на практике разбираем задачи из контрольных."
        val LONG_TEXT = (1..12).joinToString(" ") { "Абзац $it: преподаватель подробно объясняет материал и отвечает на вопросы." }
    }
}
