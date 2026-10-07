package dev.alllexey.itmowidgets.feature.recordbook

import dev.alllexey.itmowidgets.feature.recordbook.ui.sheets.SheetScoresBottomSheet
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.core.debug.MemorySubjectLinksRepository
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.Espresso.onView
import android.content.Intent
import android.view.View
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.SettingsNavigationTestActivity
import dev.alllexey.itmowidgets.core.debug.PreviewAppearance
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.core.sport.SportScorePeriod
import dev.alllexey.itmowidgets.core.sport.SportScoreRepository
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.core.ui.GroupPosition
import dev.alllexey.itmowidgets.core.ui.TeacherLevelTone
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectSheetState
import dev.alllexey.itmowidgets.feature.recordbook.ui.DetailItem
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPreviewFixtures
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPreviewFixtures.Phase
import dev.alllexey.itmowidgets.feature.recordbook.ui.SubjectHubAdapter
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPreviewActivity
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.Appearances.assertEffective
import dev.alllexey.itmowidgets.testing.toRecordbook
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import dev.alllexey.itmowidgets.testing.ViewChecks.assertTextFits
import dev.alllexey.itmowidgets.testing.ViewChecks.descendants
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalTime
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.ext.android.getViewModel

@RunWith(AndroidJUnit4::class)
class RecordbookVisualTest {
    @Test fun subjectTeacherWithIsuHasAccessibleProfileNavigationInEveryAppearance() {
        val defaultPrimary = mutableMapOf<Boolean, Int>()
        Appearances.default.forEachIndexed { index, spec ->
            withFixture(Phase.MIDDLE, spec.toRecordbook()) { scenario ->
                scenario.onActivity { activity ->
                    val container = activity.findViewById<View>(R.id.recordbook_test_container)
                    spec.assertEffective(container, defaultPrimary)
                    if (spec.widthDp > 0) assertEquals((spec.widthDp * container.resources.displayMetrics.density).toInt(), container.width)
                    RecordbookPreviewActivity.lessonsGateway = RecordbookPreviewActivity.MemoryLessons(listOf(
                        hubLesson(7, "2026-06-03", RecordbookPreviewFixtures.MATH_ID, 1,
                            SettingsNavigationTestActivity.LONG_NAME, 300001, RecordbookPreviewFixtures.MATH)
                    ))
                }
                openSubject(scenario, "Математический")
                scrollTo(scenario) { items -> items.indexOfFirst { it is DetailItem.Teacher } }
                scenario.onActivity { activity ->
                    val row = activity.teacherRow()
                    spec.assertEffective(row, defaultPrimary)
                    assertEquals(SettingsNavigationTestActivity.LONG_NAME, row.findViewById<TextView>(R.id.name).text.toString())
                    assertTrue(row.isClickable)
                    assertTrue(row.isFocusable)
                    assertEquals(View.VISIBLE, row.findViewById<View>(R.id.trailing).visibility)
                    assertEquals(activity.getString(R.string.teacher_open_profile),
                        row.createAccessibilityNodeInfo().actionList.single { it.id == AccessibilityActionCompat.ACTION_CLICK.id }.label)
                    assertTextFits(row)
                    dev.alllexey.itmowidgets.testing.ViewChecks.assertTouchTargets(row)
                }
                TestUi.settle(if (Screenshots.enabled) 500 else 80)
                lateinit var activity: RecordbookPreviewActivity
                scenario.onActivity { activity = it }
                TestUi.awaitFrameCommit(activity)
                screenshot("teacher-$index")
                scenario.onActivity {
                    it.teacherRow().performClick()
                    assertEquals(listOf(300001), it.openedProfiles)
                }
            }
        }
    }

