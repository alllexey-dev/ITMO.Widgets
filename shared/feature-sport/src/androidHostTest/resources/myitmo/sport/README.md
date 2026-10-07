# Vendored MyItmoApi sport fixtures

Synthetic answers, not captured university responses or account exports. They are
copied unchanged from MyItmoApi (`M/`) commit `3a85bd82a9c9a6101ae0f8aeb85cc3ed12f7bf5e`
(`gradle/myitmoapi.ref` at the time of KM-10c) and read by
`feature/sport/data/golden/SportGoldenTest.kt`.

| File | Source |
|---|---|
| `schedule.json` | `M/kmp/fixtures/sport/schedule.json` |
| `filters.json` | `M/kmp/fixtures/sport/filters.json` |
| `time-slots.json` | `M/kmp/fixtures/sport/time-slots.json` |
| `attempts.json` | `M/kmp/fixtures/sport/attempts.json` |
| `score.json` | `M/kmp/fixtures/sport/score.json` |
| `score-empty.json` | `M/kmp/fixtures/sport/score-empty.json` |
| `semesters.json` | `M/kmp/fixtures/sport/semesters.json` |
| `current-semester.json` | `M/kmp/fixtures/sport/current-semester.json` |
| `chosen.json` | `M/kmp/fixtures/sport/chosen.json` |
| `lessons.json` | `M/kmp/fixtures/sport/lessons.json` |
| `sign-in-error.json` | `M/kmp/fixtures/sport/sign-in-error.json` |
| `sign-out-error.json` | `M/kmp/fixtures/sport/sign-out-error.json` |
| `empty.json` | `M/kmp/fixtures/sport/empty.json` |

Backend sport and FCM fixtures are not copied: the tests read them by path from
`shared/backend-client/src/commonTest/resources/contract/` (CO-09a).

Refresh the commit above whenever a fixture is updated from a newer pin.
