package dev.alllexey.itmowidgets.feature.resources.data

/**
 * Backend's vendored links answers and request bodies, copied unchanged from
 * `shared/backend-client/src/commonTest/resources/contract/` at Backend commit ad8fa2dc3681dd57b6e8b797ace2f97322742555
 * (`BACKEND_COMMIT`). Kept as constants, not JVM resources, so the tests run in `commonTest`. Every value is synthetic.
 */
object LinksRemoteFixtures {

    /** `http/links/subjectLinks.json`. */
    const val SUBJECT_LINKS = """{
  "success": true,
  "data": {
    "mine": [
      {
        "id": "00000000-0000-4000-8000-000000000101",
        "subjectId": 501,
        "subjectName": "Математический анализ",
        "periodKey": "2026-1",
        "category": "SCORES",
        "url": "https://example.org/links/1",
        "title": null,
        "visibility": "PRIVATE",
        "flowId": null,
        "audienceLabel": null,
        "status": "PRIVATE",
        "reviewNote": null,
        "score": 2,
        "myVote": 0,
        "isMine": true,
        "reportedByMe": false,
        "author": null,
        "updatedAt": "2026-10-02T10:00:00Z"
      },
      {
        "id": "00000000-0000-4000-8000-000000000102",
        "subjectId": 501,
        "subjectName": "Математический анализ",
        "periodKey": "2026-1",
        "category": "QUEUE",
        "url": "https://example.org/links/2",
        "title": "Материалы",
        "visibility": "FLOW",
        "flowId": 7001,
        "audienceLabel": "ЛЕК МАТАН 3.1",
        "status": "PENDING",
        "reviewNote": null,
        "score": 1,
        "myVote": 0,
        "isMine": true,
        "reportedByMe": false,
        "author": null,
        "updatedAt": "2026-10-03T10:00:00Z"
      },
      {
        "id": "00000000-0000-4000-8000-000000000103",
        "subjectId": 501,
        "subjectName": "Математический анализ",
        "periodKey": "2026-1",
        "category": "CHAT",
        "url": "https://example.org/links/3",
        "title": "Материалы",
        "visibility": "ALL",
        "flowId": null,
        "audienceLabel": null,
        "status": "REJECTED",
        "reviewNote": "Ссылка ведёт не на тот предмет",
        "score": 0,
        "myVote": 0,
        "isMine": true,
        "reportedByMe": false,
        "author": null,
        "updatedAt": "2026-10-04T10:00:00Z"
      },
      {
        "id": "00000000-0000-4000-8000-000000000104",
        "subjectId": 501,
        "subjectName": "Математический анализ",
        "periodKey": "2026-1",
        "category": "OTHER",
        "url": "https://example.org/links/4",
        "title": "Материалы",
        "visibility": "ALL",
        "flowId": null,
        "audienceLabel": null,
        "status": "HIDDEN",
        "reviewNote": null,
        "score": -1,
        "myVote": 0,
        "isMine": true,
        "reportedByMe": false,
        "author": null,
        "updatedAt": "2026-10-01T10:00:00Z"
      }
    ],
    "shared": [
      {
        "id": "00000000-0000-4000-8000-000000000100",
        "subjectId": 501,
        "subjectName": "Математический анализ",
        "periodKey": "2026-1",
        "category": "MATERIALS",
        "url": "https://example.org/links/0",
        "title": "Материалы",
        "visibility": "ALL",
        "flowId": null,
        "audienceLabel": null,
        "status": "PUBLISHED",
        "reviewNote": null,
        "score": 3,
        "myVote": 1,
        "isMine": false,
        "reportedByMe": true,
        "author": {
          "isu": 100002,
          "name": "Друг Первый",
          "pictureUrl": "https://example.org/avatars/100002.jpg",
          "groups": [
            {
              "name": "К3240",
              "course": 2,
              "facultyShortName": "ФИТИП"
            },
            {
              "name": "К3240c",
              "course": 2,
              "facultyShortName": "ФИТИП"
            }
          ],
          "capabilities": {
            "canViewSchedule": true,
            "canViewSport": true,
            "canViewFriends": true
          }
        },
        "updatedAt": "2026-10-01T10:00:00Z"
      },
      {
        "id": "00000000-0000-4000-8000-000000000105",
        "subjectId": 501,
        "subjectName": "Математический анализ",
        "periodKey": "2026-1",
        "category": "TASKS",
        "url": "https://example.org/links/5",
        "title": "Материалы",
        "visibility": "FLOW",
        "flowId": 7001,
        "audienceLabel": "ЛЕК МАТАН 3.1",
        "status": "PUBLISHED",
        "reviewNote": null,
        "score": -2,
        "myVote": -1,
        "isMine": false,
        "reportedByMe": false,
        "author": {
          "isu": 100002,
          "name": "Друг Первый",
          "pictureUrl": "https://example.org/avatars/100002.jpg",
          "groups": [
            {
              "name": "К3240",
              "course": 2,
              "facultyShortName": "ФИТИП"
            },
            {
              "name": "К3240c",
              "course": 2,
              "facultyShortName": "ФИТИП"
            }
          ],
          "capabilities": {
            "canViewSchedule": true,
            "canViewSport": true,
            "canViewFriends": true
          }
        },
        "updatedAt": "2026-10-02T10:00:00Z"
      },
      {
        "id": "00000000-0000-4000-8000-000000000106",
        "subjectId": 501,
        "subjectName": "Математический анализ",
        "periodKey": "2026-1",
        "category": "NOTES",
        "url": "https://example.org/links/6",
        "title": null,
        "visibility": "ALL",
        "flowId": null,
        "audienceLabel": null,
        "status": "PUBLISHED",
        "reviewNote": null,
        "score": -3,
        "myVote": 0,
        "isMine": false,
        "reportedByMe": false,
        "author": {
          "isu": 100002,
          "name": "Друг Первый",
          "pictureUrl": "https://example.org/avatars/100002.jpg",
          "groups": [
            {
              "name": "К3240",
              "course": 2,
              "facultyShortName": "ФИТИП"
            },
            {
              "name": "К3240c",
              "course": 2,
              "facultyShortName": "ФИТИП"
            }
          ],
          "capabilities": {
            "canViewSchedule": true,
            "canViewSport": true,
            "canViewFriends": true
          }
        },
        "updatedAt": "2026-10-03T10:00:00Z"
      }
    ],
    "previous": [
      {
        "id": "00000000-0000-4000-8000-000000000107",
        "subjectId": 501,
        "subjectName": "Математический анализ",
        "periodKey": "2025-1",
        "category": "RECORDINGS",
        "url": "https://example.org/links/7",
        "title": "Материалы",
        "visibility": "ALL",
        "flowId": null,
        "audienceLabel": null,
        "status": "PUBLISHED",
        "reviewNote": null,
        "score": -4,
        "myVote": 0,
        "isMine": false,
        "reportedByMe": false,
        "author": {
          "isu": 100002,
          "name": "Друг Первый",
          "pictureUrl": "https://example.org/avatars/100002.jpg",
          "groups": [
            {
              "name": "К3240",
              "course": 2,
              "facultyShortName": "ФИТИП"
            },
            {
              "name": "К3240c",
              "course": 2,
              "facultyShortName": "ФИТИП"
            }
          ],
          "capabilities": {
            "canViewSchedule": true,
            "canViewSport": true,
            "canViewFriends": true
          }
        },
        "updatedAt": "2026-10-04T10:00:00Z"
      },
      {
        "id": "00000000-0000-4000-8000-000000000108",
        "subjectId": 501,
        "subjectName": "Математический анализ",
        "periodKey": "2025-1",
        "category": "EXAM",
        "url": "https://example.org/links/8",
        "title": "Материалы",
        "visibility": "ALL",
        "flowId": null,
        "audienceLabel": null,
        "status": "PUBLISHED",
        "reviewNote": null,
        "score": -5,
        "myVote": 1,
        "isMine": false,
        "reportedByMe": false,
        "author": {
          "isu": 100002,
          "name": "Друг Первый",
          "pictureUrl": "https://example.org/avatars/100002.jpg",
          "groups": [
            {
              "name": "К3240",
              "course": 2,
              "facultyShortName": "ФИТИП"
            },
            {
              "name": "К3240c",
              "course": 2,
              "facultyShortName": "ФИТИП"
            }
          ],
          "capabilities": {
            "canViewSchedule": true,
            "canViewSport": true,
            "canViewFriends": true
          }
        },
        "updatedAt": "2026-10-01T10:00:00Z"
      }
    ],
    "pinnedId": "00000000-0000-4000-8000-000000000100",
    "audiences": [
      {
        "flowId": 7001,
        "label": "ЛЕК МАТАН 3.1",
        "typeId": 1,
        "depth": 1
      },
      {
        "flowId": 7003,
        "label": "ПРАК МАТАН 3.1.2",
        "typeId": 3,
        "depth": 2
      }
    ],
    "premoderation": true
  },
  "error": null
}"""

