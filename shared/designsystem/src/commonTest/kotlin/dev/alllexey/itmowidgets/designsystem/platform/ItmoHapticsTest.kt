package dev.alllexey.itmowidgets.designsystem.platform

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlin.test.Test
import kotlin.test.assertEquals

class ItmoHapticsTest {
    private class RecordingFeedback : HapticFeedback {
        val played = mutableListOf<HapticFeedbackType>()

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            played += hapticFeedbackType
        }
    }

    @Test
    fun materialStaysSilent() {
        val feedback = RecordingFeedback()
        val haptics = PlatformHaptics(ItmoPlatformStyle.Material, feedback)

        ItmoHapticEvent.entries.forEach(haptics::perform)

        assertEquals(emptyList(), feedback.played)
    }

    @Test
    fun iosPlaysSuccessAndErrorThroughCompose() {
        val feedback = RecordingFeedback()
        val haptics = PlatformHaptics(ItmoPlatformStyle.Ios, feedback)

        ItmoHapticEvent.entries.forEach(haptics::perform)

        assertEquals(listOf(HapticFeedbackType.Confirm, HapticFeedbackType.Reject), feedback.played)
    }
}
