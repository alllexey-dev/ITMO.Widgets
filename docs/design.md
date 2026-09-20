# Design guide

One visual language across the Android application, grown from the screens the
user already likes: the schedule and the sport cards. Quiet surfaces, compact
layout, clear hierarchy, small meaningful accents. This is the target contract
for every screen; card styles are pinned by `DesignCardResourcesTest`.

## Principle

**One visual language, not identical screens.**

- Information over decoration. Colour, size and motion explain meaning.
- A compact card has one focus object and secondary context.
- Hierarchy comes from surfaces, typography and spacing, not shadows.
- Never stack a filled surface, a coloured stroke, a large status, a badge and a
  decorative progress on one element.
- Screens that already work are kept. Unification does not mean a redesign, a
  new UI framework or features outside the roadmap.

## Colour and surfaces

Material 3 with the existing `Theme.Material3.DynamicColors.DayNight`. Every
screen must work in light, dark and dynamic palettes; never assume the wallpaper.

| Role | Rule |
|---|---|
| Screen background | `colorSurface`; never override the window background with a fixed colour |
| Ordinary card | `colorSurfaceContainerLow`, elevation 0 dp |
| Nested neutral panel | a surface-container level such as `colorSurfaceContainerHighest` |
| Primary text | `colorOnSurface` |
| Metadata and decorative icons | `colorOnSurfaceVariant` |
| Action, current item, small accent | `colorPrimary` |
| Selected contextual surface | `colorSecondaryContainer` with `colorOnSecondaryContainer` |
| Quiet stroke and divider | `colorOutlineVariant` |

`on…Container` colours are used only on their container. Do not fill large
cards with `colorPrimary` to make them prominent; the user rejects heavy tonal
fills. Lesson types, grades, sport statuses and ring sectors are domain
semantics with their own deliberate light and dark values and contrast checks;
a status is also readable by text or icon, never by colour alone.

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
| `Card.CompactSummary` / `Card.Summary` | 20 / 24 dp radius | Recordbook summary and overview |
| `Card.SettingsGroup` | 20 dp radius, no stroke, inner dividers | One card per settings or profile group, never per row |
| `Card.ScheduleDay` | 16 dp radius, 16 dp between days | A day of lessons with its timeline |

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
- User-visible strings live in resources and are Russian. Remote names are
  trimmed at the mapper.
- Grade codes are contiguous: `2FX`, `3E`, `3D`. Short status plus number pairs
  do not wrap into ambiguous lines. One number is not repeated in neighbouring views.

## Icons, actions and selection

- Official Material Symbols, rounded outline family, 24 dp viewport, named after
  the symbol (`ic_calendar_add`). One meaning, one symbol. No emoji or text glyphs.
- Tint through semantic attributes; teacher and location icons are neutral.
- Filled variants only for a selected/active state or legibility.
- A button may look smaller than 48 dp through insets; its touch area stays 48 dp.
- Filled button for the strong primary action, tonal for an ordinary prominent
  action, outlined or text for secondary and contextual ones.
- Every actionable icon has a localized `contentDescription`; decorative ones `@null`.
- Selection is shown by a check or a container surface plus `selected` or
  `checkable` state for TalkBack, and the whole row is the target. A closed or
  private row never looks selectable.
- The bottom navigation keeps `labelVisibilityMode="selected"`; no permanent
  label row, no large titles above root content. Contextual screens use a back
  button and a concise title.

## Refresh and loading

| Screen | Contract |
|---|---|
| Schedule, both sport tabs, recordbook and subject details | `applyAppRefreshColors()` from `core/ui/RefreshAppearance.kt`: indicator `colorPrimary`, background `android.R.attr.colorBackground` |
| ITMO.ID web sign-in | Explicit exception: light theme, library default indicator |

- First load: a Material indicator in the bounded content area, no flash of an
  empty state before it.