    /** `http/links/saveSubjectLink.json`. */
    const val SAVE_SUBJECT_LINK = """{
  "success": true,
  "data": {
    "id": "00000000-0000-4000-8000-000000000101",
    "subjectId": 501,
    "subjectName": "Математический анализ",
    "periodKey": "2026-1",
    "category": "QUEUE",
    "url": "https://example.org/links/2",
    "title": "Материалы",
    "visibility": "FLOW",
    "flowId": 7001,
    "audienceLabel": "ЛЕК МАТАН 3.1",
    "status": "PENDING",
    "reviewNote": null,
    "score": 1,
    "myVote": 0,
    "isMine": true,
    "reportedByMe": false,
    "author": null,
    "updatedAt": "2026-10-03T10:00:00Z"
  },
  "error": null
}"""

    /** `http/links/deleteSubjectLink.json`. */
    const val DELETE_SUBJECT_LINK = """{
  "success": true,
  "data": {},
  "error": null
}"""

    /** `http/links/pinSubjectLink.json`. */
    const val PIN_SUBJECT_LINK = """{
  "success": true,
  "data": {
    "mine": [
      {
        "id": "00000000-0000-4000-8000-000000000102",
        "subjectId": 501,
        "subjectName": "Математический анализ",
        "periodKey": "2026-1",
        "category": "QUEUE",
        "url": "https://example.org/links/2",
        "title": "Материалы",
        "visibility": "FLOW",
        "flowId": 7001,
        "audienceLabel": "ЛЕК МАТАН 3.1",
        "status": "PENDING",
        "reviewNote": null,
        "score": 1,
        "myVote": 0,
        "isMine": true,
        "reportedByMe": false,
        "author": null,
        "updatedAt": "2026-10-03T10:00:00Z"
      }
    ],
    "shared": [],
    "previous": [],
    "pinnedId": null,
    "audiences": [],
    "premoderation": false
  },
  "error": null
}"""

