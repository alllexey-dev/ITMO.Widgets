package dev.alllexey.itmowidgets.feature.social.presentation

/**
 * The role without its qualifications: the first clause before a parenthesis or a comma, capitalised
 * («преподаватель (квалификационная категория …)» → «Преподаватель»).
 */
fun shortRole(title: String): String =
    title.substringBefore('(').substringBefore(',').trim().replaceFirstChar { it.titlecase() }

/**
 * A department short enough for the headline: an abbreviation given in trailing parentheses, a short name as it
 * is, otherwise the initials of its first clause («Факультет информационных технологий и программирования» →
 * «ФИТиП»). The full name stays in the facts card.
 */
fun shortDepartment(department: String): String {
    val clause = department.substringBefore(',').trim()
    ABBREVIATION.find(clause)?.let { return it.groupValues[1].trim() }
    if (clause.length <= SHORT_DEPARTMENT) return clause
    val initials = clause.split(WORD_BREAK)
        .map { word -> word.trim { !it.isLetterOrDigit() } }
        .filter { it.isNotEmpty() && it.lowercase() !in SKIPPED_WORDS }
        .joinToString("") { word ->
            when {
                word.lowercase() == "и" -> "и"
                word.length > 1 && word.all { it.isUpperCase() || it.isDigit() } -> word
                else -> word.first().uppercase()
            }
        }
    return if (initials.length >= 2) initials else clause
}

private const val SHORT_DEPARTMENT = 20
private val ABBREVIATION = Regex("\\(([^()]{2,12})\\)$")
private val WORD_BREAK = Regex("[\\s-]+")
private val SKIPPED_WORDS = setOf("в", "во", "на", "по", "для", "с", "со", "о", "об", "при")
