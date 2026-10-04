package dev.alllexey.itmowidgets.core.navigation

/**
 * Entry points into the main screen: the actions of widget, tile, shortcut and
 * notification intents, and the activity they address by class name.
 *
 * Placed widgets, the tile and posted notifications hold pending intents with
 * these actions, so the values never change. A feature that builds such an
 * intent must not import the activity, so it uses `AppEntryIntentFactory`.
 */
object AppEntryIntents {

    const val MAIN_ACTIVITY = "dev.alllexey.itmowidgets.app.MainActivity"

    const val ACTION_OPEN_SPORT = "dev.alllexey.itmowidgets.action.OPEN_SPORT"
    const val ACTION_OPEN_USER_PROFILE = "dev.alllexey.itmowidgets.action.OPEN_USER_PROFILE"
    const val ACTION_OPEN_SCHEDULE = "dev.alllexey.itmowidgets.action.OPEN_SCHEDULE"
    const val ACTION_OPEN_SCHEDULE_CHANGES = "dev.alllexey.itmowidgets.action.OPEN_SCHEDULE_CHANGES"
    const val ACTION_OPEN_RECORDBOOK = "dev.alllexey.itmowidgets.action.OPEN_RECORDBOOK"
    const val ACTION_OPEN_RECORDBOOK_SUBJECT = "dev.alllexey.itmowidgets.action.OPEN_RECORDBOOK_SUBJECT"
    const val ACTION_OPEN_BARS_LOGIN = "dev.alllexey.itmowidgets.action.OPEN_BARS_LOGIN"
    const val ACTION_OPEN_QR_PASS = "dev.alllexey.itmowidgets.action.OPEN_QR_PASS"
    const val ACTION_OPEN_TODAY = "dev.alllexey.itmowidgets.action.OPEN_TODAY"
}
