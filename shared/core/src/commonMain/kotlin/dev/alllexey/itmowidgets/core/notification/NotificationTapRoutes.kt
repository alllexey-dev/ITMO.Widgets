package dev.alllexey.itmowidgets.core.notification

import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.push.FcmDecoder
import dev.alllexey.itmowidgets.client.push.FriendshipEventPayload
import dev.alllexey.itmowidgets.client.push.SportAutoSignLessonsPayload
import dev.alllexey.itmowidgets.client.push.SportFreeSignLessonsPayload
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.navigation.TabRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Where a tap on a Backend push opens the app, by the payload type of its `data` envelope (`docs/features/
 * notifications.md`, Wire contract): a friendship event opens the actor's profile, a sport booking its lesson (the
 * sport tab when the push names several). The iOS notification delegate hands the route to the shared route queue.
 *
 * Reads only the fields it routes by, so a payload the handlers would reject still opens its tab; an unknown type or
 * a `data` string that is not an envelope opens nothing.
 */
object NotificationTapRoutes {

    /** The key of the envelope's JSON string in the push's `userInfo` (FCM's data message on Android). */
    const val DATA_KEY = "data"

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
