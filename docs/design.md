# Design guide

One visual language across the Android application, grown from the screens the
user already likes: the schedule and the sport cards. Quiet surfaces, compact
layout, clear hierarchy, small meaningful accents. This is the target contract
for every screen. Compose screens take every value from `ItmoTheme` in
`:shared:designsystem` ([Tokens](#tokens)); XML screens keep the v2.2 look from
the app theme and `res/values` until their port deletes them, and their card
styles are pinned by `DesignCardResourcesTest`.

## Principle

**One visual language, not identical screens.**

- Information over decoration. Colour, size and motion explain meaning.
- A compact card has one focus object and secondary context.
- Hierarchy comes from surfaces, typography and spacing, not shadows.
- Never stack a filled surface, a coloured stroke, a large status, a badge and a
  decorative progress on one element.
- Screens that already work keep their look. Screens move to Compose
  Multiplatform ([ADR 0017](decisions/0017-cmp-ui-in-common-main.md)) as parity
  ports: a port matches its XML screen against a JVM reference capture and lists
  every deliberate deviation in its PR. The Material 3 Expressive look arrives
  later as one token change ([ADR 0021](decisions/0021-m3-expressive-order.md)),
  not screen by screen. Unification does not mean a redesign or features
  outside the roadmap.

## Tokens

`ItmoTheme` (`shared/designsystem`, package `dev.alllexey.itmowidgets.designsystem`)
is the one source of design values for Compose. Its schema has the Material 3
Expressive shape from the start (extended colours, a corner scale with
`largeIncreased`, emphasized type, `MotionScheme`), so component APIs do not
change when the values do; the values are v2.2's until the M3E token change.
Components and screens read tokens only through `ItmoTheme.*`. No raw colour,
`.dp` spacing or font size outside `shared/designsystem`; experimental and
expressive Material APIs only inside it, behind `Itmo*` wrappers.

| Slot | Contents and today's values |
|---|---|
| `ItmoTheme.colorScheme` | The M3 colour roles. `ColorSource.Platform` (default): dynamic colour on Android 12+, otherwise, and on iOS, the static M3 baseline scheme (the `#6750A4` family that API 26-30 get from `Theme.Material3.DynamicColors.DayNight`). `ColorSource.Seed` generates MDC's content-based scheme from a seed (previews, tests) |
| `ItmoTheme.extendedColors` | The app's own colours beside the scheme, one slot per entry of `res/values{,-night}/colors.xml`: lesson types, recordbook passed, sport scores and conditions, teacher levels. Derived per scheme: sport condition containers (the accent mixed 12 % over `surfaceContainerLowest`) and teacher levels (harmonized towards `primary`). In dark, lesson types keep their light values until the M3E change |
| `ItmoTheme.shapes` | Corner scale `extraSmall` 4, `small` 8, `medium` 12, `large` 16, `largeIncreased` 20, `extraLarge` 28, `extraLargeIncreased` 32, `extraExtraLarge` 48 dp, `full`; card family `cardContent` 20, `cardSummary` 24, `cardHero` 28, `scheduleDay` 16 dp; connected groups 20 dp outer, 4 dp inner, 2 dp gap; stroke 1 dp, elevation 0 dp |
| `ItmoTheme.spacing` | `related` 4, `compact` 8, `content` 12, `group` 16, `section` 24, `screenMargin` 16, `cardPadding` 16, `summaryPadding` 20, `touchTarget` 48, `fabStackClearance` 152, `statePadding` 32, `stateIcon` 64, `stateInlineIcon` 56 dp |
| `ItmoTheme.typography`, `ItmoTheme.emphasizedTypography` | The 15 M3 type roles at MDC 1.13's `TextAppearance.Material3.*` values on the system font, each with an emphasized twin; pinned, so a material3 bump cannot change a ported screen's text |
| `ItmoTheme.motion` | Durations `quick` 180, `standard` 220, `emphasis` 260, `reveal` 300, `progress` 700, `progressSlow` 1000 ms; the skeleton pulse 1200 ms down to alpha 0.55; easing `(0.2, 0, 0, 1)`; Material's `MotionScheme.standard()` for components until the M3E change |

- `cardSummary` (24 dp) is off the corner scale on purpose: it is named so the
  M3E change can move it with the rest.
- Reduced motion: `rememberReducedMotion()` is true when the system animator
  duration scale is 0 (iOS reports false until its host reads Reduce Motion); a
  kit animation then shows its end state at once.
- Parity with the XML screens is tested: `DesignTokensParityTest` (`:app`) keeps
  extended colours, spacing and shapes equal to `res/values`, `ThemeParityTest`
  the static scheme and `TypographyParityTest` the type scale.
- `shared/designsystem/tokens/itmo-tokens.json` exports the tokens (static light
  and dark schemes by role, extended colours with the derivation rules, shapes,
  spacing, type, motion; colours `#RRGGBB`, sizes dp or sp, durations ms;
  `schemaVersion` grows with a breaking change). The iOS client and the web app
  take their tokens from it instead of copying values. It is generated, never
  edited or merged by hand: after a token change or a rebase run
  `scripts/verify.sh run -- :shared:designsystem:exportDesignTokens`;
  `DesignTokensExportTest` fails while it is stale.

## Colour and surfaces

Material 3 colour roles, named here by their token; the XML theme attribute
stands in parentheses while XML screens remain. Compose reads them from
`ItmoTheme.colorScheme`, XML screens from the app theme on
`Theme.Material3.DynamicColors.DayNight`, with the same values. Every screen
must work in light, dark and dynamic palettes; never assume the wallpaper.

| Role | Rule |
|---|---|
| Screen background | `surface` (`colorSurface`); never override the window background with a fixed colour |
| Ordinary card | `surfaceContainerLow` (`colorSurfaceContainerLow`), elevation 0 dp |
| Nested neutral panel | a surface-container level such as `surfaceContainerHighest` (`colorSurfaceContainerHighest`) |
| Primary text | `onSurface` (`colorOnSurface`) |
| Metadata and decorative icons | `onSurfaceVariant` (`colorOnSurfaceVariant`) |
| Action, current item, small accent | `primary` (`colorPrimary`) |
| Selected contextual surface | `secondaryContainer` with `onSecondaryContainer` (`colorSecondaryContainer`, `colorOnSecondaryContainer`) |
| Quiet stroke and divider | `outlineVariant` (`colorOutlineVariant`) |

`on…Container` colours are used only on their container. Do not fill large
cards with `primary` to make them prominent; the user rejects heavy tonal
fills. Lesson types, grades, sport statuses and ring sectors are domain
semantics with their own deliberate light and dark values and contrast checks;
a status is also readable by text or icon, never by colour alone.

The tone of a teacher's AI summary is such a stable colour of meaning:
`teacher_level_*` from red to green (`very_negative`, `negative`, `mixed`,
`positive`, `very_positive`, light `#D32F2F`, `#E06C00`, `#B58900`, `#689F38`,
`#2E7D32`, dark `#FF6E6E`, `#FFA24C`, `#FFD54F`, `#AED581`, `#4CAF50`),
only harmonized towards the primary colour (`core/ui/TeacherLevelTone.kt`;
`teacherLevel*` in `ItmoTheme.extendedColors`), so
every palette keeps five distinct tones. The dot is decorative; its row says
the tone in words.

## Geometry and the card family

The grid is 4 dp. Compactness never shrinks the touch target.

| Parameter | Value |
|---|---|
| Screen horizontal margin | 16 dp |
| Compact list gap | 8 dp |
| Gap between groups | 16–24 dp |
| Gap between related rows | 4 dp |
| Card padding | 16 dp; 20 dp for a large summary |
| Minimum touch target | 48 × 48 dp |
| Card elevation | 0 dp |

| Variant | Shape | Use |
|---|---|---|
| `Card.Content` | 20 dp radius, no stroke | Subject, control, user rows |
| `Card.Content.Outlined` | 20 dp radius, 1 dp `colorOutlineVariant` | Sport lesson and booking cards, debug cards |
| `Card.Content.Tonal` | 20 dp radius, no stroke, `colorSurfaceContainerLow` | The AI summary of a teacher's reviews above the reviews |
| `Card.CompactSummary` / `Card.Summary` | 20 / 24 dp radius | Recordbook summary; PE sport card |
| `Card.Hero` | 28 dp radius, no stroke, `colorSurfaceContainer` | The subject result and the person on a profile, above the page's connected groups |
| `Card.SettingsGroup` | 20 dp radius, no stroke, inner dividers | One card per settings or profile group, never per row |
| `Card.ScheduleDay` | 16 dp radius, 16 dp between days | A day of lessons with its timeline |

### Connected groups

A list section of a contextual page (the subject page, the links sheet) is a
heading over one connected group, not a card per row and not rows on the bare
background:

- The heading (`item_section_heading.xml`): `titleSmall` in `colorPrimary`,
  16 dp in from the group's edge, 24 dp above (16 dp in a sheet, 8 dp for the
  first one), 8 dp below; `accessibilityHeading`.
- The rows: one surface, `colorSurfaceContainerLow` (the surface of schedule
  days; `colorSurfaceContainerHigh` inside a sheet, which is itself
  `colorSurfaceContainerLow`), 2 dp apart
  (`design_group_gap`); the first and the last row round 20 dp outside
  (`design_group_radius_outer`), every corner between rows is 4 dp
  (`design_group_radius_inner`). `core/ui/ConnectedGroup.kt`
  (`GroupPosition`, `View.bindGroupPosition`) draws a row by its position
  (single, first, middle, last) with a ripple of the same shape, so a
  RecyclerView row needs no decoration. A row is at least 56 dp high with
  16 dp of content padding.
- A sub-heading inside a section (a control group) is `titleSmall` in
  `colorOnSurface` with its value at the end and starts a group of its own; a
  group right after another group without a heading keeps 12 dp to it.
- Own and others' items share the row style; an own one is told by a small
  `моя` badge (`colorSecondaryContainer`), never by a different surface or
  outline. Others' links carry the compact vote pill `▲ N ▼`
  (`view_link_vote_pill.xml`: a 32 dp pill inside 48 dp arrow targets).
- Captions put a second fact on a second line or join facts with a comma; no
  « · ». The last row that leads further (`Все ссылки, N`, `Все пары, N`,
  `Добавить ссылку`) is `item_group_action_row.xml`.

Chips, date tiles and avatars have their own geometry. Vertical padding is
symmetric; height grows with content and font scale. Clipped text is fixed
through constraints, wrapping, font metrics and insets, never with a fixed height.

## Typography and content

- Card title `titleMedium`; time as the scanning anchor `titleSmall`; metadata
  `bodySmall`/`bodyMedium`. Settings rows keep `bodyLarge` titles and
  `bodyMedium` descriptions.
- A small set of Material roles; not every label is bold.
- Long titles wrap; secondary metadata may be shortened when the full value is
  in the details; an important status is never shortened into ambiguity.
- User-visible strings are Russian and live in the owning module's catalog
  file (`composeResources/values/strings_<file>.xml` once moved; shared ones in
  `:shared:core`); one key names a string on every platform. Remote names are
  trimmed at the mapper.
- Copy is short and says what the user needs, not how the app works: no
  instructions for standard gestures (pull to refresh, tap), no descriptions or
  footers that repeat the title, no internals (matching rules, token handling,
  sync mechanics) outside the consent and privacy screens.
- Fixed names: the university site is `My ITMO`; the project server opt-in is
  `Подключение к ITMO.Widgets` (not "сервисы").
- Grade codes are contiguous: `2FX`, `3E`, `3D`. Short status plus number pairs
  do not wrap into ambiguous lines. One number is not repeated in neighbouring views.

## Icons, actions and selection

- Official Material Symbols Rounded, one family (weight 400, optical size 24,
  FILL 0), named after the symbol (`ic_calendar_add`). One meaning, one symbol.
  No emoji or text glyphs.
- A 960 viewport drawn at 24 dp: `<group android:translateY="960">`, fill
  `#FF000000`, no `android:tint` or theme colour in the drawable; the tint is
  set at the use site, through semantic attributes or `ItmoTheme` roles;
  teacher and location icons are neutral.
- The registry is `docs/design/icons.tsv` (`id`, `symbol`, `fill`, `kind`,
  `sf_symbol`, `note`), and `scripts/icons-fetch.py` writes the icons; never
  draw a Material Symbol by hand. Kinds: `shared` (fetched by the script),
  `custom` (drawn by hand, own glyph and fills) and `android` (system-tinted
  masks of shortcuts, notifications and the tile, exempt from the format);
  `sf_symbol` names the iOS SF Symbol. Code names an icon with `AppIcon`.
- FILL 1 only for a selected or active state (the selected navigation tab).
- A button may look smaller than 48 dp through insets; its touch area stays 48 dp.
- Filled button for the strong primary action, tonal for an ordinary prominent
  action, outlined or text for secondary and contextual ones.
- Every actionable icon has a localized `contentDescription`; decorative ones `@null`.
- Selection is shown by a check or a container surface plus `selected` or
  `checkable` state for TalkBack, and the whole row is the target. A closed or
  private row never looks selectable.
- The bottom navigation shows the label of the selected tab only
  (`labelVisibilityMode="selected"`, `NavigationBarTokens.LabelsOnSelectedOnly`;
  an open option, see [Expressive components](#expressive-components)); no large
  titles above root content. Contextual screens use a back button and a concise
  title (`AppTopBar`).

## Refresh and loading

| Screen | Contract |
|---|---|
| Compose screens | `AppRefreshBox`: indicator `primary` on a `background` container |
| XML lists: home, schedule, both sport tabs and a friend's sport, friends and a user's friends, recordbook and the subject page | `applyAppRefreshColors()` from `core/ui/RefreshAppearance.kt`: indicator `colorPrimary`, background `android.R.attr.colorBackground` |
| ITMO.ID web sign-in | Explicit exception: library default indicator. The window follows the app theme (light or dark); only the ITMO.ID page inside it is light |

- First load without any cache: a skeleton (`core/ui/SkeletonView`, styles
  `Widget.ItmoWidgets.Skeleton.List` and `.Cards`) in the bounded content area
  that the content will take, pulsing between 55 % and full opacity every 1.2 s
  and static when the system has animations off; no flash of an empty state
  before it. A screen that has a cached answer never shows it: the cache renders
  in the first frame and the refresh reports through the pull-to-refresh
  indicator over the content.
- Refresh: existing data and scroll position stay. Only a refresh the user asked
  for (pull-to-refresh, `Повторить`) shows the indicator; the automatic refresh
  on entry, on resume, after a period change or the BARS switch is silent and
  the new data simply replaces the old. A skeleton is therefore never followed
  by an indicator. The schedule keeps its loaded range including
  pagination and replaces it with one snapshot; an error keeps the old data.
- Card actions show local progress inside their button without changing its
  geometry and ignore a second tap.
- Score rings and capacity bars are not loading indicators.
- Settings root and offline pages render persisted values immediately; only the
  privacy page has a network state, visible at least 300 ms. Settings QR previews open
  ready or with an error, never with an intermediate spinner.

## Empty, error and feedback states

One family with two sizes: a full state in the content area (64 dp icon) and a
compact one inside a section (56 dp icon), each with optional icon, title,
description and optional action. `Widget.ItmoWidgets.ContentState.*` styles hold
the geometry.

- Loading, content, empty and error occupy the same bounded area.
- Empty has a title; a description only when the reason or the next step is
  not obvious from it. An error is never shown as
  "nothing found"; an unknown value is never shown as zero.
- Retry is a tonal action; a primary action leading out of an empty screen may be
  filled; a text button is fine in a compact secondary message.
- A refresh error with data on screen keeps the data and shows a snackbar with
  retry. Toasts are not used for recoverable errors.
- List and placeholder switch atomically after `submitList` completes; no message
  under the previous list on an intermediate frame.

## Screen behaviour

- Five bottom tabs, contextual screens in the overlay. Ordinary state changes do
  not recreate the screen or lose scroll position. Root selection or reselection
  closes the whole overlay stack.
- Motion explains a change and honours the system animator scale (scale 0
  shows the end state at once). Compose components move with springs from
  `MotionScheme.standard()`; fixed durations come from `ItmoTheme.motion`. The
  XML screens use the same values: 180 to 300 ms for transitions and reveals
  (the sport month label 180 ms, screen slides and the shared axis 220 ms, the
  sport status 260 ms, the QR settings preview 300 ms) and 700 or 1000 ms for a
  progress that fills (the sport score). Do not animate unchanged text or flash
  the screen on refresh. Sport tabs switch by
  tap and pager gesture; the sport date strip moves only with its arrows
  ([Tab swipe](#tab-swipe), rules 3 and 4); the month label animates only when
  the month changes.
- Every mutable visual property is set on rebind. Past schedule days keep content
  alpha 0.72; today and future days 1.0; inner lesson rows get no second alpha layer.
- Schedule timeline markers: a hollow neutral circle for a lesson not yet
  started, a filled dot for a past one, a circle with an inner dot in
  `colorPrimary` for the next official lesson in the loaded range, a filled accent
  marker for the lesson in progress. Markers are 12 dp in a 14 dp stable area and
  cover the line with the day surface. Auto-sign rows use the same row and a
  short text note, not a separate card style. States recompute on return and at
  the minute boundary without network or scroll reset.
- The sport score ring keeps a constant angular gap between attendance and bonus,
  a visible minimum for every non-zero sector, identical geometry on the first
  and later frames, and values above 100 do not remove the gap.
- The friend picker is modal: recent chips, bounded search, the whole row as the
  target, explicit confirmation. Selection is a `colorSecondaryContainer` row
  surface; a closed schedule shows a lock and leads to the profile.
- Settings and the profile use quiet group cards with stable row updates.
- Widgets and the QR pass keep their own launcher-adapted palette and readability
  rules; sign-in branding is a separate exception.
- The home feed shows only cards with something to say, in a fixed order, each
  on `Card.Content` with rows of at least 48 dp; hints use `Card.Content.Outlined`
  and close with a trailing icon button. The lesson in progress is told
  by its time in `colorPrimary`, a filled badge and a thin progress line under
  the row, never by a filled row. Pull-to-refresh keeps the
  cards; one snackbar reports a partial failure. The `Мой ИТМО` FAB stays at the
  bottom end and the list reserves space under it: a list under a stack of FABs
  (home, schedule) ends with `design_fab_stack_clearance` of bottom padding and
  `clipToPadding="false"`, so its last item scrolls clear of them.

### Tab swipe

Android only. The kit's `designsystem/gesture/` package holds the pieces:
`TabSwipeDefaults` (the numbers below), `tabSwipeHandover`, `tabSwipeBlocked`
and `TabSwipeRegistry`; `GestureRulesTest` enforces rules 3 and 5.

iOS has no tab swipe (owner decision, 2026-10-06): paging between tab bar tabs
is not an iOS convention, so the tabs switch by the native tab bar only, inner
horizontal content just scrolls, and the leading edge belongs to the back
swipe. Shared screens keep `tabSwipeHandover` and `tabSwipeBlocked` (rule 3);
with no tab pager around them they change nothing. `TabSwipeRegistry` and
`TabSwipeDefaults.edgeDeadZone` stay in the kit, unused on iOS for now.

1. The five bottom tabs switch by a tap and by a horizontal swipe on a tab
   root, in bar order, one tab per swipe, no wrap-around.
2. The swipe exists only on tab roots: never on overlays, pushed screens,
   sheets, dialogs, sign-in, onboarding, or while one of them is open; not where
   the navigation rail shows (medium and expanded widths).
3. Inner horizontal content wins: every horizontal scroller on a tab root uses
   `tabSwipeHandover`; it scrolls first, never flies over into the next tab,
   and a new swipe at its edge switches the tab. `Мой спорт`/`Запись` keep
   their pager swipe under this rule.
4. Days never move by a horizontal swipe on content: day lists scroll
   vertically; a week strip changes the day by a tap and the week by its arrows
   and is `tabSwipeBlocked`.
5. System gestures win: no gesture exclusion; the back edges stay the
   system's.
6. Diagonal drags belong to vertical lists (2x slop for the tab pager).
7. Feedback: the bar's indicator moves to the target tab when the drag passes
   the commit threshold, with one threshold haptic; no hint, coach mark, peek
   animation or new text.
8. Accessibility: the tabs stay bar buttons and nothing needs the swipe; the
   tab pager exposes no page semantics and is off under TalkBack and Switch
   Access.
9. Reduced motion: the drag still follows the finger; the settle and tap
   switches are instant.
10. Entry points land on screens: widgets, links, notifications and shortcuts
    open a route with its tab, never a page index; a switch by a route, a tap
    or Back never slides through the tabs in between.

## Shared components

Compose screens build from the kit in `shared/designsystem`
(`designsystem/components/`): stateless components over primitives (state and
callbacks in; text, icons and formatted dates as parameters; no feature or
`core` domain types, no network or clock), each with previews (synthetic data,
long Russian names, only in `@Preview` functions and `designsystem/preview/`)
and baselines in all four Material appearances and the three iOS ones under
`shared/designsystem/screenshots/`. A port uses the kit and grows it instead of
drawing its own variant. The last column says how a component looks under the
iOS style ([Platform styles](#platform-styles)); "Same" means one drawing on
both platforms.

| Component | Use | Replaces | On iOS |
|---|---|---|---|
| `AppTopBar`, `AppTopBarAction` | Contextual screen: back or close, a title of up to two lines (one over an optional one-line subtitle, such as the My ITMO page's host), trailing actions | `MaterialToolbar` and the hand-built contextual headers | The inline navigation bar: one centred line of headline, the navigation button and the actions in the tint, a hairline when content scrolls under it |
| `ItmoNavigationBar`, `ItmoNavigationBarItem` | Root tabs; the FILL 1 icon and the label on the selected tab | `BottomNavigationView` (`Widget.ItmoWidgets.BottomNavigationView`) | Not used: the SwiftUI shell's native tab bar |
| `ContentState`, `ContentStateLoading` | Full and compact loading, empty and error states with an optional action | `Widget.ItmoWidgets.ContentState.*` layouts | The action is a button capsule; loading is the large activity indicator |
| `Skeleton` | First load without a cache, list or cards | `core/ui/SkeletonView.kt` | Shapes in `systemFill`, cards with the inset group's corners; geometry and pulse stay |
| `AppRefreshBox` | Pull-to-refresh that the user asked for | `SwipeRefreshLayout` with `applyAppRefreshColors()` | Behaves as `UIRefreshControl`: the content follows the pull, the spinner's spokes above it, the refresh haptic at the threshold |
| `ProgressButton` | A button with its own progress, same size, second tap ignored | `core/ui/ButtonProgress.kt` | A `UIButton` capsule: filled is prominent, tonal and outlined are bordered, text is plain; a press dims it |
| `ButtonRow` | Two buttons side by side, stacked at full width when the labels do not fit | `core/ui/ButtonRow.kt` | Its buttons take the `ProgressButton` capsules |
| `Pill` | The `моя` badge, a count, a status in its tone over a 12 % wash | `bg_home_badge` and the badge and status pill views | Capsules in the iOS type |
| `ToneDot` | The review tone dot, an empty slot until the tone arrives | `bg_teacher_level_dot.xml` with `ImageView.bindLevel` | Same |
| `Avatar` | A photo from the given URL through the host's image loader, initials otherwise | `core/ui/AvatarView.kt` | Same |
| `Modifier.connectedGroupItem`, `GroupPosition`, `GroupSurface` | A row of a connected group, drawn by its position, on a screen or in a sheet | `core/ui/ConnectedGroup.kt` (`View.bindGroupPosition`) | Cells of an inset group: one 26 pt radius, square inner corners, no gap, hairline separators; the elevated colours in a sheet |
| `SectionHeading`, `SectionSubheading` | The heading over a group; a sub-heading with its value | `item_section_heading.xml`; `item_recordbook_control_group.xml` | A section header in `secondaryLabel` at UIKit's insets |
| `GroupActionRow` | The last row of a group that leads further | `item_group_action_row.xml` | A cell with the icon in the tint and the disclosure indicator |
| `LinkRow`, `VotePill` | A link with its own badge or the vote pill | `item_subject_link.xml`, `core/ui/SubjectLinkRow.kt`, `view_link_vote_pill.xml` | A cell; the vote arrows are 44 pt targets, the pill on `tertiarySystemFill` |
| `UserRow`, `UserSelectionRow` | A person with actions; a selectable person in a picker | the user row of the XML social lists; rows with `bindSelectionAccessibility` | A cell with the disclosure indicator; the selection is the checkmark accessory with the selection haptic |
| `SettingsGroup`, `SettingsGroupFooter`, `SettingsRow` and its toggle, choice, navigation, info, action and selection variants | Settings and profile groups, one card per group | `Card.SettingsGroup` and `item_setting_row.xml`, `item_setting_toggle.xml`, `item_setting_divider.xml` | A section of an inset-grouped list with UIKit's rows; the toggle is a `UISwitch` in the accent |
| `DetailsHeader` | The head of every details sheet | `view_details_header.xml`, `core/ui/DetailsHeader.kt` | Title 2 semibold, secondary text and icons in `secondaryLabel`, the map button a tinted capsule |
| `SheetScaffold`, `SheetHandle` | A bottom sheet body: handle, header, one bounded content area (288 dp minimum where states switch), footer | the handle and header of each sheet layout | No handle (the host's grabber); the header is the sheet's navigation bar ([Platform styles](#platform-styles)) |
| `ConfirmDialog`, `ChoiceDialog`, `ReportDialog` | A confirmation, a single choice, reporting a review or a link | `MaterialAlertDialogBuilder` dialogs | Compose-drawn iOS alerts with capsule buttons; no hero icon |
| `ScoreRing` | The sport score ring | `core/ui/CircularProgressBar.kt` | Same |
| `GradeScale` | A 0-100 bar with grade ticks | `feature/recordbook/ui/GradeScaleView.kt` | Same |
| `TimelineMarker`, `TimelineLine` | The schedule timeline | `feature/schedule/ui/list/ScheduleRows.kt` of `:shared:feature-schedule` | Same |
| `StepsIndicator` | Progress dots of a flow | `OnboardingStepsView`, the first-run flow's dot strip | A `UIPageControl` row of equal dots, the current one in the tint |

Experimental and expressive Material components reach screens only through
`Itmo*` wrappers in `shared/designsystem`, so a material3 line switch stays in
the module.

XML screens that are not ported yet keep XML, Material components and their
helpers. That shared layer is deliberately small: card variants, named
dimensions, refresh helper, content-state styles, the accessible selection row
(`bindSelectionAccessibility`), the
contextual screen header and the details-sheet header
(`view_details_header.xml`: title, kind, date with the time range and duration,
teacher, flow, place, map button) that every bottom sheet with a session starts
with. Shared helpers belong to `core/ui`; screen-specific behaviour to
`feature/<name>/ui`. Extract only rules that genuinely repeat; do not build a
universal renderer.

## Platform styles

The kit draws in one of two looks, `ItmoPlatformStyle.Material` and
`ItmoPlatformStyle.Ios` ([ADR 0023](decisions/0023-ios-client.md)). `ItmoTheme`
picks it once (`platformStyle = defaultPlatformStyle()`: Material on Android,
iOS on iOS); previews and tests pass it explicitly. Only the kit reads the
style: every component has one `when` over it, with the same parameters,
semantics and test tags in both branches, so feature code never learns which
look drew and never branches on the platform (`ItmoTheme.platformStyle` is
internal to the kit). Android's look and its baselines do not change with the
iOS style.

What adapts under `Ios`:

- **Type.** Apple's text styles (`IosTypeScaleTokens`) in place of the M3
  roles: `bodyLarge` is body 17, `titleMedium` headline, `titleLarge` title 3.
  `IosTextStyle` carries the tracking that makes CMP's single optical size of
  SF match UIKit's width; components add no letter spacing.
- **Spacing and shape.** `ItmoSpacing.Ios` (screen margin 20, row padding 16,
  touch target 44) and `ItmoShapes.Ios` (every card and group 26, square inner
  group corners, no group gap); `LocalMinimumInteractiveComponentSize` is 44 pt.
  UIKit metrics without a Material slot live in `IosMetrics`, each with the
  UIKit view it was measured on: row height 53, separator 1 pt inset 16 (56
  with an icon), sheet radius 38, alert 320 wide with radius 34 and 48 pt
  capsule buttons, menu 250 wide, segmented control 31 high.
- **Colour.** The scheme stays the static one (iOS has no wallpaper colours);
  the accent, the tint of buttons, switches, checkmarks and links, is
  `colorScheme.primary`, as the SwiftUI screens tint their native controls.
  Grouped backgrounds, cells, separators, labels and fills come from
  `ItmoTheme.iosColors` by their UIKit names (`groupedBackground`,
  `groupedCell`, `separator`, `secondaryLabel`, `systemFill`, `systemRed`,
  `overlay` and the sheet's elevated pair), light and dark.
- **Controls.** Rows are cells of inset groups with disclosure and checkmark
  accessories; buttons are `UIButton` capsules that dim when pressed instead of
  a ripple; the switch is a `UISwitch`, the button group a
  `UISegmentedControl`, the spinner `UIActivityIndicatorView`'s spokes,
  progress a flat `UIProgressView`; dialogs are iOS alerts and `ItmoMenu` a
  context-menu panel, both drawn in Compose.
- **Sheets.** SwiftUI's `.sheet` hosts a `SheetScaffold` body and owns the
  grabber, corners, detents, insets and dismissal; the scaffold draws no handle
  and its header is the sheet's navigation bar.
- **Expressive off.** `ItmoTheme(expressive = ...)` is ignored under `Ios`;
  the M3E look is Android's only.

What stays the same: content visuals are one drawing on both platforms (the
score ring, the grade scale, the timeline marker, the avatar and the hero
avatar mask without the expressive shape, the steps indicator's geometry,
Material Symbols icons, the lesson-type accents and the extended colours);
only the chrome around them follows the style. Text, icons and behaviour of a
screen do not change between styles.

- **Haptics.** `rememberItmoHaptics()` inside the kit only, a no-op under
  Material: selection when a segment or picker value changes, toggle when a
  switch or checkbox flips, one refresh tick when a pull passes the threshold,
  success, warning and error when a kit action reports its outcome (a
  destructive confirmation). Never on plain taps, scrolling or navigation.
- **No UIKit views in the kit.** The iOS variants draw in Compose only: no
  `UIKitView`, no UIKit interop, no blur or glass (opaque fills at the measured
  colours), circular corners at UIKit's radii. Every variant renders under
  Robolectric, so it has JVM baselines like the Material one.
- **Baselines.** The harness renders every preview in `ios-light`, `ios-dark`
  and `ios-narrow` (font scale 1.3 at 320 dp, light) as
  `<Preview>_ios-<appearance>.png`. The kit records them always; a feature
  module records them with
  `scripts/verify.sh shots <module> --record -Pshots.appearance=ios`, and from
  then on every run verifies them (the QR pass is the first such module). An
  iOS change re-records only `_ios-*` files; a changed Material PNG in the same
  diff is a bug. Accessibility checks assert 44 pt targets under `Ios`.
- **The JVM font trap.** JVM captures draw the iOS type with Robolectric's
  fallback font, not SF Pro, which is never committed or bundled. Glyph shapes
  and exact line breaks are checked only on the simulator:
  `scripts/ios/screenshots.sh <UITests class>` in light and dark, plus an AX1
  run, for every screen that gets iOS baselines. A text that wraps or clips
  with SF but not in the JVM capture is a finding of that screen; the
  `ios-narrow` baseline is the JVM's guard for long text.

## Expressive components

The Material 3 Expressive look arrives as one token change
([ADR 0021](decisions/0021-m3-expressive-order.md)); until then every value in
this guide is v2.2's. After it, each feature takes the rules below in one
expressive pass over its ported screens, through the `Itmo*` wrappers only.
Behaviour, semantics, strings and stable identifiers do not change in a pass.

- **Intensity: Foundational.** The quiet surfaces stay. Emphasis comes from
  type, shape and motion on a few elements, never from colour fills.
- **At most two hero moments per flow.** The candidates: the QR pass reveal,
  the person profile hero, the sport score ring and the lesson in progress on
  home; the subject result card stays the only hero of the subject page. Only a
  hero gets expressive motion, wavy progress, emphasized display type or a
  `MaterialShapes` mask (the profile avatar only). A list row never does.
- **Loading.** `LoadingIndicator` for a wait shorter than about 5 s (a section,
  a sheet, a dialog), the contained `LoadingIndicator` in pull-to-refresh
  (`AppRefreshBox`). Never inside a button: `ProgressButton` keeps its small
  circular indicator. First loads keep the skeleton. Determinate progress in
  dense rows stays a flat linear bar; a wavy one only for a hero.
- **Tabs and toggles.** The sport tabs become secondary tabs or a connected
  button group; tabs stay only where the content swipes. A segmented toggle
  (the friend picker's scope) becomes a connected button group, single-select
  with a selection required; so may a single-select chip set of up to four
  options. Filter chips stay chips. Detail screens get no tabs.
- **Buttons and toolbars.** A split button only where a primary action has a
  menu of variants (the `.ics` export and its ranges). A floating toolbar only
  for more than two actions, never docked together with the navigation bar.
- **Motion in spring terms.** Components take `MotionScheme.standard()`;
  `MotionScheme.expressive()` only for hero moments; fixed tweens become
  springs; contextual screens and sheets follow predictive back. Under reduced
  motion the end state shows at once.
- **Shapes.** Connected groups already have the expressive connected-list shape
  (`largeIncreased` 20 dp outside, `extraSmall` 4 dp inside) and keep it.
- **Not yet.** Flexible app bars, expressive list items, the `SearchBar` state
  and `BottomAppBar` are missing from the pinned material3 line
  (`1.12.0-alpha03`) and wait for the next line; no Android-only actuals stand
  in for them unless the owner asks.
- **Out of the pass.** Widgets, the quick-settings tile, shortcut and
  notification icons keep their own palette; WebViews and the ITMO.ID page stay
  as they are.

Three values are open options. Screens keep today's value until the owner's
answer is recorded with the M3E token change:

| Option | Today | Alternatives |
|---|---|---|
| FAB pairs on home (`Мой ИТМО` and the QR pass) and the schedule (scroll to top and friends) | Two stacked FABs | A FAB menu; a floating toolbar |
| Navigation labels | On the selected tab only | On every tab, as M3E asks |
| `cardSummary` radius | 24 dp, off the corner scale | 28 dp where the card is the page hero (the PE sport card), 20 dp otherwise; 20 dp everywhere |

## Verification tiers

Screens and kit components are checked by JVM screenshot baselines
([ADR 0022](decisions/0022-jvm-screenshot-tests.md)); an emulator is used only
for what the JVM cannot render. Screenshots and fixtures contain synthetic data
only. Compilation is not visual verification.

- **Every PR.** `scripts/verify.sh quick`, plus `scripts/verify.sh shots <module>`
  for a UI change: light and dark baselines of every preview, compared pixel for
  pixel, with accessibility checks (touch targets, labels, contrast) on every
  capture. CI's `verify-quick` runs `shots all` and never records. A baseline
  changes only with the code behind it, and every new or changed PNG is looked
  at before it is committed.
- **Four appearances** (light; dark; a seeded palette at font scale 1.3 on a
  320 dp width; dark with another seed at 1.3 on 320 dp) for the kit, always,
  and for a feature's screens on its last port; once recorded, every run
  verifies them.
- **Ports.** Before a screen is ported, a JVM reference capture of its XML
  screen is recorded under the future preview's name; the port's re-record diff
  is the parity diff.
- **Emulator** only for Android-only surfaces: widgets, the quick-settings tile,
  shortcuts, WebView, notifications and the instrumented tests of screens not
  ported yet, through `scripts/verify.sh ui` on a pool emulator.
- **Release candidate.** The full matrix: `scripts/verify.sh shots all --gallery <dir>`
  plus an emulator pass that covers
  - light and dark theme, at least one non-default Material You palette on
    Android 12+; the ITMO.ID window in both themes around its light page;
  - a narrow phone (320 dp content width) and the primary test device; font
    scale 1.0 and 1.3;
  - long Russian names and titles; loading, content, empty and error; first
    load, refresh with cache, refresh error, retry, in-button progress, view
    recreation;
  - touch targets, TalkBack descriptions, selected and unavailable states;
  - first and last transition frames; no clipped dates or chips, no jumps, no
    leftover alpha after recycling, no lost scroll.

### Running the visual tests

Screenshot tests run on the JVM (Roborazzi on Robolectric) from each module's
`@Preview`s:

```bash
scripts/verify.sh shots <module>                  # compare shared/<module>: light and dark (the kit: all four)
scripts/verify.sh shots <module> --record         # re-record after a deliberate change, then look at every PNG
scripts/verify.sh shots <module> --record -Pshots.appearance=full   # also the two narrow appearances
scripts/verify.sh shots app                       # :app: XML reference captures and the harness proof
scripts/verify.sh shots all                       # every module plus :app, what CI runs
scripts/verify.sh shots all --gallery <dir>       # every capture in four appearances into <dir>, no compare
```

- Baselines live in `<module>/screenshots/` as `<Preview>_<appearance>.png`
  (`<Preview>_<state>_<appearance>.png` for a named preview state); XML
  references of screens not ported yet are listed in
  `<module>/screenshots/references.txt`. A run also fails on a stale baseline
  or a preview without one.
- A failed comparison writes `<name>_compare.png` (reference, diff, new) to
  `<module>/build/outputs/roborazzi/`; in CI the run uploads them as the
  `screenshot-diffs` artefact. Baselines recorded on the Mac verify on the CI's
  Ubuntu runner under one per-pixel tolerance for every capture
  (`ShotsCompare` in `shared/testing`): a pixel counts as changed above an
  RGBA distance of 0.03, about 4 of 255 per channel, which absorbs the
  anti-aliasing of curved edges and text between arm64 and x86_64; one changed
  pixel still fails.
- Galleries are for review only; never copy a gallery PNG into `screenshots/`.

Instrumented tests stay for what only a device shows: widgets, the
quick-settings tile, WebView, notifications and the platform list, plus the
visual tests of screens not ported yet. They run on a pool emulator through
`scripts/verify.sh ui`, never with bare `adb` or Gradle commands against a
shared device:

```bash
scripts/emulator.sh up            # prints ANDROID_SERIAL=emulator-<port>; `up --api 30` for API 30
ANDROID_SERIAL=emulator-<port> scripts/verify.sh ui WidgetPreviewTest
scripts/emulator.sh down          # always, also after a failure
```

- `up` holds one of the two `emulator` build slots for the emulator's
  lifetime, boots the read-only pool AVD `itmo-pool-api35` (or
  `itmo-pool-api30`) headless on port 5560 or higher, and waits for the boot.
  Every boot starts from a clean image and nothing written during a run survives
  `down`. `scripts/emulator.sh list` shows the pool, the slots and the running
  emulators.
- `verify.sh ui` takes bare class names, FQCNs or `Class#method`, comma
  separated, and runs `:app:connectedGithubDebugAndroidTest` for them. It
  refuses any serial that is not a running emulator; `emulator-5554` belongs to
  the integrator, and the owner's phone is never a target.
- The pool AVDs are created once by the owner with `scripts/emulator.sh init`;
  agents only run `init --dry-run`.

By default the visual suites run in the light appearance only and write no
screenshots, which keeps the suite short. Two instrumentation arguments switch
the full checks on:

- `appearanceMatrix=full` runs each visual test in all four appearances: light;
  dark; font scale 1.3 with a dynamic seed on a 320 dp width; dark with font
  scale 1.3 and another seed on a 320 dp width. Every assertion runs for every
  appearance.
- `captureScreenshots=true` saves the PNGs under
  `/sdcard/Android/data/dev.alllexey.itmowidgets/cache/<suite>-screenshots/`
  (the profile and social suites use `files/` instead of `cache/`).

`verify.sh ui` passes no instrumentation arguments, so a lane run proves the
light pass only. The full matrix with PNGs is an integrator run on
`emulator-5554`; a PR that needs it says so. Before a UI change is called
done, the affected suites run with both arguments and someone looks at the
PNGs; the default run only proves the layout holds in light. `Screenshots`
exports PNGs to Gradle's
`app/build/outputs/connected_android_test_additional_output/githubDebugAndroidTest/connected/`;
copy a completed suite's screenshots into ignored `vibe/` before another
connected run replaces that directory.

Preview hosts set `delegate.localNightMode` before attaching their base context;
setting it in `onCreate` can trigger an extra recreation and consume one-shot
feedback before a test observes it. Assert the effective font scale, night mode,
width and seeded palette, not just fixture values. Capture after layout/frame
commit and completed snackbar animations; allow the SystemUI compositor to
settle when switching appearances. Wait for feedback dismissal after retry.
These are local host overrides: preserve the emulator's global theme/font
settings, and restore them if a separate test explicitly changes them.

## Reference implementations

- Schedule day: `feature/schedule/ui/list/ScheduleDayCard.kt` and `feature/schedule/ui/list/ScheduleRows.kt` of `:shared:feature-schedule`.
- Sport cards: `res/layout/item_sport_booking.xml`, the `Запись` lesson card `SportLessonCard` in
  `:shared:feature-sport`, details sheet `feature/sport/ui/common/SportCommonDetailsBottomSheet.kt`.
- Settings and profile groups: `res/values/styles.xml`, `feature/settings/ui/SettingsScreen.kt` in `:shared:feature-settings`.
- System surfaces: the quick-settings tile icon `res/drawable/ic_tile_qr.xml`
  (Material Symbols `qr_code`, white, no theme tint: SystemUI colours tile icons
  and resolves no app theme attributes); the shortcut icons
  `res/drawable/ic_shortcut_qr.xml` and `ic_shortcut_today.xml`, adaptive icons
  without a tint (white background like the launcher icon, `qr_code` and
  `schedule` 36 dp in `#4984E2` in the safe zone of the 108 dp foreground).
- Recordbook row: `SubjectRow` in `feature/recordbook/ui/RecordbookScreen.kt` of
  `:shared:feature-recordbook` (name, metadata, a number with a thin bar or a
  grade badge). The name and the new-mark dot for unread marks share a
  horizontal row centred vertically: the name takes the remaining width and
  wraps, the dot (8 dp, `primary`, `compact` spacing before it) follows it like
  the new-change dot of the schedule changes history and adds `Новое` at the
  start of the row's TalkBack description.
  A total from a connected sheet stands where the points would be while the
  official points are empty: `sheet_mark` (`ic_table`, 16 dp,
  `colorOnSurfaceVariant`, 4 dp before the value) and the value (`titleMedium`,
  one line, at most 96 dp) in `score_group` (`wrap_content`, at least 64 dp),
  without the bar; TalkBack reads `Из таблицы: …`.
- Subject page: `feature/recordbook/ui/SubjectHubAdapter.kt` (the result card
  with `GradeScaleView` and the sheet total, then connected groups of links,
  chats, controls, teachers and lessons), `res/layout/item_subject_hero.xml`,
  `res/layout/item_recordbook_control.xml`,
  `res/layout/item_recordbook_control_group.xml`.
- Connected groups: `core/ui/ConnectedGroup.kt`, `res/layout/item_section_heading.xml`,
  `res/layout/item_group_action_row.xml`; link rows `res/layout/item_subject_link.xml`
  with `res/layout/view_link_vote_pill.xml`, bound by `core/ui/SubjectLinkRow.kt`.
- Sheet total on the subject page: `res/layout/item_subject_sheet_score.xml`
  included at the bottom of the result card under a hairline (`ic_table`, the
  value in `titleMedium`, `путь, лист «Лист»` and a status line in
  `bodySmall`, the status under the 48 dp `⋮` so it never wraps beside it, the
  glyph in line with the card's content edge; failures in `colorError`), or the
  text button `Мои баллы из таблицы` with `ic_table` in its place, bound in
  `feature/recordbook/ui/SubjectHubAdapter.kt`.
- The «Мои баллы» sheet: `SheetScoresSheet` of `:shared:feature-recordbook`
  (`ui/sheets/SheetScoresSheet.kt`) on the kit's `SheetScaffold` (handle, title,
  subject, one bounded area of at least 288 dp for loading, failures and the
  choices; choice rows with the title in `bodyLarge`, the caption in
  `bodySmall`, the value in `titleSmall` at the end, `ic_check` in `primary`
  and `selected` for the current total; the whole row is the target), hosted
  by `feature/recordbook/ui/sheets/SheetScoresBottomSheet.kt`.
- The `.ics` sheet: `IcsExportSheetContent` (`:shared:feature-settings`,
  `feature/settings/ui/ics`) (handle, title,
  subtitle, one area of at least 288 dp for the ranges, loading, the file,
  an empty range and failures; the ranges are connected-group rows: title
  `bodyLarge`, days `bodySmall`, chevron), hosted by
  `feature/settings/ui/IcsExportBottomSheet.kt`.
- Link sheets: `feature/resources/ui/SubjectLinksBottomSheet.kt` (connected
  groups per category), `LinkEditorBottomSheet.kt`, `LinkActionsBottomSheet.kt`,
  `res/layout/item_subject_link.xml`.
- Person profile: Compose in `:shared:feature-social`
  (`feature/social/ui/profile/UserProfileScreen.kt`): the hero `ProfileHero.kt`
  (`Card.Hero`: avatar, name, one short line, the ISU number with a copy symbol
  in a 48 dp target, the friendship badge or status and buttons in the kit's
  `ButtonRow`, side by side or stacked at full width when the labels do not
  fit, as `Принять заявку` and `Отклонить` at 1.3 on 320 dp), and in
  `ProfileSections.kt` the fact sections as connected groups of informational
  rows, the `ITMO.Widgets` group with its entries, and the reviews heading.
- Teacher reviews: the own review as a group of its own, the others as one
  connected group, `feature/social/ui/reviews/TeacherReviewRow.kt` (`subject, date` with
  `⋮`; the full text; a footer: on the left who wrote it in `bodyMedium`, a
  named author or the source as a link, otherwise `Анонимный отзыв`, and under
  it the verification in `bodySmall`, `Вёл у автора` with a 16 dp `ic_check` in
  `colorPrimary` or a muted `Не подтверждён`; on the right the kit's vote pill
  centred on that column; 12 dp under the footer,
  16 dp under a row that ends with its text), `OwnTeacherReviewRow.kt` (`мой`,
  a status pill as a 12 % wash of its tone: content-based palettes make
  `…Container` colours too dark for the tone as text).
- AI summary: `feature/social/ui/reviews/TeacherSummaryCard.kt` (`Card.Content.Tonal`, a
  header with `auto_awesome` and a muted `ИИ`, the tone row with the dot, pros
  and cons as icon rows, tag chips and five scales
  collapsed behind the text button `Подробнее`/`Свернуть` whose 48 dp target
  reaches into the card padding).
- Tone dot: `res/drawable/bg_teacher_level_dot.xml` tinted by
  `core/ui/TeacherLevelTone.kt` (`ImageView.bindLevel`), in the lesson sheet's
  teacher fact (`fact_mark` in `res/layout/item_sport_detail_fact.xml`) and the
  subject page's teacher rows (`level_dot` in `res/layout/item_subject_teacher.xml`);
  its place is reserved where a late dot would otherwise move the row.
- Review editor: Compose in `:shared:feature-reviews` (`feature/reviews/ui/ReviewEditorSheet.kt`),
  hosted by `feature/reviews/ui/ReviewEditorBottomSheet.kt`. A form sheet without a handle
  that cannot be dragged or dismissed outside: Back and the close button ask
  `Не сохранять отзыв?` only when something changed. The title has a second line
  with the teacher's short name; subject suggestions are filter chips that mark
  the picked one.
- Clickable teacher fact in details headers: `res/layout/item_sport_detail_fact.xml`,
  `core/ui/DetailsHeader.kt` (`bindAction` resets chevron, ripple, touch target
  and accessibility action when the identifier is absent).
- Avatar image failure: `core/ui/AvatarView.kt` keeps current initials ready and
  guards the posted Glide fallback against a newer binding or successful load.
- Friend picker: Compose in `:shared:feature-social`
  (`feature/friendselector/ui/FriendSelectorSheetContent.kt`) on the kit sheet host,
  `FriendSelectorDialogFragment` at 90 % height. Rows are the kit's `UserSelectionRow`
  with `onOpen` (a closed schedule ends in a lock and opens the profile, a long
  press opens it from any row); the chosen row lies on `colorSecondaryContainer`;
  the scope is `ItmoButtonGroup`.
- Home feed: Compose in `:shared:feature-home`
  (`feature/home/ui/HomeScreen.kt`, each card in its feature's `ui/home`), see
  [Home and quick actions](features/home.md#feed). The schedule changes and
  new marks cards are `Card.Content` cards that close: a header with
  `ic_edit_calendar` (`ic_menu_book`) in `primary`, the title, the count
  `Pill` and a trailing 48 dp close button, then the headline (the subject
  names) wrapping without truncation; the whole card opens the history (the
  recordbook).
- Schedule changes: Compose in `:shared:feature-schedule`
  (`feature/schedule/ui/changes/ScheduleChangesScreen.kt`). A history row is a
  `Card.Content` card, not clickable: the subject with an 8 dp `primary` dot for
  a new change (centred on the subject's first line), the main line, one text
  per changed field so a wrapped field never runs into the next, `вид · поток`;
  one TalkBack node. Day titles are `titleSmall` in `onSurfaceVariant` headings
  above their rows; an empty history is the kit's `ContentState` with
  `ic_history`. On lesson cards the
  mark `change_indicator` (`ic_edit_calendar`, 16 dp, `colorPrimary`) after the
  video-call mark in `feature/schedule/ui/list/ScheduleRows.kt` of `:shared:feature-schedule`; in the lesson sheet
  the `Изменения` block (divider, heading, `было → стало` lines) in
  `feature/schedule/ui/details/LessonDetailsSections.kt` of `:shared:feature-schedule` and
  the informational flow row between teacher and place of the kit's `DetailsHeader`.
- Visual tests: `feature/recordbook/RecordbookVisualTest.kt`, `feature/resources/SubjectLinksVisualTest.kt`.
