package dev.alllexey.itmowidgets.client.app

import kotlinx.serialization.Serializable

/**
 * One app's release metadata, independent of Backend and client versions. All three strings are required and
 * never `null`; a missing or `null` one fails decoding. [note] is plain text (not HTML or Markdown) for an update
 * notice and may be empty. The client neither compares versions nor enforces an update.
 */
@Serializable
data class AppVersionInfo(
    val minVersion: String,
    val latestVersion: String,
    val note: String,
)
