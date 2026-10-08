# shared/feature-social

## Owns
- `commonMain`, packages `dev.alllexey.itmowidgets.feature.social.*`: friends, people search, person profile,
  another user's friends. `data/` (repositories, mappers, `home/`, `demo/`), `domain/` (with `model/`),
  `presentation/` (four ViewModels, `ProfileFacts`, `ProfileReviews`), `ui/` (`friends/`, `search/`, `profile/`,
  `userfriends/`, `list/`, `reviews/` rows for the profile, `home/` friend-request card, previews).
- `dev.alllexey.itmowidgets.feature.friendselector.*`: the friend picker sheet other features open (`data/` with
  the recent-friends history, `domain/`, `presentation/`, `ui/`).
- `di/`: the Koin modules `socialModule` and `friendSelectorModule` (its `FriendRepository` also serves sport);
  `iosMain`: `socialIosModule(inviteUrl)`, `IosSocialRoutes`, `FriendSelectorIosRoute`, `SocialShares`.
- `strings_social.xml` in `composeResources/values/`, exported to `:app` as Android resources.
- `commonTest`: repositories, ViewModels, screens; `androidHostTest`: `SocialModuleTest`, `FriendSelectorModuleTest`,
  `FriendSelectorSheetTest`, `SocialScreenshotTest`.
- Not here: the four Fragments, `FriendSelectorDialogFragment` and `FriendshipPushHandler` stay in `app/` under
  `feature/social/` and `feature/friendselector/`; `di/bridge/SocialBridge.kt` bridges Koin and Hilt.

## Depends on
- `:shared:core`, `:shared:designsystem`; `:shared:testing` in tests only. Koin and the KMP lifecycle.
- Backend's `users` and `friends` areas behind `BackendGate`; MyItmoApi 2.x for people search and personalities.

## Verify
`scripts/verify.sh quick`, then `scripts/verify.sh shots feature-social`; `scripts/verify.sh klibs feature-social`
after an `iosMain` change.

## Hot files
- Every file here: lane L13; `iosMain`, `iosTest`: lane L18. `build.gradle.kts`: L13 writes, L04 reviews.
- `strings_social.xml`: L13; keys are never renamed.
- Stable identifiers held here: the `recent_schedule_friends` key of `app_preferences`. The `/u/` App Link path,
  `OPEN_USER_PROFILE` and the `friends` channel are app-side entries of `StableIdentifiersTest`.
- `SocialRulesTest` (Konsist, lane L06): only the data layer reads MyItmoApi personality models; only data mappers
  and demo data build viewer capabilities.

## Docs
- [Social](../../docs/features/social.md), [friend selector](../../docs/features/friend-selector.md).
- ADRs [0004](../../docs/decisions/0004-privacy-audiences.md), [0005](../../docs/decisions/0005-friendships.md),
  [0006](../../docs/decisions/0006-people-search.md) and [0009](../../docs/decisions/0009-person-profile.md).
