# Subject links

Product behaviour: [Как это работает: ссылки предметов](../how-it-works/subject-links.md).

HTTPS links per subject and period, private or shared with a schedule flow or
everybody. They live on the recordbook's [subject page](subject-page.md); there
is no separate links screen. `core/resources` holds the contract,
`feature/resources` (`:shared:feature-resources`) the data, sheets and view
models. Backend contract: `../itmo-widgets-backend/docs/contracts/subject-links.md`;
moderation model: decision
[0008](https://github.com/alllexey-dev/itmo-widgets-backend/blob/dev/docs/decisions/0008-community-moderation.md).

## Model

- `ResourceScope(subjectId, subjectName, periodKey)`: the recordbook
  `discipline_id` and the period key `YYYY-S` (`YYYY` the start of the academic
  year, `S` 1 for September-January, 2 for February-August;
  `ResourceScope.periodKey`, the recordbook's default-period rule). Links exist
  for past periods too, never for physical education.
- `SubjectLink`: a `LinkCategory`, a URL, an optional title (at most 120
  characters) and a visibility. `LinkCategory` declaration order is the display
  order. Labels, icons and visibility texts are in
  `core/resources/SubjectLinkTexts.kt` (`:shared:core`), shared by the recordbook
  and the sheets. A chat is an ordinary `CHAT` link, never in the subject page's
  short list.
- Visibility: `PRIVATE` (owner only), `FLOW` (students with exactly this
  MyITMO schedule `flow_id` of the subject and period, any nesting depth), `ALL`
  (every student of the subject). Flow ids are unique per cohort year. Backend
  records flows from the uploaded schedule and offers the viewer's current ones
  as `SubjectLinksSnapshot.audiences` (`LinkAudience`, sorted by depth, then
  name). While `premoderation` is true both wait for review: an `ALL` link for
  a moderator; a `FLOW` link is approved by Backend's ISU check when ISU
  confirms the author is in the flow and the flow has at most 50 members
  (configurable on Backend), otherwise a moderator decides.
- Others always see published content; the owner also sees review states and
  the moderator's reason. `С прошлых лет` holds approved `ALL` links of
  `MATERIALS`, `TASKS`, `RECORDINGS`, `NOTES` and `EXAM` from earlier periods.
- Votes are +1/-1, tapping the selected thumb takes it back. One pin per viewer
  and period. Any HTTPS host is accepted: the app checks scheme, host and no
  user info, Backend never fetches a URL. Links open only through
  `core/util/HttpsNavigationPolicy` (`app/shell/entries/ResourcesEntries.kt`).

## Subject page

The subject page's `Материалы` tab is the home of links and chats
([subject page](subject-page.md)): without the connection it is only a gate.
Chats come first, then the links by category, the pinned one first, then by
`SubjectLinkRanking` (higher score first, then newer); the MyITMO LMS page is
not shown. While the page is open the shown order stays
(`core/presentation/StableOrder`): a vote changes a score in place and never
moves a row; a new screen or a pull ranks afresh. Votes go through
`RecordbookSubjectViewModel.voteLink`, one at a time. Own links show `моя` and
their review state, never a vote; under a `VOTE` restriction the pill has no
vote buttons. Like and dislike use outlined thumbs, filled for the selected vote, on both platforms.

The recordbook reads the cached snapshot through `SubjectLinksRepository.peek`
first, then refreshes the scope.

## Sheets

Keys `AppRoutes.SubjectLinks`, `AppRoutes.LinkEditor`, `AppRoutes.LinkActions`
and the dialog `AppRoutes.ReportLink`, all with
`core/navigation/SubjectLinksArgs`. Bodies are Compose in
`:shared:feature-resources` (`feature.resources.ui`); `ResourcesEntries.kt` in
`app/` registers the keys and runs the Android effects. Every entry gets its
own view model from Koin (`ResourcesKoinModule.kt`), so the actions sheet and
the report dialog each have their own `SubjectLinksViewModel`. The shell refuses
a second sheet or dialog of a class already shown; a sheet that opens another
sheet, a screen or the report closes itself first.

- `SubjectLinksSheetRoute`: sections in category order, `Чаты`, then
  `С прошлых лет`; within a category own and others' links are ranked together
  by `SubjectLinkRanking` and keep their first-shown order while open
  (`StableOrder`). Refreshes silently on open.
- `LinkEditorSheetRoute`: pastes the clipboard on first focus when it holds a
  single HTTPS link. `guessCategory` suggests a category by site until the user
  picks one. Audiences are ordered widest to narrowest: `ALL`, the viewer's flows
  broad to nested, `PRIVATE`; while premoderation is on `ALL` and every flow
  carry the review hint; a chosen flow no longer offered falls back to
  `PRIVATE`; without the connection only `PRIVATE`. The new link's UUID survives
  process death, so a retried save reaches the same link.
- `LinkActionsSheetRoute` (long press): open, author profile (ISU above 0,
  `navigator.openUserProfile`), copy, `Мои баллы` for a Google Sheet of any
  author (`GoogleSheetUrl.parse`, opens `AppRoutes.SheetScores`, see
  [sheet scores](sheet-scores.md)), pin (own always, others' with the
  connection), edit and delete (own), report (others' with the connection, no
  `REPORT` restriction, not yet reported). One action at a time; every action
  but a vote closes the sheet on success; a deleted link closes it unless an
  action is pending.
- `ReportLinkForm` on the kit's `ReportDialog`: a reason is required, the
  comment (up to 500) is trimmed and sent as none when blank; not dismissable
  while sending.

## Without the connection

Links are `PRIVATE` and stay on the device; own local links can be pinned,
edited and deleted. Votes, reports, non-private visibility and editing a link
already on the server fail with `AppError.CustomServicesDisabled`. The first
refresh with the connection uploads local links and pins as private server
links under their UUIDs; a refused link stays local and is retried, and an edit
made during the upload is sent next time.

## Storage and synchronization

- `core/resources/SubjectLinksRepository` is the port;
  `feature/resources/data/SubjectLinksRepositoryImpl` implements it with Core's
  `SubjectLinksApi` (`BackendClient.links`), the connection gate and an injected
  wall clock. `DemoMode` is checked first; every call passes
  `BackendGate.mayCallBackend()`. Koin's `resourcesModule` builds the one
  repository shared by the sheets, the recordbook and sign-out (`links` cleaner).
- `SubjectLinkMappers.kt` is the one place that reads Core's types: an unknown
  category shows as `OTHER`, an unknown status shows no badge.
- With the connection the server is the source of truth: every action goes to it
  at once and its answer updates the cached snapshot. No background sync.
- `SubjectLinksFileStore` keeps `filesDir/subject_links/cache.json` (local links,
  local pins, last server answer per scope). It is persistent data, not
  `cacheDir`, because device-only links must not be evicted. kotlinx JSON format
  3; formats 1 and 2 still read (local links and pins kept, cached answers
  dropped, so each scope refetches once). Writes go through `AtomicTextFile`; a
  corrupt file or unknown format is reported and never replaced with an empty
  one. Backup and device transfer exclude the directory.
- A failed refresh keeps the cached snapshot; a never-loaded scope shows its
  local links if any, otherwise an error.
- Errors (`ResourcesErrors.kt`, `BackendException.asAppError()`): 401 ->
  `Unauthorized`, 403 `restricted` -> `Restricted` (restrictions are refreshed),
  other 403 -> `Forbidden`, 404 -> `NotFound`, no answer -> `Network`, anything
  else (409 included) -> `Unknown`. Upload of device-only links stops at the
  first `Unauthorized` and keeps the rest local; `Forbidden`, `NotFound` and
  `Unknown` skip only that link. An `Unauthorized` refresh does not mark the
  scope failed.
- Restrictions from `GET /api/users/me/restrictions` hide vote buttons (`VOTE`)
  and report (`REPORT`); an unknown capability blocks like `ALL`. Private links,
  deletion, pins and adding links stay available.
- As a `SessionDataCleaner` the repository cancels and joins calls in flight
  before the next account's token appears, deletes the file and memory state and
  drops late answers of the previous session.

## iOS

The iOS app ([iOS app](../ios.md)) hosts the same Compose sheets in SwiftUI
sheets through `shared/ios` `screens/ResourcesScreens.kt` and
`iosApp/Sources/Features/Resources/LinkSheets.swift`; `Routes+Resources.swift`
maps the three sheet routes, the graph loads `resourcesModule` with
`resourcesIosModule`, and the [subject page](subject-page.md#ios) shows links
and chats as on Android. What differs:

- One router sheet at a time; from the subject page the actions sheet replaces
  itself with the editor or `Мои баллы` (`AppRouter.replaceSheet(with:)`). The
  author's profile closes every sheet.
- The report is a Compose dialog over the actions sheet with its own view model;
  `AppRoutes.ReportLink` has no iOS surface.
- The editor does not read the clipboard (iOS asks the user before an app reads
  it). Copy confirms with `links_copied`; failures show as a banner.
- The demo shows `DemoSubjectLinks` and refuses every change
  (`error_demo_unavailable`) with no request.
- Tests: `ResourcesIosModuleTest` (`scripts/ios/test.sh kn
  :shared:feature-resources`) and `ReviewsLinksUITests`.

## Not in the app

No cross-subject list of own links, no home-feed card for moderation results.
Moderation has no app UI; operators use the Backend moderation API
(`../itmo-widgets-backend/docs/ops/moderation.md`). Strict verification of flow
membership is deferred (decision 0008).

## Verification

```bash
./gradlew :shared:feature-resources:testAndroidHostTest :app:testGithubDebugUnitTest
scripts/verify.sh shots feature-resources
```

Tests live in `:shared:feature-resources` (`commonTest`, `androidHostTest`,
goldens in `shared/feature-resources/screenshots/`) and `ResourcesEntriesTest` in
`:app`; the subject page rows are covered by the recordbook's tests.
