# Social

Product behaviour: [Как это работает: друзья](../how-it-works/friends.md).

Friends, requests, people search and the person card. Backend is the authority
for relationships and access; the app only renders viewer-scoped capabilities
and never enforces privacy itself. Backend data requires
`Подключение к ITMO.Widgets`; the My ITMO part of a person's profile works
without it. The device fetches that profile directly through MyItmoApi; Backend
does not proxy it or call My ITMO with the viewer's token. Its service account
still resolves current study groups in `UserData`, see
[Current study groups on read](https://github.com/alllexey-dev/itmo-widgets-backend/blob/dev/docs/contracts/friendships.md#current-study-groups-on-read).

The friend picker is the schedule's ([friend picker](schedule.md#friend-picker)); `app/` keeps only
`FriendshipPushHandler` ([notifications](notifications.md)) and debug fixtures.

## Privacy

The owner sets three audiences on «Сервер ITMO.Widgets» -> `Кто видит`
([settings](../settings.md#privacy)): `Расписание`, `Спорт` and `Список друзей`,
each `Все`, `Друзья` or `Никто`.

- Name, group and ISU are always visible to authenticated users; nothing in the
  app implies these official identity fields can be hidden.
- Defaults: `Друзья` for schedule and sport (new users), `Все` for the friends
  list (existing and new accounts).
- `Все` allows any authenticated app user, regardless of the viewer's own
  choices; `Друзья` requires mutual friendship; `Никто` allows only the owner.
  There is no reciprocal sharing requirement.
- Upgrading never silently broadens an audience: enabled legacy settings became
  `Друзья`, disabled ones `Никто`.
- Backend, not the app, enforces audiences and friendship (decision
  [0004](https://github.com/alllexey-dev/itmo-widgets-backend/blob/dev/docs/decisions/0004-privacy-audiences.md)).
  Only the owner privacy endpoint returns audience settings; other responses
  carry viewer-scoped permissions. Unknown settings are disabled and shown
  without an invented audience; a failed save restores the confirmed values.
- Connecting reloads the audiences; loading and retry (`Сервер не ответил`,
  `Повторить`) stay visible at least 300 ms to avoid a flash, without delaying
  the request. Disconnecting forgets them at once.
- Friends are explicit: a request stays pending until the other side accepts or
  rejects or the sender cancels; a crossed request becomes a friendship at once.
  Blocking does not exist and a rejected request is not a block.
- There is no review-visibility setting: the author chooses `Анонимно` per
  review ([teacher reviews](reviews.md#editor)).

## Repositories

- `SocialRepository` loads friends, incoming and outgoing requests and the own
  Backend profile in one refresh and caches them as `LoadState` flows. Every
  action returns the fresh `UserProfile` and folds it into the cached lists, so
  screens never refresh after acting.
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
- `core/social/PersonPhotosRepository` is the cross-feature directory-photo port. Its shared implementation,
  `PersonPhotosRepositoryImpl`, reuses `PersonRepository`'s session cache (including people without a photo),
  coalesces concurrent lookups by ISU and limits them to four. Failures leave initials and can retry on the next
  screen visit; the demo follows `DemoSocial`, and a signed-out real session makes no lookup. It stores no second
  photo cache. The lesson sheet and subject page ViewModels put `pictureUrl` into their state on both platforms;
  Android uses `PersonListRow`, iOS the standard `ItmoAvatar`/`AsyncImage`, with no directory calls from a View.
- One `SocialRepositoryImpl` serves the screens, the bell's friend requests, the
  schedule's friend picker (through `core/friend/FriendRepository`), the profile
  tab, `FriendshipPushHandler` and sport.

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
- Друзья (`FriendsViewModel`): the only home of friends and requests. The people search sits on top; while the
  query is blank the lists show: `Заявки` (incoming and sent), `Друзья` (rows open the card; removing a friend lives
  in the card) and `Поделиться профилем`. Without the connection the screen is a gate with `Подключить`. A row in
  flight keeps its buttons inert. A request's status line says when it was sent from `UserProfile.requestedAt`
  through the shared `eventMoment`, the bell's wording; without the time it is the status alone.
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

## Person profile

`AppRoutes.UserProfile` (argument `UserScreenArgs.ISU`): `UserProfileRoute`
with `UserProfileViewModel`, rendered by `UserProfileScreen` (the person card). It
opens for any positive ISU and combines four independently loaded parts: `PersonRepository` (My ITMO identity,
positions, rooms, education), `SocialRepository.profile(isu)` (the ITMO.Widgets block), `TeacherReviewsRepository`
and, for someone My ITMO lists a position for, `TeacherLessonsGateway.taughtBy(isu)` (the viewer's own lessons with
them; the first positive answer is enough). A card exists when either identity source succeeds. `userProfileUiState`
builds it:

- Name and photo come from My ITMO when the person exists, else from Backend;
  a missing photo uses initials, never another source's photo. Backend's group
  is used only without a My ITMO person.
- The header: avatar, full name, `roles`, `ИСУ N` with copy, up to two `actions`. `Написать отзыв`
  (`ProfileReviews.canWrite`) comes first for someone who teaches the viewer; the friendship's next step
  (`Добавить в друзья`, `Отменить заявку`, `Принять заявку` + `Отклонить`) is for a user of ITMO.Widgets; a student
  Backend does not know (`SocialAbsence.NOT_REGISTERED`) gets `Пригласить в ITMO.Widgets`; nothing without the
  connection, for a pure teacher or on the own card. `onAction` ignores an action the card does not offer.
- `Преподавание` (`TeachingBlock`) for anyone with a position, lessons with the viewer or reviews: the lesson they
  give now or next today as the card's hero (`TeacherNowLine` from the optional `TeacherNowGateway`, fetched with
  the lessons and dropped with them at the deadline), the subjects they teach the viewer (`Ведёт у вас` in the current term, `Было у вас осенью 2025` or `весной 2026`
  for an earlier one, from `TeacherLessons.past`), `Где найти`
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
