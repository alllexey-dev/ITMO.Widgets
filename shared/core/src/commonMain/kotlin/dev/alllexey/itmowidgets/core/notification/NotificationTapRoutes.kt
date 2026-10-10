package dev.alllexey.itmowidgets.core.notification

import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.push.FcmDecoder
import dev.alllexey.itmowidgets.client.push.FriendshipEventPayload
import dev.alllexey.itmowidgets.client.push.SportAutoSignLessonsPayload
import dev.alllexey.itmowidgets.client.push.SportFreeSignLessonsPayload
import dev.alllexey.itmowidgets.core.navigation.AppEntryIntents
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.navigation.EntryRouteParser
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.navigation.TabRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Where a tap on a notification opens the app (`docs/features/notifications.md`). A Backend push routes by the
 * payload type of its `data` envelope (Wire contract): a friendship event opens the actor's profile, a sport booking
 * its lesson (the sport tab when the push names several). A local notification of the iOS notifier carries its
 * [NotificationDestination] as [userInfoOf] writes it, Android's entry action and its arguments as strings, which
 * `EntryRouteParser` reads as it reads Android's notification intents. The iOS notification delegate hands the route
 * to the shared route queue.
 *
 * Reads only the fields it routes by, so a payload the handlers would reject still opens its tab; an unknown type or
 * a `data` string that is not an envelope opens nothing, and so does an action no local notification carries.
 */
object NotificationTapRoutes {

    /** The key of the envelope's JSON string in the push's `userInfo` (FCM's data message on Android). */
    const val DATA_KEY = "data"

    /** The key of a local notification's `AppEntryIntents` action. */
    const val ACTION_KEY = "action"

    /** The key of the profile's ISU next to [AppEntryIntents.ACTION_OPEN_USER_PROFILE]. */
    const val ISU_KEY = "isu"

    /**
     * The `userInfo` of a local notification that opens [destination]: plain strings only, so the system stores it
     * as it is. A subject's arguments use the keys of Android's intent extras ([RecordbookSubjectArgs]).
     */
    fun userInfoOf(destination: NotificationDestination): Map<String, String> = when (destination) {
        NotificationDestination.Sport -> action(AppEntryIntents.ACTION_OPEN_SPORT)
        is NotificationDestination.UserProfile ->
            action(AppEntryIntents.ACTION_OPEN_USER_PROFILE) + (ISU_KEY to destination.isu.toString())
        NotificationDestination.ScheduleChanges -> action(AppEntryIntents.ACTION_OPEN_SCHEDULE_CHANGES)
        NotificationDestination.Recordbook -> action(AppEntryIntents.ACTION_OPEN_RECORDBOOK)
        is NotificationDestination.RecordbookSubject ->
            action(AppEntryIntents.ACTION_OPEN_RECORDBOOK_SUBJECT) + destination.args.userInfo()
        NotificationDestination.BarsLogin -> action(AppEntryIntents.ACTION_OPEN_BARS_LOGIN)
    }

    /**
     * The route of a tapped notification's `userInfo`: a push's [DATA_KEY] envelope, else a local notification's
     * [ACTION_KEY] with its arguments; null for anything else. Values may be strings or numbers (an older build
     * stored the ISU as a number).
     */
    fun entryRoute(userInfo: Map<*, *>): EntryRoute? =
        entryRoute(userInfo[DATA_KEY] as? String) ?: localRoute(userInfo)

    fun entryRoute(data: String?): EntryRoute? {
        val envelope = try {
            FcmDecoder.envelope(data ?: return null)
        } catch (_: BackendException) {
            return null
        }
        return when (envelope.type) {
            FriendshipEventPayload.TYPE -> envelope.payload.actorIsu()?.let(::profileRoute)
            SportFreeSignLessonsPayload.TYPE, SportAutoSignLessonsPayload.TYPE ->
                sportRoute(envelope.payload.lessonId())
            else -> null
        }
    }

