package dev.alllexey.itmowidgets.feature.social.data

import dev.alllexey.itmoapi.myitmo.personalities.Education
import dev.alllexey.itmoapi.myitmo.personalities.Personality
import dev.alllexey.itmoapi.myitmo.personalities.Position
import dev.alllexey.itmoapi.myitmo.personalities.Room
import dev.alllexey.itmowidgets.feature.social.domain.model.Person
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonEducation
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonPosition
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonRoom

/** Contacts, gender and the exchange flag are dropped here; they never leave the data layer. */
internal fun Personality.toPerson(isu: Int) = Person(
    isu = isu,
    name = fio.trim(),
    photoUrl = photoUrl.clean(),
    positions = positions.mapNotNull(Position::toModel).distinct(),
    rooms = rooms.mapNotNull(Room::toModel).distinct(),
    education = education.mapNotNull(Education::toModel).distinct(),
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
