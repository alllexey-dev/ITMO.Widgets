# Screen inventory

Every UI host of the Android application: 26 Fragments, 12 bottom sheets, 2
dialogs and 3 activities, 43 in total, plus the overlay host
`AppOverlayHostFragment` and `SpoilerCropActivity`, which extends CanHub's
`CropImageActivity`. 34 `@HiltViewModel` classes and 13 `*VisualTest.kt` files
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
find app/src -name '*VisualTest.kt' | wc -l                                               # 13
```

## App shell

Feature doc: [Architecture](../architecture.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `app/MainActivity.kt` | Activity | `AppUpdateGateViewModel`, `OnboardingGateViewModel`, `SportMyViewModel` (shared with the sport tab) | launcher, App Links, shortcuts, notification intents | `app/SettingsNavigationTestActivity.kt` | — |
| `app/AppOverlayHostFragment.kt` | Fragment (`NavHostFragment`) | — | `MainNavigationCoordinator.openScreen`, hosts `overlay_nav_graph` | `app/SettingsNavigationTestActivity.kt` | — |
| `core/ui/spoiler/SpoilerCropActivity.kt` | Activity (`CropImageActivity`) | — | `SpoilerCropContract` from `SpoilerImagePicker` (settings, widget step) | — | — |

## Sign-in

Feature doc: [Sign-in](auth.md); the hidden demo entry in [Demo session](demo.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/auth/ui/AuthFragment.kt` hosting `AuthRoute` (`:shared:feature-account`) | Fragment | `AuthViewModel` (Koin) | `auth`, start destination of `main_nav_graph` | — | `AccountScreenshotTest` (`:shared:feature-account`), `app/DemoModeFlowTest.kt`, `app/MainActivitySessionRoutingTest.kt` |
| `feature/auth/ui/LoginActivity.kt` hosting `LoginScreen` (`:shared:feature-account`) around an `AndroidView` `SwipeRefreshLayout` and WebView | Activity | `InteractiveLoginViewModel` (Koin) | started by `AuthFragment` | — | `AccountScreenshotTest` (`:shared:feature-account`, error state) |

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
| `feature/recordbook/ui/RecordbookFragment.kt` hosting `RecordbookRoute` (`:shared:feature-recordbook`) | Fragment | `RecordbookViewModel` (Koin) | `navigation_recordbook`, `AppRoot.RECORDBOOK` | `feature/recordbook/ui/RecordbookPreviewActivity.kt` | `RecordbookScreenshotTest` (`:shared:feature-recordbook`) |
| `feature/recordbook/ui/RecordbookSubjectFragment.kt` | Fragment | `RecordbookSubjectViewModel` | `recordbook_subject`, `AppScreen.RECORDBOOK_SUBJECT` | `feature/recordbook/ui/RecordbookPreviewActivity.kt` | `feature/recordbook/RecordbookVisualTest.kt`, `core/ui/DesignComponentsVisualTest.kt` (layout) |
| `feature/recordbook/ui/RecordbookPeriodBottomSheet.kt` hosting `RecordbookPeriodSheetContent` (`:shared:feature-recordbook`) | bottom sheet | — | `RecordbookPeriodBottomSheet.show` from `RecordbookFragment` | — | `RecordbookScreenshotTest` (`:shared:feature-recordbook`) |
| `feature/recordbook/ui/sheets/SheetScoresBottomSheet.kt` | bottom sheet | `SheetScoresViewModel` | `AppNavigator.openSheetScores` | `feature/recordbook/ui/RecordbookPreviewActivity.kt` | `feature/recordbook/RecordbookVisualTest.kt` (JVM `SheetScoresSheetTest` and the `SheetScoresSheet_*` goldens of `:shared:feature-recordbook`) |
| `feature/recordbook/ui/BarsLoginActivity.kt` | Activity | `BarsLoginViewModel` | started by `RecordbookFragment`, `RecordbookSubjectFragment` and `MainActivity` for `AppEntryIntents.ACTION_OPEN_BARS_LOGIN` | — | — |

## Schedule

