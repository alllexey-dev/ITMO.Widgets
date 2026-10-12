package dev.alllexey.itmowidgets.testing

import dev.alllexey.itmowidgets.core.debug.PreviewAppearance

// The settings debug host's view of the shared matrix.

/** Also serves the schedule widget and cards, the widget preview and the design component visual tests. */
fun Appearances.Spec.toSettingsPreview(): PreviewAppearance = toPreview()
