# Social

Friends, requests, people search and person profiles. Backend is the authority
for relationships and access; the app only renders viewer-scoped capabilities.
Backend data requires `Подключение к ITMO.Widgets`; the My ITMO part of a
person's profile works without it. The device fetches the identity header and
facts directly through MyItmoApi. Backend does not proxy that profile or call
My ITMO with the viewer's token. Its separate service account still resolves
current study groups in `UserData`, as described in
[Current study groups on read](../../../itmo-widgets-backend/docs/contracts/friendships.md#current-study-groups-on-read).

## Repositories

- `SocialRepository` loads friends, incoming and outgoing requests and the own
  Backend profile in one refresh, caches them as `LoadState` flows (`core/result/LoadState`) and exposes
  `profile(isu)`, `userFriends(isu)`, `lookup(isus)` and the actions `sendRequest`, `acceptRequest`,
  `rejectRequest`, `cancelRequest`, `removeFriend`. Every action returns the
  fresh `UserProfile` and folds it into the cached lists, so screens never
  refresh after acting. It is a `SessionDataCleaner`.
- `PeopleSearchRepository` searches people by name through MyITMO
  (`searchPersonalities`, 20 per page) and annotates registered users through
  Backend lookup in chunks of 50. Phone and e-mail from the directory never leave
  the data layer. See decision [0006](../decisions/0006-people-search.md).
- `feature/social/domain/PersonRepository` calls `MyItmoApi.getPersonality(isu)`
  through `PersonRepositoryImpl`, independently of the Backend opt-in. It keeps
  a memory cache by ISU and is a singleton `SessionDataCleaner`. The mapper
  retains name, photo, positions, rooms and education, not contacts, gender or
  exchange status. Empty strings and repeated facts are normalized at this
  boundary. Only this endpoint's observed HTTP 400 with numeric `error_code=100`
  and explicit `result=null` means `NotFound`; other 400 responses remain errors.
  HTTP 404, a successful null result and a mismatched result ISU also mean
  `NotFound`. The MyItmoApi library itself keeps the HTTP error unchanged.
- `core/reviews/TeacherReviewsRepository` supplies the optional review section;
  its implementation belongs to `feature/reviews/data`, see [reviews](reviews.md).
- `core/friend/FriendRepository` is a facade over `SocialRepository` for the
  schedule picker, exposing friends as plain `UserSummary` values.

The social and picker data live in `:shared:feature-social` `commonMain`
(`feature/social/data`, `feature/friendselector/data`) and Koin constructs them
in `socialModule` and `friendSelectorModule`: one `SocialRepositoryImpl` serves
the screens, the friend-requests home card (`HomeCardSource` under the
`social` qualifier), the picker and the session cleaners (`social`, `person`
and `friend-history`, reaching sign-out through `di/bridge/SessionCleanersBridge.kt`).
`MeViewModel` reads the same Koin single. Android code still on Hilt
(`FriendshipPushHandler`, `SportDataRepositoryImpl`) reads `SocialRepository`
and `FriendRepository` through `di/bridge/SocialBridge.kt`, which forwards the
same Koin singles.
`TeacherReviewsRepositoryImpl` is a Hilt singleton reached through
`ReviewsBridge`. `cachedProfile`, `cachedUserFriends` and `cachedReviews` are
unavailable while the opt-in is off or unknown. Disabling it clears the caches,
changes friends and requests to `LoadState.Disabled` and clears the own Backend
profile. `SocialRepositoryImpl` keeps the lists, the caches, the opt-in and a
request generation in one immutable snapshot that changes only through
`MutableStateFlow.update`, so the non-suspend reads need no lock; a late
response from before disabling or sign-out cannot refill the cleared cache,
even after re-enabling. Stale opt-in reads cannot clear or revive a newer
connection either. `PersonRepositoryImpl` keeps its cache the same way.

`RelationshipState` is viewer-relative: `NONE`, `OUTGOING`, `INCOMING`,
`FRIENDS`, `BLOCKED` (reserved, never produced yet). `UserSummary.sharing`
carries the viewer's capabilities, not the owner's audiences.

Identity publication: `BackendIdentitySync` uploads the ITMO.ID id token on cold
start, after sign-in and when services are enabled. A failed upload is retried
by `IdentitySyncWork` with exponential backoff, up to six attempts, and every
failure is recorded in the diagnostics journal. Until the upload lands,
`UserData.name` from Backend is empty; views using that identity render
`Пользователь ИСУ N` through `Context.userDisplayName`. The person profile
prefers its direct My ITMO identity when available.

## Profile tab (`feature/me`)

Header with avatar, name, study group from Backend and ISU. A `Друзья` card
with the friends count and an incoming-requests badge, `Найти людей` and
`Приватность` rows (the latter opens the settings privacy page); when services
are off, one row explains it and opens settings. An `Приложение` card holds
settings and debug tools; notification state and the version live in settings.
Above sign-out, two compact tonal buttons with logos open the GitHub repository
and the `@itmowidgets` Telegram channel. Telegram tries the `tg://` deep link
first and falls back to the web page. The tab refreshes social data on start
and when services are re-enabled. A `Поделиться` icon beside the own name,
shown once the ISU is known, shares the own profile link
([app-links.md](app-links.md)).

## Friends screen (`feature/social`, overlay `FRIENDS`)

Two tabs on one list: `Друзья` (rows with `Удалить`, confirmed by a dialog) and
`Заявки` (incoming with `Принять`/`Отклонить`, outgoing with `Отменить`),
grouped by section headers. The requests tab carries a badge with the incoming
count. A search icon opens people search. Disabled services show a locked state
that leads to settings.

## People search (overlay `USER_SEARCH`)

Query after a 300 ms debounce; results split into `В ITMO.Widgets` (with the
relationship action: add, cancel, accept) and `Остальные` (with `Пригласить`,
which opens a share sheet with the release link). `Показать ещё` loads the next
page. Actions update the row from the returned profile without a new search.
The whole row opens the person's profile in both sections, including
`Остальные`; the separate invitation action remains a share action.

## Person profile (overlay `USER_PROFILE`, argument `UserScreenArgs.ISU`)

One screen opens for any positive ISU, not just registered users. It combines
three independently loaded parts: `PersonRepository` (My ITMO identity and
facts), `SocialRepository.profile(isu)` (the ITMO.Widgets block), and
`TeacherReviewsRepository` (reviews of the person as a teacher). A page exists
when either identity source succeeds. Its name and photo come from My ITMO when
that person exists, otherwise from Backend; a missing photo uses initials rather
than another source's photo. Empty Backend names use `Context.userDisplayName`.

`UserProfileAdapter` renders one RecyclerView in the language of the subject
page: a result card, then sections whose headings are in `colorPrimary` over
connected groups ([design](../design.md#connected-groups)); no line is joined
by « · ». In this order:

1. The hero card (`item_profile_header.xml`, `Card.Hero`): photo or initials,
   the name and one short line: the role of the first position without its
   qualifications (`shortRole`: the first clause before a parenthesis or a
   comma, capitalised, «Преподаватель»), a short department when the position
   has no title, otherwise the student group with its course (`M3234, 2 курс`).
   Backend's group is used only without a My ITMO person; without either the
   line is absent. Under it the ISU number alone with a 16 dp copy symbol: a
   48 dp target drawn into the gaps around the line that copies the number
   (below Android 13 a toast `Номер ИСУ скопирован`), TalkBack reads
   `Номер ИСУ N, скопировать`. With a social block the card ends with the
   friendship (below).
2. `Должности`: every position, its title and the department on the second
   line; a position with no title uses its department once.
3. `Где найти`: rooms with the building on the second line.
4. `ITMO.Widgets` (with a social block): `Друзья`, `Расписание`, `Спорт` as one
   group, the privacy hint under it and, for a friend, `Удалить из друзей`.
5. `Учёба`: the group with its course and faculty on the second line. Backend's
   primary group supplies it only when the My ITMO person is absent.
6. `Отзывы`, see [Reviews](#reviews) below.

Fact rows are informational, not clickable, with accessible category
descriptions (`Должность: Доцент`).

The friendship lives in the hero card, only with a social block: a friend has
the badge `в друзьях` and the viewer `это вы`, without buttons; `Заявка
отправлена` over `Отменить заявку` (tonal); `Хочет добавить вас` over `Принять
заявку` (filled) and `Отклонить` (tonal); anyone else `Добавить в друзья`
(filled); a blocked person nothing. A friend is removed by the error-coloured
text button `Удалить из друзей` under the `ITMO.Widgets` group, after
confirmation. Sharing rows follow Backend's viewer capabilities; own schedule
and sport are available to self. A closed row keeps its surface, fades its
content and shows a lock, not an action. Open rows lead to `USER_FRIENDS`,
`USER_SCHEDULE` or `USER_SPORT` with the ISU and displayed name.

### Reviews

The last section shows reviews of the person as a teacher, only with the
connection ([teacher reviews](reviews.md#the-profile-section)). It exists when
there is at least one review, an own review, or `Написать`: the viewer may
write (`canWrite`), has no review of this person yet and the person teaches
(Backend's `knownTeacher` or any My ITMO position). The heading `Отзывы` with
the count after it (TalkBack `Отзывы, N`) counts the own review too and
carries `Написать`, which opens the review editor. The AI summary card follows
the heading; the own review is a group of its own 8 dp under it and the others'
reviews one connected group 16 dp further down.

- The viewer's own review comes first with its status, anonymity, rejection
  reason and, once published, its score; its menu edits it or deletes it after
  `Удалить отзыв?`.
- Other reviews follow in Backend's order with votes and `Пожаловаться` in the
  menu. A named author heads the card as a link and opens that person's profile.
- `UserProfileViewModel` keeps one review mutation at a time (`busyId`): a vote
  or deletion disables that card's controls, and the answer replaces the
  section. A failure shows the action snackbar and keeps the section.
- The view model also collects `TeacherReviewsRepository.observeUpdates()` for
  its ISU, so a save in the editor, a report or a mutation from another open
  profile updates the section in place, without a reload or a scroll jump. Such
  an update also delivers reviews that were late.

### Loading and failures

- First entry stays on a skeleton until all three parts leave `Loading`,
  either by a reply or a cached answer. If the My ITMO person or Backend
  profile is ready while another part is loading, a 3-second coroutine
  deadline starts. It resets if neither identity remains ready. Without any
  ready identity there is no deadline: the skeleton stays.
- At the deadline the ready page appears without inserting middle blocks
  later. Late reviews append at the bottom; a late person or social block is
  kept in the repository cache, does not enter the shown page and counts as
  a partial failure. If the page loses its last ready identity, those deferred
  identity replies apply normally again.
- Replies for cache-seeded parts replace their visible data as they arrive;
  only parts declared late are deferred. Visible-page `Повторить` is silent,
  updates parts incrementally and has no deadline. It can immediately use a
  cached late identity. Retry from a full-screen error starts over with the
  skeleton, cache seeds and deadline; it does not restore the old page.
- A shown page does not turn into a full-screen error until all three requests
  have replied. An absent person plus absent Backend profile shows only
  `Профиль не найден`, without a description or retry. Other identity failures
  without a page show `Не удалось загрузить`, a reason and `Повторить`.
- My ITMO `NotFound` is quiet when Backend provides a profile. Backend
  `NotFound` or `CustomServicesDisabled` simply hides its block; reviews
  `CustomServicesDisabled` hides the section. Any other failed part with a
  page shows one `Часть данных не загрузилась` snackbar with `Повторить`,
  after all three requests finish.

A diff commit switches content and placeholders atomically and checks both the
current binding and render revision. RecyclerView has no item animator and
uses `PREVENT_WHEN_EMPTY` to restore scroll after view recreation. Review text
is never truncated; date and source formatting are specified in [reviews](reviews.md).

### Entry points

Fragments open profiles through `core/ui/navigation.Fragment.openUserProfile(isu)`:

- Friends, incoming/outgoing requests and another person's friends list.
- Both people-search sections and people shown on the home feed.
- The friend picker (a locked row tap or long-press).
- Friends in lesson and sport details.
- The teacher in the lesson, pending-sport and sport-details headers.
- `Преподаватели` rows on the subject page when an ISU is available.

Sheets dismiss before navigating. My ITMO `Long` identifiers pass through
`UserScreenArgs.profileIsu` (`1..Int.MAX_VALUE`); no ISU is guessed from a name.
Rows without a usable ISU remain informational and have neither chevron nor
click action. Friend-sport cards remain read-only and do not open teacher
profiles. Friendship pushes retain the Activity entry point
`MainActivity.ACTION_OPEN_USER_PROFILE`, which opens `AppScreen.USER_PROFILE`
through the navigation coordinator. A shared link `https://<host>/u/{isu}`
opens it the same way above the profile tab. The top bar's `Поделиться` icon,
shown only with a `Content` page (the own profile too), shares that link; see
[app-links.md](app-links.md).

## Another user’s friends (overlay `USER_FRIENDS`)

The person profile has a `Друзья` row controlled by Backend’s `canViewFriends`.
The screen shows only accepted friends, with every row and capability relative
to the signed-in viewer. Rows open person profiles; the list has no mutation
buttons or request tabs. First loading, empty, denied, disabled services and
retryable errors are distinct: list placeholders, `Пока нет друзей` with
`Обновить`, the hidden list without an action, the settings action and a retry.
A failed refresh of a shown list keeps it and offers `Повторить` in a snackbar.
The screen is `UserFriendsRoute` from `:shared:feature-social`, hosted by
`UserFriendsFragment` on Android. Refresh retains content on network failure,
but discards it if authorization is revoked; returning to the screen rechecks
access. No target list is stored in the viewer’s own friends cache, but the
repository keeps the last answer per ISU (`cachedUserFriends`) and the last
profile per ISU from any list, screen or action (`cachedProfile`). Both are
cleared on sign-out and when the opt-in is disabled. A reopened friends list
can render its cached snapshot with `refreshing`; the composite person profile
follows the loading contract above.

`Кто видит список друзей` is an independent privacy choice: `Все` (the default
for both existing and new accounts), `Друзья`, `Никто`. Backend enforces it before
reading the list and never exposes pending requests or the owner’s raw audience.

## Shared list row

Every social list renders the same row: avatar, name, `ISU • group` subtitle,
optional status line, up to two action buttons or a chevron, section headers and
a load-more row. Presentation builds `UserRowUi` values with `UiText` labels.
In Compose, `ui/list/UserList` in `:shared:feature-social` renders them with the
kit's `UserRow` and maps `UserAction` to its label (`UserAction.label()`);
another user's friends uses it. The friends screen and people search still use
`item_user_row.xml` with `UserListAdapter` until their ports.

## Verification

`PersonRepositoryImplTest`, `SocialRepositoryImplTest`, `ProfileFactsTest`,
`ProfileReviewsTest`, `UserProfileStateTest` and `UserProfileViewModelTest`
cover the source boundary, cache invalidation, the short role and the
headline, the facts, the reviews section and deterministic
loading/deadline/action behavior. `UserProfileVisualTest` exercises all
profile states, the hero with the copied ISU number, the grouped facts, every
friendship state, delayed parts, recycling, accessibility, photo failure and
scroll restoration in the full appearance matrix. Another user's friends is
`UserFriendsScreen` in `:shared:feature-social`: `SocialScreenshotTest` records
every state (`UserFriendsScreen_<state>`) and `UserList`, and
`UserFriendsScreenTest` and `UserListTest` cover the states, row taps, actions,
load-more and 48 dp targets.
See [visual test commands](../design.md#running-the-visual-tests).

## Not implemented yet

Blocking (Backend has no block model), live privacy audiences on the profile
tab, and FCM-driven refresh of the badge when the app is closed.
