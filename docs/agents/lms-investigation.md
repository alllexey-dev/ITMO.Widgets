# Tuxedo LMS investigation

An experimental Android-only LMS screen (`LmsLoginActivity` + `LmsLoginViewModel`) is wired to the transport in
`feature-recordbook/data/lms` (`AndroidLmsGateway`, `LmsJournalClient`, `LmsScoresRepositoryImpl`).
A signed-in Android emulator completed the ITMO.ID flow and displayed the expected LMS score.
The iOS entry and automatic prevention of duplicate sheet scores are not implemented.

## Observed on 2026-11-12

- The LMS frontend's public configuration points to `https://api.tuxedo.se.ifmo.ru`.
- The student progress page requests `GET /api/v1/student-journal/overall` with `programId` and `courseId`, then
  `GET /api/v1/student-journal/modules` with those parameters, `courseGradeId`, `pageSize` and `pageNumber`. An
  expanded module requests `GET /api/v1/student-journal/modules/{moduleId}/tasks`.
- The frontend reads `courseGradeId`, `elementsTotal`, `content`, module identifiers and names, `earnedPoints`,
  `minGrade`, `maxGrade`, `percentGrade` and the verification status. This is a private contract and can change.
- `GET /api/v1/sso/authorize?redirectUri=https://lms.tuxedo.se.ifmo.ru/sso/token` redirects to the Tuxedo Keycloak
  realm. Its `itmo-id` identity provider then redirects to ITMO.ID using client `suo-fpiikt`. The frontend exchanges
  the authorization code and state with `POST /api/v1/sso/token` and uses a separate LMS bearer token; it can refresh
  that token using `/api/v1/sso/refresh-access`.

## Remaining verification

The Android emulator flow was checked with an existing ITMO.ID session, including discovery, account matching and a
confirmed RK. Exercise the interactive login fallback, session expiration and account changes on more devices. The
LMS API is private and may change. Do not copy tokens or account data into fixtures or logs. A future integration
needs reliable semester metadata and a rule for detecting when the sheet already contains the LMS score.
