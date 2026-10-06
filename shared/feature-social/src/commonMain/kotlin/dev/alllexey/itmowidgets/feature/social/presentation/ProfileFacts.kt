package dev.alllexey.itmowidgets.feature.social.presentation

import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.feature.social.domain.model.Person

enum class ProfileFactKind { POSITION, ROOM, EDUCATION }

data class ProfileFact(val kind: ProfileFactKind, val title: String, val detail: String?, val course: Int?)

fun profileFacts(person: Person?, fallbackGroup: UserGroup?): List<ProfileFact> {
    if (person == null) return fallbackGroup?.let {
        listOf(ProfileFact(ProfileFactKind.EDUCATION, it.name, it.facultyShortName, it.course))
    }.orEmpty()
    return buildList {
        person.positions.forEach {
            add(ProfileFact(ProfileFactKind.POSITION, checkNotNull(it.title ?: it.department),
                it.department.takeIf { _ -> it.title != null }, null))
        }
        person.rooms.forEach {
            add(ProfileFact(ProfileFactKind.ROOM, it.number, it.building, null))
        }
        person.education.forEach {
            add(ProfileFact(ProfileFactKind.EDUCATION, checkNotNull(it.group ?: it.faculty),
                it.faculty.takeIf { _ -> it.group != null }, it.course))
        }
    }
}

/**
 * One short line under the name: the role of the first position («Преподаватель»), or a short department when the
 * position has no title, otherwise the student group. The full positions follow under «Должности».
 */
sealed interface ProfileHeadline {
    data class Position(val role: String) : ProfileHeadline
    data class Group(val name: String, val course: Int?) : ProfileHeadline
}

fun profileHeadline(person: Person?, group: UserGroup?): ProfileHeadline? {
    person?.positions?.firstOrNull()?.let { position ->
        val role = position.title?.let(::shortRole)?.takeIf(String::isNotEmpty) ?: position.department?.let(::shortDepartment)
        if (role != null) return ProfileHeadline.Position(role)
    }
    person?.education?.firstOrNull { it.group != null }?.let { return ProfileHeadline.Group(checkNotNull(it.group), it.course) }
    return group?.let { ProfileHeadline.Group(it.name, it.course) }
}

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
