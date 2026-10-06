package dev.alllexey.itmowidgets.feature.reviews.data

/**
 * Synthetic Backend answers for teacher reviews and summary levels, shaped like CO-06's vendored `http/reviews/`
 * answers at Backend commit ad8fa2dc3681dd57b6e8b797ace2f97322742555 (`BACKEND_COMMIT` of
 * `shared/backend-client/src/commonTest/resources/contract/`). Kept as constants, not JVM resources, so they move to
 * `commonTest` unchanged. No value here is a real person or ISU number.
 */
object ReviewsRemoteFixtures {

    const val TEACHER_ISU = 200001
    const val NAMED_ID = "00000000-0000-4000-8000-000000000201"
    const val ANONYMOUS_ID = "00000000-0000-4000-8000-000000000202"
    const val COPY_ID = "00000000-0000-4000-8000-000000000203"
    const val MINE_ID = "00000000-0000-4000-8000-000000000210"
    const val PROVIDER_URL = "https://example.org/reviews"

    /**
     * `teacherReviews.json`: a named and an anonymous community review and a Reviews copy, the viewer's own review
     * and a summary with an unknown tag; padding the mapping trims.
     */
    const val REVIEWS = """{
  "success": true,
  "data": {
    "teacherIsu": 200001,
    "providerUrl": " https://example.org/reviews ",
    "reviews": [
      {
        "id": "00000000-0000-4000-8000-000000000201",
        "kind": "COMMUNITY",
        "subjectTitle": " Математический анализ ",
        "writtenOn": "2026-10-06",
        "writtenBeforeYear": null,
        "text": " Синтетический отзыв под именем. ",
        "score": 4,
        "myVote": 1,
        "verified": true,
        "reportedByMe": false,
        "author": {
          "isu": 100002,
          "name": " Друг Первый ",
          "pictureUrl": "https://example.org/avatars/100002.jpg",
          "groups": [{ "name": "К3240", "course": 2, "facultyShortName": "ФИТИП" }],
          "capabilities": { "canViewSchedule": true, "canViewSport": false, "canViewFriends": true }
        },
        "sourceTitle": null,
        "sourceLink": null
      },
      {
        "id": "00000000-0000-4000-8000-000000000202",
        "kind": "COMMUNITY",
        "subjectTitle": null,
        "writtenOn": "2026-09-06",
        "writtenBeforeYear": null,
        "text": "Синтетический анонимный отзыв.",
        "score": -1,
        "myVote": -1,
        "verified": false,
        "reportedByMe": true,
        "author": null,
        "sourceTitle": null,
        "sourceLink": null
      },
      {
        "id": "00000000-0000-4000-8000-000000000203",
        "kind": "REVIEWS",
        "subjectTitle": "Программирование",
        "writtenOn": null,
        "writtenBeforeYear": 2024,
        "text": "Синтетическая копия отзыва.",
        "score": 0,
        "myVote": 0,
        "verified": false,
        "reportedByMe": false,
        "author": null,
        "sourceTitle": "Reviews",
        "sourceLink": "https://example.org/reviews/1"
      }
    ],
    "mine": {
      "id": "00000000-0000-4000-8000-000000000210",
      "subjectTitle": "Математический анализ",
      "text": " Мой синтетический отзыв. ",
      "anonymous": true,
      "status": "PUBLISHED",
      "reviewNote": null,
      "score": 2,
      "verified": true,
      "writtenOn": "2026-10-06"
    },
    "canWrite": true,
    "canVote": true,
    "canReport": false,
    "knownTeacher": true,
    "summary": {
      "reviewCount": 7,
      "description": " Синтетическое описание отзывов. ",
      "pros": ["Понятно объясняет", " "],
      "cons": ["Строгие сроки"],
      "tags": ["STRICT_DEFENSE", "SOME_NEW_TAG", "HARD_EXAM"],
      "scales": [
        { "kind": "EXPLAINS", "value": "HIGH", "reason": "Синтетическое пояснение" },
        { "kind": "ATTITUDE", "value": "MEDIUM", "reason": "Синтетическое пояснение" },
        { "kind": "FAIRNESS", "value": "LOW", "reason": "Синтетическое пояснение" },
        { "kind": "STRICTNESS", "value": "NOT_ENOUGH_DATA", "reason": null },
        { "kind": "WORKLOAD", "value": "HIGH", "reason": "Синтетическое пояснение" }
      ],
      "level": "POSITIVE",
      "confidence": "HIGH",
      "generatedAt": "2026-10-04T03:00:00Z"
    }
  },
  "error": null
}"""

    /** The answer every mutation returns: the viewer's own review waiting for review, a low-confidence summary. */
    const val AFTER_MUTATION = """{
  "success": true,
  "data": {
    "teacherIsu": 200001,
    "providerUrl": "https://example.org/reviews",
    "reviews": [],
    "mine": {
      "id": "00000000-0000-4000-8000-000000000210",
      "subjectTitle": null,
      "text": "Мой синтетический отзыв после правки.",
      "anonymous": false,
      "status": "PENDING",
      "reviewNote": null,
      "score": 0,
      "verified": false,
      "writtenOn": "2026-10-06"
    },
    "canWrite": true,
    "canVote": true,
    "canReport": true,
    "knownTeacher": true,
    "summary": {
      "reviewCount": 3,
      "description": "Синтетическое описание.",
      "pros": [],
      "cons": [],
      "tags": [],
      "scales": [
        { "kind": "EXPLAINS", "value": "NOT_ENOUGH_DATA", "reason": null },
        { "kind": "ATTITUDE", "value": "NOT_ENOUGH_DATA", "reason": null },
        { "kind": "FAIRNESS", "value": "NOT_ENOUGH_DATA", "reason": null },
        { "kind": "STRICTNESS", "value": "NOT_ENOUGH_DATA", "reason": null },
        { "kind": "WORKLOAD", "value": "NOT_ENOUGH_DATA", "reason": null }
      ],
      "level": "MIXED",
      "confidence": "LOW",
      "generatedAt": "2026-10-04T03:00:00Z"
    }
  },
  "error": null
}"""

    /** `teacherSummaryLevels.json`: one level per asked ISU in [levels], in the order asked. */
    fun levels(levels: Map<Int, String>): String = levels.entries.joinToString(
        separator = ",",
        prefix = """{"success":true,"data":[""",
        postfix = """],"error":null}""",
    ) { (isu, level) -> """{"teacherIsu":$isu,"level":"$level"}""" }
}
