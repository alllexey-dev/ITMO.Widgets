package dev.alllexey.itmowidgets.core.storage

import okio.FileSystem

/** okio's `FileSystem.SYSTEM`, which okio declares per platform and not in common code. */
internal expect val SystemFileSystem: FileSystem