    /** `http/links/voteSubjectLink.json`. */
    const val VOTE_SUBJECT_LINK = """{
  "success": true,
  "data": {
    "id": "00000000-0000-4000-8000-000000000100",
    "subjectId": 501,
    "subjectName": "Математический анализ",
    "periodKey": "2026-1",
    "category": "MATERIALS",
    "url": "https://example.org/links/0",
    "title": "Материалы",
    "visibility": "ALL",
    "flowId": null,
    "audienceLabel": null,
    "status": "PUBLISHED",
    "reviewNote": null,
    "score": 3,
    "myVote": 1,
    "isMine": false,
    "reportedByMe": true,
    "author": {
      "isu": 100002,
      "name": "Друг Первый",
      "pictureUrl": "https://example.org/avatars/100002.jpg",
      "groups": [
        {
          "name": "К3240",
          "course": 2,
          "facultyShortName": "ФИТИП"
        },
        {
          "name": "К3240c",
          "course": 2,
          "facultyShortName": "ФИТИП"
        }
      ],
      "capabilities": {
        "canViewSchedule": true,
        "canViewSport": true,
        "canViewFriends": true
      }
    },
    "updatedAt": "2026-10-01T10:00:00Z"
  },
  "error": null
}"""

    /** `http/links/reportSubjectLink.json`. */
    const val REPORT_SUBJECT_LINK = """{
  "success": true,
  "data": {
    "id": "00000000-0000-4000-8000-000000000100",
    "subjectId": 501,
    "subjectName": "Математический анализ",
    "periodKey": "2026-1",
    "category": "MATERIALS",
    "url": "https://example.org/links/0",
    "title": "Материалы",
    "visibility": "ALL",
    "flowId": null,
    "audienceLabel": null,
    "status": "PUBLISHED",
    "reviewNote": null,
    "score": 3,
    "myVote": 1,
    "isMine": false,
    "reportedByMe": true,
    "author": {
      "isu": 100002,
      "name": "Друг Первый",
      "pictureUrl": "https://example.org/avatars/100002.jpg",
      "groups": [
        {
          "name": "К3240",
          "course": 2,
          "facultyShortName": "ФИТИП"
        },
        {
          "name": "К3240c",
          "course": 2,
          "facultyShortName": "ФИТИП"
        }
      ],
      "capabilities": {
        "canViewSchedule": true,
        "canViewSport": true,
        "canViewFriends": true
      }
    },
    "updatedAt": "2026-10-01T10:00:00Z"
  },
  "error": null
}"""

