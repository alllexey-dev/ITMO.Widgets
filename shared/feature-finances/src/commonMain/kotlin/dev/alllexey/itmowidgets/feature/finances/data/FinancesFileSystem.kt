package dev.alllexey.itmowidgets.feature.finances.data

import okio.FileSystem

/** okio's `FileSystem.SYSTEM`, declared per platform; tests pass a fake through the store's internal constructor. */
internal expect val FinancesFileSystem: FileSystem