    private fun localRoute(userInfo: Map<*, *>): EntryRoute? {
        val action = (userInfo[ACTION_KEY] as? String)?.takeIf { it in LOCAL_ACTIONS } ?: return null
        return EntryRouteParser.parse(
            action = action,
            isu = userInfo.int(ISU_KEY),
            subject = userInfo.subjectArgs(),
        )
    }

    private fun action(value: String) = mapOf(ACTION_KEY to value)

    private fun RecordbookSubjectArgs.userInfo(): Map<String, String> = listOfNotNull(
        RecordbookSubjectArgs.ENTRY_ID to entryId.toString(),
        RecordbookSubjectArgs.PROGRAM_ID to programId.toString(),
        RecordbookSubjectArgs.SEMESTER to semester.toString(),
        RecordbookSubjectArgs.STUDY_YEAR_KEY to studyYear,
        barsPlan?.let { RecordbookSubjectArgs.BARS_PLAN to it.toString() },
        barsType?.let { RecordbookSubjectArgs.BARS_TYPE to it },
        barsIdentifier?.let { RecordbookSubjectArgs.BARS_IDENTIFIER to it },
    ).toMap()

    /** The subject's arguments as stored; `EntryRouteParser` drops a set that does not describe a page. */
    private fun Map<*, *>.subjectArgs(): RecordbookSubjectArgs? {
        val entryId = number(RecordbookSubjectArgs.ENTRY_ID) ?: return null
        val programId = number(RecordbookSubjectArgs.PROGRAM_ID) ?: return null
        val semester = int(RecordbookSubjectArgs.SEMESTER) ?: return null
        val studyYear = this[RecordbookSubjectArgs.STUDY_YEAR_KEY] as? String ?: return null
        return RecordbookSubjectArgs(
            entryId = entryId,
            programId = programId,
            semester = semester,
            studyYear = studyYear,
            barsPlan = number(RecordbookSubjectArgs.BARS_PLAN),
            barsType = this[RecordbookSubjectArgs.BARS_TYPE] as? String,
            barsIdentifier = this[RecordbookSubjectArgs.BARS_IDENTIFIER] as? String,
        )
    }

    /** A string or a number (on iOS an `NSNumber`, whose string form is its value); null for anything else. */
    private fun Map<*, *>.number(key: String): Long? = when (val value = this[key]) {
        null, is Boolean -> null
        is String -> value.toLongOrNull()
        else -> value.toString().toLongOrNull()
    }

    private fun Map<*, *>.int(key: String): Int? =
        number(key)?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

    /** The actions [userInfoOf] writes; never `ACTION_VIEW` or the widget and shortcut actions. */
    private val LOCAL_ACTIONS = setOf(
        AppEntryIntents.ACTION_OPEN_SPORT,
        AppEntryIntents.ACTION_OPEN_USER_PROFILE,
        AppEntryIntents.ACTION_OPEN_SCHEDULE_CHANGES,
        AppEntryIntents.ACTION_OPEN_RECORDBOOK,
        AppEntryIntents.ACTION_OPEN_RECORDBOOK_SUBJECT,
        AppEntryIntents.ACTION_OPEN_BARS_LOGIN,
    )

    private fun profileRoute(isu: Int) = EntryRoute(AppTab.ME, overlay = AppRoutes.UserProfile(isu))

    private fun sportRoute(lessonId: Long?) =
        EntryRoute(AppTab.SPORT, request = lessonId?.let { TabRequest.SportLesson(it) })

    private fun JsonObject.actorIsu(): Int? =
        runCatching { get("user")?.jsonObject?.get("isu")?.jsonPrimitive?.intOrNull }.getOrNull()?.takeIf { it > 0 }

    /** The one lesson of the push; `null` for several, none or a malformed list. */
    private fun JsonObject.lessonId(): Long? =
        runCatching { get("sportLessons")?.jsonArray?.singleOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.longOrNull }
            .getOrNull()?.takeIf { it > 0 }
}
