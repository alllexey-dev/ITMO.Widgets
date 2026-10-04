package dev.alllexey.itmowidgets.testing

import dev.alllexey.itmowidgets.core.debug.PreviewAppearance

// The schedule debug hosts' view of the shared matrix; a port deletes this file with its hosts.

fun Appearances.Spec.toScheduleLifecycle(): PreviewAppearance = toPreview()
fun Appearances.Spec.toScheduleChanges(): PreviewAppearance = toPreview()