    @Test fun subjectTeacherRowsShowLevelDots() {
        val defaultPrimary = mutableMapOf<Boolean, Int>()
        Appearances.default.forEachIndexed { index, spec ->
            withFixture(Phase.MIDDLE, spec.toRecordbook()) { scenario ->
                val levels = RecordbookPreviewActivity.MemoryLevels(mutableMapOf(300001 to TeacherLevel.VERY_POSITIVE, 300002 to TeacherLevel.NEGATIVE))
                scenario.onActivity { activity ->
                    spec.assertEffective(activity.findViewById(R.id.recordbook_test_container), defaultPrimary)
                    RecordbookPreviewActivity.levelsRepository = levels
                    RecordbookPreviewActivity.lessonsGateway = RecordbookPreviewActivity.MemoryLessons(listOf(
                        hubLesson(7, "2026-06-03", RecordbookPreviewFixtures.MATH_ID, 1,
                            SettingsNavigationTestActivity.LONG_NAME, 300001, RecordbookPreviewFixtures.MATH),
                        hubLesson(8, "2026-06-04", RecordbookPreviewFixtures.MATH_ID, 3, "Практикова Полина Петровна", 300002,
                            RecordbookPreviewFixtures.MATH),
                        hubLesson(9, "2026-06-05", RecordbookPreviewFixtures.MATH_ID, 2, "Лабораторов Лев Львович", 300003,
                            RecordbookPreviewFixtures.MATH),
                        hubLesson(10, "2026-06-06", RecordbookPreviewFixtures.MATH_ID, 2, "Безисунов Борис Борисович", 1,
                            RecordbookPreviewFixtures.MATH).copy(teacherIsu = null),
                    ))
                }
                openSubject(scenario, "Математический")
                scrollTo(scenario) { items -> items.indexOfFirst { it is DetailItem.Teacher } }
                scenario.onActivity { activity ->
                    assertEquals(listOf(setOf(300001, 300002, 300003)), levels.calls.toList())
                    val rows = activity.teacherRows()
                    assertEquals(4, rows.size)
                    val dots = rows.map { it.findViewById<ImageView>(R.id.level_dot) }
                    assertEquals(listOf(View.VISIBLE, View.VISIBLE, View.INVISIBLE, View.GONE), dots.map { it.visibility })
                    assertEquals(TeacherLevelTone.VERY_POSITIVE.color(activity), dots[0].imageTintList?.defaultColor)
                    assertEquals(TeacherLevelTone.NEGATIVE.color(activity), dots[1].imageTintList?.defaultColor)
                    assertEquals("${SettingsNavigationTestActivity.LONG_NAME}, тон отзывов: в основном положительные",
                        rows[0].findViewById<TextView>(R.id.name).contentDescription)
                    assertEquals("Практикова Полина Петровна, тон отзывов: скорее отрицательные",
                        rows[1].findViewById<TextView>(R.id.name).contentDescription)
                    assertNull(rows[2].findViewById<TextView>(R.id.name).contentDescription)
                    // The reserved place keeps every chevron and name in one column.
                    val chevrons = rows.take(3).map { it.findViewById<View>(R.id.trailing) }
                    assertEquals(1, chevrons.map { it.left }.distinct().size)
                    assertEquals(1, rows.take(3).map { it.findViewById<View>(R.id.name).width }.distinct().size)
                    assertFalse(rows[3].isClickable)
                    rows.forEach { row ->
                        assertTextFits(row)
                        dev.alllexey.itmowidgets.testing.ViewChecks.assertTouchTargets(row)
                    }
                }
                TestUi.settle(if (Screenshots.enabled) 500 else 80)
                lateinit var activity: RecordbookPreviewActivity
                scenario.onActivity { activity = it }
                TestUi.awaitFrameCommit(activity)
                screenshot("teacher-levels-$index")
                scenario.onActivity {
                    it.teacherRows()[1].performClick()
                    assertEquals(listOf(300002), it.openedProfiles)
                }
            }
        }
    }

