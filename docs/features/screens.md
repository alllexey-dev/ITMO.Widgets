# Screen inventory

Every UI host of the Android application: 26 Fragments, 12 bottom sheets, 2
dialogs and 3 activities, 43 in total, plus the overlay host
`AppOverlayHostFragment` and `SpoilerCropActivity`, which extends CanHub's
`CropImageActivity`. 34 `@HiltViewModel` classes and 16 `*VisualTest.kt` files
back them. Each feature has its own section; a change to a screen updates only
the rows of its section.

Columns:

- **Host**: the class, relative to `app/src/main/java/dev/alllexey/itmowidgets/`.
- **Kind**: Fragment, bottom sheet (`BottomSheetDialogFragment`), dialog
  (`DialogFragment`) or Activity.
- **ViewModel**: the `@HiltViewModel` it obtains, with its scope when it is not
  the host itself.
- **Entry**: the `main_nav_graph` or `overlay_nav_graph` destination id, the
  `AppRoot` or `AppScreen` value, the `AppNavigator` method, or the class that
  shows or starts it.
- **Debug host**: the class under `app/src/debug/` that renders it without a
  session, relative to the same package root.
- **Visual test**: the `*VisualTest.kt` files under `app/src/androidTest/` that
  capture it; «layout» marks a test that inflates only the layout.

A dash means there is none.

## Recount

Run from the repository root; the numbers above must match.

```bash
grep -rE 'class [A-Za-z0-9_]+[^:]*: *Fragment\(' app/src/main/java | wc -l                  # 26
grep -rE 'class [A-Za-z0-9_]+[^:]*: *BottomSheetDialogFragment\(' app/src/main/java | wc -l # 12
grep -rE 'class [A-Za-z0-9_]+[^:]*: *DialogFragment\(' app/src/main/java | wc -l            # 2
grep -rE 'class [A-Za-z0-9_]+[^:]*: *AppCompatActivity\(' app/src/main/java | wc -l         # 3
grep -r '@HiltViewModel' app/src/main/java | wc -l                                        # 34
find app/src -name '*VisualTest.kt' | wc -l                                               # 16
```

## App shell

