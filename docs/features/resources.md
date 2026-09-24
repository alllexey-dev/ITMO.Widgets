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

A chat is an ordinary link with category `CHAT`; it is never a chip and is
listed in its own block. Labels, icons and visibility texts live in
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
score is the sum. A viewer can add another student's link to their own list
(`Добавить к себе`), pin one link per period (pinning the pinned link unpins it)
and report a link (`Не открывается`, `Другой предмет`, `Спам`, `Другое`, optional
comment). Any HTTPS host is accepted; the app checks the scheme, the host and
the absence of user info, and Backend never fetches a URL. Links open in the
browser only through `core/util/HttpsNavigationPolicy`
(`core/ui/LinkOpener.kt`); without a browser a snackbar says so.

## Subject page

The links block starts with a `Ссылки` header whose trailing `Все` (a 48 dp
text button with a chevron) opens the links sheet whenever the subject has
links, even with none yet: the sheet has its own empty state and
`Добавить ссылку`. `subjectLinkChips` in `core/resources` builds at most four
chips in this order: the pinned link, the MyITMO LMS page (`lms_link`, shown as
`LMS`), then every other non-chat link of the period, own, added and shared
alike, by `SubjectLinkRanking`: the higher score first, of equal scores the
newer link. A link is shown once. An own link is a filled
`colorSurfaceContainerHighest` chip with `colorOnSurface` text, the others stay outlined. `Ещё N` counts the
other non-chat links and also opens the links sheet; the last chip is `+`,
which reads `Добавить ссылку` when it is alone. A tap opens the link, a long
press opens its actions. `Чаты` follows the chips: own and shared chat links
with the title (or the host) and the visibility label.

The recordbook reads the cached snapshot through `SubjectLinksRepository.peek`
first, so a second visit opens without a spinner, then refreshes the scope.

## Sheets

The sheets sit on the Activity's FragmentManager and belong to no back stack.
`AppNavigator.openSubjectLinks`, `openLinkEditor` and `openLinkActions` take
`core/navigation/SubjectLinksArgs`; `MainNavigationCoordinator` shows one sheet
per tag and nothing once the state is saved.

- `SubjectLinksBottomSheet` (`Ссылки` and the subject name): one section per
  category in declaration order, `Чаты` after them, `С прошлых лет` last.
  Within a category own and others' links are ranked together by
  `SubjectLinkRanking`; an own row sits on a rounded `colorSurfaceContainerHigh`
  surface (20 dp, as other list rows) and its caption names who sees it. A row
  shows the title or host and a line with the host when titled, the visibility
  of an own link or the author's group of another's, the study year of a past
  link, `закреплена` and the owner's review state. Others' links have vote
  arrows and a `+` to add them; an own link shows its score, or a lock while it
  is private. The sheet refreshes silently on open; a failed first load offers
  `Повторить`, an empty one says `Ссылок пока нет`. The add button opens the
  editor.
- `LinkEditorBottomSheet` (`Новая ссылка` / `Изменить ссылку`): the URL is
  pasted from the clipboard when the sheet gets focus and the clipboard holds a
  single HTTPS link. `guessCategory` suggests a category by site until the user
  picks one (Google Sheets → `SCORES`, Google Forms → `QUEUE`, GitHub → `TASKS`,
  YouTube, VK Video → `RECORDINGS`, Notion → `NOTES`, `lms.itmo.ru` →
  `MATERIALS`, Telegram, VK chats, WhatsApp → `CHAT`). The title is optional
  and its hint is the category name. `Кто видит` is a list of radio rows
  (at least 48 dp) ordered from the widest audience to the narrowest: `Все`
  (with `После проверки` while premoderation is on), every flow of the viewer
  from broad to nested (the flow name over the kind of classes from
  `lessonTypeNameRes`), then `Только я`. A chosen flow that is no longer offered falls
  back to `Только я`; without the connection only `Только я` is listed with a
  line saying sharing needs the connection. The new link's UUID survives
  process death, so a retried save reaches the same link.
