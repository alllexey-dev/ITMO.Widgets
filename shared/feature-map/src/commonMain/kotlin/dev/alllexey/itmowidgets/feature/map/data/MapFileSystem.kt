package dev.alllexey.itmowidgets.feature.map.data

import okio.FileSystem

/** The device's file system for the map cache; tests pass a fake one. */
internal expect val MapFileSystem: FileSystem
