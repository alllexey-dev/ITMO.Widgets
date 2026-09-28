package dev.alllexey.itmowidgets.feature.social.data

import api.myitmo.model.personality.Education
import api.myitmo.model.personality.Personality
import api.myitmo.model.personality.Position
import api.myitmo.model.personality.Room
import dev.alllexey.itmowidgets.feature.social.domain.model.Person
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonEducation
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonPosition
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonRoom

internal fun Personality.toPerson(isu: Int) = Person(
    isu = isu,
    name = fio?.trim().orEmpty(),
    photoUrl = photoUrl.clean(),
    positions = positions.orEmpty().mapNotNull(Position::toModel).distinct(),
    rooms = rooms.orEmpty().mapNotNull(Room::toModel).distinct(),
    education = education.orEmpty().mapNotNull(Education::toModel).distinct(),
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
    return PersonEducation(group, course?.trim()?.toIntOrNull()?.takeIf { it > 0 }, faculty)
}

private fun String?.clean(): String? = this?.trim()?.takeIf(String::isNotEmpty)
