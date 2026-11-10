package dev.alllexey.itmowidgets.feature.social.data

import dev.alllexey.itmowidgets.core.url.HttpsNavigationPolicy
import dev.alllexey.itmoapi.myitmo.personalities.Education
import dev.alllexey.itmoapi.myitmo.personalities.Personality
import dev.alllexey.itmoapi.myitmo.personalities.Position
import dev.alllexey.itmoapi.myitmo.personalities.Room
import dev.alllexey.itmowidgets.feature.social.domain.model.Person
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonContact
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonEducation
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonPosition
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonRoom

/** Gender and the exchange flag are dropped; directory contacts stay on the device. */
internal fun Personality.toPerson(isu: Int) = Person(
    isu = isu,
    name = fio.trim(),
    photoUrl = photoUrl.clean(),
    positions = positions.mapNotNull(Position::toModel).distinct(),
    rooms = rooms.mapNotNull(Room::toModel).distinct(),
    education = education.mapNotNull(Education::toModel).distinct(),
    contacts = contacts.flatMap { group ->
        group.contact.mapNotNull { value ->
            val cleaned = value.clean() ?: return@mapNotNull null
            PersonContact(group.contactAlias.trim(), cleaned, contactDestination(group.contactAlias, cleaned))
        }
    }.distinct(),
)

private fun Position.toModel(): PersonPosition? {
    val title = positionName.clean()
    val department = departmentName.clean()
    return if (title == null && department == null) null else PersonPosition(title, department)
}

private fun Room.toModel(): PersonRoom? {
    val number = roomNumber.clean() ?: return null
    return PersonRoom(number, bldName.clean())
}

private fun Education.toModel(): PersonEducation? {
    val group = group.clean()
    val faculty = facultyName.clean()
    if (group == null && faculty == null) return null
    return PersonEducation(group, course.trim().toIntOrNull()?.takeIf { it > 0 }, faculty)
}

internal fun String?.clean(): String? = this?.trim()?.takeIf(String::isNotEmpty)

/** Only known contact types become destinations; unknown directory values remain readable. */
internal fun contactDestination(alias: String, value: String): String? {
    val type = alias.trim().lowercase()
    val trimmed = value.trim()
    if (trimmed.isEmpty() || trimmed.any { it.code < 32 || it.code == 127 }) return null
    if ((type.contains("почт") || type.contains("mail")) &&
        trimmed.matches(Regex("[A-Za-z0-9._+%-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"))) return "mailto:$trimmed"
    if (type.contains("телефон") || type.contains("phone")) {
        val number = trimmed.filterNot { it == ' ' || it == '-' || it == '(' || it == ')' }
        if (number.matches(Regex("\\+?[0-9]{3,}"))) return "tel:$number"
    }
    if (trimmed.any { it.isWhitespace() }) return null
    if (HttpsNavigationPolicy.isNavigable(trimmed)) return trimmed
    val base = when {
        type.contains("linkedin") -> "https://www.linkedin.com/"
        type.contains("twitter") || type == "x" -> "https://twitter.com/"
        type == "vk" || type.contains("вконтакте") -> "https://vk.com/"
        type.contains("telegram") || type.contains("телеграм") -> "https://t.me/"
        else -> return null
    }
    val path = trimmed.removePrefix("@")
    return if (path.matches(Regex("[A-Za-z0-9._/-]+")) && !path.contains("..")) base + path else null
}
