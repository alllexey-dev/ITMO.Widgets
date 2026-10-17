package dev.alllexey.itmowidgets.core.navigation

import kotlinx.serialization.json.Json

/**
 * Turns an entry intent into an [EntryRoute]; plain values in, so it has no Android type and iOS shares it.
 * Malformed public intents never open an arbitrary profile or subject page.
 */
object EntryRouteParser {

    /** `Intent.ACTION_VIEW`, spelled out so the parser stays common code. */
    const val ACTION_VIEW = "android.intent.action.VIEW"

    /**
     * An intent replayed from Recents ([launchedFromHistory]) already ran its route and opens the app as it was.
     * A [ACTION_VIEW] intent carries an app [link]; links that are not the app's open nothing. A widget's [lesson] is
     * the JSON of its [LessonDetailsArgs] (malformed: the schedule alone), [sportLesson] a positive sport lesson id.
     */
    fun parse(
        action: String?,
        isu: Int? = null,
        subject: RecordbookSubjectArgs? = null,
        launchedFromHistory: Boolean = false,
        link: String? = null,
        lesson: String? = null,
        sportLesson: Long? = null,
        sportPredicted: Boolean = false,
    ): EntryRoute? = if (launchedFromHistory) null else when (action) {
        ACTION_VIEW -> linkRoute(AppLinks.parse(link))
        AppEntryIntents.ACTION_OPEN_SCHEDULE -> EntryRoute(AppTab.SCHEDULE)
        AppEntryIntents.ACTION_OPEN_LESSON -> EntryRoute(AppTab.SCHEDULE, overlay = lessonArgs(lesson)?.let(AppRoutes::LessonDetails))
        AppEntryIntents.ACTION_OPEN_SPORT -> EntryRoute(
            AppTab.SPORT,
            request = sportLesson?.takeIf { it != 0L }?.let { TabRequest.SportLesson(it, predicted = sportPredicted) }
        )
        AppEntryIntents.ACTION_OPEN_SCHEDULE_CHANGES -> EntryRoute(AppTab.HOME, overlay = AppRoutes.Bell)
        AppEntryIntents.ACTION_OPEN_USER_PROFILE -> isu?.takeIf { it > 0 }?.let(::profileRoute)
        AppEntryIntents.ACTION_OPEN_RECORDBOOK -> EntryRoute(AppTab.RECORDBOOK)
        // Arguments that do not describe a page still open the recordbook, where the subject is one tap away.
        AppEntryIntents.ACTION_OPEN_RECORDBOOK_SUBJECT -> EntryRoute(
            AppTab.RECORDBOOK,
            overlay = subject?.validOrNull()?.let(AppRoutes::RecordbookSubject)
        )
        AppEntryIntents.ACTION_OPEN_BARS_LOGIN -> EntryRoute(AppTab.RECORDBOOK, activity = ActivityRoute.BARS_LOGIN)
        AppEntryIntents.ACTION_OPEN_CONNECTIONS -> CONNECTIONS
        AppEntryIntents.ACTION_OPEN_QR_PASS ->
            EntryRoute(AppTab.HOME, overlay = AppRoutes.QrPass, shortcutId = EntryShortcuts.QR_PASS)
        AppEntryIntents.ACTION_OPEN_TODAY ->
            EntryRoute(AppTab.SCHEDULE, request = TabRequest.ScheduleToday, shortcutId = EntryShortcuts.TODAY)
        else -> null
    }

    private fun linkRoute(link: AppLink?): EntryRoute? = when (link) {
        is AppLink.Profile -> profileRoute(link.isu)
        is AppLink.SportLesson -> EntryRoute(AppTab.SPORT, request = TabRequest.SportLesson(link.lessonId))
        is AppLink.PredictedSportLesson ->
            EntryRoute(AppTab.SPORT, request = TabRequest.SportLesson(link.prototypeLessonId, predicted = true))
        // The web sign-in sheet reads the link as a scan; it becomes «Подключить устройство».
        is AppLink.Pair -> EntryRoute(AppTab.ME, overlay = AppRoutes.WebLogin(code = link.link.toUrl()))
        AppLink.Malformed -> EntryRoute(AppTab.HOME, alert = AppRoutes.LinkUnavailable)
        null -> null
    }

    private fun lessonArgs(json: String?): LessonDetailsArgs? =
        json?.let { runCatching { lessonJson.decodeFromString(LessonDetailsArgs.serializer(), it) }.getOrNull() }

    /** The JSON a widget writes into a lesson's entry intent or URL. */
    fun lessonJson(args: LessonDetailsArgs): String = lessonJson.encodeToString(LessonDetailsArgs.serializer(), args)

    private val lessonJson = Json { ignoreUnknownKeys = true }

    private fun profileRoute(isu: Int) = EntryRoute(AppTab.ME, overlay = AppRoutes.UserProfile(isu))

    /** «Подключённые устройства» over the Me tab: a new-connection notification opens it. */
    val CONNECTIONS = EntryRoute(AppTab.ME, overlay = AppRoutes.Connections)
}
