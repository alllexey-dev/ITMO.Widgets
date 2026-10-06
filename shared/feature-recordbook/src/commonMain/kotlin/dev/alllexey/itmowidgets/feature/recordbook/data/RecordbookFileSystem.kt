package dev.alllexey.itmowidgets.feature.recordbook.data

import okio.FileSystem

/**
 * okio's `FileSystem.SYSTEM`, which okio declares per platform and not in common code: the real file system of the
 * recordbook's stores (`marks/`, `sheet_scores/`). Tests pass a fake through the stores' internal constructors.
 */
internal expect val RecordbookFileSystem: FileSystem
