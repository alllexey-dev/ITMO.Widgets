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
| `app/MainActivity.kt` hosting `app/shell/ShellHost.kt` | Activity | `AppUpdateGateViewModel`, `OnboardingGateViewModel` (in `ShellHost`) | launcher, App Links, shortcuts, notification intents | — | — |
| `core/ui/spoiler/SpoilerCropActivity.kt` | Activity (`CropImageActivity`) | — | `SpoilerCropContract` from `SpoilerImagePicker` (settings, widget step) | — | — |

## Sign-in

Feature doc: [Sign-in](auth.md); the hidden demo entry in [Demo session](demo.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `app/shell/entries/AccountEntries.kt` hosting `AuthRoute` (`:shared:feature-account`) | Navigation 3 entry | `AuthViewModel` (Koin) | `auth`, start destination of `main_nav_graph` | — | `AccountScreenshotTest` (`:shared:feature-account`), `app/DemoModeFlowTest.kt`, `app/MainActivitySessionRoutingTest.kt` |
| `feature/auth/ui/LoginActivity.kt` hosting `LoginScreen` (`:shared:feature-account`) around an `AndroidView` `SwipeRefreshLayout` and WebView | Activity | `InteractiveLoginViewModel` (Koin) | started by `AuthFragment` | — | `AccountScreenshotTest` (`:shared:feature-account`, error state) |

## Onboarding

Feature doc: [First-run flow](onboarding.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `app/shell/entries/AccountEntries.kt` hosting `OnboardingScreen` (`:shared:feature-account`) with `AndroidView` widget previews | Navigation 3 entry | `OnboardingViewModel` (Koin) | `onboarding` | — | `AccountScreenshotTest` (`:shared:feature-account`) |

## Home

Feature doc: [Home and quick actions](home.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `app/shell/entries/HomeEntries.kt` hosting `HomeRoute` (`:shared:feature-home`) | Navigation 3 entry | `HomeViewModel` (Koin) | `navigation_home`, `AppRoot.HOME` | — | `HomeScreenshotTest` (`:shared:feature-home`) |

## QR pass

Feature docs: [QR pass](qr.md), [Widgets](widgets.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `app/shell/entries/HomeEntries.kt` hosting `QrPassRoute` (`:shared:feature-qr`) | Navigation 3 entry | `QrCodeViewModel` (Koin) | `qr_pass`, `AppScreen.QR_PASS` | — | `QrScreenshotTest` (`:shared:feature-qr`) |

## My ITMO web

Feature docs: [My ITMO in the app](my-itmo-web.md), [Home and quick actions](home.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `app/shell/entries/AccountEntries.kt` hosting `MyItmoWebScreen` (`:shared:feature-account`) around an `AndroidView` WebView | Navigation 3 entry | — | `my_itmo_web`, `AppScreen.MY_ITMO_WEB` | — | `AccountScreenshotTest` (`:shared:feature-account`) |

## Recordbook

Feature docs: [Recordbook](recordbook.md), [Subject page](subject-page.md),
[Mark tracking](marks-tracking.md), [Sheet scores](sheet-scores.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `app/shell/entries/RecordbookEntries.kt` hosting `RecordbookRoute` (`:shared:feature-recordbook`) | Navigation 3 entry | `RecordbookViewModel` (Koin) | `navigation_recordbook`, `AppRoot.RECORDBOOK` | — | `RecordbookScreenshotTest` (`:shared:feature-recordbook`) |
| `app/shell/entries/RecordbookEntries.kt` hosting `RecordbookSubjectRoute` (`:shared:feature-recordbook`) | Navigation 3 entry | `RecordbookSubjectViewModel` (Koin) | `recordbook_subject`, `AppScreen.RECORDBOOK_SUBJECT` | — | `RecordbookScreenshotTest`, `RecordbookSubjectScreenTest`, `SubjectHubSectionsTest` (`:shared:feature-recordbook`) |
| `app/shell/entries/RecordbookEntries.kt` hosting `RecordbookPeriodSheetContent` (`:shared:feature-recordbook`) | Navigation 3 sheet | — | `RecordbookPeriodBottomSheet.show` from `RecordbookFragment` | — | `RecordbookScreenshotTest` (`:shared:feature-recordbook`) |
| `app/shell/entries/RecordbookEntries.kt` hosting `SheetScoresSheet` | Navigation 3 sheet | `SheetScoresViewModel` | `AppNavigator.openSheetScores` | — | - (JVM `SheetScoresSheetTest` and the `SheetScoresSheet_*` goldens of `:shared:feature-recordbook`) |
| `feature/recordbook/ui/BarsLoginActivity.kt` | Activity | `BarsLoginViewModel` | started by `RecordbookFragment`, `RecordbookSubjectFragment` and `MainActivity` for `AppEntryIntents.ACTION_OPEN_BARS_LOGIN` | — | — |

## Schedule

Feature docs: [Schedule](schedule.md), [Schedule changes](schedule-changes.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `app/shell/entries/ScheduleEntries.kt` hosting `ScheduleRoute` (`:shared:feature-schedule`) | Navigation 3 entry | `ScheduleViewModel` (Koin) | `navigation_schedule`, `AppRoot.SCHEDULE` | — | `ScheduleScreenshotTest`, `ScheduleRouteTest` (`:shared:feature-schedule`) |
| `app/shell/entries/ScheduleEntries.kt` hosting `UserScheduleScreen` around `ScheduleRoute` (`:shared:feature-schedule`) | Navigation 3 entry | `ScheduleViewModel` (Koin) | `user_schedule`, `AppScreen.USER_SCHEDULE` | — | `ScheduleScreenshotTest` (`UserScheduleScreen_content`) |
| `app/shell/entries/ScheduleEntries.kt` hosting `ScheduleChangesRoute` (`:shared:feature-schedule`) | Navigation 3 entry | `ScheduleChangesViewModel` (Koin) | `schedule_changes`, `AppScreen.SCHEDULE_CHANGES` | — | `ScheduleScreenshotTest` (`:shared:feature-schedule`) |
| `app/shell/entries/ScheduleEntries.kt` hosting `LessonDetailsSheetRoute` (`:shared:feature-schedule`) | Navigation 3 sheet | `LessonDetailsViewModel` (Koin) | `AppNavigator.openLessonDetails` | — | `ScheduleScreenshotTest`, `LessonDetailsSheetTest` (`:shared:feature-schedule`) |
| `app/shell/entries/ScheduleEntries.kt` hosting `PendingSportDetailsContent` (`:shared:feature-schedule`) | Navigation 3 sheet | — | `AppNavigator.openPendingSportDetails` | — | `ScheduleScreenshotTest`, `PendingSportDetailsSheetTest` (`:shared:feature-schedule`) |

## Friend selector

Feature doc: [Friend selector](friend-selector.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `app/shell/entries/SocialEntries.kt` hosting `FriendSelectorSheetRoute` (`:shared:feature-social`) | Navigation 3 sheet | `FriendSelectorViewModel` (Koin) | `friend_selector` dialog destination, from `ScheduleFragment` | — | `SocialScreenshotTest` (`:shared:feature-social`) |

## Sport

Feature doc: [Sport](sport.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `app/shell/entries/SportEntries.kt` hosting `SportRoute` (`:shared:feature-sport`): `SportScreen` with `SportMyScreen` and `SportSignScreen` | Navigation 3 entry | `SportMyViewModel`, `SportSignViewModel` (Koin, the entry's store) | `navigation_sport`, `AppRoot.SPORT`, `SportLessonRequest` | — | `SportScreenshotTest` (`:shared:feature-sport`) |
| `app/shell/entries/SportEntries.kt` hosting `UserSportRoute` (`:shared:feature-sport`) | Navigation 3 entry | `UserSportViewModel` (Koin, the entry's store) | `user_sport`, `AppScreen.USER_SPORT` | — | `SportScreenshotTest` (`:shared:feature-sport`) |
| `app/shell/entries/SportEntries.kt` hosting `SportDetailsSheetContent` | Navigation 3 sheet | — | the sport tab (either page); a schedule row that is a known booking | — | — (JVM `SportDetailsSheetTest` and the `SportDetailsSheetContent_*` goldens of `:shared:feature-sport`) |

## Subject links

Feature doc: [Subject links](resources.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `app/shell/entries/ResourcesEntries.kt` hosting `SubjectLinksSheetRoute` (`:shared:feature-resources`) | Navigation 3 sheet | `SubjectLinksViewModel` (Koin) | `AppNavigator.openSubjectLinks` | - | - (JVM `SubjectLinksSheetTest` and the `SubjectLinksSheetContent_*` goldens of `:shared:feature-resources`) |
| `app/shell/entries/ResourcesEntries.kt` hosting `LinkEditorSheetRoute` (`:shared:feature-resources`) | Navigation 3 sheet | `LinkEditorViewModel` (Koin) | `AppNavigator.openLinkEditor` | - | - (JVM `LinkEditorSheetTest`, `LinkEditorPasteTest` and the `LinkEditorSheetContent_*` goldens) |
| `app/shell/entries/ResourcesEntries.kt` hosting `LinkActionsSheetRoute` (`:shared:feature-resources`) | Navigation 3 sheet | `SubjectLinksViewModel` (Koin) | `AppNavigator.openLinkActions` | - | - (JVM `LinkActionsSheetTest` and the `LinkActionsSheetContent_*` goldens) |
| `app/shell/entries/ResourcesEntries.kt` hosting `ReportLinkForm` (`:shared:feature-resources`) | Navigation 3 dialog | `SubjectLinksViewModel` (Koin) | from `LinkActionsBottomSheet` | - | - (JVM `ReportLinkDialogTest` and the `ReportLinkDialog_*` goldens) |

## Teacher reviews

Feature doc: [Teacher reviews](reviews.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `app/shell/entries/ReviewsEntries.kt` hosting `ReviewEditorSheet` (`:shared:feature-reviews`) | Navigation 3 sheet (form) | `ReviewEditorViewModel` (Koin) | `AppNavigator.openReviewEditor` | - | - (JVM `ReviewEditorSheetTest` and the `ReviewEditorSheetContent_*` goldens of `:shared:feature-reviews`) |
| `app/shell/entries/ReviewsEntries.kt` hosting `ReportReviewForm` (`:shared:feature-reviews`) | Navigation 3 dialog | `ReportReviewViewModel` (Koin) | `AppNavigator.openReviewReport` | - | - (JVM `ReportReviewDialogTest` and the `ReportReviewDialog_*` goldens of `:shared:feature-reviews`) |

## Social

Feature doc: [Social](social.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `app/shell/entries/SocialEntries.kt` hosting `FriendsRoute` (`:shared:feature-social`) | Navigation 3 entry | `FriendsViewModel` (Koin) | `friends`, `AppScreen.FRIENDS` | — | `SocialScreenshotTest` (`:shared:feature-social`) |
| `app/shell/entries/SocialEntries.kt` hosting `UserSearchRoute` (`:shared:feature-social`) | Navigation 3 entry | `UserSearchViewModel` (Koin) | `user_search`, `AppScreen.USER_SEARCH` | — | `SocialScreenshotTest` (`:shared:feature-social`) |
| `app/shell/entries/SocialEntries.kt` hosting `UserProfileRoute` (`:shared:feature-social`) | Navigation 3 entry | `UserProfileViewModel` (Koin) | `user_profile`, `AppScreen.USER_PROFILE` | — | `SocialScreenshotTest` (`:shared:feature-social`) |
| `app/shell/entries/SocialEntries.kt` hosting `UserFriendsRoute` (`:shared:feature-social`) | Navigation 3 entry | `UserFriendsViewModel` (Koin) | `user_friends`, `AppScreen.USER_FRIENDS` | — | `SocialScreenshotTest` (`:shared:feature-social`) |

## Profile tab

Feature doc: [Profile tab](me.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `app/shell/entries/AccountEntries.kt` hosting `MeRoute` (`:shared:feature-account`) | Navigation 3 entry | `MeViewModel` (Koin) | `navigation_me`, `AppRoot.ME` | — | `AccountScreenshotTest` (`:shared:feature-account`) |

## Web sign-in

Feature doc: [Web sign-in](web-login.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `app/shell/entries/AccountEntries.kt` hosting `WebLoginSheetRoute` (`:shared:feature-account`) | Navigation 3 sheet | `WebLoginViewModel` (Koin) | `AppNavigator.openWebLogin` | — | `AccountScreenshotTest`, `WebLoginSheetTest` (`:shared:feature-account`) |

## Settings

Feature docs: [Settings](../settings.md), [Calendar](calendar.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `app/shell/entries/SettingsEntries.kt` hosting `SettingsScreen` (`:shared:feature-settings`) with `AndroidView` widget previews | Navigation 3 entry | `SettingsViewModel`, `CustomSpoilerViewModel` (Koin) | `settings`, `AppScreen.SETTINGS` | `feature/settings/ui/SettingsPreviewActivity.kt` | `SettingsScreenshotTest` (`:shared:feature-settings`), `feature/settings/WidgetPreviewTest.kt` |
| `app/shell/entries/SettingsEntries.kt` hosting `DiagnosticsScreen` (`:shared:feature-settings`) | Navigation 3 entry | `DiagnosticsViewModel` (Koin) | `diagnostics`, `AppScreen.DIAGNOSTICS` | — | `SettingsScreenshotTest` (`:shared:feature-settings`) |
| `app/shell/entries/SettingsEntries.kt` hosting `IcsExportSheetContent` (`:shared:feature-settings`) | Navigation 3 sheet | `IcsExportViewModel` (Koin) | from `SettingsFragment` | — | `SettingsScreenshotTest` (`:shared:feature-settings`) |

## Update offer

Feature doc: [Update offer](update.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `app/shell/entries/AccountEntries.kt` hosting `AppUpdateRoute` (`:shared:feature-account`) | Navigation 3 entry | `AppUpdateViewModel` (Koin) | `app_update`, `AppScreen.APP_UPDATE` | — | `AccountScreenshotTest` (`:shared:feature-account`) |

## Debug tools

Feature doc: [Debug tools](debug.md).

| Host | Kind | ViewModel | Entry | Debug host | Visual test |
|---|---|---|---|---|---|
| `app/shell/entries/AccountEntries.kt` hosting `DebugToolsScreen` (`:app`) | Navigation 3 entry | `DebugToolsViewModel` (Hilt) | `debug_tools`, `AppScreen.DEBUG_TOOLS` | — | `DebugToolsScreenshotTest` (`:app`) |