Feature doc: [Architecture](../architecture.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `app/MainActivity.kt` | Activity | `AppUpdateGateViewModel`, `OnboardingGateViewModel`, `SportMyViewModel` (shared with the sport tab) | launcher, App Links, shortcuts, notification intents | `app/SettingsNavigationTestActivity.kt` | — |
| `app/AppOverlayHostFragment.kt` | Fragment (`NavHostFragment`) | — | `MainNavigationCoordinator.openScreen`, hosts `overlay_nav_graph` | `app/SettingsNavigationTestActivity.kt` | — |
| `core/ui/spoiler/SpoilerCropActivity.kt` | Activity (`CropImageActivity`) | — | `SpoilerCropContract` from `SpoilerImagePicker` (settings, widget step) | — | — |

## Sign-in

Feature doc: the sign-in gate is part of [First-run flow](onboarding.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/auth/ui/AuthFragment.kt` | Fragment | `AuthViewModel` | `auth`, start destination of `main_nav_graph` | — | — |
| `feature/auth/ui/LoginActivity.kt` | Activity | `InteractiveLoginViewModel` | started by `AuthFragment` | — | `core/ui/DesignComponentsVisualTest.kt` (layout) |

## Onboarding

Feature doc: [First-run flow](onboarding.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/onboarding/ui/OnboardingFragment.kt` hosting `OnboardingScreen` (`:shared:feature-account`) with `AndroidView` widget previews | Fragment | `OnboardingViewModel` (Koin) | `onboarding` | `app/SettingsNavigationTestActivity.kt` (`onboardingFixture`) | `AccountScreenshotTest` (`:shared:feature-account`), `feature/onboarding/OnboardingVisualTest.kt` |

## Home

Feature doc: [Home and quick actions](home.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/home/ui/HomeFragment.kt` hosting `HomeRoute` (`:shared:feature-home`) | Fragment | `HomeViewModel` (Koin) | `navigation_home`, `AppRoot.HOME` | `app/SettingsNavigationTestActivity.kt` (`HomeDebugFixtures`) | `HomeScreenshotTest` (`:shared:feature-home`), `app/MainNavigationTest.kt` |

## QR pass

Feature docs: [QR pass](qr.md), [Widgets](widgets.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/qr/ui/QrCodeFragment.kt` hosting `QrPassRoute` (`:shared:feature-qr`) | Fragment | `QrCodeViewModel` (Koin) | `qr_pass`, `AppScreen.QR_PASS` | `app/SettingsNavigationTestActivity.kt` (`QrDebugFixtures`) | `QrScreenshotTest` (`:shared:feature-qr`), `feature/home/HomeQrVisualTest.kt` |

## My ITMO web

Feature docs: [My ITMO in the app](my-itmo-web.md), [Home and quick actions](home.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/web/ui/MyItmoWebFragment.kt` hosting `MyItmoWebScreen` (`:shared:feature-account`) around an `AndroidView` WebView | Fragment | — | `my_itmo_web`, `AppScreen.MY_ITMO_WEB` | `feature/web/ui/MyItmoWebPreviewFragment.kt` in `app/SettingsNavigationTestActivity.kt` | `AccountScreenshotTest` (`:shared:feature-account`), `feature/home/HomeWebVisualTest.kt` |

## Recordbook

Feature doc: [Recordbook](recordbook.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/recordbook/ui/RecordbookFragment.kt` | Fragment | `RecordbookViewModel` | `navigation_recordbook`, `AppRoot.RECORDBOOK` | `feature/recordbook/ui/RecordbookPreviewActivity.kt` | `feature/recordbook/RecordbookVisualTest.kt`, `feature/recordbook/RecordbookBarsVisualTest.kt`, `core/ui/DesignComponentsVisualTest.kt` (layout) |
| `feature/recordbook/ui/RecordbookSubjectFragment.kt` | Fragment | `RecordbookSubjectViewModel` | `recordbook_subject`, `AppScreen.RECORDBOOK_SUBJECT` | `feature/recordbook/ui/RecordbookPreviewActivity.kt` | `feature/recordbook/RecordbookVisualTest.kt`, `feature/recordbook/RecordbookBarsVisualTest.kt`, `core/ui/DesignComponentsVisualTest.kt` (layout) |
| `feature/recordbook/ui/RecordbookPeriodBottomSheet.kt` | bottom sheet | — | `RecordbookPeriodBottomSheet.show` from `RecordbookFragment` | `feature/recordbook/ui/RecordbookPreviewActivity.kt` | `feature/recordbook/RecordbookVisualTest.kt` |
| `feature/recordbook/ui/sheets/SheetScoresBottomSheet.kt` | bottom sheet | `SheetScoresViewModel` | `AppNavigator.openSheetScores` | `feature/recordbook/ui/RecordbookPreviewActivity.kt` | `feature/recordbook/SheetScoresVisualTest.kt`, `feature/recordbook/RecordbookVisualTest.kt` |
| `feature/recordbook/ui/BarsLoginActivity.kt` | Activity | `BarsLoginViewModel` | started by `RecordbookFragment`, `RecordbookSubjectFragment` and `MainActivity` for `AppEntryIntents.ACTION_OPEN_BARS_LOGIN` | — | — |

## Schedule

Feature doc: [Schedule](schedule.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/schedule/ui/ScheduleFragment.kt` | Fragment | `ScheduleViewModel` | `navigation_schedule`, `AppRoot.SCHEDULE`; child of `UserScheduleFragment` | `feature/schedule/ui/ScheduleLifecycleTestActivity.kt` | `feature/schedule/ScheduleCardsVisualTest.kt`, `feature/schedule/LessonDetailsVisualTest.kt`, `core/ui/DesignComponentsVisualTest.kt` (layout) |
| `feature/schedule/ui/UserScheduleFragment.kt` | Fragment | — (hosts `ScheduleFragment`) | `user_schedule`, `AppScreen.USER_SCHEDULE` | — | — |
| `feature/schedule/ui/changes/ScheduleChangesFragment.kt` hosting `ScheduleChangesRoute` (`:shared:feature-schedule`) | Fragment | `ScheduleChangesViewModel` (Koin) | `schedule_changes`, `AppScreen.SCHEDULE_CHANGES` | — | `ScheduleScreenshotTest` (`:shared:feature-schedule`) |
| `feature/schedule/ui/details/LessonDetailsBottomSheet.kt` | bottom sheet | `LessonDetailsViewModel` | `AppNavigator.openLessonDetails` | `feature/schedule/ui/ScheduleLifecycleTestActivity.kt` | `feature/schedule/LessonDetailsVisualTest.kt` |
| `feature/schedule/ui/details/PendingSportDetailsBottomSheet.kt` | bottom sheet | — | `AppNavigator.openPendingSportDetails` | `feature/schedule/ui/ScheduleLifecycleTestActivity.kt` | `feature/schedule/LessonDetailsVisualTest.kt` |

## Friend selector

Feature doc: [Schedule](schedule.md) (friends' schedules).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/friendselector/ui/FriendSelectorDialogFragment.kt` | bottom sheet | `FriendSelectorViewModel` | `friend_selector` dialog destination, from `ScheduleFragment` | `app/SettingsNavigationTestActivity.kt` | — |

## Sport

Feature doc: [Sport](sport.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/sport/ui/common/SportFragment.kt` | Fragment | — (pages own theirs) | `navigation_sport`, `AppRoot.SPORT` | — | — |
| `feature/sport/ui/my/SportMyFragment.kt` | Fragment | `SportMyViewModel` (Activity scope) | page of `SportPagerAdapter` | `feature/sport/ui/SportScoreCollapsePreviewActivity.kt` | `feature/sport/cards/SportBookingCardsVisualTest.kt` (cards), `core/ui/DesignComponentsVisualTest.kt` (layout) |
| `feature/sport/ui/sign/SportSignFragment.kt` hosting `SportSignRoute` (`:shared:feature-sport`) | Fragment | `SportSignViewModel` (Koin, Activity scope) | page of `SportPagerAdapter` | `feature/sport/ui/SportCardsPreviewActivity.kt` (lesson list) | `SportScreenshotTest` (`:shared:feature-sport`) |
| `feature/sport/ui/user/UserSportFragment.kt` | Fragment | `UserSportViewModel` | `user_sport`, `AppScreen.USER_SPORT` | — | — |
| `feature/sport/ui/common/SportCommonDetailsBottomSheet.kt` | bottom sheet | — | `SportMyFragment`, `SportSignFragment`; `MainNavigationCoordinator.openSportDetails` for a schedule row that is a known booking | `feature/sport/ui/SportCardsPreviewActivity.kt` | — (JVM `SportDetailsSheetTest` and the `SportDetailsSheetContent_*` goldens of `:shared:feature-sport`) |

## Subject links

Feature doc: [Subject links](resources.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/resources/ui/SubjectLinksBottomSheet.kt` | bottom sheet | `SubjectLinksViewModel` | `AppNavigator.openSubjectLinks` | `app/SubjectLinksPreviewActivity.kt` | `feature/resources/SubjectLinksVisualTest.kt` |
| `feature/resources/ui/LinkEditorBottomSheet.kt` | bottom sheet | `LinkEditorViewModel` | `AppNavigator.openLinkEditor` | `app/SubjectLinksPreviewActivity.kt` | `feature/resources/SubjectLinksVisualTest.kt` |
| `feature/resources/ui/LinkActionsBottomSheet.kt` | bottom sheet | `SubjectLinksViewModel` | `AppNavigator.openLinkActions` | `app/SubjectLinksPreviewActivity.kt` | `feature/resources/SubjectLinksVisualTest.kt` |
| `feature/resources/ui/ReportLinkDialogFragment.kt` | dialog | `SubjectLinksViewModel` | from `LinkActionsBottomSheet` | `app/SubjectLinksPreviewActivity.kt` | — |

## Teacher reviews

Feature doc: [Teacher reviews](reviews.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/reviews/ui/ReviewEditorBottomSheet.kt` | bottom sheet | `ReviewEditorViewModel` | `AppNavigator.openReviewEditor` | `feature/reviews/ui/ReviewEditorPreviewActivity.kt` | `feature/reviews/ReviewEditorVisualTest.kt` |
| `feature/reviews/ui/ReportReviewDialogFragment.kt` | dialog | `ReportReviewViewModel` | `AppNavigator.openReviewReport` | `feature/reviews/ui/ReviewEditorPreviewActivity.kt` | `feature/reviews/ReviewEditorVisualTest.kt` |

## Social

Feature doc: [Social](social.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/social/ui/FriendsFragment.kt` hosting `FriendsRoute` (`:shared:feature-social`) | Fragment | `FriendsViewModel` (Koin) | `friends`, `AppScreen.FRIENDS` | — | `SocialScreenshotTest` (`:shared:feature-social`) |
| `feature/social/ui/UserSearchFragment.kt` hosting `UserSearchRoute` (`:shared:feature-social`) | Fragment | `UserSearchViewModel` (Koin) | `user_search`, `AppScreen.USER_SEARCH` | — | `SocialScreenshotTest` (`:shared:feature-social`) |
| `feature/social/ui/UserProfileFragment.kt` | Fragment | `UserProfileViewModel` | `user_profile`, `AppScreen.USER_PROFILE` | `feature/social/ui/UserProfilePreviewActivity.kt`, `app/SettingsNavigationTestActivity.kt` | `feature/social/UserProfileVisualTest.kt` |
| `feature/social/ui/UserFriendsFragment.kt` hosting `UserFriendsRoute` (`:shared:feature-social`) | Fragment | `UserFriendsViewModel` (Koin) | `user_friends`, `AppScreen.USER_FRIENDS` | `app/SettingsNavigationTestActivity.kt` | `SocialScreenshotTest` (`:shared:feature-social`) |

## Profile tab

Feature doc: [Profile tab](me.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/me/ui/MeFragment.kt` hosting `MeRoute` (`:shared:feature-account`) | Fragment | `MeViewModel` (Koin) | `navigation_me`, `AppRoot.ME` | `app/SettingsNavigationTestActivity.kt` (`AccountDebugFixtures`) | `AccountScreenshotTest` (`:shared:feature-account`), `feature/settings/ProfileBackMotionTest.kt`, `app/MainNavigationTest.kt` |

## Web sign-in

Feature doc: [Web sign-in](web-login.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/weblogin/ui/WebLoginBottomSheet.kt` | bottom sheet | `WebLoginViewModel` | `AppNavigator.openWebLogin` | `feature/weblogin/ui/WebLoginPreviewActivity.kt` | `feature/weblogin/WebLoginVisualTest.kt` |

## Settings

Feature doc: [Settings](../settings.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/settings/ui/SettingsFragment.kt` | Fragment | `SettingsViewModel`, `CustomSpoilerViewModel` | `settings`, `AppScreen.SETTINGS` | `app/SettingsNavigationTestActivity.kt`, `feature/settings/ui/SettingsPreviewActivity.kt` (layout) | — |
| `feature/settings/ui/DiagnosticsFragment.kt` | Fragment | `DiagnosticsViewModel` | `diagnostics`, `AppScreen.DIAGNOSTICS` | — | — |
| `feature/settings/ui/IcsExportBottomSheet.kt` | bottom sheet | `IcsExportViewModel` | from `SettingsFragment` | `app/SettingsNavigationTestActivity.kt` | — |

## Update offer

Feature doc: [Update offer](update.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/update/ui/AppUpdateFragment.kt` | Fragment | `AppUpdateViewModel` | `app_update`, `AppScreen.APP_UPDATE` | `feature/update/ui/AppUpdatePreviewActivity.kt` | `feature/update/AppUpdateVisualTest.kt` |

## Debug tools

Feature doc: [Debug tools](debug.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/debug/ui/DebugToolsFragment.kt` hosting `DebugToolsScreen` (`:app`) | Fragment | `DebugToolsViewModel` (Hilt) | `debug_tools`, `AppScreen.DEBUG_TOOLS` | — | `DebugToolsScreenshotTest` (`:app`) |