    /** `http/users/myRestrictions.json`. */
    const val MY_RESTRICTIONS = """{
  "success": true,
  "data": [
    {
      "id": "00000000-0000-4000-8000-000000000401",
      "capability": "SUBMIT_RESOURCES",
      "reason": "Спам в ссылках",
      "startsAt": "2026-10-01T09:00:00Z",
      "expiresAt": "2026-10-08T09:00:00Z"
    },
    {
      "id": "00000000-0000-4000-8000-000000000402",
      "capability": "VOTE",
      "reason": "Накрутка голосов",
      "startsAt": "2026-10-02T09:00:00Z",
      "expiresAt": null
    },
    {
      "id": "00000000-0000-4000-8000-000000000403",
      "capability": "REPORT",
      "reason": "Ложные жалобы",
      "startsAt": "2026-10-03T09:00:00Z",
      "expiresAt": "2026-11-03T09:00:00Z"
    },
    {
      "id": "00000000-0000-4000-8000-000000000404",
      "capability": "WRITE_REVIEWS",
      "reason": "Оскорбления",
      "startsAt": "2026-10-04T09:00:00Z",
      "expiresAt": null
    },
    {
      "id": "00000000-0000-4000-8000-000000000405",
      "capability": "ALL",
      "reason": "Повторные нарушения",
      "startsAt": "2026-10-04T12:00:00Z",
      "expiresAt": "2027-10-04T12:00:00Z"
    }
  ],
  "error": null
}"""

    /** `errors/unauthorized.json`. */
    const val UNAUTHORIZED = """{
  "success": false,
  "data": null,
  "error": {
    "message": "Authentication required",
    "code": "unauthorized"
  }
}"""

    /** `requests/SaveSubjectLinkRequest.json`. */
    const val SAVE_SUBJECT_LINK_REQUEST = """{
  "subjectId": 501,
  "subjectName": "Математический анализ",
  "periodKey": "2026-1",
  "category": "MATERIALS",
  "url": "https://example.org/links/new",
  "title": "Конспекты лекций",
  "visibility": "FLOW",
  "flowId": 7001
}"""

    /** `requests/ResourceVoteRequest.json`. */
    const val RESOURCE_VOTE_REQUEST = """{
  "value": 1
}"""

    /** `requests/ModerationReportRequest.json`. */
    const val MODERATION_REPORT_REQUEST = """{
  "reason": "OTHER",
  "comment": "Синтетическая жалоба"
}"""

    /** `requests/PinSubjectLinkRequest.json`. */
    const val PIN_SUBJECT_LINK_REQUEST = """{
  "periodKey": "2026-1",
  "linkId": "00000000-0000-4000-8000-000000000101"
}"""
}
