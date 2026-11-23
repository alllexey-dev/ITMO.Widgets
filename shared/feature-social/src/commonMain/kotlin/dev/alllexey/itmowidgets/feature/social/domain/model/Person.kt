package dev.alllexey.itmowidgets.feature.social.domain.model

data class Person(
    val isu: Int,
    val name: String,
    val photoUrl: String?,
    val positions: List<PersonPosition>,
    val rooms: List<PersonRoom>,
    val education: List<PersonEducation>,
    val contacts: List<PersonContact> = emptyList(),
)

data class PersonPosition(val title: String?, val department: String?)

data class PersonRoom(val number: String, val building: String?)

data class PersonEducation(val group: String?, val course: Int?, val faculty: String?)

/**
 * A directory contact: its [kind] read from the My ITMO alias, the alias itself as [label] (shown for an unknown kind),
 * the [value] as shown (`@handle` for Telegram, X and VK, the bare LinkedIn slug) and an optional safe destination.
 */
data class PersonContact(val kind: ContactKind, val label: String, val value: String, val url: String?)

/** The My ITMO contact types (`contact_alias`); [OTHER] keeps the alias as its label. */
enum class ContactKind { EMAIL, PHONE, WEB, VK, TELEGRAM, LINKEDIN, SKYPE, TWITTER, FACEBOOK, LIVEJOURNAL, OTHER }
