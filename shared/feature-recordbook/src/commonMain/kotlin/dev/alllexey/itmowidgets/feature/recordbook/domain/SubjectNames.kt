package dev.alllexey.itmowidgets.feature.recordbook.domain

/** One normalisation for every name match in the study feature: case, ё, whitespace. */
fun subjectNameKey(name: String): String = name.lowercase().replace('ё', 'е').replace(Regex("\\s+"), " ").trim()
