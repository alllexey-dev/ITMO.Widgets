package dev.alllexey.itmowidgets.feature.schedule.data.remote

/**
 * Synthetic answers shaped like MyItmoApi's `fixtures/schedule/personal.json` at the pin and Backend's
 * `contract/http/schedule/userLessons.json` (vendored by L19 into `:shared:backend-client`). Every value is invented.
 */
internal object ScheduleRemoteFixtures {

    /**
     * MyITMO's personal schedule for 2026-10-05..2026-10-07: a lecture with padded text, a remote practice without a
     * subject, teacher, room or group, a day with only a note and a sport lesson. `type` and `intersections` are
     * fields the clients do not read.
     */
    const val MY_ITMO_PERSONAL = """{
  "code": 0,
  "message": null,
  "data": [
    {
      "day_number": 1,
      "week_number": 6,
      "date": "2026-10-05",
      "note": null,
      "type": 0,
      "intersections": [],
      "lessons": [
        {
          "pair_id": 9100001,
          "subject": " Тестовая дисциплина ",
          "subject_id": 71001,
          "note": " Принести ноутбук ",
          "type": "Лекция",
          "time_start": "08:20",
          "time_end": "09:50",
          "teacher_id": 200101,
          "teacher_name": " Преподаватель Тестовый ",
          "room": "1404",
          "building": "Кронверкский пр., д.49, лит.А ",
          "format": "Очно",
          "work_type": "Лекции",
          "work_type_id": 1,
          "group": " T3100 ",
          "flow_type_id": 2,
          "flow_id": 81001,
          "zoom_url": null,
          "zoom_password": null,
          "zoom_info": null,
          "bld_id": 13,
          "format_id": 1,
          "main_bld_id": 13
        },
        {
          "pair_id": 9100002,
          "subject": null,
          "subject_id": 71002,
          "type": "Практика",
          "time_start": "10:00",
          "time_end": "11:30",
          "room": "  ",
          "building": "",
          "format": "Дистанционно",
          "work_type": "Практические занятия",
          "work_type_id": 3,
          "group": "",
          "flow_type_id": 2,
          "flow_id": 81002,
          "zoom_url": "https://example.invalid/meeting/1",
          "zoom_password": "synthetic",
          "zoom_info": "Ссылка в чате",
          "format_id": 3
        }
      ]
    },
    {
      "day_number": 2,
      "week_number": 6,
      "date": "2026-10-06",
      "note": "День без пар",
      "type": 0,
      "intersections": [],
      "lessons": []
    },
    {
      "day_number": 3,
      "week_number": 6,
      "date": "2026-10-07",
      "note": null,
      "type": 0,
      "intersections": [],
      "lessons": [
        {
          "pair_id": 9100003,
          "subject": "Физическая культура",
          "subject_id": 71003,
          "note": null,
          "type": "Спорт",
          "time_start": "13:30",
          "time_end": "15:00",
          "teacher_id": 200102,
          "teacher_name": "Тренер Тестовый",
          "room": "Зал 2",
          "building": "Ломоносова ул., д.9",
          "format": "Очно",
          "work_type": "Спорт",
          "work_type_id": 11,
          "group": "СПОРТ 1",
          "flow_type_id": 3,
          "flow_id": 81003,
          "bld_id": 273,
          "format_id": 1,
          "main_bld_id": 273
        }
      ]
    }
  ]
}"""

    /** Another user's lessons for 2026-10-05..2026-10-08 as Backend stores them: two on the 6th, one on the 8th. */
    const val BACKEND_USER_LESSONS = """{
  "success": true,
  "data": [
    {
      "pairId": 3100001,
      "date": "2026-10-06",
      "start": "08:20:00",
      "end": "09:50:00",
      "type": "Лекции",
      "typeId": 1,
      "note": " Принести ноутбук ",
      "subjectName": " Математический анализ ",
      "subjectId": 501,
      "groupName": " ЛЕК МАТАН 3.1 ",
      "flowId": 7001,
      "flowTypeId": 2,
      "teacherIsu": 200001,
      "teacherFio": " Преподаватель Тестовый ",
      "room": "1404",
      "building": "Кронверкский пр., д.49",
      "buildingId": 13,
      "mainBuildingId": 13,
      "format": "Очно",
      "formatId": 1
    },
    {
      "pairId": 3100002,
      "date": "2026-10-06",
      "start": "10:00:00",
      "end": "11:30:00",
      "type": "Практические занятия",
      "typeId": 3,
      "note": null,
      "subjectName": "Программирование",
      "subjectId": 502,
      "groupName": "ПРАК ПРОГ 3.1.2",
      "flowId": 7002,
      "flowTypeId": 2,
      "teacherIsu": null,
      "teacherFio": null,
      "room": " ",
      "building": null,
      "buildingId": null,
      "mainBuildingId": null,
      "format": "Дистанционно",
      "formatId": 3
    },
    {
      "pairId": 3100003,
      "date": "2026-10-08",
      "start": "13:30:00",
      "end": "15:00:00",
      "type": "Лабораторные работы",
      "typeId": 2,
      "note": null,
      "subjectName": "Физика",
      "subjectId": 503,
      "groupName": "ЛАБ ФИЗ 3.1",
      "flowId": 7003,
      "flowTypeId": 2,
      "teacherIsu": 200002,
      "teacherFio": "Преподаватель Второй",
      "room": "2220",
      "building": "Ломоносова ул., д.9",
      "buildingId": 273,
      "mainBuildingId": 273,
      "format": "Очно",
      "formatId": 1
    }
  ],
  "error": null
}"""
}
