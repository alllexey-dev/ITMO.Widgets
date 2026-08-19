package dev.alllexey.itmowidgets.feature.schedule.ui.widget

internal fun compactLessonDetails(location: String, teacher: String?): String {
    return listOf(location.trim(), teacher?.let(::compactTeacherName).orEmpty())
        .filter(String::isNotBlank)
        .joinToString(" · ")
}

internal fun compactTeacherName(value: String): String {
    val parts = value.trim().split(WHITESPACE).filter(String::isNotBlank)
    if (parts.size < 2) return parts.firstOrNull().orEmpty()

    return buildString {
        append(parts.first())
        parts.drop(1).take(2).forEach { part ->
            append(' ')
            append(part.first())
            append('.')
        }
    }
}

private val WHITESPACE = Regex("\\s+")
