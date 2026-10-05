package dev.alllexey.itmowidgets.core.storage

import java.io.File
import okio.Path.Companion.toOkioPath

/** An [AtomicTextFile] at a `java.io.File`, for the app-side stores that still take one. */
fun AtomicTextFile(file: File): AtomicTextFile = AtomicTextFile(file.toOkioPath())
