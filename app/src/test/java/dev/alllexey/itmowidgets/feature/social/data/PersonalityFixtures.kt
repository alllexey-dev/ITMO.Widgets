package dev.alllexey.itmowidgets.feature.social.data

/**
 * Synthetic MyITMO personalities answers, copied from MyItmoApi's `kmp/fixtures/personalities/` at the pinned
 * commit 3a85bd82a9c9a6101ae0f8aeb85cc3ed12f7bf5e (`gradle/myitmoapi.ref`). Kept as constants, not JVM
 * resources, so they move to `commonTest` unchanged. No value here is a real person, ISU number or contact.
 */
object PersonalityFixtures {

    /** `person.json`: a profile with a position and an education record. */
    const val PERSON_ISU = 100001

    const val PERSON = """{
  "error_code": 0,
  "result": {
    "isu": 100001,
    "fio": "Тестовый Профиль Синтетический",
    "gender": "male",
    "photo": "https://example.invalid/photo/100001.jpg",
    "contacts": [{ "contact": ["test@example.invalid"], "contact_alias": "E-mail" }],
    "rooms": [],
    "positions": [{ "department_name": "Тестовый отдел", "department_link": "https://example.invalid/dep", "position_name": "Инженер", "vacation": null, "start_vacation": null, "end_vacation": null }],
    "powers": [],
    "levels": null,
    "education": [{ "course": "3", "faculty_name": "Тестовый факультет", "group": "T3100" }],
    "activities": null,
    "exchange_training": false
  }
}"""

    /** `student.json`. */
    const val STUDENT_ISU = 123456

    const val STUDENT = """{
  "error_code": 0,
  "result": {
    "isu": 123456,
    "fio": "Тестовый Студент",
    "gender": "male",
    "photo": "https://example.test/student.jpg",
    "contacts": [],
    "rooms": [],
    "positions": [],
    "powers": [],
    "levels": null,
    "education": [
      {
        "course": "3",
        "group": "T1234",
        "faculty_name": "Тестовый факультет"
      }
    ],
    "activities": null,
    "exchange_training": false
  }
}"""

    /** `employee.json`. */
    const val EMPLOYEE_ISU = 234567

    const val EMPLOYEE = """{
  "error_code": 0,
  "result": {
    "isu": 234567,
    "fio": "Тестовый Сотрудник",
    "gender": "male",
    "photo": "https://example.test/employee.jpg",
    "contacts": [
      {
        "contact": [
          "teacher@example.test"
        ],
        "contact_alias": "Электронная почта"
      }
    ],
    "rooms": [],
    "positions": [
      {
        "department_name": "Тестовое подразделение",
        "department_link": "https://example.test/department",
        "position_name": "Преподаватель",
        "vacation": null,
        "start_vacation": null,
        "end_vacation": null
      }
    ],
    "powers": [
      {
        "dep_name": "Тестовое подразделение",
        "dep_link": "https://example.test/department",
        "power_name": "Тестовое полномочие"
      }
    ],
    "levels": {
      "rank": "Тестовое звание",
      "degree": "Тестовая степень"
    },
    "education": [],
    "activities": null,
    "exchange_training": false
  }
}"""

    /** `service.json`: no photo and no facts. */
    const val SERVICE_ISU = 345678

    const val SERVICE = """{
  "error_code": 0,
  "result": {
    "isu": 345678,
    "fio": "Тестовая Служебная Запись",
    "gender": "male",
    "photo": null,
    "contacts": [],
    "rooms": [],
    "positions": [],
    "powers": [],
    "levels": null,
    "education": [],
    "activities": null,
    "exchange_training": false
  }
}"""

    /** `missing-person.json`, observed with HTTP 400 for an unknown ISU. */
    const val MISSING_PERSON = """{
  "error_code": 100,
  "result": null
}"""

    /** `search.json`: one hit of 31; the directory e-mail must not leave the data layer. */
    const val SEARCH = """{
  "error_code": 0,
  "result": {
    "count": 31,
    "data": [
      {
        "id": 123456,
        "fio": "Тестовый Студент",
        "gender": "male",
        "phone": "",
        "email": "student@example.test",
        "work": "",
        "education": [],
        "photo": "https://example.test/student.jpg"
      }
    ]
  }
}"""

    /** `search-empty.json`. */
    const val SEARCH_EMPTY = """{
  "error_code": 0,
  "result": {
    "count": 0,
    "data": []
  }
}"""
}
