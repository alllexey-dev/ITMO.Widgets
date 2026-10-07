package dev.alllexey.itmowidgets.feature.settings.ui.ics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.schedule.IcsFile
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsExportUiState
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsRangeKind
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The cases of the View sheet on `IcsExportSheetContent` at 320 dp and font scale 1.3: every state in one area of the
 * same height, at least 288 dp; the ranges send their kind; the file is sent or opened, the open action only when an
 * app opens `.ics`; an empty range offers another and a failure retries; the hidden states are not there to tap.
 * Choosing the dates, sharing and opening are the host's intents and stay in `SettingsNavigationTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h891dp")
class IcsExportSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val chosen = mutableListOf<IcsRangeKind>()
    private val sent = mutableListOf<IcsFile>()
    private val opened = mutableListOf<IcsFile>()
    private var others = 0
    private var retries = 0
    private val actions = IcsExportActions(
        onChoose = { chosen += it },
        onSend = { sent += it },
        onOpen = { opened += it },
        onChooseAnother = { others++ },
        onRetry = { retries++ },
    )

    private var state: IcsExportUiState by mutableStateOf(IcsExportSamples.choose)
    private var openable by mutableStateOf(true)

    private var locale: Locale? = null

    /** Compose plurals follow the process locale, which the app keeps Russian; the JVM's is not. */
    @Before
    fun russian() {
        locale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ru"))
    }

    @After
    fun restoreLocale() {
        locale?.let(Locale::setDefault)
    }

    @Test
    fun icsExportSheetShowsEveryStateInOneArea() {
        show()
        val heights = STATES.map { shown ->
            state = shown
            compose.waitForIdle()
            compose.assertNoTextOverflow()
            compose.assertTouchTargets()
            compose.onNodeWithTag(IcsExportTestTags.AREA).fetchSemanticsNode().size.height
        }

        assertEquals("Every state takes the same area: $heights", 1, heights.distinct().size)
        val minimum = with(compose.density) { 288.dp.roundToPx() }
        assertTrue("The area is ${heights.first()} px, under 288 dp", heights.first() >= minimum)
    }

    @Test
    fun eachRangeSendsItsKindAndTheOtherStatesAreNotThere() {
        show()
        for (text in listOf("Неделя", "2–8 октября", "2 недели", "2–15 октября", "До конца семестра", "до 31 января",
            "Свои даты", "Выбрать в календаре")) {
            compose.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
        }
        for (tag in listOf(IcsExportTestTags.SEND, IcsExportTestTags.OPEN, IcsExportTestTags.STATE)) {
            compose.onNodeWithTag(tag).assertDoesNotExist()
        }

        IcsRangeKind.entries.forEach { compose.onNodeWithTag(IcsExportTestTags.range(it)).performClick() }

        assertEquals(IcsRangeKind.entries, chosen)
    }

    @Test
    fun theFileIsSentOrOpenedAndOpenOnlyWhenAnAppOpensIt() {
        state = IcsExportSamples.ready
        show()
        compose.onNodeWithText("23 пары, 2–8 октября").assertIsDisplayed()
        compose.onNodeWithTag(IcsExportTestTags.RANGES).assertDoesNotExist()

        compose.onNodeWithTag(IcsExportTestTags.SEND).performClick()
        compose.onNodeWithTag(IcsExportTestTags.OPEN).performClick()
        openable = false
        compose.waitForIdle()

        compose.onNodeWithTag(IcsExportTestTags.OPEN).assertDoesNotExist()
        assertEquals(listOf(IcsExportSamples.file), sent)
        assertEquals(listOf(IcsExportSamples.file), opened)
    }

    @Test
    fun anEmptyRangeOffersAnotherAndAFailureRetries() {
        state = IcsExportUiState.Empty
        show()
        compose.onNodeWithText("В этом периоде пар нет").assertIsDisplayed()
        compose.onNodeWithText("Выбрать другой период").performClick()

        state = IcsExportSamples.failed
        compose.waitForIdle()
        compose.onNodeWithText("Выбрать другой период").assertDoesNotExist()
        compose.onNodeWithText("Повторить").performClick()

        assertEquals(1, others)
        assertEquals(1, retries)
        assertTrue(chosen.isEmpty())
    }

    private fun show() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, LARGE_FONT)) {
                ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                    Box(Modifier.width(320.dp).heightIn(max = 802.dp)) {
                        IcsExportSheetContent(state, actions, canOpen = { openable })
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private companion object {
        const val LARGE_FONT = 1.3f
        val STATES = listOf(
            IcsExportSamples.choose,
            IcsExportUiState.Preparing,
            IcsExportSamples.ready,
            IcsExportUiState.Empty,
            IcsExportSamples.failed,
        )
    }
}