    @Test fun subjectIsOnePageWithHeroLinksChatsControlsAndLessons() {
        Appearances.default.forEach { spec ->
            withFixture(Phase.MIDDLE, spec.toRecordbook()) { scenario ->
                openSubject(scenario, "Математический")
                scenario.onActivity { activity ->
                    assertEquals(RecordbookPreviewFixtures.MATH, activity.findViewById<TextView>(R.id.title).text.toString())
                    assertEquals(activity.getString(R.string.subject_subtitle, "Экзамен", 2), activity.findViewById<TextView>(R.id.subtitle).text.toString())
                    assertEquals(0, activity.window.decorView.descendants().count { it.javaClass.simpleName == "TabLayout" })
                    val items = activity.hubItems()
                    assertTrue(items.first() is DetailItem.Hero)
                    assertEquals(DetailItem.Section(R.string.links_title), items[1])
                    assertEquals("72", activity.findViewById<TextView>(R.id.points).text.toString())
                    assertEquals(activity.getString(R.string.subject_grade_next, "4C", "3"), activity.findViewById<TextView>(R.id.hint).text.toString())
                    // Pin, LMS, then links by score, three rows at most, and «Все ссылки» with what the sheet lists.
                    val links = activity.hubRows { it is DetailItem.Link || it is DetailItem.Lms || it is DetailItem.AllLinks }
                    assertEquals(listOf(activity.getString(R.string.subject_link_lms), "Записи лекций весны 2026 года с разбором задач",
                        "Конспекты", activity.getString(R.string.links_all_count, 7)),
                        links.map { it.findViewById<TextView>(R.id.title).text.toString() })
                    assertConnectedGroup(activity, links)
                    links.forEach { assertTrue(it.height >= 48 * activity.resources.displayMetrics.density - 1) }
                    val video = links[1]
                    assertEquals(View.GONE, video.findViewById<View>(R.id.own_badge).visibility)
                    assertTrue(video.findViewById<View>(R.id.votes).isShown)
                    assertEquals("5", video.findViewById<TextView>(R.id.score).text.toString())
                    dev.alllexey.itmowidgets.testing.ViewChecks.assertTouchTargets(video)
                    assertEquals(View.GONE, links[0].findViewById<View>(R.id.votes).visibility)
                    links.last().performClick()
                    video.performLongClick()
                    assertEquals(listOf("links", "actions:video"), RecordbookPreviewActivity.linkNavigation.toList())
                    video.findViewById<View>(R.id.vote_up).performClick()
                    val chats = items.filterIsInstance<DetailItem.Chat>()
                    assertEquals(listOf("own-chat", "flow-chat"), chats.map { it.link.id })
                    assertEquals(DetailItem.Section(R.string.links_chats), items[items.indexOf(chats.first()) - 1])
                    assertTrue(items.indexOf(chats.last()) < items.indexOf(DetailItem.Section(R.string.subject_controls_title)))
                    val groups = items.filterIsInstance<DetailItem.Group>()
                    assertEquals(3, groups.size)
                    assertEquals(listOf(false, true, false), groups.map { it.group.belowMinimum.isNotEmpty() })
                    assertEquals(2, items.count { it is DetailItem.Lesson })
                    assertEquals(DetailItem.AllLessons(4, GroupPosition.LAST), items.last())
                    assertVisibleTextFits(activity.window.decorView)
                }
                settle()
                scenario.onActivity { activity ->
                    val video = activity.hubRows { it is DetailItem.Link }.first()
                    assertEquals("6", video.findViewById<TextView>(R.id.score).text.toString())
                    assertTrue(video.findViewById<View>(R.id.vote_up).isSelected)
                }
                screenshot("subject-top-${spec.name}")
                scrollTo(scenario) { items -> items.indexOfFirst { it is DetailItem.Chat } - 1 }
                scenario.onActivity { activity ->
                    val chats = activity.hubRows { it is DetailItem.Chat }
                    assertConnectedGroup(activity, chats)
                    assertTrue(chats[0].findViewById<View>(R.id.own_badge).isShown)
                    assertEquals(View.GONE, chats[1].findViewById<View>(R.id.own_badge).visibility)
                    assertVisibleTextFits(activity.window.decorView)
                }
                screenshot("subject-chats-${spec.name}")
                scrollTo(scenario) { items -> items.indexOfFirst { it is DetailItem.Group } }
                scenario.onActivity { activity ->
                    assertTrue(activity.hubList().texts().contains(activity.getString(R.string.recordbook_group_labs)))
                    assertVisibleTextFits(activity.window.decorView)
                }
                screenshot("subject-groups-${spec.name}")
                scrollTo(scenario) { items -> items.indexOfFirst { it is DetailItem.Group && it.group.belowMinimum.isNotEmpty() } }
                scenario.onActivity { activity ->
                    val badge = activity.hubList().descendants().first { it.id == R.id.below_minimum && it.isShown } as TextView
                    assertEquals(activity.getString(R.string.recordbook_group_below_minimum), badge.text.toString())
                    assertTrue(activity.hubList().texts().contains(activity.getString(R.string.recordbook_group_tests)))
                    assertVisibleTextFits(activity.window.decorView)
                }
                screenshot("subject-below-minimum-${spec.name}")
                scrollToEnd(scenario)
                scenario.onActivity { activity ->
                    activity.hubRows { it is DetailItem.AllLessons }.single().performClick()
                }
                settle()
                scrollToEnd(scenario)
                scenario.onActivity { activity ->
                    assertEquals(4, activity.hubItems().count { it is DetailItem.Lesson })
                    assertTrue(activity.hubItems().none { it is DetailItem.AllLessons })
                    assertTouchTargets(activity)
                    assertVisibleTextFits(activity.window.decorView)
                }
                screenshot("subject-lessons-${spec.name}")
                scenario.recreate()
                settle()
                scenario.onActivity { assertVisibleTextFits(it.window.decorView) }
            }
        }
    }

    @Test fun creditSubjectCountsDownToTheCredit() {
        withFixture(Phase.MIDDLE, PreviewAppearance(widthDp = 320, fontScale = 1.3f)) { scenario ->
            openSubject(scenario, "Иностранный")
            scenario.onActivity { activity ->
                assertEquals(activity.getString(R.string.subject_credit_next, "8"), activity.findViewById<TextView>(R.id.hint).text.toString())
                // Without links yet «Ссылки» holds one row that adds the first one.
                val add = activity.hubRows { it is DetailItem.Link || it is DetailItem.Lms || it is DetailItem.AllLinks || it is DetailItem.AddLink }.single()
                assertEquals(activity.getString(R.string.links_add), add.findViewById<TextView>(R.id.title).text.toString())
                add.performClick()
                assertEquals(listOf("editor"), RecordbookPreviewActivity.linkNavigation.toList())
                assertVisibleTextFits(activity.window.decorView)
            }
            screenshot("subject-credit-narrow")
        }
    }

    @Test fun sessionSubjectShowsItsGradeAndNoHint() {
        withFixture(Phase.SESSION, Appearances.light.toRecordbook()) { scenario ->
            openSubject(scenario, "Алгоритмы")
            scenario.onActivity { activity ->
                assertEquals("4C", activity.findViewById<TextView>(R.id.grade).text.toString())
                assertEquals(View.GONE, activity.findViewById<View>(R.id.hint).visibility)
                assertVisibleTextFits(activity.window.decorView)
            }
            screenshot("subject-session")
        }
    }

