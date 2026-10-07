# Social

- Friends and requests, people search, another user's friends, the person
  profile with teacher reviews and the AI summary, and the schedule friend
  picker are Compose Multiplatform in `:shared:feature-social`
  (`FriendsRoute`, `UserSearchRoute`, `UserFriendsRoute`, `UserProfileRoute`,
  `FriendSelectorSheetRoute`), hosted by the unchanged `FriendsFragment`,
  `UserSearchFragment`, `UserFriendsFragment`, `UserProfileFragment` and
  `FriendSelectorDialogFragment`; the iOS app hosts the same routes. Their XML
  layouts, adapters, View helpers and View tests are gone, replaced by JVM
  tests and four-appearance goldens in `SocialScreenshotTest`.
- The social and picker data live in `commonMain` behind Koin: one
  `SocialRepositoryImpl` serves the screens, the friend-requests home card,
  the picker, the profile tab, sport and the friendship push, whose Hilt users
  reach it through `di/bridge/SocialBridge.kt`.
- Backend data comes through Core 2.0 (`UsersApi`, `FriendsApi`, the
  friendship push through `FcmDecoder`), people search and the person's
  My ITMO facts through MyItmoApi 2.x personalities.
- The remove-friend and delete-review confirmations are the design system's
  confirm dialog, and the friend picker sits on its sheet host.
- `SocialRulesTest` keeps MyItmoApi's personality models, and with them the
  directory's phone and e-mail, in `feature/social/data`, and lets only data
  mappers and demo data build `UserSharing`.
