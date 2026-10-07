# Subject links

Students keep HTTPS links per subject and period and see the links shared with
one of their schedule flows or with everybody. Links live on the subject page of
the recordbook (see [recordbook](recordbook.md#subject-page)); there is no
separate links screen. `core/resources` holds the contract, `feature/resources`
the data, the sheets and their view models. The Backend contract is
`../itmo-widgets-backend/docs/contracts/subject-links.md`; the moderation model
is decision [0008](../decisions/0008-community-moderation.md).

## Model

`ResourceScope(subjectId, subjectName, periodKey)` uses the recordbook
`discipline_id` and the period key `YYYY-S`: `YYYY` is the start of the academic
year, `S` is 1 for September–January and 2 for February–August
(`ResourceScope.periodKey`, the same rule as the recordbook's default period).
Links exist for past periods as well, but never for physical education.

A `SubjectLink` has a category, a URL, an optional title (at most 120
characters) and a visibility. Declaration order of `LinkCategory` is the
display order:

| Category | Label | Icon |
|---|---|---|
| `SCORES` | `Таблица баллов` | `ic_table` |
| `QUEUE` | `Очередь на сдачу` | `ic_format_list_numbered` |
| `MATERIALS` | `Материалы курса` | `ic_folder` |
| `TASKS` | `Задания` | `ic_assignment` |
| `RECORDINGS` | `Записи лекций` | `ic_videocam` |
| `NOTES` | `Конспекты` | `ic_edit_note` |
| `EXAM` | `К экзамену` | `ic_school` |
| `CHAT` | `Чат` | `ic_chat` |
| `OTHER` | `Другое` | `ic_link` |

A chat is an ordinary link with category `CHAT`; it never joins the short
list of the subject page and is listed in its own block. Labels, icons and visibility texts live in
`core/ui/SubjectLinkTexts.kt`, so the recordbook and the sheets share them.

| Visibility | Label | Who sees it |
|---|---|---|
| `PRIVATE` | `Только я` | the owner |
| `FLOW` | the flow name, e.g. `ФИЗ ПИИКТ 3.2.1` | students with exactly this schedule flow (`flowId`) of the subject and period |
| `ALL` | `Все` | every student of the subject, after review while premoderation is on |

A `FLOW` link names one MyITMO schedule `flow_id` of the author, of any
nesting: `ФИЗ ПИИКТ 3` (lectures), `ФИЗ ПИИКТ 3.2` (practice),
`ФИЗ ПИИКТ 3.2.1` (labs). A link for `3.2.1` reaches only that lab group, one
for `3` the whole lecture flow. Flow ids are unique per cohort year, so the same
names of two different years never share links. Backend records flows from the
schedule the app uploads and offers every flow the viewer has now as
`SubjectLinksSnapshot.audiences` (`LinkAudience(flowId, label, typeId, depth)`,
sorted by depth, then name); `SubjectLink.flowId` and `audienceLabel` name the
flow of a `FLOW` link. Flow links are published at once; `ALL` waits for a
moderator while `premoderation` is true.

Other students always see published content. The owner additionally sees
`на проверке`, `отклонена` or `скрыта`; the actions sheet shows the moderator's
reason for a rejected or hidden link. Past periods contribute
`С прошлых лет`: approved `ALL` links of `MATERIALS`, `TASKS`, `RECORDINGS`,
`NOTES` and `EXAM` from earlier periods of the subject.

Votes are +1/−1 arrows; tapping the current arrow takes the vote back, and the
score is the sum. A viewer can pin one link per period (pinning the pinned
link unpins it) and report a link (`Не открывается`, `Другой предмет`, `Спам`,
`Другое`, optional comment). Any HTTPS host is accepted; the app checks the scheme, the host and
the absence of user info, and Backend never fetches a URL. Links open in the
browser only through `core/util/HttpsNavigationPolicy`
(`core/ui/LinkOpener.kt`); without a browser a snackbar says so.

## Subject page

`Ссылки` is a heading over one connected group
([design](../design.md#connected-groups)) and is there whenever the subject
has links, even with none yet. `subjectLinkChips` in `core/resources` builds
at most three link rows (`SubjectHubState.LINK_ROWS`) in this order: the
pinned link, the MyITMO LMS page (`lms_link`, shown as `LMS` with its host),
then every other non-chat link of the period, own and shared alike, by
`SubjectLinkRanking`: the higher score first, of equal scores the newer link.
A link is shown once. While the page is open the order it has shown stays
(`core/presentation/StableOrder`): a vote changes a score in place but neither moves a
row nor changes which three are shown; a new screen or a pull ranks afresh, and
a link seen for the first time follows the shown ones. A row (`item_subject_link.xml`, bound by
`core/ui/SubjectLinkRow.kt`) has the category symbol, the title (or the
category name) and the host on the second line. Own and others' links share
the style: an own link is told only by the `моя` badge, another student's
link has the vote pill `▲ N ▼` (`view_link_vote_pill.xml`) whose arrows vote
from the page like in the sheet (`RecordbookSubjectViewModel.voteLink`; tapping
the current arrow takes the vote back, one vote at a time, a failure is a
snackbar). Without the connection or under a `VOTE` restriction the pill keeps
the score without arrows. A tap opens the link, a long press opens its
actions. The last row is `Все ссылки, N` (`N` counts what the sheet lists,
chats and past years included), which opens the links sheet; a subject without
links has `Добавить ссылку` there instead. `Чаты` follows as a group of its
own: own and shared chat links with the messenger's symbol, the title (or the
host), the visibility label and `моя` on an own one.

The recordbook reads the cached snapshot through `SubjectLinksRepository.peek`
first, so a second visit opens without a spinner, then refreshes the scope.

## Sheets

The sheets sit on the Activity's FragmentManager and belong to no back stack.
`AppNavigator.openSubjectLinks`, `openLinkEditor` and `openLinkActions` take
`core/navigation/SubjectLinksArgs`; `MainNavigationCoordinator` shows one sheet
per tag and nothing once the state is saved. Each sheet's body is Compose
Multiplatform in `:shared:feature-resources` (package
`dev.alllexey.itmowidgets.feature.resources.ui`), drawn inside a Fragment host in
`app/` that keeps its class name, `TAG` and `newInstance` and runs the
Android effects (opening a link, the clipboard, navigation, snackbars). The
three sheets are `ItmoBottomSheetFragment`s that open expanded and as tall as
their content; every host obtains its view model from Koin, and the actions
sheet and the report dialog each have their own `SubjectLinksViewModel`.

- `SubjectLinksBottomSheet` (`SubjectLinksSheetRoute`; `Ссылки` and the
  subject name): one section per category in declaration order, `Чаты` after
  them, `С прошлых лет` last. A section is a heading over one connected group
  on the sheet's group surface, a tonal step above the sheet's
  `colorSurfaceContainerLow`. Within a category own and others' links are
  ranked together by `SubjectLinkRanking` and share the kit's `LinkRow`: the
  title or host and a caption with the host when titled, who sees the link
  (`Все` or the flow, for own and others' links alike; the author is in the
  actions sheet), the study year of a past link, `закреплена` and the owner's
  review state (`на проверке`, `отклонена`, `скрыта`), joined by commas. An
  own link has the `моя` badge; others' links have the kit's `VotePill`
  `▲ N ▼` (a negative score in the error colour, the own vote in
  `colorPrimary`), whose arrows are hidden under a `VOTE` restriction and
  without the connection. While the sheet is open its rows keep the order they
  were first shown in (`StableOrder`): a vote updates the pill in place, a new
  link follows the shown ones, and a new sheet or `Повторить` ranks afresh. The
  sheet refreshes silently on open; the first load shows placeholder rows, a
  failed one offers `Повторить`, an empty one says `Ссылок пока нет`. A tap
  opens a link, a long press its actions; the add button under the list opens
  the editor.
- `LinkEditorBottomSheet` (`LinkEditorSheetRoute`; `Новая ссылка` /
  `Изменить ссылку`): the window resizes for the keyboard, so `Сохранить` stays
  pinned above it. The URL is pasted from the clipboard when the sheet first
  gets focus and the clipboard holds a single HTTPS link. `guessCategory`
  suggests a category by site until the user picks one (Google Sheets ->
  `SCORES`, Google Forms -> `QUEUE`, GitHub -> `TASKS`, YouTube, VK Video ->
  `RECORDINGS`, Notion -> `NOTES`, `lms.itmo.ru` -> `MATERIALS`, Telegram, VK
  chats, WhatsApp -> `CHAT`); the category chips scroll the chosen one into
  view. The title is optional and its hint is the category name. `Кто видит`
  is a list of radio rows (at least 48 dp) ordered from the widest audience to
  the narrowest: `Все` (with `После проверки` while premoderation is on), every
  flow of the viewer from broad to nested (the flow name over the kind of
  classes from `lessonTypeName`), then `Только я`. A chosen flow that is no
  longer offered falls back to `Только я`; without the connection only
  `Только я` is listed with a line saying sharing needs the connection. The new
  link's UUID survives process death, so a retried save reaches the same link.
- `LinkActionsBottomSheet` (`LinkActionsSheetRoute`, long press): the link's
  title and caption as in the list. Another student's link has the vote pill at
  the end of its title (tapping the current arrow takes the vote back); the
  score follows the repository and a vote keeps the sheet open. The arrows are
  hidden under a `VOTE` restriction and without the connection. An own shared
  link shows its score without arrows, an own private one none. An own rejected
  or hidden link shows `Причина: <reason>` in the error colour. Then
  `Открыть`; for another student's link with a known profile (ISU above 0)
  `Автор: <name>`, which opens their profile (`openUserProfile`);
  `Скопировать ссылку` (the address to the clipboard; below Android 13 a toast
  `Ссылка скопирована`, the system shows its own above); `Мои баллы` for a
  Google Sheet address of any author (`GoogleSheetUrl.parse`), which closes the
  sheet and opens the recordbook's connection sheet through
  `AppNavigator.openSheetScores` with the link's address and scope
  ([sheet scores](recordbook.md#sheet-scores)); `Закрепить` / `Открепить`
  (own links always, others' with the connection); `Изменить` and `Удалить`
  for own links, the delete confirmed by the kit's `ConfirmDialog`
  (`Удалить ссылку?`); `Пожаловаться` for another student's link with the
  connection, no `REPORT` restriction and no earlier report, which opens
  `ReportLinkDialogFragment`. Actions run one at a time: while one is in flight
  the rows that act ignore taps. A failure is a snackbar. Every action but a
  vote closes the sheet on success, and a link deleted here or elsewhere closes
  it unless an action is pending.
- `ReportLinkDialogFragment` (`ReportLinkForm` on the kit's `ReportDialog`;
  `Жалоба на ссылку`): `Не открывается`, `Другой предмет`, `Спам`, `Другое` and
  an optional comment of up to 500 characters, trimmed and sent as none when
  blank. `Отправить` needs a reason and no report in flight; back and a tap
  outside close the dialog only while nothing is being sent. A failure keeps the
  dialog with its text under the comment; an accepted report closes it. The
  reason and the comment survive a recreation.

## Without the connection

Without `Подключение к ITMO.Widgets` links are `PRIVATE` and stay on the device:
the editor offers only `Только я` and says `Поделиться можно с подключением к
ITMO.Widgets`; an own local link can be pinned, edited and deleted. Votes,
reports, non-private visibility and editing a link that is already on the
server fail with `AppError.CustomServicesDisabled`. The first
refresh with the connection uploads local links and pins as private server
links under their UUIDs; a link the server refuses stays local and is retried
next time, and an edit made while the upload was in flight is sent next time.

## Storage and synchronization

`core/resources/SubjectLinksRepository` is the port;
`feature/resources/data/SubjectLinksRepositoryImpl` (in `:shared:feature-resources` `commonMain`, package
`dev.alllexey.itmowidgets.feature.resources.data`) implements it with Core
2.0's `SubjectLinksApi` (`BackendClient.links`: `subjectLinks`, `saveSubjectLink`,
`deleteSubjectLink`, `pinSubjectLink`, `voteSubjectLink`, `reportSubjectLink`,
`myRestrictions`), the connection gate and an injected wall clock. `DemoMode` is
checked first and every call passes `BackendGate.mayCallBackend()`.
`SubjectLinkMappers.kt` is the one place that reads Core's types: a category a
newer Backend adds shows as `OTHER`, a status it adds shows no badge, and authors
map through `UserData.toUserSummary()` (`core/model/ClientUserMapping.kt`). Koin's `resourcesModule` builds the
one repository that the sheets, the recordbook and sign-out (the `links` cleaner, through
`di/bridge/SessionCleanersBridge.kt`) share; Core's `SubjectLinksApi`, `AppDirectories`, the clock, `DemoMode` and
the dispatchers come from `di/bridge/CoreBridge.kt`. With the connection the server is the source of truth: every action
goes to it at once and its answer updates the cached snapshot. There is no
background synchronization.

`SubjectLinksFileStore` keeps `filesDir/subject_links/cache.json`: local links,
local pins and the last server answer per scope. It is persistent data, not
`cacheDir`, because device-only links must not be evicted. The file is kotlinx
JSON in format 3: app-owned rows (local links with the request their first
upload sends, pins, and per scope the links, authors and audiences of the last
answer) with ISO instants; the repository maps Backend's answers into these rows.
Formats 1 and 2, which 2.2 wrote with Gson, still read: their local links and
pins are kept exactly and their cached answers are dropped, so every scope is
refetched once on its next open; the first write after that is format 3, which
2.2 cannot read. Writes go through `AtomicTextFile` (`cache.json.new`, sync,
atomic move); a corrupted file or an unknown format is reported and never
replaced with an empty one. Cloud backup and device transfer exclude the
directory. A failed refresh keeps the cached snapshot; a scope that never
loaded shows its local links if it has any, otherwise an error.

Backend errors map to `AppError` through `BackendException.asAppError()` (`ResourcesErrors.kt`): 401 ->
`Unauthorized`, 403 `restricted` -> `Restricted` (and the restrictions are
refreshed; the caller still sees `Restricted`), any other 403 -> `Forbidden`, 404 ->
`NotFound`, no answer -> `Network`, anything else, including 409, -> `Unknown`.
The upload of device-only links stops at the first `Unauthorized` and keeps the
remaining links and pins local; `Forbidden`, `NotFound` and `Unknown` skip only
that link. An `Unauthorized` refresh does not mark the scope failed. Active
restrictions from `GET /api/users/me/restrictions` hide the vote arrows (`VOTE`)
and `Пожаловаться` (`REPORT`); a capability a newer Backend adds blocks like
`ALL`; private links, deletion, pins and adding others'
links stay available. The repository is a `SessionDataCleaner`: it cancels and
joins calls in flight before the next account's token appears, deletes the
file and memory state, and drops late answers of the previous session.

## Not in the app

There is no screen listing one's own links across subjects, no home-feed card
for moderation results and no synchronization or version labels. Moderation
has no Android UI; operators
use the Backend moderation API (`../itmo-widgets-backend/docs/ops/moderation.md`).
Strict verification of flow membership is deferred, see
[0008](../decisions/0008-community-moderation.md).

## Verification

```bash
scripts/verify.sh run -- :shared:feature-resources:testAndroidHostTest :app:testGithubDebugUnitTest
scripts/verify.sh shots feature-resources
```

JVM tests cover the order of the short list, ranking ties and the count of the rest (`SubjectLinkChipsTest`),
the headings and group positions of the sheet (`SubjectLinkRowsTest`), period keys
(`ResourceScopeTest`), category guessing (`LinkCategoryGuessTest`), the
repository without and with the connection, the upload of local links, cached
snapshots on errors, session cleanup and a corrupted file
(`SubjectLinksRepositoryImplTest` and `SubjectLinksFileStoreTest` in the module's `commonTest`, the 2.2 files in
`SubjectLinks22GoldenTest`, sign-out on the real graph in `ResourcesReviewsSessionTest`), and the sheet and editor
view models, including the ranking within a category, votes that keep the actions sheet open and rows that keep
their place after a vote (`SubjectLinksViewModelTest`, `StableOrderTest`, `LinkEditorViewModelTest`).

The Compose bodies have host tests in `:shared:feature-resources`: `SubjectLinksSheetTest` (sections in order,
votes through the view model, own rows among others, review states, a restriction, the missing connection, long
titles at 320 dp and font scale 1.3, the four states), `LinkEditorSheetTest` (the form, the audiences and the
save), `LinkActionsSheetTest` (votes that keep the sheet open, `Мои баллы` only for a Google Sheet, the own score
without arrows and none for a private link, a restriction, an own rejected link with its reason, pinning and
reporting, the confirmed delete, a deleted link, one action at a time) and `ReportLinkDialogTest` (reasons, the
failure under the comment, the comment's limit). In `:app`, `ResourcesHostsKoinTest` runs the four hosts over Koin
and an in-memory repository (votes, pins, deletes and reports through the hosts, the report dialog across a
recreation) and `LinkEditorPasteTest` the clipboard paste. Their looks are the four-appearance goldens
`SubjectLinksSheetContent_*`, `LinkEditorSheetContent_*`, `LinkActionsSheetContent_*` and `ReportLinkDialog_*` in
`shared/feature-resources/screenshots/`. The subject page's `Ссылки` rows, votes, `Все ссылки, N`,
`Добавить ссылку` and chats are covered by the recordbook's tests; votes from the page and `canVote` by
`RecordbookSubjectViewModelTest`.
