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
    const val ACTION_OPEN_DEVICES = "dev.alllexey.itmowidgets.action.OPEN_DEVICES"
    /** One connected client over «Устройства», its connection id in [EXTRA_CONNECTION]. */
    const val ACTION_OPEN_CONNECTION = "dev.alllexey.itmowidgets.action.OPEN_CONNECTION"
    /** A widget's lesson: the lesson sheet over the schedule, its [LessonDetailsArgs] as JSON in [EXTRA_LESSON]. */
    const val ACTION_OPEN_LESSON = "dev.alllexey.itmowidgets.action.OPEN_LESSON"

    const val EXTRA_LESSON = "dev.alllexey.itmowidgets.extra.LESSON"
    /** With [ACTION_OPEN_SPORT]: the sport lesson to show, and [EXTRA_SPORT_PREDICTED] for a predicted one. */
    const val EXTRA_SPORT_LESSON = "dev.alllexey.itmowidgets.extra.SPORT_LESSON"
    const val EXTRA_SPORT_PREDICTED = "dev.alllexey.itmowidgets.extra.SPORT_PREDICTED"
    const val EXTRA_CONNECTION = "dev.alllexey.itmowidgets.extra.CONNECTION"
}
