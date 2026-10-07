package dev.alllexey.itmowidgets.feature.reviews.data

import okio.FileSystem

/**
 * okio's `FileSystem.SYSTEM`, which okio declares per platform and not in common code: the real file system of
 * the teacher levels store. Tests pass a fake through the store's internal constructor.
 */
internal expect val ReviewsFileSystem: FileSystem