- Refresh: existing data and scroll position stay; the indicator reports work
  without replacing the screen. The schedule keeps its loaded range including
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
- Empty explains why there is nothing and what to do. An error is never shown as
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
- Motion explains a change: 150–250 ms, honouring the system animator scale. Do
  not animate unchanged text or flash the screen on refresh. Sport tabs switch by
  tap and pager gesture; the sport date strip moves only with its arrows; the
  month label animates only when the month changes.
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
  and close with a trailing icon button. The lesson in progress sits on
  `colorSecondaryContainer` with its own on-colours. Pull-to-refresh keeps the
  cards; one snackbar reports a partial failure. The `Мой ИТМО` FAB stays at the
  bottom end and the list reserves space under it.

## Shared components

Keep XML, Material components and the current architecture. The shared layer is
deliberately small: card variants, named dimensions, refresh helper, content-state
styles, the accessible selection row (`bindSelectionAccessibility`), the user row
(`item_user_row.xml`) and the contextual screen header. Shared helpers belong to
`core/ui`; screen-specific behaviour to `feature/<name>/ui`. Extract only rules
that genuinely repeat; do not build a universal renderer.

## Verification matrix

Every meaningful UI change is checked on an emulator before it is called done:

- light and dark theme, at least one non-default Material You palette on
  Android 12+; the ITMO.ID window in its light theme;
- a narrow phone (320 dp content width) and the primary test device; font scale
  1.0 and 1.3;
- long Russian names and titles; loading, content, empty and error; first load,
  refresh with cache, refresh error, retry, in-button progress, view recreation;
- touch targets, TalkBack descriptions, selected and unavailable states;
- first and last transition frames; no clipped dates or chips, no jumps, no
  leftover alpha after recycling, no lost scroll.

Screenshots and fixtures contain synthetic data only. Compilation is not visual
verification.

### Running the visual tests

By default `./gradlew :app:connectedDebugAndroidTest` runs every visual test in
the light appearance only and writes no screenshots, which keeps the suite
short. Two instrumentation arguments switch the full checks on:

- `appearanceMatrix=full` runs each visual test in all four appearances: light;
  dark; font scale 1.3 with a dynamic seed on a 320 dp width; dark with font
  scale 1.3 and another seed on a 320 dp width. Every assertion runs for every
  appearance.
- `captureScreenshots=true` saves the PNGs under
  `/sdcard/Android/data/dev.alllexey.itmowidgets/cache/<suite>-screenshots/`
  (the profile and social suites use `files/` instead of `cache/`).

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.appearanceMatrix=full \
  -Pandroid.testInstrumentationRunnerArguments.captureScreenshots=true
```

The same run through `adb`, for one class:

```bash
adb shell am instrument -w -e appearanceMatrix full -e captureScreenshots true \
  -e class dev.alllexey.itmowidgets.feature.onboarding.OnboardingVisualTest \
  dev.alllexey.itmowidgets.test/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/data/dev.alllexey.itmowidgets/cache/onboarding-screenshots
```

Before calling a UI change done, run the affected suites with both arguments
and look at the PNGs; the default run only proves the layout holds in light.

## Reference implementations

- Schedule day: `res/layout/item_day_schedule.xml`, `feature/schedule/ui/DayScheduleAdapter.kt`.
- Sport cards: `res/layout/item_sport_lesson.xml`, `res/layout/item_sport_booking.xml`,
  details sheet `feature/sport/ui/common/SportCommonDetailsBottomSheet.kt`.
- Settings and profile groups: `res/values/styles.xml`, `feature/settings/ui/SettingsRenderer.kt`.
- Recordbook card: `res/layout/item_recordbook_subject.xml`.
- User row and public profile: `res/layout/item_user_row.xml`, `res/layout/fragment_user_profile.xml`.
- Friend picker: `res/layout/dialog_friend_selector.xml`.
- Home feed: `res/layout/fragment_home.xml`, `res/layout/item_home_*.xml`,
  `feature/home/ui/HomeFeedAdapter.kt`, `feature/home/HomeFeedVisualTest.kt`.
- Visual tests: `feature/sport/cards/SportCardsVisualTest.kt`,
  `feature/recordbook/RecordbookVisualTest.kt`, `feature/friendselector/SelectionRowsTest.kt`.
