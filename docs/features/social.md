# Social

Product behaviour: [Как это работает: друзья](../how-it-works/friends.md).

Friends, requests, people search and the person card. Backend is the authority
for relationships and access; the app only renders viewer-scoped capabilities
and never enforces privacy itself. Backend data requires
`Подключение к ITMO.Widgets`; the My ITMO part of a person's profile works
without it. The device fetches that profile directly through MyItmoApi; Backend
does not proxy it or call My ITMO with the viewer's token. Its service account
still resolves current study groups in `UserData`, see
[Current study groups on read](../../../itmo-widgets-backend/docs/contracts/friendships.md#current-study-groups-on-read).

## Modules and routes

- Domain, presentation, data and Compose screens of `feature/social` live in `:shared:feature-social` `commonMain`
  (no Android types); the Koin module `socialModule` (`SocialModule.kt`) declares the singles and the ViewModels
  (`viewModelOf`), and binds `FriendRepositoryImpl` as the core `FriendRepository` the schedule's friend picker and
  sport read.
- Android: `shell/entries/SocialEntries.kt` registers `AppRoutes.Friends`
  (`FriendsRoute`, with the search), `AppRoutes.UserFriends` (`UserFriendsRoute`), `AppRoutes.UserProfile`
  (`UserProfileRoute`); the friend picker is the schedule's ([friend picker](friend-selector.md)). Entries perform the platform effects:
  sharing, clipboard, invitation (`feature/social/ui/SocialSharing.kt`),
  navigation.
- `app/` keeps only `FriendshipPushHandler` (bound in
  `di/ComponentBindingsModule.kt`, see [notifications](notifications.md)) and the
  debug fixtures (`SocialDebugFixtures`, `FriendSelectorFixture`).
- Texts are the module's `strings_social.xml`; `:app` reads four ids as Android
  resources (`person_isu_label`, `person_isu_copied`, `user_search_invite_text`,
  `user_search_invite_chooser`).

## Repositories

- `SocialRepository` loads friends, incoming and outgoing requests and the own
  Backend profile in one refresh through Core's `UsersApi` and `FriendsApi`,
  mapping users with `UserData.toUserSummary()` (`core/model/ClientUserMapping.kt`),
  and caches them as `LoadState` flows. It exposes `profile(isu)`,
  `userFriends(isu)`, `lookup(isus)` and the actions `sendRequest`,
  `acceptRequest`, `rejectRequest`, `cancelRequest`, `removeFriend`. Every action
  returns the fresh `UserProfile` and folds it into the cached lists, so screens
  never refresh after acting.
- `PeopleSearchRepository` searches by name through
  `MyItmoClient.personalities.searchPersonalities` (20 per page) and annotates
  registered users through Backend lookup in chunks of 50. Phone and e-mail from
  the directory never leave the data layer. See decision
  [0006](https://github.com/alllexey-dev/itmo-widgets-backend/blob/dev/docs/decisions/0006-people-search.md).
- `PersonRepository` (`PersonRepositoryImpl`) calls
  `MyItmoClient.personalities.getPersonality(isu)` independently of the opt-in,
  with a memory cache by ISU. The mapper keeps name, photo, positions, rooms and
  education, never contacts, gender or exchange status. `NotFound` is: HTTP 404,
  this endpoint's HTTP 400 with numeric `error_code=100` and explicit
  `result=null`, a successful null result, or a mismatched result ISU. Other
  400s stay errors; MyItmoApi itself keeps the HTTP error unchanged.
- `core/reviews/TeacherReviewsRepository` supplies the optional review section
  ([reviews](reviews.md)).
- `core/friend/FriendRepository` is a facade over `SocialRepository` for the
  schedule picker.

One `SocialRepositoryImpl` serves the screens, the bell's friend requests
(`FriendRequestsBellSource`, a `BellSource` under the `social` qualifier), the picker, `MeViewModel`,
`FriendshipPushHandler` and `SportDataRepositoryImpl`.

### Invariants

- Every repository checks `DemoMode` before a network call and answers from
  `DemoSocial`; Backend calls also pass `BackendGate.mayCallBackend()`.
- `cachedProfile`, `cachedUserFriends` and `cachedReviews` are unavailable while
  the opt-in is off or unknown. Disabling it clears the caches, turns friends and
  requests into `LoadState.Disabled` and clears the own Backend profile.
- `SocialRepositoryImpl` keeps lists, caches, opt-in and a request generation in
  one immutable snapshot changed only through `MutableStateFlow.update`: a late
  response from before disabling or sign-out cannot refill a cleared cache, even
  after re-enabling, and stale opt-in reads cannot clear or revive a newer
  connection. `PersonRepositoryImpl` keeps its cache the same way.
- Sign-out runs the `social`, `person` and `friend-history` session cleaners.
- Only `feature/social/data` reads MyItmoApi's personality models, and only data
  mappers and demo data build `UserSharing`; `SocialRulesTest` (`:konsist`)
  enforces both.
- `RelationshipState` is viewer-relative: `NONE`, `OUTGOING`, `INCOMING`,
  `FRIENDS`, `BLOCKED` (reserved, never produced). `UserSummary.sharing` carries
  the viewer's capabilities, not the owner's audiences.

### Identity publication

`BackendIdentitySync` uploads the ITMO.ID id token on cold start, after sign-in
and when services are enabled. Android retries a failed upload through `IdentitySyncWork`
with exponential backoff, up to six attempts; iOS retries on each return to the foreground
through `AppForeground`. Every failure goes to the diagnostics journal. Until it lands, `UserData.name` is empty and views render
`Пользователь ИСУ N` through `userDisplayName` (`core/text/UserNames.kt`).

People's names read «Фамилия Имя». ITMO.ID's `name` claim lacks one of the three parts and has no fixed order,
so the own profile's name is `personName` of `family_name` and `given_name` (`IdTokenCurrentUserProvider`), and
Backend builds `UserData.name` the same way on identity sync; the `name` claim is used only when a part is missing.
`UserData` carries the parts as `familyName` and `givenName` (null until the account uploads them); `UserSummary`
and `CurrentUser` keep them. Avatar initials (`personInitials` in `core/text/UserNames.kt`, also called by the iOS
screens through the shared framework) are the first letters of the two parts when both are there, never a split of
`name`; without them, and for My ITMO teachers («Фамилия Имя Отчество»), words 1 and 2 of the name.

## Screens

- Profile tab: [me.md](me.md).
- Друзья (`FriendsRoute`, `FriendsViewModel`, `FriendsUiState`): the only home of friends and requests
  (DECISIONS.md, Друзья). The people search sits on top; while the query is blank the lists show: `Заявки` (incoming
  with `Принять` filled and `Отклонить` tonal under the row, sent ones with `Отменить` at the end), `Друзья` with the
  count (rows open the card; removing a friend lives in the card) and `Поделиться профилем` (the viewer's Backend
  profile, `FriendsUiState.self`). The header subtitle is `3 друга, 1 заявка`. An empty screen says `Пока никого` and
  offers the link; without the connection the screen is a gate with `Подключить` (`SettingsPage.SERVICES`). A row in
  flight keeps its buttons inert. A request's status line says when it was sent (`Хочет добавить вас, вчера`) from
  `UserProfile.requestedAt` (Backend's `requestedAt` from f9dd1e0) through `requestStatus` and the shared
  `eventMoment` (`12:05`, `вчера`, `28 сент.`), the bell's wording; without the time it is the status alone.
- People search, in Друзья: a query searches 300 ms after the last keystroke through `PeopleSearchRepository` and
  stands in place of the lists (`PeopleSearchResults`: `В ITMO.Widgets` first, then `Остальные`, `Показать ещё`);
  clearing the field returns to the lists at once. Actions update the found row from the returned profile without a
  new search; `Пригласить` sends `FriendsEvent.Invite` and the host shares the invitation (Android with the download
  link of its distribution).
- Another user's friends (`UserFriendsRoute`, `UserFriendsViewModel`), gated by
  Backend's `canViewFriends`, under `Друзья` with the owner's name. A refresh keeps content on a network failure but
  discards it when authorization is revoked; returning to the screen rechecks access. A hidden list (403) shows as
  the plain empty state: someone else's privacy is not a gate. The target list is never stored in the viewer's own
  friends cache; `cachedUserFriends` keeps the last answer per ISU. Backend enforces the `Кто видит список друзей`
  choice (`Все` by default) and never exposes pending requests or the owner's raw audience.
- Shared list row: presentation builds `UserRowUi` values (`M3206, 2 курс` and a status line); `ui/list/UserList.kt`
  renders them as kit `ListRow`s with the avatar (`PersonRow`, `personGroup`, `userList`).

## Person profile

`AppRoutes.UserProfile` (argument `UserScreenArgs.ISU`): `UserProfileRoute`
with `UserProfileViewModel`, rendered by `UserProfileScreen` (the person card, DECISIONS.md, Карточка человека). It
opens for any positive ISU and combines four independently loaded parts: `PersonRepository` (My ITMO identity,
positions, rooms, education), `SocialRepository.profile(isu)` (the ITMO.Widgets block), `TeacherReviewsRepository`
and, for someone My ITMO lists a position for, `TeacherLessonsGateway.taughtBy(isu)` (the viewer's own lessons with
them; the first positive answer is enough). A card exists when either identity source succeeds. `userProfileUiState`
builds it:

- Name and photo come from My ITMO when the person exists, else from Backend;
  a missing photo uses initials, never another source's photo. Backend's group
  is used only without a My ITMO person.
- The centred header: avatar, full name, `roles` (each position as `shortRole, shortDepartment`, `Студент, M3206`,
  `в друзьях`, `это вы`), `ИСУ N` with copy, up to two `actions`, the first filled. `Написать отзыв`
  (`ProfileReviews.canWrite`) comes first for someone who teaches the viewer; the friendship's next step
  (`Добавить в друзья`, `Отменить заявку`, `Принять заявку` + `Отклонить`) is for a user of ITMO.Widgets; a student
  Backend does not know (`SocialAbsence.NOT_REGISTERED`) gets `Пригласить в ITMO.Widgets`; nothing without the
  connection, for a pure teacher or on the own card. `onAction` ignores an action the card does not offer.
- `Преподавание` (`TeachingBlock`) for anyone with a position, lessons with the viewer or reviews: the lesson they
  give now or next today as the card's hero (`TeacherNowLine` from the optional `TeacherNowGateway`, fetched with
  the lessons and dropped with them at the deadline), the subjects they teach the viewer (`Ведёт у вас`), `Где найти`
  per room, then the reviews. `Учёба` (`StudyBlock`) for a student or a
  user with open rows: the groups (`M3206, 2 курс` with the faculty), then `Расписание`, `Спорт` and `Друзья` as
  Backend's viewer capabilities allow (all open to self); they lead to `AppRoutes.UserSchedule`, `UserSport` and
  `UserFriends` with the ISU and name. `teachingFirst` puts `Преподавание` first when they teach the viewer.
- `Удалить из друзей` is the card's last row for a friend; `requestRemoveFriend` asks first (`ConfirmRemove`).
- Someone else's hidden data is simply absent: no hint, no lock.
- The entry (`UserProfileExits`) shares the profile link (only with a `Content` page), copies the ISU and opens user
  screens, the review editor and report, the invitation, author profiles and source links.
- List items have stable keys, so a late part or a vote never moves what is already on screen.

### Reviews

The last section, only with the connection; see
[teacher reviews](reviews.md#the-profile-section). `UserProfileViewModel` runs
one review mutation at a time (`busyId`) and collects
`TeacherReviewsRepository.observeUpdates()` for its ISU, so a save in the
editor, a report or a mutation from another open profile updates the section in
place; such an update also delivers late reviews.

### Loading and failures

- First entry stays on a skeleton until all four parts leave `Loading` (reply
  or cached answer; a cached person without positions settles the lessons part at once). Once one identity is ready while another part loads, a
  3-second deadline (`PART_DEADLINE`) starts; it resets if no identity remains
  ready. Without any ready identity there is no deadline.
- At the deadline the ready page appears and middle blocks are never inserted
  later. Late reviews append at the bottom; a late person or social block stays
  in the repository cache, is not shown and counts as a partial failure; late lessons are dropped for this load, so
  they never reorder the blocks or the buttons. If the page loses its last ready identity, deferred replies apply
  normally again.
- Cache-seeded parts are replaced as replies arrive; only parts declared late
  are deferred. Visible-page retry is silent, incremental and has no deadline;
  retry from a full-screen error starts over with skeleton, seeds and deadline.
- A shown page never turns into a full-screen error until all three requests
  replied. Absent person plus absent Backend profile is `Профиль не найден`
  without retry; other identity failures without a page offer retry.
- My ITMO `NotFound` is quiet when Backend has a profile. Backend `NotFound` or
  `CustomServicesDisabled` hides its block; reviews `CustomServicesDisabled`
  hides the section. Any other failed part with a page shows one partial-failure
  snackbar after all three requests finish.

### Entry points

Android opens profiles through `Nav3AppNavigator.openUserProfile(isu)`, which
opens `AppRoutes.UserProfile` only for an ISU that `UserScreenArgs.profileIsu`
accepts (`1..Int.MAX_VALUE`; My ITMO `Long` ids pass through it). No ISU is
guessed from a name: rows without a usable ISU stay informational, without a
chevron. Sources: social lists and the search in Друзья, the bell, the friend picker,
friends and teachers in lesson and sport details, subject page `Преподаватели`.
Sheets dismiss before navigating. Friend-sport cards stay read-only. A
friendship push sends `AppEntryIntents.ACTION_OPEN_USER_PROFILE`, which
`EntryRouteParser` turns into the profile tab with the person above it; a
`https://<host>/u/{isu}` link does the same ([app-links.md](app-links.md)).

## Tests

JVM and Compose tests in `:shared:feature-social` (`commonTest`,
`androidHostTest`, goldens in `SocialScreenshotTest`), `FriendshipPushHandlerTest`
in `app/src/test`, `SocialRulesTest` in `:konsist`; see
[screenshot tests](../testing.md#screenshot-tests).

## iOS

The iOS app ([iOS app](../ios.md)) draws Друзья, the person card and another user's friends as SwiftUI screens over
the same view models (ADR 0031), in `iosApp/Sources/Features/Social/`; `Routes+Social.swift` maps the three routes
to `.swiftUI` targets and the Профиль root is the me tab ([profile tab](me.md#ios)). What differs:

- Entries: the Профиль tab, Сегодня's bell, a friendship push and a `/u/<ISU>` route; `https` links do not reach the
  app yet ([degradations](../ios.md#degradations)).
- The search is the kit's `ItmoSearchField` under the header (the navigation bar's drawer would put it above the title).
- Sharing goes through `SocialShares` and `PlatformActions`; the invitation
  names the App Store page, or the site while the app has no App Store record.
  Copying the ISU shows `person_isu_copied`, since iOS confirms nothing itself.
- The report of a review is a SwiftUI sheet over `ReportReviewViewModel` (`AppRoutes.ReportReview`), the review
  editor one over `ReviewEditorViewModel` ([reviews](reviews.md#ios)).
- Tests: `SocialIosModuleTest` (`scripts/ios/test.sh kn :shared:feature-social`), the social snapshot references and
  `UITests/SocialUITests` on the demo session.

## Not implemented yet

Blocking (Backend has no block model), live privacy audiences on the profile
tab, and FCM-driven refresh of the badge when the app is closed.

Parts of the person card the design (DECISIONS.md) asks for that need data the app does not have yet:

- `Сейчас ведёт пару до 13:10` for a teacher draws `TeacherNowGateway.lessonOf(isu)` (`core/schedule/TeacherNow.kt`),
  but nothing binds the gateway yet: its MyITMO implementation needs MyItmoApi 2.2.0. Koin injects it with
  `getOrNull()`, so the card simply has no lesson-now hero until the binding lands.
- A friend's `Сейчас на спорте до 13:10` hero: the status exists in the schedule's friend picker, but the social
  feature has no core port to read it.
- Counts on the study rows (`3 общие пары в неделю`, `2 записи на неделе`) and common friends: nothing computes them.
- The lesson type of `Ведёт у вас` (`лабораторные`): `TeacherLessons` carries subjects only.
- All reviews are listed; there is no `Все 24 отзыва` collapse.