- `LinkActionsBottomSheet` (long press): another student's link starts with
  the same vote arrows and score as the list row (`view_link_votes.xml`,
  `LinkVotes.kt`; tapping the current arrow takes the vote back); the score
  follows the repository and a vote keeps the sheet open. The arrows are
  hidden under a `VOTE` restriction and without the connection. An own shared
  link shows its score without arrows, an own private one none. Then
  `Открыть`; `Добавить к себе` /
  `Убрать из своих` for others' links; `Закрепить` / `Открепить`; `Изменить`
  and `Удалить` (confirmed) for own links; `Пожаловаться` opens
  `ReportLinkDialogFragment` once per link. Actions run one at a time; a
  failure is a snackbar. Every action but a vote closes the sheet on success.

## Without the connection

Without `Подключение к ITMO.Widgets` links are `PRIVATE` and stay on the device:
the editor offers only `Только я` and says `Поделиться можно с подключением к
ITMO.Widgets`; an own local link can be pinned, edited and deleted. Votes,
reports, adding others' links, non-private visibility and editing a link that
is already on the server fail with `AppError.CustomServicesDisabled`. The first
refresh with the connection uploads local links and pins as private server
links under their UUIDs; a link the server refuses stays local and is retried
next time, and an edit made while the upload was in flight is sent next time.

## Storage and synchronization

`core/resources/SubjectLinksRepository` is the port;
`feature/resources/data/SubjectLinksRepositoryImpl` implements it with the
Core API (`subjectLinks`, `saveSubjectLink`, `deleteSubjectLink`,
`setSubjectLinkSaved`, `pinSubjectLink`, `voteSubjectLink`,
`reportSubjectLink`, `myRestrictions`), the connection gate and an injected
wall clock. With the connection the server is the source of truth: every action
goes to it at once and its answer updates the cached snapshot. There is no
background synchronization.

`SubjectLinksFileStore` keeps `filesDir/subject_links/cache.json`: local links,
local pins and the last server answer per scope. It is persistent data, not
`cacheDir`, because device-only links must not be evicted. Writes go through a
temporary file, `fsync` and an atomic move; a corrupted file is reported and
never replaced with an empty one. Cloud backup and device transfer exclude the
directory. A failed refresh keeps the cached snapshot; a scope that never
loaded shows its local links if it has any, otherwise an error.

Backend error codes map to `AppError`: `restricted` → `Restricted` (and the
restrictions are refreshed), `permission_denied` → `Forbidden`, `not_found` →
`NotFound`, anything else, including 409, → `Unknown`. Active restrictions from
`GET /api/users/me/restrictions` hide the vote arrows (`VOTE`) and
`Пожаловаться` (`REPORT`); private links, deletion, pins and adding others'
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
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.alllexey.itmowidgets.feature.resources.SubjectLinksVisualTest
```

JVM tests cover chip order, ranking ties and `Ещё N` (`SubjectLinkChipsTest`), period keys
(`ResourceScopeTest`), category guessing (`LinkCategoryGuessTest`), the
repository without and with the connection, the upload of local links, cached
snapshots on errors, session cleanup and a corrupted file
(`SubjectLinksRepositoryImplTest`), and the sheet and editor view models, including the ranking within a category
and votes that keep the actions sheet open (`SubjectLinksViewModelTest`,
`LinkEditorViewModelTest`).
`SubjectLinksVisualTest` runs the real sheets in `SubjectLinksPreviewActivity`
over the debug-only `MemorySubjectLinksRepository`: every category with chats
and past years, voting, the editor with three nested flows, the editor with a guessed link and available audiences,
the editor without the connection, an own rejected link, another student's
link, an own row ranked between others' rows on its tonal surface, voting in
the actions sheet, the own score without arrows, arrows hidden by a restriction,
and long titles at a large font on a narrow screen. The subject page's
`Ссылки` header, chips and chats are covered by `RecordbookVisualTest`.
