package dev.alllexey.itmowidgets.designsystem.components.controls

import dev.alllexey.itmowidgets.designsystem.platform.ItmoHapticEvent
import dev.alllexey.itmowidgets.designsystem.platform.ItmoHaptics

/** A fake [ItmoHaptics] for `LocalItmoHaptics`: it records the events a kit component fires, in order. */
internal class RecordingHaptics : ItmoHaptics {
    val events = mutableListOf<ItmoHapticEvent>()

    override fun perform(event: ItmoHapticEvent) {
        events += event
    }
}
