package dev.alllexey.itmowidgets.feature.sport.data.bell

/**
 * Queue results another process recorded: the iOS notification service books in Swift and leaves each result in the
 * App Group. [take] hands them over once and forgets them. Android books in the app process and has none.
 */
fun interface SportResultInbox {
    suspend fun take(): List<SportResult>
}
