package dev.alllexey.itmowidgets.feature.social.data.demo

import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.demo.DemoPerson
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.social.PersonSearchResult
import dev.alllexey.itmowidgets.feature.social.domain.model.Person
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonEducation
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonPosition
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonRoom

/**
 * Anna's friends, requests and the people of the demo set as Backend and the My ITMO directory would describe them.
 * Teachers are not ITMO.Widgets users: Backend does not know them, My ITMO does.
 */
object DemoSocial {

    val me: UserSummary get() = DemoPeople.ME_PERSON.summary()

    fun friends(): List<UserProfile> = DemoPeople.FRIENDS.map { it.profile(RelationshipState.FRIENDS) }

    fun requests(): FriendRequests = FriendRequests(
        incoming = listOf(DemoPeople.SOFIA.profile(RelationshipState.INCOMING)),
        outgoing = listOf(DemoPeople.ARTEM.profile(RelationshipState.OUTGOING))
    )

    /** A registered student as the viewer sees them, or null for teachers and strangers. */
    fun profile(isu: Int): UserProfile? {
        val person = DemoPeople.byIsu(isu)?.takeIf { it.group != null } ?: return null
        return person.profile(relationshipWith(isu))
    }

    fun userFriends(isu: Int): List<UserProfile>? {
        val friends = FRIENDS_OF[isu] ?: return null
        return friends.map { it.profile(relationshipWith(it.isu)) }
    }

    fun lookup(isus: List<Int>): List<UserProfile> = isus.distinct().mapNotNull(::profile)

    fun person(isu: Int): Person? {
        val person = DemoPeople.byIsu(isu) ?: return null
        val group = person.group
        return if (group != null) {
            Person(
                isu = isu,
                name = person.name,
                photoUrl = null,
                positions = emptyList(),
                rooms = emptyList(),
                education = listOf(PersonEducation(group, DemoPeople.ME_COURSE, FACULTIES.getValue(group.first())))
            )
        } else {
            val (title, department, room) = STAFF.getValue(isu)
            Person(
                isu = isu,
                name = person.name,
                photoUrl = null,
                positions = listOf(PersonPosition(title, department)),
                rooms = listOfNotNull(room),
                education = emptyList()
            )
        }
    }

    /** People whose name contains every word of [query], as the My ITMO directory would find them. */
    fun search(query: String): List<PersonSearchResult> {
        val words = normalized(query).split(' ').filter(String::isNotEmpty)
        if (words.isEmpty()) return emptyList()
        return DemoPeople.EVERYONE
            .filter { person -> words.all { it in normalized(person.name) } }
            .map { person -> PersonSearchResult(person.isu, person.name, null, profile(person.isu)) }
    }

    private fun relationshipWith(isu: Int): RelationshipState = when (isu) {
        in DemoPeople.FRIENDS.map(DemoPerson::isu) -> RelationshipState.FRIENDS
        DemoPeople.SOFIA.isu -> RelationshipState.INCOMING
        DemoPeople.ARTEM.isu -> RelationshipState.OUTGOING
        else -> RelationshipState.NONE
    }

    private fun DemoPerson.profile(relationship: RelationshipState) = UserProfile(summary(), relationship)

    private fun normalized(text: String) = text.lowercase().replace('ё', 'е').replace(Regex("\\s+"), " ").trim()

    private val FACULTIES = mapOf(
        'K' to "факультет инфокоммуникационных технологий",
        'M' to "факультет информационных технологий и программирования"
    )

    private const val KRONVA = "Кронверкский пр., д.49, лит.А"
    private const val LOMO = "ул. Ломоносова, д.9, лит.М"
    private const val BIRZHA = "Биржевая линия, д.14-16, лит.А"
    private const val SPORT_CENTRE = "кафедра физического воспитания"

    private val STAFF: Map<Int, Triple<String, String, PersonRoom?>> = mapOf(
        DemoPeople.MATH_TEACHER.isu to Triple("доцент", "факультет систем управления и робототехники", PersonRoom("1404", KRONVA)),
        DemoPeople.DISCRETE_TEACHER.isu to Triple("старший преподаватель", "факультет инфокоммуникационных технологий", PersonRoom("1229", LOMO)),
        DemoPeople.ALGORITHMS_TEACHER.isu to Triple("доцент", "факультет информационных технологий и программирования", PersonRoom("2310", KRONVA)),
        DemoPeople.DATABASES_TEACHER.isu to Triple("доцент", "факультет инфокоммуникационных технологий", PersonRoom("311", BIRZHA)),
        DemoPeople.ENGLISH_TEACHER.isu to Triple("старший преподаватель", "центр иностранных языков", PersonRoom("1206", LOMO)),
        DemoPeople.SWIMMING_COACH.isu to Triple("преподаватель", SPORT_CENTRE, null),
        DemoPeople.VOLLEYBALL_COACH.isu to Triple("старший преподаватель", SPORT_CENTRE, null),
        DemoPeople.TABLE_TENNIS_COACH.isu to Triple("преподаватель", SPORT_CENTRE, null),
        DemoPeople.FITNESS_COACH.isu to Triple("преподаватель", SPORT_CENTRE, null),
        DemoPeople.HISTORY_TEACHER.isu to Triple("доцент", "факультет социальных и гуманитарных наук", PersonRoom("301", BIRZHA))
    )

    private val FRIENDS_OF: Map<Int, List<DemoPerson>> = mapOf(
        DemoPeople.IVAN.isu to listOf(DemoPeople.ME_PERSON, DemoPeople.MARIA, DemoPeople.DMITRY),
        DemoPeople.MARIA.isu to listOf(DemoPeople.ME_PERSON, DemoPeople.IVAN, DemoPeople.POLINA),
        DemoPeople.DMITRY.isu to listOf(DemoPeople.ME_PERSON, DemoPeople.IVAN, DemoPeople.ARTEM),
        DemoPeople.POLINA.isu to listOf(DemoPeople.ME_PERSON, DemoPeople.MARIA, DemoPeople.SOFIA)
    )
}