Feature doc: [Schedule](schedule.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/schedule/ui/ScheduleFragment.kt` hosting `ScheduleRoute` (`:shared:feature-schedule`) | Fragment | `ScheduleViewModel` (Koin) | `navigation_schedule`, `AppRoot.SCHEDULE` | — | `ScheduleScreenshotTest`, `ScheduleRouteTest` (`:shared:feature-schedule`) |
| `feature/schedule/ui/UserScheduleFragment.kt` hosting `UserScheduleScreen` around `ScheduleRoute` (`:shared:feature-schedule`) | Fragment | `ScheduleViewModel` (Koin) | `user_schedule`, `AppScreen.USER_SCHEDULE` | — | `ScheduleScreenshotTest` (`UserScheduleScreen_content`) |
| `feature/schedule/ui/changes/ScheduleChangesFragment.kt` hosting `ScheduleChangesRoute` (`:shared:feature-schedule`) | Fragment | `ScheduleChangesViewModel` (Koin) | `schedule_changes`, `AppScreen.SCHEDULE_CHANGES` | — | `ScheduleScreenshotTest` (`:shared:feature-schedule`) |
| `feature/schedule/ui/details/LessonDetailsBottomSheet.kt` | bottom sheet | `LessonDetailsViewModel` | `AppNavigator.openLessonDetails` | — | `ScheduleScreenshotTest`, `LessonDetailsSheetTest` (`:shared:feature-schedule`) |
| `feature/schedule/ui/details/PendingSportDetailsBottomSheet.kt` | bottom sheet | — | `AppNavigator.openPendingSportDetails` | — | `ScheduleScreenshotTest`, `PendingSportDetailsSheetTest` (`:shared:feature-schedule`) |

## Friend selector

Feature doc: [Friend selector](friend-selector.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/friendselector/ui/FriendSelectorDialogFragment.kt` hosting `FriendSelectorSheetRoute` (`:shared:feature-social`) | bottom sheet (kit sheet host) | `FriendSelectorViewModel` (Koin) | `friend_selector` dialog destination, from `ScheduleFragment` | `app/SettingsNavigationTestActivity.kt` (`FriendSelectorFixture`) | `SocialScreenshotTest` (`:shared:feature-social`) |

## Sport

Feature doc: [Sport](sport.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/sport/ui/common/SportFragment.kt` hosting `SportRoute` (`:shared:feature-sport`): `SportScreen` with `SportMyScreen` and `SportSignScreen` | Fragment | `SportMyViewModel`, `SportSignViewModel` (Koin, the Fragment's store) | `navigation_sport`, `AppRoot.SPORT`, `SportLessonRequest` | `feature/sport/ui/SportCardsPreviewActivity.kt` (booking cards, lesson list) | `SportScreenshotTest` (`:shared:feature-sport`) |
| `feature/sport/ui/user/UserSportFragment.kt` hosting `UserSportRoute` (`:shared:feature-sport`) | Fragment | `UserSportViewModel` (Koin, the Fragment's store) | `user_sport`, `AppScreen.USER_SPORT` | — | `SportScreenshotTest` (`:shared:feature-sport`) |
| `feature/sport/ui/common/SportCommonDetailsBottomSheet.kt` | bottom sheet | — | `SportFragment` (either page); `MainNavigationCoordinator.openSportDetails` for a schedule row that is a known booking | `feature/sport/ui/SportCardsPreviewActivity.kt` | — (JVM `SportDetailsSheetTest` and the `SportDetailsSheetContent_*` goldens of `:shared:feature-sport`) |

## Subject links

Feature doc: [Subject links](resources.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/resources/ui/SubjectLinksBottomSheet.kt` hosting `SubjectLinksSheetRoute` (`:shared:feature-resources`) | bottom sheet | `SubjectLinksViewModel` (Koin) | `AppNavigator.openSubjectLinks` | - | - (JVM `SubjectLinksSheetTest`, `ResourcesHostsKoinTest` and the `SubjectLinksSheetContent_*` goldens of `:shared:feature-resources`) |
| `feature/resources/ui/LinkEditorBottomSheet.kt` hosting `LinkEditorSheetRoute` (`:shared:feature-resources`) | bottom sheet | `LinkEditorViewModel` (Koin) | `AppNavigator.openLinkEditor` | - | - (JVM `LinkEditorSheetTest`, `LinkEditorPasteTest`, `ResourcesHostsKoinTest` and the `LinkEditorSheetContent_*` goldens) |
| `feature/resources/ui/LinkActionsBottomSheet.kt` hosting `LinkActionsSheetRoute` (`:shared:feature-resources`) | bottom sheet | `SubjectLinksViewModel` (Koin) | `AppNavigator.openLinkActions` | - | - (JVM `LinkActionsSheetTest`, `ResourcesHostsKoinTest` and the `LinkActionsSheetContent_*` goldens) |
| `feature/resources/ui/ReportLinkDialogFragment.kt` hosting `ReportLinkForm` (`:shared:feature-resources`) | dialog | `SubjectLinksViewModel` (Koin) | from `LinkActionsBottomSheet` | - | - (JVM `ReportLinkDialogTest`, `ResourcesHostsKoinTest` and the `ReportLinkDialog_*` goldens) |

## Teacher reviews

Feature doc: [Teacher reviews](reviews.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/reviews/ui/ReviewEditorBottomSheet.kt` hosting `ReviewEditorSheet` (`:shared:feature-reviews`) | bottom sheet (form) | `ReviewEditorViewModel` (Koin) | `AppNavigator.openReviewEditor` | - | - (JVM `ReviewEditorSheetTest`, `ReviewsHostsKoinTest` and the `ReviewEditorSheetContent_*` goldens of `:shared:feature-reviews`) |
| `feature/reviews/ui/ReportReviewDialogFragment.kt` hosting `ReportReviewForm` (`:shared:feature-reviews`) | dialog | `ReportReviewViewModel` (Koin) | `AppNavigator.openReviewReport` | - | - (JVM `ReportReviewDialogTest`, `ReviewsHostsKoinTest` and the `ReportReviewDialog_*` goldens of `:shared:feature-reviews`) |

## Social

Feature doc: [Social](social.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/social/ui/FriendsFragment.kt` hosting `FriendsRoute` (`:shared:feature-social`) | Fragment | `FriendsViewModel` (Koin) | `friends`, `AppScreen.FRIENDS` | — | `SocialScreenshotTest` (`:shared:feature-social`) |
| `feature/social/ui/UserSearchFragment.kt` hosting `UserSearchRoute` (`:shared:feature-social`) | Fragment | `UserSearchViewModel` (Koin) | `user_search`, `AppScreen.USER_SEARCH` | — | `SocialScreenshotTest` (`:shared:feature-social`) |
| `feature/social/ui/UserProfileFragment.kt` hosting `UserProfileRoute` (`:shared:feature-social`) | Fragment | `UserProfileViewModel` (Koin) | `user_profile`, `AppScreen.USER_PROFILE` | `app/SettingsNavigationTestActivity.kt` (`SocialDebugFixtures`) | `SocialScreenshotTest` (`:shared:feature-social`) |
| `feature/social/ui/UserFriendsFragment.kt` hosting `UserFriendsRoute` (`:shared:feature-social`) | Fragment | `UserFriendsViewModel` (Koin) | `user_friends`, `AppScreen.USER_FRIENDS` | `app/SettingsNavigationTestActivity.kt` (`SocialDebugFixtures`) | `SocialScreenshotTest` (`:shared:feature-social`) |

## Profile tab

Feature doc: [Profile tab](me.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/me/ui/MeFragment.kt` hosting `MeRoute` (`:shared:feature-account`) | Fragment | `MeViewModel` (Koin) | `navigation_me`, `AppRoot.ME` | `app/SettingsNavigationTestActivity.kt` (`AccountDebugFixtures`) | `AccountScreenshotTest` (`:shared:feature-account`), `feature/settings/ProfileBackMotionTest.kt`, `app/MainNavigationTest.kt` |

## Web sign-in

Feature doc: [Web sign-in](web-login.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/weblogin/ui/WebLoginBottomSheet.kt` hosting `WebLoginSheetRoute` (`:shared:feature-account`) | bottom sheet | `WebLoginViewModel` (Koin) | `AppNavigator.openWebLogin` | — | `AccountScreenshotTest`, `WebLoginSheetTest` (`:shared:feature-account`) |

## Settings

Feature doc: [Settings](../settings.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/settings/ui/SettingsFragment.kt` hosting `SettingsScreen` (`:shared:feature-settings`) with `AndroidView` widget previews | Fragment | `SettingsViewModel`, `CustomSpoilerViewModel` (Koin) | `settings`, `AppScreen.SETTINGS` | `app/SettingsNavigationTestActivity.kt`, `feature/settings/ui/SettingsPreviewActivity.kt` | `SettingsScreenshotTest` (`:shared:feature-settings`), `feature/settings/SettingsNavigationTest.kt`, `feature/settings/WidgetPreviewTest.kt` |
| `feature/settings/ui/DiagnosticsFragment.kt` hosting `DiagnosticsScreen` (`:shared:feature-settings`) | Fragment | `DiagnosticsViewModel` (Koin) | `diagnostics`, `AppScreen.DIAGNOSTICS` | — | `SettingsScreenshotTest` (`:shared:feature-settings`) |
| `feature/settings/ui/IcsExportBottomSheet.kt` hosting `IcsExportSheetContent` (`:shared:feature-settings`) | bottom sheet | `IcsExportViewModel` (Koin) | from `SettingsFragment` | `app/SettingsNavigationTestActivity.kt` | `SettingsScreenshotTest` (`:shared:feature-settings`), `feature/settings/SettingsNavigationTest.kt` |

## Update offer

Feature doc: [Update offer](update.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/update/ui/AppUpdateFragment.kt` hosting `AppUpdateRoute` (`:shared:feature-account`) | Fragment | `AppUpdateViewModel` (Koin) | `app_update`, `AppScreen.APP_UPDATE` | — | `AccountScreenshotTest` (`:shared:feature-account`) |

## Debug tools

Feature doc: [Debug tools](debug.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `feature/debug/ui/DebugToolsFragment.kt` hosting `DebugToolsScreen` (`:app`) | Fragment | `DebugToolsViewModel` (Hilt) | `debug_tools`, `AppScreen.DEBUG_TOOLS` | — | `DebugToolsScreenshotTest` (`:app`) |
