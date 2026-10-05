# Golden contract fixtures

What released clients and Web exchange with Backend, recorded from the real controllers, Spring's
Jackson configuration and `FcmService`, with mocked services and synthetic data. The tests in
`src/test/kotlin/dev/alllexey/itmowidgets/backend/contract/` check Backend against these files on
every build; consumers (the Core decode suites, the shared backend client, Web) read them as they
are.

## Layout

| Path | Content |
|---|---|
| `http/<area>/<operation>.json` | The whole response body (`ApiResponse`) of one app route; `<operation>` is the Core 1.7.0 method name |
| `http/{admin,weblogin}/<operationId>.json` | The response body of one `/api/admin/` or `/api/web/` route; named after its `docs/openapi.json` operationId |
| `requests/<Type>.json` | A request body as Core's Gson writes it: declaration order, `null` fields omitted, `LocalTime` as `08:20` |
| `fcm/<TYPE>.json` | The FCM data map of one `type`: `recipient_isu` as sent, `data` parsed from its JSON string |
| `index.json` | One entry per fixture, sorted by `id`: `id`, `kind` (`http`, `request`, `fcm`), `method`, `path`, `file`, `minCore` |

- The 62 routes are the 56 `ItmoWidgetsApi` and 6 `ItmoWidgetsModerationApi` methods of Core 1.7.0:
  every Backend route except `/api/admin/**` and `/api/web/**`. A test fails when a controller
  outside those prefixes gains a route that has no fixture.
- The 30 admin and web sign-in routes (`AdminWebContractTest`) record the response shape Web reads:
  one fixture per route, no request fixtures, and no rule to cover every enum value or nullable
  field. A test fails when an admin or web sign-in controller gains a route that has no fixture.
- `minCore` is the oldest decoded Core release (`1.2.0` or `1.7.0`) that calls the route or sends
  the body; a suite for an older Core skips newer entries. `1.8.0` marks a fixture no released Core
  reads: `appVersionInfoIos` (`GET /api/app/version-info?platform=IOS`, the same route as
  `appVersionInfo` with another query, for the shared client) and the admin and web sign-in
  routes. `method` and `path` are `null` outside `http`; `path` never carries the query.
- `SportLessonIds` is the bare `List<Long>` body of `POST /api/sport/sign/sync`.
- Together the app fixtures set every optional field, show every nullable field as `null` at least
  once, carry both sport entry subtypes and every value of the enums the routes return. The values
  are fixture data, not route behaviour: `moderationCases` lists every case status side by side.

## Comparison

Semantic: object key order is ignored; two ISO date-times are equal when their instant and offset
are (Gson writes `12:00+03:00` where Jackson writes `12:00:00+03:00`); everything else is exact,
including integral against floating numbers and `08:20` against `08:20:00`.

## Recording

```bash
scripts/verify.sh run -- test --tests 'dev.alllexey.itmowidgets.backend.contract.*' -Pcontract.record=true
```

- Only a card that adds a route or an optional response field records. Any other difference is a
  wire change and needs the compatibility rule of `docs/contracts/` first; a refactor leaves every
  file here unchanged.
- A recording run writes only missing or semantically different files and reproduces every
  unchanged file byte for byte, so an `index.json` rebase conflict is solved by re-recording, never
  by hand.
- Request fixtures stand for what released clients send. Recording only creates a missing one;
  an existing one is never rewritten.
- A pull request that changes a file here names the fixture ids in a `Contract change:` line, so
  the client side re-syncs.
