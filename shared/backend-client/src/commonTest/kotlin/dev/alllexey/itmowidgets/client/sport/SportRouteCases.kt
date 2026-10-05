package dev.alllexey.itmowidgets.client.sport

import dev.alllexey.itmowidgets.client.sport.model.SportAutoSignRequest
import dev.alllexey.itmowidgets.client.sport.model.SportFreeSignRequest
import dev.alllexey.itmowidgets.client.support.RouteCase
import dev.alllexey.itmowidgets.client.users.SyntheticUsers

/** One [RouteCase] per public function of [SportApi], with synthetic arguments; names are the fixture IDs. */
object SportRouteCases {
    const val ENTRY_ID = 11L

    /** Above `Int.MAX_VALUE`, so a path written through an `Int` would show. */
    const val LESSON_ID = 2_147_483_648L

    val lessonIds = listOf(9001L, 9002L)
    val freeRequest = SportFreeSignRequest(lessonId = 9001, forceSign = true)
    val autoRequest = SportAutoSignRequest(prototypeLessonId = 9101)

    val syncSportLessons = RouteCase("syncSportLessons") { sport.syncSportLessons(lessonIds) }
    val friendsSportBookings = RouteCase("friendsSportBookings") { sport.friendsSportBookings() }
    val userSportBookings = RouteCase("userSportBookings") { sport.userSportBookings(SyntheticUsers.OTHER_ISU) }

    val mySportFreeSignEntries = RouteCase("mySportFreeSignEntries") { sport.mySportFreeSignEntries() }
    val createSportFreeSignEntry = RouteCase("createSportFreeSignEntry") {
        sport.createSportFreeSignEntry(freeRequest)
    }
    val cancelSportFreeSignEntry = RouteCase("cancelSportFreeSignEntry") { sport.cancelSportFreeSignEntry(ENTRY_ID) }
    val cancelSportFreeSignEntryByLesson = RouteCase("cancelSportFreeSignEntryByLesson") {
        sport.cancelSportFreeSignEntryByLesson(LESSON_ID)
    }
    val currentSportFreeSignQueues = RouteCase("currentSportFreeSignQueues") { sport.currentSportFreeSignQueues() }
    val markSportFreeSignEntrySatisfiedByLesson = RouteCase("markSportFreeSignEntrySatisfiedByLesson") {
        sport.markSportFreeSignEntrySatisfiedByLesson(LESSON_ID)
    }

    val sportAutoSignLimits = RouteCase("sportAutoSignLimits") { sport.sportAutoSignLimits() }
    val mySportAutoSignEntries = RouteCase("mySportAutoSignEntries") { sport.mySportAutoSignEntries() }
    val createSportAutoSignEntry = RouteCase("createSportAutoSignEntry") {
        sport.createSportAutoSignEntry(autoRequest)
    }
    val cancelSportAutoSignEntry = RouteCase("cancelSportAutoSignEntry") { sport.cancelSportAutoSignEntry(ENTRY_ID) }
    val cancelSportAutoSignEntryByLesson = RouteCase("cancelSportAutoSignEntryByLesson") {
        sport.cancelSportAutoSignEntryByLesson(LESSON_ID)
    }
    val currentSportAutoSignQueues = RouteCase("currentSportAutoSignQueues") { sport.currentSportAutoSignQueues() }
    val markSportAutoSignEntrySatisfiedByLesson = RouteCase("markSportAutoSignEntrySatisfiedByLesson") {
        sport.markSportAutoSignEntrySatisfiedByLesson(LESSON_ID)
    }

    val all: List<RouteCase> = listOf(
        syncSportLessons,
        friendsSportBookings,
        userSportBookings,
        mySportFreeSignEntries,
        createSportFreeSignEntry,
        cancelSportFreeSignEntry,
        cancelSportFreeSignEntryByLesson,
        currentSportFreeSignQueues,
        markSportFreeSignEntrySatisfiedByLesson,
        sportAutoSignLimits,
        mySportAutoSignEntries,
        createSportAutoSignEntry,
        cancelSportAutoSignEntry,
        cancelSportAutoSignEntryByLesson,
        currentSportAutoSignQueues,
        markSportAutoSignEntrySatisfiedByLesson,
    )
}
