package dev.alllexey.itmowidgets.feature.social.domain.model

data class Person(
    val isu: Int,
    val name: String,
    val photoUrl: String?,
    val positions: List<PersonPosition>,
    val rooms: List<PersonRoom>,
    val education: List<PersonEducation>,
)

data class PersonPosition(val title: String?, val department: String?)

data class PersonRoom(val number: String, val building: String?)

data class PersonEducation(val group: String?, val course: Int?, val faculty: String?)
