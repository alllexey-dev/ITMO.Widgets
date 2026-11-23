package dev.alllexey.itmowidgets.feature.social.data

import dev.alllexey.itmowidgets.core.url.HttpsNavigationPolicy
import dev.alllexey.itmoapi.myitmo.personalities.Education
import dev.alllexey.itmoapi.myitmo.personalities.Personality
import dev.alllexey.itmoapi.myitmo.personalities.Position
import dev.alllexey.itmoapi.myitmo.personalities.Room
import dev.alllexey.itmowidgets.feature.social.domain.model.ContactKind
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
            personContact(group.contactAlias, cleaned)
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

/** The kind of a My ITMO `contact_alias` (lowercase Latin in the directory, older Russian labels too). */
internal fun contactKind(alias: String): ContactKind {
    val type = alias.trim().lowercase()
    return when {
        type.contains("mail") || type.contains("почт") -> ContactKind.EMAIL
        type.contains("phone") || type.contains("телефон") -> ContactKind.PHONE
        type == "web" || type == "site" || type.contains("сайт") -> ContactKind.WEB
        type == "vk" || type.contains("vkontakte") || type.contains("вконтакте") -> ContactKind.VK
        type.contains("telegram") || type.contains("телеграм") -> ContactKind.TELEGRAM
        type.contains("linkedin") -> ContactKind.LINKEDIN
        type.contains("skype") -> ContactKind.SKYPE
        type.contains("twitter") || type == "x" -> ContactKind.TWITTER
        type.contains("facebook") -> ContactKind.FACEBOOK
        type == "lj" || type.contains("livejournal") -> ContactKind.LIVEJOURNAL
        else -> ContactKind.OTHER
    }
}

/**
 * A directory value as a contact: only known shapes become destinations, everything else stays readable plain text.
 * Social handles get a tidy value (`@handle`, the bare LinkedIn slug); mail, phone and sites are shown as they are.
 * Skype is shut down, so it never links.
 */
internal fun personContact(alias: String, value: String): PersonContact {
    val kind = contactKind(alias)
    val trimmed = value.trim()
    val plain = PersonContact(kind, alias.trim(), trimmed, null)
    if (trimmed.isEmpty() || trimmed.any { it.code < 32 || it.code == 127 }) return plain
    if (kind == ContactKind.EMAIL && trimmed.matches(EMAIL)) return plain.copy(url = "mailto:$trimmed")
    if (kind == ContactKind.PHONE) {
        val number = trimmed.filterNot { it == ' ' || it == '-' || it == '(' || it == ')' }
        if (number.matches(PHONE)) return plain.copy(url = "tel:$number")
    }
    if (trimmed.any(Char::isWhitespace)) return plain
    return when (kind) {
        ContactKind.VK -> handle(trimmed, VK_HOSTS, DOTTED_HANDLE)?.let { plain.copy(value = "@$it", url = "https://vk.com/$it") }
        ContactKind.TELEGRAM -> handle(trimmed, TELEGRAM_HOSTS, HANDLE)?.let { plain.copy(value = "@$it", url = "https://t.me/$it") }
        ContactKind.TWITTER -> handle(trimmed, TWITTER_HOSTS, HANDLE)?.let { plain.copy(value = "@$it", url = "https://twitter.com/$it") }
        ContactKind.FACEBOOK -> handle(trimmed, FACEBOOK_HOSTS, DOTTED_HANDLE)?.let { plain.copy(url = "https://www.facebook.com/$it") }
        ContactKind.LINKEDIN -> linkedInSlug(trimmed)?.let { plain.copy(value = it, url = "https://www.linkedin.com/in/$it") }
        ContactKind.WEB, ContactKind.LIVEJOURNAL -> siteUrl(trimmed)?.let { plain.copy(url = it) }
        ContactKind.SKYPE -> null
        ContactKind.EMAIL, ContactKind.PHONE, ContactKind.OTHER -> trimmed.takeIf(HttpsNavigationPolicy::isNavigable)?.let { plain.copy(url = it) }
    } ?: plain
}

/** `handle`, `@handle`, `<host>/handle` or a link to it on one of [hosts]; null for a query, a deeper path or other text. */
private fun handle(value: String, hosts: Set<String>, pattern: Regex): String? {
    val rest = withoutHost(value, hosts) ?: return null
    if (rest.contains('?') || rest.contains('#')) return null
    return rest.trimEnd('/').removePrefix("@").takeIf(pattern::matches)
}

/** `in/<slug>`, `in/<slug>/`, `in/<slug>?trk=...`, a bare `<slug>` or a profile link, as the slug alone. */
private fun linkedInSlug(value: String): String? {
    val rest = withoutHost(value, LINKEDIN_HOSTS) ?: return null
    return rest.substringBefore('?').substringBefore('#').trimEnd('/').removePrefix("in/").takeIf(LINKEDIN_SLUG::matches)
}

/** The path after a known host (with or without a scheme), the value itself without one, null for another host. */
private fun withoutHost(value: String, hosts: Set<String>): String? {
    val schemeless = SCHEME.find(value)?.let { value.substring(it.range.last + 1) }
    val host = (schemeless ?: value).substringBefore('/').lowercase().removePrefix("www.").removePrefix("m.")
    return when {
        host in hosts -> (schemeless ?: value).substringAfter('/', "")
        schemeless != null -> null
        else -> value
    }
}

/** An https link as it is; a single host-like token without a scheme (`t.me/<h>`, `<name>.livejournal.com/`) gets one. */
private fun siteUrl(value: String): String? = when {
    HttpsNavigationPolicy.isNavigable(value) -> value
    value.contains("://") || !value.matches(HOST_LIKE) -> null
    else -> "https://$value".takeIf(HttpsNavigationPolicy::isNavigable)
}

private val EMAIL = Regex("[A-Za-z0-9._+%-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
private val PHONE = Regex("\\+?[0-9]{3,}")
private val SCHEME = Regex("^https?://", RegexOption.IGNORE_CASE)
private val HANDLE = Regex("[A-Za-z0-9_]+")
private val DOTTED_HANDLE = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*")
private val LINKEDIN_SLUG = Regex("[A-Za-z0-9_%-]+")
private val HOST_LIKE = Regex("([A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?\\.)+[A-Za-z]{2,}(:[0-9]{1,5})?([/?#]\\S*)?")
private val VK_HOSTS = setOf("vk.com", "vk.ru")
private val TELEGRAM_HOSTS = setOf("t.me", "telegram.me")
private val TWITTER_HOSTS = setOf("twitter.com", "x.com")
private val FACEBOOK_HOSTS = setOf("facebook.com", "fb.com")
private val LINKEDIN_HOSTS = setOf("linkedin.com")
