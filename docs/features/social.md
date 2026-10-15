# Social

Product behaviour: [Как это работает: друзья](../how-it-works/friends.md).

Friends, requests, people search and person profiles. Backend is the authority
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
  (`FriendsRoute`), `AppRoutes.UserSearch` (`UserSearchRoute`),
  `AppRoutes.UserFriends` (`UserFriendsRoute`), `AppRoutes.UserProfile`
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

## Screens

- Profile tab: [me.md](me.md).
- Friends (`FriendsRoute`, `FriendsViewModel`): friends and requests tabs; a row
  in flight keeps its buttons inert; removal goes through the kit's
  `ConfirmDialog`.
- People search (`UserSearchRoute`, `UserSearchViewModel`): 300 ms debounce,
  clearing the field resets at once without the debounce. Actions update the row
  from the returned profile without a new search. The Android entry shares the
  invitation with the download link of its distribution.
- Another user's friends (`UserFriendsRoute`, `UserFriendsViewModel`), gated by
  Backend's `canViewFriends`. A refresh keeps content on a network failure but
  discards it when authorization is revoked; returning to the screen rechecks
  access. The target list is never stored in the viewer's own friends cache;
  `cachedUserFriends` keeps the last answer per ISU. Backend enforces the
  `Кто видит список друзей` choice (`Все` by default) and never exposes pending
  requests or the owner's raw audience.
- Shared list row: presentation builds `UserRowUi` values; `ui/list/UserList`
  renders them with the kit's `UserRow` (`UserAction.label()`).

## Person profile

`AppRoutes.UserProfile` (argument `UserScreenArgs.ISU`): `UserProfileRoute`
with `UserProfileViewModel`, rendered by `UserProfileScreen`. It opens for any
positive ISU and combines three independently loaded parts: `PersonRepository`
(My ITMO identity and facts), `SocialRepository.profile(isu)` (the ITMO.Widgets
block) and `TeacherReviewsRepository`. A page exists when either identity
source succeeds.

- Name and photo come from My ITMO when the person exists, else from Backend;
  a missing photo uses initials, never another source's photo. Backend's group
  is used only without a My ITMO person.
- The headline role is `shortRole`: the first clause of the first position
  before a parenthesis or comma, capitalised.
- Sharing rows follow Backend's viewer capabilities; own schedule and sport are
  available to self. Open rows lead to `AppRoutes.UserFriends`,
  `AppRoutes.UserSchedule` or `AppRoutes.UserSport` with the ISU and name.
- The entry (`UserProfileExits`) shares the profile link (only with a `Content`
  page), copies the ISU and opens user screens, the review editor and report,
  author profiles and source links.
- List items have stable keys, so a late part or a vote never moves what is
  already on screen.

### Reviews

The last section, only with the connection; see
[teacher reviews](reviews.md#the-profile-section). `UserProfileViewModel` runs
one review mutation at a time (`busyId`) and collects
`TeacherReviewsRepository.observeUpdates()` for its ISU, so a save in the
editor, a report or a mutation from another open profile updates the section in
place; such an update also delivers late reviews.

### Loading and failures

- First entry stays on a skeleton until all three parts leave `Loading` (reply
  or cached answer). Once one identity is ready while another part loads, a
  3-second deadline (`PART_DEADLINE`) starts; it resets if no identity remains
  ready. Without any ready identity there is no deadline.
- At the deadline the ready page appears and middle blocks are never inserted
  later. Late reviews append at the bottom; a late person or social block stays
  in the repository cache, is not shown and counts as a partial failure. If the
  page loses its last ready identity, deferred replies apply normally again.
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
chevron. Sources: social lists, people search, the bell, the friend picker,
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

The iOS app ([iOS app](../ios.md)) hosts the same Compose routes through
`shared/ios` `screens/SocialScreens.kt` (`socialModule` + `socialIosModule` in
`IosKoinModules`); Swift hosts are in `iosApp/Sources/Features/Social/`,
`Routes+Social.swift` maps the four routes. What differs:

- Entries: the Me tab ([profile tab](me.md#ios)), Сегодня, a friendship
  push and a `/u/<ISU>` route; `https` links do not reach the app yet
  ([degradations](../ios.md#degradations)).
- `UserProfileIosRoute` and `UserFriendsIosRoute` (`src/iosMain`) build the
  `SavedStateHandle` with `UserScreenArgs`.
- Sharing goes through `SocialShares` and `PlatformActions`; the invitation
  names the App Store page, or the site while the app has no App Store record.
  Copying the ISU shows `person_isu_copied`, since iOS confirms nothing itself.
- Reviews are on (`PlatformCapabilities.reviews`, see [reviews](reviews.md#ios)).
- Tests: `SocialIosModuleTest` (`scripts/ios/test.sh kn :shared:feature-social`)
  and `UITests/SocialUITests` on the demo session.

## Not implemented yet

Blocking (Backend has no block model), live privacy audiences on the profile
tab, and FCM-driven refresh of the badge when the app is closed.
