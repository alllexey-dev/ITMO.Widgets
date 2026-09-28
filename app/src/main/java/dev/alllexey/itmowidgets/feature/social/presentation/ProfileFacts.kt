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