    @Test fun pastPeriodKeepsLinksWithoutLessons() {
        withFixture(Phase.MIDDLE, Appearances.light.toRecordbook()) { scenario ->
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag(RecordbookPreviewActivity.ROOT_TAG)!!
                fragment.getViewModel<RecordbookViewModel>().selectPeriod(1, 1)
            }
            settle()
            openSubject(scenario, "Математический")
            scenario.onActivity { activity ->
                val items = activity.hubItems()
                assertTrue(items.none { it is DetailItem.Lesson || it is DetailItem.LessonsMessage })
                val own = activity.hubRows { it is DetailItem.Link }.single()
                assertEquals("Таблица баллов осени", own.findViewById<TextView>(R.id.title).text.toString())
                // An own link is told by «моя» alone; it has no votes.
                assertTrue(own.findViewById<View>(R.id.own_badge).isShown)
                assertEquals(View.GONE, own.findViewById<View>(R.id.votes).visibility)
                assertEquals(activity.getString(R.string.links_all_count, 1),
                    activity.hubRows { it is DetailItem.AllLinks }.single().findViewById<TextView>(R.id.title).text.toString())
            }
            scrollTo(scenario) { items -> items.indexOfFirst { it is DetailItem.Teacher } }
            scenario.onActivity { activity ->
                val row = activity.teacherRow()
                assertFalse(row.isClickable)
                assertFalse(row.isFocusable)
                assertEquals(View.GONE, row.findViewById<View>(R.id.trailing).visibility)
                assertTrue(row.createAccessibilityNodeInfo().actionList.none { it.id == AccessibilityActionCompat.ACTION_CLICK.id })
                row.performClick()
                assertEquals(emptyList<Int>(), activity.openedProfiles)
                assertTextFits(row)
            }
            screenshot("subject-past-period")
        }
    }

    @Test fun physicalEducationPageHasTheSportOverviewAndNoLinks() {
        Appearances.default.forEach { spec ->
            withFixture(Phase.MIDDLE, spec.toRecordbook()) { scenario ->
                openSubject(scenario, "Физическая")
                scenario.onActivity { activity ->
                    val items = activity.hubItems()
                    assertTrue(items.first() is DetailItem.SportOverview)
                    assertTrue(items.none { it is DetailItem.Link || it is DetailItem.Lms || it is DetailItem.AddLink || it is DetailItem.Chat || it is DetailItem.Hero })
                    assertEquals("64", activity.findViewById<TextView>(R.id.total).text.toString())
                    assertEquals(RecordbookPreviewFixtures.PE, activity.findViewById<TextView>(R.id.title).text.toString())
                    assertVisibleTextFits(activity.window.decorView)
                }
                screenshot("subject-sport-${spec.name}")
            }
        }
    }

    @Test fun sportFailureAndMissingPeriodKeepOfficialCreditAndRetryRecoversPoints() {
        withPreview(PreviewAppearance(widthDp = 320, fontScale = 1.3f, dark = true), configure = {
            it.sportRate = "зачет"
            it.sportScore = AppResult.Failure(AppError.Network)
        }) { scenario, repository ->
            settle()
            openSubject(scenario, "Физическая")
            var originalResultTop = 0
            scenario.onActivity {
                assertVisibleTextFits(it.window.decorView)
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.error_content).visibility)
                assertEquals(View.GONE, it.findViewById<View>(R.id.score_content).visibility)
                assertEquals(it.getString(R.string.recordbook_rate_credit), it.findViewById<TextView>(R.id.official_result).text.toString())
                assertTrue(it.findViewById<View>(R.id.retry).height >= 48 * it.resources.displayMetrics.density - 1)
                val location = IntArray(2)
                it.findViewById<View>(R.id.official_result).getLocationOnScreen(location)
                originalResultTop = location[1]
            }
            screenshot("sport-error")
            repository.sportScore = AppResult.Success(SportScoreSummary(100, 20))
            scenario.onActivity { it.findViewById<View>(R.id.retry).performClick() }
            settle()
            scenario.onActivity {
                assertVisibleTextFits(it.window.decorView)
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.score_content).visibility)
                assertEquals("120", it.findViewById<TextView>(R.id.total).text.toString())
                val location = IntArray(2)
                it.findViewById<View>(R.id.official_result).getLocationOnScreen(location)
                assertEquals(originalResultTop, location[1])
            }
            screenshot("sport-recovered")
            repository.hasSportPeriod = false
            scenario.onActivity {
                it.supportFragmentManager.findFragmentByTag("detail")!!.getViewModel<RecordbookSubjectViewModel>().refresh(RefreshMode.Pull)
            }
            settle()
            scenario.onActivity {
                assertVisibleTextFits(it.window.decorView)
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.error_content).visibility)
                assertEquals(View.GONE, it.findViewById<View>(R.id.retry).visibility)
                assertEquals(it.getString(R.string.recordbook_rate_credit), it.findViewById<TextView>(R.id.official_result).text.toString())
            }
            screenshot("sport-no-period")
        }
    }

    @Test fun subjectHubProposesANameMatchAndConfirmingItShowsTheLessons() {
        RecordbookPreviewActivity.lessonsGateway = RecordbookPreviewActivity.MemoryLessons(listOf(
            hubLesson(7, "2026-06-03", subjectId = 555, typeId = 2, teacher = "Лаборант Л. Л.", isu = 9, name = "Алгоритмы и структуры данных")
        ))
        withPreview(Appearances.light.toRecordbook()) { scenario, _ ->
            settle()
            openSubject(scenario, "Алгоритмы")
            scrollToEnd(scenario)
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertTrue(activity.hubItems().any { it is DetailItem.BindingProposal })
                assertEquals(View.VISIBLE, root.findViewById<View>(R.id.confirm).visibility)
                assertEquals(activity.getString(R.string.subject_binding_proposal, "Алгоритмы и структуры данных"),
                    root.findViewById<TextView>(R.id.message).text.toString())
                assertEquals(0, root.descendants().count { it.id == R.id.type_indicator })
                assertVisibleTextFits(root)
            }
            screenshot("subject-hub-proposal")
            scenario.onActivity { it.window.decorView.findViewById<View>(R.id.confirm).performClick() }
            settle()
            scrollToEnd(scenario)
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertTrue(activity.hubItems().none { it is DetailItem.BindingProposal })
                assertEquals(1, activity.hubItems().count { it is DetailItem.Lesson })
                assertEquals(1, root.descendants().count { it.id == R.id.type_indicator })
                assertEquals(mapOf(2L to 555L), (RecordbookPreviewActivity.bindingStore as RecordbookPreviewActivity.MemoryBindings).bindings)
            }
            screenshot("subject-hub-bound")
        }
    }

    @Test fun subjectPageShowsTheSheetTotalInEveryState() {
        Appearances.default.forEach { spec ->
            withFixture(Phase.MIDDLE, spec.toRecordbook()) { scenario ->
                RecordbookPreviewActivity.MemorySheetScores.scores.value = listOf(RecordbookPreviewFixtures.sheetScore())
                openSubject(scenario, "Математический")
                scenario.onActivity { activity ->
                    val items = activity.hubItems()
                    assertTrue((items.first() as DetailItem.Hero).sheet is SubjectSheetState.Connected)
                    assertTrue(items.none { it is DetailItem.Notice })
                }
                var below = 0
                scenario.onActivity { activity ->
                    val row = activity.sheetRow()
                    assertTrue(activity.findViewById<View>(R.id.sheet_divider).isShown)
                    assertEquals(View.GONE, activity.findViewById<View>(R.id.sheet_hint).visibility)
                    assertEquals("66,3", row.findViewById<TextView>(R.id.value).text.toString())
                    assertEquals("ИТОГО баллов, лист «P3110»", row.findViewById<TextView>(R.id.caption).text.toString())
                    assertEquals(activity.getString(R.string.sheet_scores_updated_time, "12:00"), row.findViewById<TextView>(R.id.status).text.toString())
                    assertTrue(row.contentDescription.contains("66,3"))
                    val menu = row.findViewById<View>(R.id.menu)
                    val density = activity.resources.displayMetrics.density
                    assertTrue(menu.width >= 48 * density - 1 && menu.height >= 48 * density - 1)
                    assertTextFits(row)
                    below = activity.belowSheetRow()
                }
                screenshot("subject-sheet-ok-${spec.name}")
                val states = listOf(
                    SheetStatus.NETWORK to R.string.sheet_scores_offline,
                    SheetStatus.CLOSED to R.string.sheet_scores_closed,
                    SheetStatus.ROW_NOT_FOUND to R.string.sheet_scores_row_not_found,
                    SheetStatus.COLUMN_NOT_FOUND to R.string.sheet_scores_column_not_found,
                    SheetStatus.TOO_LARGE to R.string.sheet_scores_too_large,
                )
                for ((status, text) in states) {
                    RecordbookPreviewActivity.MemorySheetScores.scores.value = listOf(RecordbookPreviewFixtures.sheetScore(status))
                    settle()
                    scenario.onActivity { activity ->
                        val row = activity.sheetRow()
                        assertEquals("66,3", row.findViewById<TextView>(R.id.value).text.toString())
                        assertTrue(row.findViewById<TextView>(R.id.status).text.toString().endsWith(activity.getString(text)))
                        assertEquals(status.name, below, activity.belowSheetRow())
                        assertTextFits(row)
                    }
                    screenshot("subject-sheet-${status.name.lowercase()}-${spec.name}")
                }
                RecordbookPreviewActivity.MemorySheetScores.scores.value = listOf(
                    RecordbookPreviewFixtures.sheetScore(headerPath = LONG_PATH, tabName = "BARS (Fall semester 2026)")
                )
                settle()
                scenario.onActivity { activity ->
                    val caption = activity.sheetRow().findViewById<TextView>(R.id.caption)
                    assertFalse(caption.text.contains("·"))
                    assertTrue(caption.text.contains(" › "))
                    assertTrue(caption.lineCount > 1)
                    assertTextFits(activity.sheetRow())
                }
                screenshot("subject-sheet-long-${spec.name}")

                scenario.onActivity { it.sheetRow().findViewById<View>(R.id.menu).performClick() }
                settle()
                listOf(R.string.sheet_scores_open, R.string.sheet_scores_disconnect).forEach {
                    onView(withText(it)).inRoot(isPlatformPopup()).check(matches(isDisplayed()))
                }
                onView(withText(R.string.sheet_scores_change_total)).inRoot(isPlatformPopup()).perform(click())
                settle()
                assertTrue(RecordbookPreviewActivity.linkNavigation.contains("sheet:TOTAL"))
            }
        }
    }

    @Test fun subjectPageOffersTheSheetHintWithoutAConnection() {
        Appearances.default.forEach { spec ->
            withFixture(Phase.MIDDLE, spec.toRecordbook()) { scenario ->
                openSubject(scenario, "Математический")
                scenario.onActivity { activity ->
                    val row = activity.hintRow()
                    assertTrue((activity.hubItems().first() as DetailItem.Hero).sheet is SubjectSheetState.Hint)
                    assertEquals(View.GONE, activity.findViewById<View>(R.id.sheet_divider).visibility)
                    assertEquals(activity.getString(R.string.sheet_scores_hint), (row as TextView).text.toString())
                    assertTrue(row.height >= 48 * activity.resources.displayMetrics.density - 1)
                    assertTextFits(row)
                    row.performClick()
                }
                settle()
                assertEquals(listOf("sheet:CONNECT"), RecordbookPreviewActivity.linkNavigation.filter { it.startsWith("sheet") })
                assertEquals(RecordbookPreviewFixtures.SHEET_URL, RecordbookPreviewActivity.sheetRequests.single().url)
                scenario.onActivity { (it.supportFragmentManager.findFragmentByTag(SheetScoresBottomSheet.TAG) as DialogFragment).dismiss() }
                settle()

                val second = "https://docs.google.com/spreadsheets/d/1SyntheticSecondSheet0123456789/edit"
                val links = RecordbookPreviewActivity.resourceRepository as MemorySubjectLinksRepository
                val scope = RecordbookPreviewFixtures.MATH_SCOPE
                val snapshot = checkNotNull(links.snapshots.value[scope.key])
                val extra = snapshot.mine.first().copy(id = "flow-sheet", url = second, title = "Баллы лектора", isMine = false, score = 3)
                links.snapshots.value = links.snapshots.value + (scope.key to snapshot.copy(shared = snapshot.shared + extra))
                settle()
                scenario.onActivity { it.hintRow().performClick() }
                settle()
                onView(withText(R.string.sheet_scores_choose_link)).inRoot(isDialog()).check(matches(isDisplayed()))
                onView(withText("Таблица баллов потока, моя")).inRoot(isDialog()).check(matches(isDisplayed()))
                screenshot("subject-sheet-choose-${spec.name}")
                onView(withText("Баллы лектора")).inRoot(isDialog()).perform(click())
                settle()
                assertEquals(second, RecordbookPreviewActivity.sheetRequests.last().url)
                assertEquals(SheetScoresArgs.Step.CONNECT, RecordbookPreviewActivity.sheetRequests.last().step)
                scenario.onActivity { (it.supportFragmentManager.findFragmentByTag(SheetScoresBottomSheet.TAG) as DialogFragment).dismiss() }
                settle()
            }

            // A subject without controls: the offer in the result card is the detail; no «no details» card.
            withFixture(Phase.MIDDLE, spec.toRecordbook()) { scenario ->
                val base = RecordbookPreviewFixtures.Recordbook(Phase.MIDDLE)
                RecordbookPreviewActivity.repository = object : RecordbookRepository by base {
                    override suspend fun getControls(entryId: Long) =
                        if (entryId == HISTORY_ID) AppResult.Success(emptyList()) else base.getControls(entryId)
                }
                val links = RecordbookPreviewActivity.resourceRepository as MemorySubjectLinksRepository
                val math = checkNotNull(links.snapshots.value[RecordbookPreviewFixtures.MATH_SCOPE.key])
                val history = ResourceScope(HISTORY_ID, "История", "2025-2")
                links.snapshots.value = links.snapshots.value + (history.key to math.copy(
                    mine = listOf(math.mine.first().copy(scope = history)), shared = emptyList()
                ))
                openSubject(scenario, "История")
                scenario.onActivity { activity ->
                    val items = activity.hubItems()
                    assertTrue((items.first() as DetailItem.Hero).sheet is SubjectSheetState.Hint)
                    assertTrue(activity.hintRow().isShown)
                    assertTrue(items.none { it is DetailItem.Notice || it == DetailItem.Section(R.string.subject_controls_title) })
                }
                screenshot("subject-sheet-hint-${spec.name}")
            }
        }
    }

    private fun RecordbookPreviewActivity.heroCard(): View =
        checkNotNull(hubList().findViewHolderForAdapterPosition(hubItems().indexOfFirst { it is DetailItem.Hero })).itemView

    private fun RecordbookPreviewActivity.sheetRow(): View = heroCard().findViewById(R.id.sheet)

    private fun RecordbookPreviewActivity.hintRow(): View = heroCard().findViewById(R.id.sheet_hint)

    /** The bound rows of the items matching [predicate], in list order; they must be on screen. */
    private fun RecordbookPreviewActivity.hubRows(predicate: (DetailItem) -> Boolean): List<View> = hubItems().withIndex()
        .filter { predicate(it.value) }
        .map { checkNotNull(hubList().findViewHolderForAdapterPosition(it.index)) { "Row ${it.index} is off screen" }.itemView }

    /** Rows of one group: one width and surface, 2 dp apart, the outer corners only at the ends. */
    private fun assertConnectedGroup(activity: RecordbookPreviewActivity, rows: List<View>) {
        val density = activity.resources.displayMetrics.density
        assertEquals(1, rows.map { it.width }.distinct().size)
        rows.zipWithNext().forEach { (upper, lower) -> assertEquals(2 * density, (lower.top - upper.bottom).toFloat(), 1f) }
        val shapes = rows.map { ((it.background as android.graphics.drawable.RippleDrawable).getDrawable(0)
            as com.google.android.material.shape.MaterialShapeDrawable).shapeAppearanceModel }
        val outer = 20 * density
        val inner = 4 * density
        val bounds = android.graphics.RectF(0f, 0f, 100f, 100f)
        shapes.forEachIndexed { index, shape ->
            assertEquals(if (index == 0) outer else inner, shape.topLeftCornerSize.getCornerSize(bounds), 0.5f)
            assertEquals(if (index == shapes.lastIndex) outer else inner, shape.bottomLeftCornerSize.getCornerSize(bounds), 0.5f)
        }
    }

    /** The window position of the row under the result card. */
    private fun RecordbookPreviewActivity.belowSheetRow(): Int {
        val position = hubItems().indexOfFirst { it is DetailItem.Hero } + 1
        val view = checkNotNull(hubList().findViewHolderForAdapterPosition(position)).itemView
        return IntArray(2).also(view::getLocationInWindow)[1]
    }

    private fun withFixture(phase: Phase, appearance: PreviewAppearance, block: (ActivityScenario<RecordbookPreviewActivity>) -> Unit) {
        RecordbookPreviewActivity.appearance = appearance
        RecordbookPreviewFixtures.install(phase)
        try {
            ActivityScenario.launch<RecordbookPreviewActivity>(Intent(ApplicationProvider.getApplicationContext(), RecordbookPreviewActivity::class.java)).use {
                settle()
                block(it)
            }
        } finally {
            RecordbookPreviewFixtures.reset()
        }
    }

    private fun RecordbookPreviewActivity.teacherRow(): View {
        val position = hubItems().indexOfFirst { it is DetailItem.Teacher }
        assertTrue(position >= 0)
        return checkNotNull(hubList().findViewHolderForAdapterPosition(position)).itemView
    }

    private fun RecordbookPreviewActivity.teacherRows(): List<View> = hubItems().withIndex()
        .filter { it.value is DetailItem.Teacher }
        .map { checkNotNull(hubList().findViewHolderForAdapterPosition(it.index)).itemView }

    private fun RecordbookPreviewActivity.hubList(): RecyclerView = findViewById(R.id.recycler_view)

    private fun RecordbookPreviewActivity.hubItems(): List<DetailItem> = (hubList().adapter as SubjectHubAdapter).currentList

    private fun View.texts(): List<String> = descendants().filterIsInstance<TextView>().filter { it.isShown }.map { it.text.toString() }.toList()

    private fun assertTouchTargets(activity: RecordbookPreviewActivity) {
        val minimum = 48 * activity.resources.displayMetrics.density - 1
        activity.window.decorView.descendants().filter { it.isShown && it.isClickable }.forEach {
            assertTrue("Touch target ${it.javaClass.simpleName}", it.height >= minimum)
        }
    }

    private fun scrollTo(scenario: ActivityScenario<RecordbookPreviewActivity>, position: (List<DetailItem>) -> Int) {
        scenario.onActivity { activity ->
            (activity.hubList().layoutManager as androidx.recyclerview.widget.LinearLayoutManager)
                .scrollToPositionWithOffset(position(activity.hubItems()), 0)
        }
        settle()
    }

    private fun scrollToEnd(scenario: ActivityScenario<RecordbookPreviewActivity>) {
        scenario.onActivity { activity ->
            val list = activity.hubList()
            list.scrollToPosition(list.adapter!!.itemCount - 1)
        }
        settle()
    }

    /** Opens a subject as a tap on its list row does; the list is Compose (LR-3), so by its semantics. */
    private fun openSubject(scenario: ActivityScenario<RecordbookPreviewActivity>, namePart: String) =
        RecordbookSemantics.openSubject(scenario, namePart, ::settle)

    private fun hubLesson(pairId: Long, date: String, subjectId: Long, typeId: Int, teacher: String, isu: Long, name: String = "Предмет $subjectId") = SubjectLesson(
        pairId = pairId, date = LocalDate.parse(date).toKotlinLocalDate(), start = LocalTime.of(9, 30).toKotlinLocalTime(),
        end = LocalTime.of(11, 0).toKotlinLocalTime(), typeId = typeId, type = "",
        subjectId = subjectId, subjectName = name, flowId = subjectId * 10, teacherIsu = isu, teacherFio = teacher,
        room = "1506", building = "Кронверкский проспект, 49", formatId = 1
    )

    private fun withPreview(appearance: PreviewAppearance, configure: (PreviewRepository) -> Unit = {}, block: (ActivityScenario<RecordbookPreviewActivity>, PreviewRepository) -> Unit) {
        val repository = PreviewRepository().apply(configure)
        RecordbookPreviewActivity.appearance = appearance
        RecordbookPreviewActivity.repository = repository
        RecordbookPreviewActivity.sportRepository = object : SportScoreRepository {
            override suspend fun getScorePeriods() = AppResult.Success(if (repository.hasSportPeriod) listOf(SportScorePeriod(10, "Весна 2025/2026")) else emptyList())
            override suspend fun getScoreSummary(semesterId: Long) = repository.sportScore
        }
        try {
            ActivityScenario.launch<RecordbookPreviewActivity>(Intent(ApplicationProvider.getApplicationContext(), RecordbookPreviewActivity::class.java)).use { block(it, repository) }
        } finally {
            RecordbookPreviewActivity.bars = null
            RecordbookPreviewActivity.repository = null
            RecordbookPreviewActivity.sportRepository = null
            RecordbookPreviewActivity.lessonsGateway = RecordbookPreviewActivity.MemoryLessons()
            RecordbookPreviewActivity.bindingStore = RecordbookPreviewActivity.MemoryBindings()
        }
    }

    private fun settle() = TestUi.settle(600)

    private fun screenshot(name: String) = Screenshots.capture("recordbook-screenshots", name)

    /** Only subject names (two lines) may end in an ellipsis. */
    private fun assertVisibleTextFits(root: View) {
        assertTextFits(root, allowEllipsis = true)
        root.descendants().filterIsInstance<TextView>()
            .filter { it.isShown && it.id != R.id.name && it.id != R.id.title }
            .forEach { view ->
                val layout = view.layout ?: return@forEach
                (0 until layout.lineCount).forEach { line -> assertEquals("Ellipsis: ${view.text}", 0, layout.getEllipsisCount(line)) }
            }
    }

    private fun ViewGroup.children() = (0 until childCount).map(::getChildAt)

    private class PreviewRepository : RecordbookRepository {
        @Volatile var sportScore: AppResult<SportScoreSummary> = AppResult.Success(SportScoreSummary(66, 28))
        @Volatile var sportRate: String? = null
        @Volatile var hasSportPeriod = true
        override suspend fun getPrograms() = AppResult.Success(listOf(RecordbookProgram(1, "Тестовая образовательная программа", listOf(
            RecordbookPeriod("2026/2027", 4, 2, false), RecordbookPeriod("2026/2027", 3, 2, false),
            RecordbookPeriod("2025/2026", 2, 1, true), RecordbookPeriod("2025/2026", 1, 1, false)
        ))))
        override suspend fun getSubjects(programId: Long, semester: Int): AppResult<List<RecordbookSubject>> {
            return AppResult.Success(listOf(
                subject(1, "Математический анализ (продвинутый уровень)", "2/FX", 48.5),
                subject(2, "Алгоритмы и структуры данных", "4/C", 76.5),
                subject(3, "Физическая культура и спорт (элективная)", sportRate, null, false),
                subject(4, "Проектирование и разработка распределённых информационных систем", null, null),
                subject(5, "Иностранный язык", "зачет", 62.0),
                subject(6, "Проектная работа", "5/A", 100.0),
                subject(7, "Основы программирования", null, 0.0)
            ))
        }
        override suspend fun getControls(entryId: Long) = AppResult.Success(listOf(
            RecordbookControl(1, "Практические работы", 32.0, 20.0, 40.0, true, null, null),
            RecordbookControl(2, "Исследование сходимости последовательностей и функциональных рядов", 5.0, 4.0, 8.0, true, null, "Тестовый преподаватель с длинным именем и отчеством", 1),
            RecordbookControl(3, "Экзамен", null, null, 40.0, true, null, null),
            RecordbookControl(4, "Homework 1", 5.0, 5.0, 7.0, false, null, null),
            RecordbookControl(5, "Homework 2", 7.0, 5.0, 7.0, true, null, null),
            RecordbookControl(6, "Дополнительные баллы", null, null, null, false, null, null)
        ))
        private fun subject(id: Long, name: String, rate: String?, score: Double?, details: Boolean = true) = RecordbookSubject(
            name, id, id, if (id == 3L || id == 5L) "Зачёт" else "Экзамен", score, rate, null, null, details,
            "Тестовый преподаватель с длинным именем и отчеством"
        )
    }

    private companion object {
        const val HISTORY_ID = 6L
        val LONG_PATH = "Итоговая аттестация · " + "Сумма баллов за все контрольные и лабораторные работы семестра ".repeat(2).trim().take(120)
    }
}
