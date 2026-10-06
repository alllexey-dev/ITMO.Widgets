package dev.alllexey.itmowidgets.feature.sport.ui.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportCommon
import dev.alllexey.itmowidgets.feature.sport.domain.model.UnavailableReason
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportCommonDetailsArgs
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportShareTarget
import dev.alllexey.itmowidgets.feature.sport.presentation.common.toDetailsArgs
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/**
 * Every case of the deleted `SportDetailsSheetVisualTest` on the Compose sheet, at 320 dp and font scale 1.3 in the
 * sheet's 802 dp: the teacher, the action sent once and not again after recreation, busy and read-only, a deadline
 * that changed while the sheet was open, sharing, the free-place plurals, the queue, the prediction and the
 * conditions. Its looks live in the `SportDetailsSheetContent_*` goldens.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class SportDetailsSheetTest {

    private val dispatched = mutableListOf<SportBookingAction>()
    private val profiles = mutableListOf<Int>()
    private val shared = mutableListOf<SportShareTarget>()
    private val actions = SportDetailsActions(
        onAction = { dispatched += it },
        onShare = { shared += it },
        onProfile = { profiles += it },
    )

    private var locale: Locale? = null

    /** Compose plurals follow the process locale, which the app keeps Russian; the JVM's is not. */
    @BeforeTest
    fun russian() {
        locale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ru"))
    }

    @AfterTest
    fun restoreLocale() {
        locale?.let(Locale::setDefault)
    }

    @Test
    fun theTeacherOpensTheirProfileAndATeacherWithoutAnIsuStaysText() = runComposeUiTest {
        var item by mutableStateOf(SportDetailsSamples.open)
        setContent { Sheet(item) }
        val teacher = SportCardFixtures.lesson().teacherFio

        val click = onNodeWithText(teacher).fetchSemanticsNode().config[SemanticsActions.OnClick]
        assertEquals("Открыть профиль", click.label)
        onNodeWithText(teacher).performClick()
        assertEquals(listOf(100), profiles)

        item = SportCardFixtures.lesson().copy(teacherIsu = 0).toDetailsArgs()
        waitForIdle()
        onNodeWithText(teacher).assertHasNoClickAction()
    }

    @Test
    fun eachOfferIsSentOnceAndNotAgainAfterRecreation() = runComposeUiTest {
        val lesson = SportCardFixtures.lesson()
        val full = lesson.copy(available = 0, canSignIn = false, unavailableReasons = listOf(UnavailableReason.Full))
        val cases: List<Triple<SportCommon, SportBookingAction, String>> = listOf(
            Triple(lesson, SportBookingAction.SIGN, "Записаться"),
            Triple(lesson.copy(signed = true), SportBookingAction.CANCEL, "Отписаться"),
            Triple(full, SportBookingAction.AUTO, "Автозапись"),
            Triple(full.copy(signEntry = SportCardFixtures.entry()), SportBookingAction.CANCEL_AUTO, "Отменить"),
            Triple(SportCardFixtures.booking(), SportBookingAction.CANCEL, "Отписаться"),
            Triple(
                SportCardFixtures.booking().copy(signed = false, signEntry = SportCardFixtures.entry()),
                SportBookingAction.CANCEL_AUTO,
                "Отменить",
            ),
        )
        var item by mutableStateOf(lesson.toDetailsArgs())
        var submission by mutableStateOf(SportDetailsSubmission())
        setContent { Sheet(item, submission = submission) }

        cases.forEach { (case, action, label) ->
            item = case.toDetailsArgs()
            submission = SportDetailsSubmission()
            waitForIdle()
            assertTouchTargets()
            onNodeWithTag(SportDetailsSheetTestTags.ACTION).assertTextEquals(label).assertIsEnabled()
            val before = dispatched.size
            onNodeWithTag(SportDetailsSheetTestTags.ACTION).performClick()
            onNodeWithTag(SportDetailsSheetTestTags.ACTION).performClick()
            assertEquals(before + 1, dispatched.size, label)
            assertEquals(action, dispatched.last())
            onNodeWithTag(SportDetailsSheetTestTags.ACTION).assertIsNotEnabled()
        }

        // A recreated sheet gets the saved flag back: what was sent is never sent again.
        item = lesson.toDetailsArgs()
        submission = SportDetailsSubmission(submitted = true)
        waitForIdle()
        val sent = dispatched.size
        onNodeWithTag(SportDetailsSheetTestTags.ACTION).assertIsNotEnabled().performClick()
        assertEquals(sent, dispatched.size)

        // One recreated before its tap still sends once.
        submission = SportDetailsSubmission(submitted = false)
        waitForIdle()
        onNodeWithTag(SportDetailsSheetTestTags.ACTION).performClick()
        assertEquals(listOf(SportBookingAction.SIGN), dispatched.drop(sent))
    }

    @Test
    fun aReadOnlySheetHasNoActionAndABusyOneSendsNothing() = runComposeUiTest {
        var actionsEnabled by mutableStateOf(false)
        var busy by mutableStateOf(false)
        setContent { Sheet(SportDetailsSamples.open, actionsEnabled = actionsEnabled, busy = busy) }
        onNodeWithTag(SportDetailsSheetTestTags.ACTION).assertDoesNotExist()

        actionsEnabled = true
        busy = true
        waitForIdle()
        onNodeWithTag(SportDetailsSheetTestTags.ACTION).assertIsNotEnabled().performClick()
        assertTrue(dispatched.isEmpty())
    }

    @Test
    fun aDeadlineThatChangedWhileOpenIsRecheckedOnTapAndSendsNothing() = runComposeUiTest {
        val lesson = SportCardFixtures.lesson()
        val time = SportDetailsSamples.FixedTime(SportDetailsSamples.now)
        setContent { Sheet(lesson.toDetailsArgs(), time = time) }
        onNodeWithTag(SportDetailsSheetTestTags.ACTION).assertTextEquals("Записаться")

        time.at = lesson.start
        onNodeWithTag(SportDetailsSheetTestTags.ACTION).performClick()
        waitForIdle()

        assertTrue(dispatched.isEmpty())
        onNodeWithTag(SportDetailsSheetTestTags.ACTION).assertDoesNotExist()
        onNodeWithText("Занятие уже началось").assertExists()
    }

    @Test
    fun upcomingLessonsBookingsAndPredictionsCanBeShared() = runComposeUiTest {
        val lesson = SportCardFixtures.lesson().copy(sectionName = SectionName(LONG_SECTION))
        val past = lesson.copy(start = lesson.start - 2.days, end = lesson.end - 2.days)
        val cases = listOf<Pair<SportCommon, SportShareTarget?>>(
            lesson to SportShareTarget.Lesson(1),
            SportCardFixtures.booking() to SportShareTarget.Lesson(1),
            lesson.copy(isLessonReal = false) to SportShareTarget.Prediction(1),
            SportCardFixtures.booking(-1).copy(isLessonReal = false, signed = false) to SportShareTarget.Prediction(1),
            past to null,
            lesson.copy(lessonId = -5) to null,
        )
        var item by mutableStateOf(lesson.toDetailsArgs())
        setContent { Sheet(item) }

        cases.forEachIndexed { index, (case, target) ->
            item = case.toDetailsArgs()
            waitForIdle()
            if (target == null) {
                onNodeWithTag(SportDetailsSheetTestTags.SHARE).assertDoesNotExist()
            } else {
                val share = onNodeWithTag(SportDetailsSheetTestTags.SHARE)
                val label = share.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
                assertEquals(listOf("Поделиться"), label, "case $index")
                share.performClick()
                assertEquals(target, shared.last(), "case $index")
                assertTouchTargets()
            }
        }
        assertEquals(4, shared.size)
    }

    @Test
    fun theFreePlaceLabelFollowsRussianPluralForms() = runComposeUiTest {
        var item by mutableStateOf(SportDetailsSamples.open)
        setContent { Sheet(item) }

        listOf(
            1 to "Свободное место", 2 to "Свободных места", 4 to "Свободных места", 5 to "Свободных мест",
            11 to "Свободных мест", 21 to "Свободное место", 0 to "Свободных мест",
        ).forEach { (available, expected) ->
            item = SportCardFixtures.lesson().copy(available = available, limit = 40).toDetailsArgs()
            waitForIdle()
            tagged(SportDetailsSheetTestTags.CAPACITY_LABEL).assertTextEquals(expected)
            tagged(SportDetailsSheetTestTags.CAPACITY_FREE).assertTextEquals(if (available == 0) "нет" else "$available")
            assertNoTextOverflow()
        }
    }

    @Test
    fun aLessonShowsItsRingDurationAndConditions() = runComposeUiTest {
        setContent { Sheet(SportDetailsSamples.lesson) }

        val capacity = onNodeWithTag(SportDetailsSheetTestTags.CAPACITY).fetchSemanticsNode()
        assertEquals(
            listOf("Занято 13 из 20 мест, свободно 7"),
            capacity.config.getOrNull(SemanticsProperties.ContentDescription),
        )
        onNodeWithText("90 мин").assertExists()
        onNodeWithText("Пересечение с парой").assertExists()
        assertTouchTargets()
        assertNoTextOverflow()
    }

    @Test
    fun aQueuedBookingShowsItsRequestsAndHistoryWithoutCapacity() = runComposeUiTest {
        setContent { Sheet(SportDetailsSamples.queue) }

        onNodeWithTag(SportDetailsSheetTestTags.CAPACITY).assertDoesNotExist()
        onNodeWithText("2 / 5", useUnmergedTree = true).assertExists()
        onNodeWithText(SportDetailsSamples.queue.sectionName).assertExists()
        onNodeWithText("Заявка создана", useUnmergedTree = true).performScrollTo()
        onNodeWithText("6 сент., 18:30", useUnmergedTree = true).assertExists()
        assertTouchTargets()
        assertNoTextOverflow()
    }

    @Test
    fun aPredictionInventsNoPlacesAndKeepsItsPlace() = runComposeUiTest {
        setContent { Sheet(SportDetailsSamples.prediction) }

        onNodeWithTag(SportDetailsSheetTestTags.CAPACITY).assertDoesNotExist()
        onNodeWithTag(SportDetailsSheetTestTags.CONDITIONS).assertExists()
        onNodeWithText("Ждём расписание", useUnmergedTree = true).assertExists()
        onNodeWithText("Запишем, если занятие выйдет с теми же условиями.", useUnmergedTree = true).assertExists()
        onNodeWithText(SportCardFixtures.lesson().roomName).assertExists()
        assertNoTextOverflow()
    }

    @Test
    fun conditionsSeparateWarningsWaitingAndRestrictions() = runComposeUiTest {
        val base = SportCardFixtures.lesson()
        val cases = listOf(
            base.copy(intersection = true) to "Можно записаться",
            base.copy(available = 0, canSignIn = false) to "Можно ждать место",
            base.copy(canSignIn = false, unavailableReasons = listOf(UnavailableReason.HealthGroupMismatch)) to
                "Записаться нельзя",
            base.copy(isLessonReal = false, canSignIn = false) to "Ждём расписание",
            base.copy(canSignIn = false) to "My ITMO не разрешил запись",
            base.copy(typeId = 5, available = 0, canSignIn = false, unavailableReasons = listOf(UnavailableReason.DebtOnly)) to
                "Записаться нельзя",
            base.copy(canSignIn = false, unavailableReasons = listOf(UnavailableReason.Other("Явный запрет MyITMO"))) to
                "Записаться нельзя",
        )
        var item by mutableStateOf(base.toDetailsArgs())
        setContent { Sheet(item) }

        cases.forEach { (case, title) ->
            item = case.toDetailsArgs()
            waitForIdle()
            onNodeWithText(title, useUnmergedTree = true).assertExists()
            if (case.intersection) onNodeWithText("Пересечение с парой", useUnmergedTree = true).assertExists()
            assertNoTextOverflow()
        }
        onNodeWithText("Явный запрет MyITMO", useUnmergedTree = true).assertExists()
    }

    @Test
    fun friendsShowTheirRegistrationAndOpenTheirProfiles() = runComposeUiTest {
        setContent { Sheet(SportDetailsSamples.withFriends) }

        assertEquals(3, onAllNodesWithTag(SportDetailsSheetTestTags.FRIEND).fetchSemanticsNodes().size)
        onNodeWithText("Друзья · 3", useUnmergedTree = true).assertExists()
        onNodeWithText("Записан", useUnmergedTree = true).assertExists()
        onNodeWithText("Автозапись · 3 из 12", useUnmergedTree = true).assertExists()
        onNodeWithText("Не записан", useUnmergedTree = true).assertExists()
        onNodeWithText("Тестовый друг с длинным именем").performScrollTo().performClick()
        assertEquals(listOf(900001), profiles)
        assertTouchTargets()
    }

    private fun ComposeUiTest.tagged(tag: String) = onNodeWithTag(tag, useUnmergedTree = true)

    @Composable
    private fun Sheet(
        item: SportCommonDetailsArgs,
        time: AcademicTimeProvider = SportDetailsSamples.time,
        submission: SportDetailsSubmission = SportDetailsSubmission(),
        actionsEnabled: Boolean = true,
        busy: Boolean = false,
    ) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, NARROW_FONT_SCALE)) {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                Box(Modifier.size(NARROW_WIDTH, SHEET_HEIGHT)) {
                    SportDetailsSheet(item, time, submission, actions, Modifier.size(NARROW_WIDTH, SHEET_HEIGHT),
                        actionsEnabled = actionsEnabled, busy = busy)
                }
            }
        }
    }

    private companion object {
        const val NARROW_FONT_SCALE = 1.3f
        val NARROW_WIDTH = 320.dp
        val SHEET_HEIGHT = 802.dp
        const val LONG_SECTION = "Фитнес (функциональная тренировка с элементами кроссфита и растяжкой)"
    }
}
