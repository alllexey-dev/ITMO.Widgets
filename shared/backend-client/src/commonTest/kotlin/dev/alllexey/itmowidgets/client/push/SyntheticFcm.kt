package dev.alllexey.itmowidgets.client.push

/**
 * FCM `data` strings as Backend 1.7.0 writes them with Gson until gate R: declaration order, `null` fields omitted
 * and `OffsetDateTime.toString()`, which drops `:00` seconds. The values equal the vendored `fcm/` fixtures (BK-03's
 * synthetic data), so both must decode to equal payloads.
 */
object SyntheticFcm {

    const val GSON_FRIENDSHIP_EVENT: String =
        """{"type":"FRIENDSHIP_EVENT_PAYLOAD","payload":{"event":"REQUEST_RECEIVED","user":{"isu":100002,""" +
            """"name":"Друг Первый","pictureUrl":"https://example.org/avatars/100002.jpg","groups":[""" +
            """{"name":"К3240","course":2,"facultyShortName":"ФИТИП"},""" +
            """{"name":"К3240c","course":2,"facultyShortName":"ФИТИП"}],""" +
            """"capabilities":{"canViewSchedule":true,"canViewSport":true,"canViewFriends":true}},""" +
            """"occurredAt":"2026-10-05T12:00+03:00"}}"""

    private const val GSON_LESSONS: String =
        """{"sportLessons":[{"id":9001,"sectionId":41,"sectionName":"Плавание","sectionLevel":1,"level":1,""" +
            """"typeId":2,"buildingId":13,"roomName":"Бассейн","start":"2026-10-07T10:00+03:00",""" +
            """"end":"2026-10-07T11:30+03:00","timeSlotId":3,"teacherIsu":200001,"teacherFio":"Тренер Тестовый"},""" +
            """{"id":9002,"sectionId":41,"sectionName":"Плавание","sectionLevel":1,"level":1,"typeId":2,""" +
            """"roomName":"Онлайн","start":"2026-10-08T12:00+03:00","end":"2026-10-08T13:30+03:00",""" +
            """"timeSlotId":3,"teacherIsu":200001,"teacherFio":"Тренер Тестовый"}]}"""

    const val GSON_SPORT_AUTO_SIGN_LESSONS: String =
        """{"type":"SPORT_AUTO_SIGN_LESSONS_PAYLOAD","payload":$GSON_LESSONS}"""

    /** [gson] with every date-time written as Jackson does, with `:00` seconds. */
    fun jacksonDates(gson: String): String = Regex("""T(\d\d:\d\d)([+-]\d\d:\d\d)""").replace(gson, "T$1:00$2")
}
