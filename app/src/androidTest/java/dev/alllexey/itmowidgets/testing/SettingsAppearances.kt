package dev.alllexey.itmowidgets.testing

import dev.alllexey.itmowidgets.core.debug.PreviewAppearance

// The settings debug hosts' view of the shared matrix; a port deletes this file with its hosts.

fun Appearances.Spec.toSettingsNavigation(): PreviewAppearance = toPreview()

/** Also serves the schedule widget and cards, the widget preview and the design component visual tests. */
fun Appearances.Spec.toSettingsPreview(): PreviewAppearance = toPreview()
