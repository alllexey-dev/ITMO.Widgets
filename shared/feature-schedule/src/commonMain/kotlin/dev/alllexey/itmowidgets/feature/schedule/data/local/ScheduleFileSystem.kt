package dev.alllexey.itmowidgets.feature.schedule.data.local

import okio.FileSystem

/**
 * okio's `FileSystem.SYSTEM`, which okio declares per platform and not in common code: the real file system of the
 * schedule's stores. Tests pass a fake through the stores' internal constructors.
 */
internal expect val ScheduleFileSystem: FileSystem
