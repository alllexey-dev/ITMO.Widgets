# Design guide

One visual language across the application, grown from the screens the user
already likes: the schedule and the sport cards. Quiet surfaces, compact layout,
clear hierarchy, small meaningful accents. This is the contract for every
screen. Screens are Compose Multiplatform: they take every value from
`ItmoTheme` in `:shared:designsystem` ([Tokens](#tokens)) and their parts from
its kit ([Shared components](#shared-components)); the redesigned Android screens build from the Material 3
Expressive kit ([The M3 Expressive kit](#the-m3-expressive-kit)) inside the [shell](#shell). Views remain only where the
system or a library draws: the home-screen widgets, the quick-settings tile,
shortcut and notification icons, the WebView slots of the ITMO.ID and BARS
sign-in pages and the spoiler image crop screen. They keep their look from
the app theme and `res/values` ([System surfaces](#system-surfaces)).

## Principle

**One visual language, not identical screens.**

- Information over decoration. Colour, size and motion explain meaning.
- A compact card has one focus object and secondary context.
- Hierarchy comes from surfaces, typography and spacing, not shadows.
- Never stack a filled surface, a coloured stroke, a large status, a badge and a
  decorative progress on one element.
- A look change goes through the tokens and the kit, not screen by screen
  ([ADR 0021](decisions/0021-m3-expressive-order.md)).

## Tokens

`ItmoTheme` (`shared/designsystem`, package `dev.alllexey.itmowidgets.designsystem`)
is the one source of design values for Compose. Its schema has the Material 3
Expressive shape from the start (extended colours, a corner scale with
`largeIncreased`, emphasized type, `MotionScheme`), so component APIs do not
change when the values do ([Expressive components](#expressive-components)).
Components and screens read tokens only through `ItmoTheme.*`. No raw colour,
`.dp` spacing or font size outside `shared/designsystem`; experimental and
expressive Material APIs only inside it, behind `Itmo*` wrappers.

| Slot | Contents and values |
|---|---|
| `ItmoTheme.colorScheme` | The M3 colour roles. `ColorSource.Platform` (the default of `AppColorSource`): dynamic colour on Android 12+, otherwise, and on iOS, the static brand scheme (`ColorSource.Static`: the TonalSpot variant of the 2021 spec from the brand blue `#4984E2`). `ColorSource.Theme` resolves the stored appearance (`resolveColorScheme`). `ColorSource.Seed` generates MDC's content-based scheme from a seed (previews, tests). On Android 12+ the outline-variant and error roles stay MDC's baseline ones, as the View theme shows them |
| `ItmoTheme.extendedColors` | The app's own colours beside the scheme, one slot per entry of `res/values{,-night}/colors.xml`: lesson types, recordbook passed, sport scores and conditions, teacher levels; plus the kit's success and warning roles (`success`, `successContainer`, `onSuccessContainer` light `#2E7D32`, `#C8F0C8`, `#0B3D10`, dark `#8BD88E`, `#1F5323`, `#C8F0C8`; `warning` and its pair light `#9A6400`, `#FFDDB0`, `#4A2800`, dark `#FFB951`, `#5C3B00`, `#FFDDB0`), which have no resource. Derived per scheme: sport condition containers (the accent mixed 12 % over `surfaceContainerLowest`) and teacher levels (harmonized towards `primary`). In dark, lesson types keep their light values |
| `ItmoTheme.shapes` | Corner scale `extraSmall` 4, `small` 8, `medium` 12, `large` 16, `largeIncreased` 20, `extraLarge` 28, `extraLargeIncreased` 32, `extraExtraLarge` 48 dp, `full`; card family `cardContent` 20, `cardSummary` 28, `cardHero` 28, `scheduleDay` 16 dp; connected groups 20 dp outer, 4 dp inner, 2 dp gap; stroke 1 dp, elevation 0 dp |
| `ItmoTheme.spacing` | `related` 4, `compact` 8, `content` 12, `group` 16, `section` 24, `screenMargin` 16, `cardPadding` 16, `summaryPadding` 20, `touchTarget` 48, `fabClearance` 96, `fabStackClearance` 152, `statePadding` 32, `stateIcon` 64, `stateInlineIcon` 56 dp |
| `ItmoTheme.typography`, `ItmoTheme.emphasizedTypography` | The 15 M3 type roles at MDC 1.13's `TextAppearance.Material3.*` values on the system font, each with an emphasized twin; pinned, so a material3 bump cannot change a ported screen's text |
| `ItmoTheme.motion` | Durations `quick` 180, `standard` 220, `emphasis` 260, `reveal` 300, `progress` 700, `progressSlow` 1000 ms; the skeleton pulse 1200 ms down to alpha 0.55; easing `(0.2, 0, 0, 1)`; Material's `MotionScheme.standard()` for components, `MotionScheme.expressive()` only for heroes (`ItmoTheme.heroMotionScheme`) |

- `cardSummary` is `extraLarge` (28 dp) like `cardHero` while the PE sport card
  is its page's hero; if it stops being the hero it becomes 20 dp.
- `ItmoTheme(expressive = true)` is the default: the expressive variants of
  [Expressive components](#expressive-components) are on for every Material
  screen.
- Reduced motion: `rememberReducedMotion()` is true when the system animator
  duration scale is 0 (iOS reports false until its host reads Reduce Motion); a
  kit animation then shows its end state at once.
- Equality with the Android resources the widgets and system surfaces use is
  tested: `DesignTokensParityTest` (`:app`) keeps extended colours, spacing and
  shapes equal to `res/values` except its listed divergences, `ThemeParityTest`
  the dynamic and seeded schemes and the M3 baseline the widgets keep on API
  26-30, and `TypographyParityTest` the type scale.
- `shared/designsystem/tokens/itmo-tokens.json` exports the tokens (static light
  and dark schemes by role, extended colours with the derivation rules, shapes,
  spacing, type, motion; colours `#RRGGBB`, sizes dp or sp, durations ms;
  `schemaVersion` grows with a breaking change). The iOS client and the web app
  take their tokens from it instead of copying values. It is generated, never
  edited or merged by hand: after a token change or a rebase run
  `./gradlew :shared:designsystem:exportDesignTokens`;
  `DesignTokensExportTest` fails while it is stale.

## Colour and surfaces

Material 3 colour roles, named here by their `ItmoTheme.colorScheme` token. On
Android 12+ the screens take the wallpaper's dynamic scheme, the same values the
View theme (`Theme.Material3.DynamicColors.DayNight`) gives the remaining Views.
Below Android 12 the screens take the brand static scheme while the widgets keep
the M3 baseline `#6750A4`; the window background follows the brand scheme there
(`theme_static_background`), so no seam shows behind the status bar. Every
screen must work in light, dark and dynamic palettes; never assume the
wallpaper.

The user picks the colours on `Оформление` ([settings](settings.md#appearance)),
stored as one `ThemeSpec` (accent, custom colour, palette style, contrast,
black background). `resolveColorScheme(spec, dark, wallpaper)` in
`designsystem/theme/ThemeSchemes.kt` is the one way from it to a scheme:
`ItmoTheme` without a `colorSource` reads `AppColorSource.current`, which the
app hosts feed from `AppearancePreferences.observeTheme()` at start, and the
widgets call the same function with `wallpaperPalette(context)` (null below
Android 12).

- The seed: `Как обои` the wallpaper's (`system_accent1_500`, the brand blue
  without one), `Фирменный` the brand blue `#4984E2`, `Свой цвет` the user's,
  and six presets around the brand blue: `Бирюзовый` `#009688`, `Зелёный`
  `#43A047`, `Янтарный` `#FFA000`, `Красный` `#E53935`, `Розовый` `#D81B60`,
  `Фиолетовый` `#8E24AA`. TonalSpot keeps only a seed's hue, so no grey preset
  is offered; `Монохром` is the grey look.
- The scheme: MaterialKolor's `dynamicColorScheme` from the seed with the
  style's `PaletteStyle` and the contrast level (0, 0.5, 1), 2021 spec, which
  has every style. `Спокойная` at `Обычный` is the brand scheme's recipe, so
  the defaults and the earlier accent presets draw exactly as before.
- `Как обои` at `Спокойная` and `Обычный` is the system's dynamic scheme
  unchanged (with the View theme's baseline outline-variant and error roles);
  another style or contrast rebuilds it from the wallpaper's seed, because the
  system's scheme has neither.
- `Чёрный фон` sets `background`, `surface`, `surfaceDim` and
  `surfaceContainerLowest` to black in the dark scheme only (MaterialKolor's
  `isAmoled` plus the two surfaces under the containers); the containers keep
  their tones, so cards stay apart from the page.

Every style and contrast keeps `onSurface` on `surface` at 4.5:1 or more (7:1
at `Высокий`) and the on-primary pairs at 3:1 or more (`ThemeSchemesTest`).
The View screens that remain, the QR tile, notifications and the launcher
icon keep their colours. `AccentSamplesPreview` shows a sample screen in four
seeds, `ThemeStylesPreview` in every style and `ThemeContrastPreview` at every
contrast and with the black background, in every appearance.

| Role | Rule |
|---|---|
| Screen background | `surface`; never override the window background with a fixed colour |
| Ordinary card | `surfaceContainerLow`, elevation 0 dp |
| Nested neutral panel | a surface-container level such as `surfaceContainerHighest` |
| Primary text | `onSurface` |
| Metadata and decorative icons | `onSurfaceVariant` |
| Action, current item, small accent | `primary` |
| Selected contextual surface | `secondaryContainer` with `onSecondaryContainer` |
| Quiet stroke and divider | `outlineVariant` |

`on…Container` colours are used only on their container. Do not fill large
cards with `primary` to make them prominent; the user rejects heavy tonal
fills. Lesson types, grades, sport statuses and ring sectors are domain
semantics with their own deliberate light and dark values and contrast checks;
a status is also readable by text or icon, never by colour alone.

The tone of a teacher's AI summary is such a stable colour of meaning:
`teacher_level_*` from red to green (`very_negative`, `negative`, `mixed`,
`positive`, `very_positive`, light `#D32F2F`, `#E06C00`, `#B58900`, `#689F38`,
`#2E7D32`, dark `#FF6E6E`, `#FFA24C`, `#FFD54F`, `#AED581`, `#4CAF50`),
only harmonized towards the primary colour (`teacherLevel*` in
`ItmoTheme.extendedColors`, drawn by the kit's `ToneDot`), so every palette
keeps five distinct tones. The dot is decorative; its row says the tone in
words.

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

The card family below names the variants the docs and the kit's KDoc refer to;
their shapes are `ItmoTheme.shapes` tokens. Under the iOS style every card and
group takes 26 pt ([Platform styles](#platform-styles)).

| Variant | Shape | Use |
|---|---|---|
| `Card.Content` | `cardContent` 20 dp, `surfaceContainerLow`, no stroke | Subject, control and user rows |
| `Card.Content.Outlined` | 20 dp, 1 dp `outlineVariant` | Sport lesson and booking cards, debug cards |
| `Card.Content.Tonal` | 20 dp, `surfaceContainerLow`, no stroke | The AI summary of a teacher's reviews above the reviews |
| `Card.CompactSummary` | `cardContent` 20 dp | The recordbook summary row |
| `Card.Summary` | `cardSummary` 28 dp, 20 dp padding | The sport score card of `Моё` and the formula of `Откуда баллы` (`ScoreCard`, `ItmoCard(hero = true)`) |
| `Card.Hero` | `cardHero` 28 dp, `surfaceContainer`, no stroke | The subject result and the person on a profile, above the page's connected groups |
| `Card.SettingsGroup` | 20 dp, no stroke, inner dividers | One card per settings or profile group, never per row (the kit's `SettingsGroup`) |
| `Card.ScheduleDay` | `scheduleDay` 16 dp, 16 dp between days | A day of lessons with its timeline (`ScheduleDayCard`) |

### Connected groups

A list section of a contextual page (the subject page, the links sheet) is a
heading over one connected group, not a card per row and not rows on the bare
background:

- The heading (the kit's `SectionHeading`): `titleSmall` in `primary`, 16 dp
  in from the group's edge, 24 dp above (16 dp in a sheet, 8 dp for the first
  one), 8 dp below; a heading for TalkBack.
- The rows: one surface, `surfaceContainerLow` (the surface of schedule days;
  `surfaceContainerHigh` inside a sheet, which is itself `surfaceContainerLow`),
  2 dp apart; the first and the last row round 20 dp outside, every corner
  between rows is 4 dp (the `ItmoTheme.shapes` group tokens).
  `Modifier.connectedGroupItem(position, surface)` draws a row by its
  `GroupPosition` (single, first, middle, last) on its `GroupSurface` and clips
  it, so a `clickable` after it ripples in the same shape. A row is at least
  56 dp high with 16 dp of content padding.
- A sub-heading inside a section (a control group, the kit's
  `SectionSubheading`) is `titleSmall` in `onSurface` with its value at the end
  and starts a group of its own; a group right after another group without a
  heading keeps 12 dp to it.
- Own and others' items share the row style; an own one is told by a small
  `моя` badge (`secondaryContainer`), never by a different surface or
  outline. Others' links carry the compact vote pill `▲ N ▼`
  (the kit's `VotePill`: a 32 dp pill inside 48 dp arrow targets).
- Captions put a second fact on a second line or join facts with a comma; no
  « · ». The last row that leads further (`Все ссылки, N`, `Все пары, N`,
  `Добавить ссылку`) is the kit's `GroupActionRow` or, on the redesigned
  screens, a `ListRow` with a chevron.

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
- Filled button for the strong primary action, tonal (`TonalButton`, the
  primary family) for an ordinary prominent action, outlined or text for
  secondary and contextual ones.
- Every actionable icon has a localized `contentDescription`; decorative ones `@null`.
- Selection is shown by a check or a container surface plus `selected` or
  `checkable` state for TalkBack, and the whole row is the target. A closed or
  private row never looks selectable.
- The bottom navigation is M3E's `ShortNavigationBar` with a label on every tab;
  a long label ellipsizes instead of overlapping its neighbours. Tab roots have
  the root header ([Shell](#shell)); contextual screens use a back button and a
  concise title (`AppTopBar`, `ItmoDetailScreen` for the redesigned ones).

## Refresh and loading

| Screen | Contract |
|---|---|
| Every screen with pull-to-refresh | The kit's `AppRefreshBox`: the contained expressive loading indicator ([Expressive components](#expressive-components)) |
| ITMO.ID web sign-in | Explicit exception: the WebView slot's `SwipeRefreshLayout` with the library default indicator. The window follows the app theme (light or dark); only the ITMO.ID page inside it is light |

- First load without any cache: the kit's `Skeleton` (`SkeletonStyle.List` or
  `.Cards`) in the bounded content area that the content will take, pulsing
  between 55 % and full opacity every 1.2 s and static when the system has
  animations off; no flash of an empty state before it. A screen that has a
  cached answer never shows it: the cache renders in the first frame and the
  refresh reports through the pull-to-refresh indicator over the content.
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
description and optional action: the kit's `ContentState` and
`ContentStateLoading` (`ContentStateSize.Full` and `.Compact`).

- Loading, content, empty and error occupy the same bounded area.
- Empty has a title; a description only when the reason or the next step is
  not obvious from it. An error is never shown as
  "nothing found"; an unknown value is never shown as zero.
- Retry is a tonal action; a primary action leading out of an empty screen may be
  filled; a text button is fine in a compact secondary message.
- A refresh error with data on screen keeps the data and shows a snackbar with
  retry. Toasts are not used for recoverable errors.
- One state drives the whole area, so list and placeholder switch in one frame;
  no message under the previous list on an intermediate frame.

## Screen behaviour

- Five bottom tabs, pushed screens above them ([Shell](#shell)).
  Ordinary state changes do not recreate the screen or lose scroll position.
  Root selection or reselection closes every pushed screen, sheet and dialog.
- Motion explains a change and honours the system animator scale (scale 0
  shows the end state at once). Components move with springs from
  `MotionScheme.standard()` (`ItmoTheme.motion.scheme`), hero moments with
  `ItmoTheme.heroMotionScheme`; the few fixed durations come from
  `ItmoTheme.motion`. A colour that appears or
  disappears fades its own alpha; never animate a colour to or from
  `Color.Transparent`, which is transparent black and greys the colour half
  way. Do not animate unchanged text or flash the screen on refresh. `Моё` and
  `Запись` of sport switch by their `SegmentedChoice` only; the sport week
  strip pages by weeks.
- Past schedule days fade their card content once to 0.72 (`ScheduleDayCard`);
  today and future days 1.0; inner lesson rows get no second alpha layer.
- Schedule timeline markers: a hollow neutral circle for a lesson not yet
  started, a filled dot for a past one, a circle with an inner dot in
  `primary` for the next official lesson in the loaded range, a filled accent
  marker for the lesson in progress. Markers are 12 dp in a 14 dp stable area and
  cover the line with the day surface. Auto-sign rows use the same row and a
  short text note, not a separate card style. States recompute on return and at
  the minute boundary without network or scroll reset.
- The sport score ring keeps a constant angular gap between attendance and bonus,
  a visible minimum for every non-zero sector, identical geometry on the first
  and later frames, and values above 100 do not remove the gap.
- The friend picker is modal: recent chips, bounded search, the whole row as the
  target, explicit confirmation. Selection is a `secondaryContainer` row
  surface; a closed schedule shows a lock and leads to the profile.
- Settings and the profile use quiet group cards with stable row updates.
- Widgets and the QR pass keep their own launcher-adapted palette and readability
  rules; sign-in branding is a separate exception.
- Сегодня is the hero, the day's timeline and `Нужно сделать` only when there is
  something ([Сегодня](features/home.md#сегодня)); the lesson in progress is
  the hero with the wavy bar ([Expressive components](#expressive-components))
  and sits on `primaryContainer` in the timeline. A list under the `Пропуск` button ends with
  `ItmoTheme.spacing.fabClearance` of bottom content padding, one under a stack
  of FABs (the schedule) with `fabStackClearance`, so its last item scrolls
  clear of them. `My ITMO` is a row of Профиль.

### Tab switching and Back

Android only; iOS has its native tab bar and `NavigationStack`s ([iOS app](ios.md#design-system)).

1. The five bottom tabs switch by a tap on the bar only. There is no swipe
   between tabs on either platform (owner, 2026-10-13); horizontal content in a
   screen (the sport pager, chip rows) just scrolls.
2. A tab switch is an M3 fade through: the old root fades out in 90 ms, the new
   one fades in over 210 ms after it, growing from 92 % (`ShellMotion`). Each
   tab keeps its scroll, saveable state and view models across switches.
3. A pushed screen comes and goes on the shared X axis: both screens move a
   tenth of the width and fade through, on the standard scheme's spatial spring.
   It covers the bar, which stays in place under it.
4. Back is the platform's predictive Back from `NavDisplay`: the top screen
   shrinks to 90 % away from the swipe edge and rounds its corners, the screen
   under it (the previous pushed screen, or the tab root with its bar) is
   already composed, grows from 95 % and drifts in from the edge; on release it
   is in place and nothing pops in. Back on another tab's root goes to Сегодня
   the same way; Back on Сегодня's root leaves the app.
5. Sheets and dialogs are their own windows with the system's predictive Back.
6. System gestures win: no gesture exclusion, the back edges stay the system's
   (`GestureRulesTest`).
7. Entry points land on screens: widgets, links, notifications and shortcuts
   open a route with its tab, never a page index.
8. The system animator scale applies: with animations off every switch is
   instant.

## Shared components

Compose screens build from the kit in `shared/designsystem`
(`designsystem/components/`): stateless components over primitives (state and
callbacks in; text, icons and formatted dates as parameters; no feature or
`core` domain types, no network or clock), each with previews (synthetic data,
long Russian names, only in `@Preview` functions and `designsystem/preview/`)
and baselines in all four Material appearances and the three iOS ones under
`shared/designsystem/screenshots/`. A screen uses the kit and grows it instead of
drawing its own variant. The last column says how a component looks under the
iOS style ([Platform styles](#platform-styles)); "Same" means one drawing on
both platforms.

| Component | Use | On iOS |
|---|---|---|
| `AppTopBar`, `AppTopBarAction` | Contextual screen: back or close, a title of up to two lines (one over an optional one-line subtitle, such as the My ITMO page's host), trailing actions | The inline navigation bar: one centred line of headline, the navigation button and the actions in the tint, a hairline when content scrolls under it |
| `ItmoNavigationBar`, `ItmoNavigationBarItem` | Root tabs: M3E's `ShortNavigationBar`, a label on every tab, the FILL 1 icon on the selected one | Not used: the SwiftUI shell's native tab bar |
| `ContentState`, `ContentStateLoading` | Full and compact loading, empty and error states with an optional action | The action is a button capsule; loading is the large activity indicator |
| `Skeleton` | First load without a cache, list or cards | Shapes in `systemFill`, cards with the inset group's corners; geometry and pulse stay |
| `AppRefreshBox` | Pull-to-refresh that the user asked for | Behaves as `UIRefreshControl`: the content follows the pull, the spinner's spokes above it, the refresh haptic at the threshold |
| `ProgressButton` | A button with its own progress, same size, second tap ignored | A `UIButton` capsule: filled is prominent, tonal and outlined are bordered, text is plain; a press dims it |
| `ItmoSwitch` | An on/off switch; with no callback it only shows the state and the row carries the toggle | A `UISwitch` in the accent with the toggle haptic |
| `ItmoActivityIndicator` | A small inline wait that must not change its slot (a switch's slot, a row) | `UIActivityIndicatorView`, medium or large |
| `ButtonRow` | Two buttons side by side, stacked at full width when the labels do not fit | Its buttons take the `ProgressButton` capsules |
| `Pill` | The `моя` badge, a count, a status in its tone over a 12 % wash | Capsules in the iOS type |
| `ToneDot` | The review tone dot, an empty slot until the tone arrives | Same |
| `Avatar` | A photo from the given URL through the host's image loader, initials otherwise | Same |
| `Modifier.connectedGroupItem`, `GroupPosition`, `GroupSurface` | A row of a connected group, drawn by its position, on a screen or in a sheet | Cells of an inset group: one 26 pt radius, square inner corners, no gap, hairline separators; the elevated colours in a sheet |
| `SectionHeading`, `SectionSubheading` | The heading over a group; a sub-heading with its value | A section header in `secondaryLabel` at UIKit's insets |
| `GroupActionRow` | The last row of a group that leads further | A cell with the icon in the tint and the disclosure indicator |
| `LinkRow`, `VotePill` | A link with its own badge or the vote pill | A cell; the vote arrows are 44 pt targets, the pill on `tertiarySystemFill` |
| `UserRow`, `UserSelectionRow` | A person with actions; a selectable person in a picker | A cell with the disclosure indicator; the selection is the checkmark accessory with the selection haptic |
| `SettingsGroup`, `SettingsGroupFooter`, `SettingsRow` and its toggle, choice, navigation, info, action and selection variants | Settings and profile groups, one card per group | A section of an inset-grouped list with UIKit's rows; the toggle is a `UISwitch` in the accent |
| `DetailsHeader` | The head of every details sheet | Title 2 semibold, secondary text and icons in `secondaryLabel`, the map button a tinted capsule |
| `SheetScaffold`, `SheetHandle` | A bottom sheet body: handle, header, one bounded content area (288 dp minimum where states switch), footer | No handle (the host's grabber); the header is the sheet's navigation bar ([Platform styles](#platform-styles)) |
| `ConfirmDialog`, `ChoiceDialog`, `ReportDialog` | A confirmation, a single choice, reporting a review or a link | Compose-drawn iOS alerts with capsule buttons; no hero icon |
| `InfoDialog` | A message with one button | An iOS alert whose one capsule is the preferred action |
| `SwatchChoiceDialog` | A choice among colours: named swatches in a grid, the picked name under it; a tap applies and the dialog stays until its button | An iOS alert with the same grid and one preferred capsule |
| `ItmoMenu` | The one menu of actions anchored to its parent, groups with separators, destructive items in the error colour | A context-menu panel drawn in Compose |
| `ScoreRing` | The sport score ring | Same |
| `GradeScale` | A 0-100 bar with grade ticks | Same |
| `TimelineMarker`, `TimelineLine` | The schedule timeline | Same |
| `StepsIndicator` | Progress dots of a flow | A `UIPageControl` row of equal dots, the current one in the tint |

Experimental and expressive Material components reach screens only through
`Itmo*` wrappers in `shared/designsystem` (`designsystem/components/expressive/`),
so a material3 line switch stays in the module. `ItmoTheme(expressive)` is the
one switch; heroes read `ItmoTheme.heroMotionScheme`. Where each is used:
[Expressive components](#expressive-components).

| Wrapper | Use | On iOS |
|---|---|---|
| `ItmoLoadingIndicator` | A wait shorter than about 5 s outside a button; `contained` in pull-to-refresh | `UIActivityIndicatorView` |
| `ItmoButtonGroup` | A single choice of two to four short options with a selection required; under expressive the kit's `SegmentedChoice` | `UISegmentedControl` with the selection haptic |
| `ItmoWavyProgress` | Determinate progress of a hero only, linear or circular | A plain `UIProgressView` |
| `ItmoHeroAvatar` | The person profile hero's avatar under the `MaterialShapes` mask | The round `Avatar` |
| `ItmoSplitButton` | A primary action with a menu of its variants | A capsule with a chevron half that opens `ItmoMenu` |
| `ItmoFabMenu` | A FAB that opens a menu of actions above it | A round filled toggle that opens `ItmoMenu` |
| `ItmoFloatingToolbar` | More than two icon actions floating over the content, never docked with the navigation bar | An opaque capsule of tinted icons |

On Android sheets and dialogs are overlay scenes of the Navigation 3 shell that
render a `SheetScaffold` body; on iOS a SwiftUI `.sheet` hosts the same body. Shared helpers that are not
drawing (texts, formatters) belong to `:shared:core`; screen-specific parts to
the feature's `ui/`. Extract only rules that genuinely repeat; do not build a
universal renderer.

## Shell

The Android shell (`app/shell/`, [architecture](architecture.md#navigation)) is the frame every screen sits in.

- Five tabs in this order, close to My ITMO: Зачётка, Расписание, Сегодня (the centre and the start tab), Спорт,
  Профиль (`AppTab`, which also names each tab's label key and its outlined and FILL 1 icons: `book_2`,
  `calendar_month`, `today`, `sprint`, `person`). The bar is `ItmoNavigationBar` (M3E `ShortNavigationBar`) with
  labels.
- Every tab root has the root header (`ItmoRootScreen`, `app/shell/entries/TabRootFrames.kt`), one line (owner,
  2026-10-13, header lab variant 7): the title (`titleLarge`) and its subtitle (`bodyMedium`) on one baseline on the
  left, the actions in the same row, centred on the title block; 56 dp. When the subtitle does not fit next to the
  title (a narrow window, a large font) it goes under it; the title never truncates, the header grows instead. It is
  static (owner, 2026-10-13): it never collapses, the content scrolls under it. Сегодня shows the date
  (`понедельник, 7 сентября`, `TodayHeaderViewModel`) and the bell with the count of unseen events, which is in this
  header only and opens the bell's sheet ([the bell](features/home.md#the-bell)). Расписание and Зачётка draw their own
  root header (`Сентябрь ▾` over the week; the period); tabs whose new screens come later show their name over their
  current content.
- The `Пропуск` extended FAB is on Сегодня only and opens the pass sheet; the widgets, the tile and the shortcut open
  the same sheet.
- Pushed screens cover the bar: back, a title, no navigation bar. The bar stays in place under them, so the
  predictive Back uncovers the root with its bar. The title and its subtitle flow as one paragraph
  (`ItmoDetailScreen`, 64 dp in one line) and a long title wraps in full, never truncated.
- Sheets come in three sizes (`SheetPolicy.Height`): content height (`FIT_CONTENT`, the pass, the links, the lesson
  sheet, the month sheet, the friend picker), 82 % of the window (`TALL`, the bell's sheet, the sport lesson sheet) or
  the whole height below the status bar (`FULL`, no key uses it now); none goes under the status bar.
- Tabs switch by a tap on the bar only, with a fade through; Back is the platform's predictive Back
  ([Tab switching and Back](#tab-switching-and-back)).

## The M3 Expressive kit

`designsystem.kit` (`shared/designsystem/.../kit/`) is the kit of the redesigned Android screens, ported from the
design lab: Material 3 Expressive, Android's only, so its previews are captured in the four Material appearances and
never in the iOS ones (`materialOnlyPackageTrees` of the kit's screenshot test); iOS draws the same screens in
SwiftUI ([ADR 0031](decisions/0031-swiftui-ios-screens.md)). Its components take formatted text, icons and colours;
what a lesson says and which pills it gets is decided by shared presentation: `core/lesson/LessonTimelines` builds
`LessonUi` and `TimelineItem` (states, pills, gaps of an hour or more, progress, change notes) for every lesson list;
a lesson is online by MyITMO's remote format or by a placeholder room (`дистант`, `онлайн`, `DayLesson.isOnline`),
and the kit's `LessonMapping.kt` draws them. The older components above serve the
screens that have not moved yet.

| Component | Use |
|---|---|
| `Tone`, `container()`, `onContainer()` | The semantic colour of a pill, tile or card: `Neutral`, `Primary`, `Tertiary`, `Success`, `Warning`, `Error`. The word carries the meaning |
| `LocalOnSheet`, `cardSurface()` | True inside a `SheetScaffold`: cards and groups step one surface up |
| `ItmoRootScreen`, `HeaderAction` | A tab root with the static root header (optionally a title menu, `Сентябрь ▾`) and an optional FAB; the body is clipped at the header's edge, so nothing scrolled shows above it; an icon action with an optional badge |
| `ItmoDetailScreen` | A pushed screen: back, the title (`titleMedium`) and its quiet subtitle as one paragraph that wraps in full, actions; static |
| `TonalButton`, `tonalButtonColors()` | The kit's tonal button in the primary family (`primaryContainer`): an active secondary action in light and dark (`Записаться` in a sport row, `Повторить`, an action card's fix); `ProgressButton`'s tonal style uses the same colours. Material's `secondaryContainer` default reads as a disabled grey pill under the app's schemes |
| `SmallButton` | An extra-small filled M3E button with a short label next to a hero's big text (`Войти` on the Сегодня hero) |
| `ItmoTheme.progressTrack` | The track of every progress bar, meter, scale and ring (`ItmoWavyProgress`, `GradeScale`, `ScoreRing`, `PointsMeter`, the score rows): the surrounding content colour (the card's "on" colour, `onBackground` on the screen) at alpha 0.16, a groove in the container's hue in light and dark |
| `ItmoCard`, `IconTile` | One topic per card (`cardContent`), a tonal card, the one hero (`cardHero`); an icon in a tone's tile |
| `StatusPill`, `PillSpec` | `идёт`, `далее`, `изменена`, `отменена`, `очередь 2 из 5`, `записаны`, `онлайн`; `solid` for `идёт` |
| `SegmentedChoice` | The one choice pattern for two to four options and in-screen tabs: connected toggle buttons, a check on the chosen one up to three options |
| `ListGroup`, `ListRow`, `SwitchListRow`, `ListGroupNote` | Connected groups of rows: icon tile or avatar, title, subtitle, lines below it, count, value, accessory, chevron, destructive; a switch row; a caption |
| `GateCard`, `ActionCard`, `LockNote`, `StaleContent` | Gated (what, why, one fix), worth doing, locked without a fix, old data dimmed under its date; empty and error stay `ContentState` |
| `LessonView`, `LessonCard` | One lesson outside a day list: subject; `тип, преподаватель`; `аудитория, корпус` (none online); note; pills; friends; with `onClick` the card opens the lesson |
| `TimelineRow`, `LessonTimeline` | A day's lessons on the rail: a dot per lesson in its type's colour, the elapsed part in `primary` (one clock: it grows monotonically, so overlapping lessons get one `сейчас` marker), `Окно 2 ч 10 мин` rows, a change note and an `одновременно с «…»` note under a lesson; with `onLessonClick` each lesson is a target |
| `toLessonView`, `toTimelineRows`, `lessonTypeAccent` | The shared lesson presentation (`core/lesson`) drawn by the kit: texts resolved, pills toned (`идёт` solid `Primary`, `далее` `Neutral`, `изменена` and `очередь` `Warning`, `отменена` `Error`, `записаны` `Success`, `онлайн` `Neutral` with the camera), lesson types coloured |
| `SectionHeader`, `TextTile` | A section's title with a neutral count at the end; a short text (`+9`) in a tone's tile, the size of an `IconTile` |
| `ValueWithSource`, `PointsMeter`, `SportRing`, `Tone.ink()` | A row's number over its quiet source (`БАРС`, `My ITMO`, `таблица`; a warning colour when stale, tappable to its sources); points over a 0-100 bar in a tone; the sport points in a small ring of visits and bonus; text in a tone on a plain surface |
| `VotePill` | The votes of a link: ▲ N ▼, the viewer's own vote in `primary`, only the score when voting is not allowed |
| `SearchField`, `FilterChips`, `ChipSpec`, `OccupancyBar` | The search of a list as a pill with a clear button and a trailing action with a badge (more filters); filter chips in a sideways row or wrapped; places taken as a short bar and `22 из 24` in a tone |
| `WeekStrip`, `WeekStripDay` | One week as seven targets: weekday, date, a dot per lesson, today in `primary`, the chosen day on `primary`; a pager of these pages by weeks |

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
  then on every run verifies them. An
  iOS change re-records only `_ios-*` files; a changed Material PNG in the same
  diff is a bug. Accessibility checks assert 44 pt targets under `Ios`.
- **The JVM font trap.** JVM captures draw the iOS type with Robolectric's
  fallback font, not SF Pro, which is never committed or bundled. Glyph shapes
  and exact line breaks are checked only on the simulator:
  `scripts/ios/screenshots.sh <UITests class>` in light and dark, plus an AX1
  run, for every screen that gets iOS baselines. A text that wraps or clips
  with SF but not in the JVM capture is a finding of that screen; the
  `ios-narrow` baseline is the JVM's guard for long text.

### SwiftUI screens (iOS)

SwiftUI-owned screens (the shell, settings, sign-in, the first-run flow, the
web sign-in, the update offer) and the system surfaces (widgets, the Control,
quick actions) follow Apple's HIG for iOS 26: native controls and Liquid Glass
tinted with the shared tokens (`Tokens.generated.swift`: `ItmoColor`,
`Font.itmo`, `ItmoSpacing`), Dynamic Type text styles, no fixed sizes. The
target look is the design lab's (`vibe/design-lab/ux-native/DECISIONS.md`,
https://design.widgets.alllexey.dev). The Compose screens draw the kit's iOS
style (`ItmoPlatformStyle.Ios`); M3 Expressive stays Android's only.

- Shell. A glass tab bar with five labelled tabs (Зачётка, Расписание,
  Сегодня, Спорт, Профиль); pushed screens keep it and show the parent as
  back. The `QR-пропуск` accessory sits above the bar only while Сегодня is
  selected and opens the pass sheet.
- Root header. One line: the title (Title 2 weight) and the subtitle on one
  baseline on the leading side, the actions (the bell on Сегодня only) in the
  same row; the subtitle goes under the title when both do not fit, the title
  never truncates. Static: it never collapses, the content scrolls under it.
  `.itmoRootHeader(title:subtitle:actions:)`.
- Pushed header. The system bar keeps back and the toolbar items; under it the
  title and the subtitle flow as one paragraph that wraps in full (a long
  subject name), static. `.itmoDetailHeader(title:subtitle:)`.
- Sheets. A SwiftUI `.sheet` with the grabber, an inline title and the close
  button; content scrolls. Three heights (`ItmoSheetHeight`): the content's
  (short content is never cut), 0.82 of the screen draggable to full, or full
  below the status bar; never under it. A Compose sheet's content is hosted in
  a SwiftUI `.sheet`, never a Compose `ModalBottomSheet`.
- Layout. Side margin `ItmoKit.margin` (16), `ItmoKit.gap` between cards,
  `ItmoSectionHeader` before a section (closer on a sheet), at most one hero
  card (`ItmoCard(tone: .primary, hero: true)`), sets of rows in an
  `ItmoListGroup` (separators inset 56 under icons, 68 under 40 pt avatars).
  Cards and groups use iOS 26's inset grouped corner, 26 pt.
- Choices. `ItmoSegmentedChoice` (the native segmented picker) for two to
  four options and in-screen tabs; filters are chips; more options a list or a
  sheet. One prominent button per area (`ItmoProminentButton`: `.glassProminent`
  in the primary role with an `onPrimary` label, readable in light and dark;
  `.controlSize(.small)` next to a hero's big text, `Войти` on Сегодня),
  others `.glass` or `.bordered`.
- Lessons. A day's list is `ItmoLessonTimeline`: a rail with a dot per lesson
  in its type colour, the elapsed part in the accent colour, the now marker,
  gap rows for an hour or more, the change note under the lesson. A single
  lesson is `ItmoLessonCard`. Both say subject; type, teacher; room, building
  (no line when online); the note; pills.
- Filters (`ItmoFilters.swift`): `ItmoSearchField` (in the content, with a trailing action and its badge),
  `ItmoChips`, `ItmoWeekStrip` (a paging scroll view of them pages by weeks), `ItmoSwitchRow` (a group's switch row)
  and `ItmoOccupancy`, the twins of Android's kit.
- Numbers (`ItmoScores.swift`): `ItmoValueWithSource` (a number over its quiet source), `ItmoProgressBar` (a
  4 pt capsule in place of `ProgressView`, read as one by VoiceOver), `ItmoPointsMeter`,
  `ItmoSportRing`, `ItmoGradeScale` (the hero's 100-point bar with the grade ticks) and `ItmoVotePill`, the same
  pieces as Android's kit. Every track is the environment's `itmoTrack`: a tinted `ItmoCard` sets its tone's colour
  at `ItmoKit.trackOpacity` (0.16), the screen and neutral cards the label colour at it, so a track is a groove in its
  container's hue, never the system grey on a light wash.
- States. Content; empty and error are the system's unavailable view with one
  glass button (`Повторить` for errors); a gate (`ItmoGateCard`) says what,
  why and one fix in place of the content; `ItmoActionCard` is something to
  do; `ItmoLockNote` is closed with no fix; stale content stays dimmed under
  its date (`ItmoStale`).
- Tones (`ItmoTone`): neutral (secondary text), primary, tertiary, ok
  (`success`), warn (`warning`) and bad (`error`), all from the shared roles.
- Icons: SF Symbols through `AppSymbol` (generated from `docs/design/icons.tsv`)
  on SwiftUI and system surfaces; Material Symbols Rounded in Compose
  ([ADR 0028](decisions/0028-strings-and-icons.md)).
- Chrome rule: a Compose route draws its own top bar and hides the navigation
  bar (edge swipe back stays on); a SwiftUI screen keeps the native bar.
- Tabs switch by the native tab bar only, no swipe.

Details: [iOS app](ios.md#design-system).

## Expressive components

Android screens use the Material 3 Expressive look
([ADR 0021](decisions/0021-m3-expressive-order.md)) through the `Itmo*`
wrappers only; a new screen follows the rules below.

- **Intensity: Foundational.** The quiet surfaces stay. Emphasis comes from
  type, shape and motion on a few elements, never from colour fills.
- **At most two hero moments per flow.** Only a hero gets expressive motion,
  wavy progress, emphasized display type or a `MaterialShapes` mask (the
  profile avatar only). A list row never does. The heroes:
  - the QR pass: a code that arrives while the screen is open grows and fades
    in on the hero motion scheme (`QrPassSheetContent`);
  - the lesson in progress on home, the home flow's second hero: its elapsed
    share is `ItmoWavyProgress`; every other schedule row keeps plain type and
    no progress;
  - the sport score card of `Моё`: the ring of visits and the counted bonus;
  - the subject page's hero card: the points in `displayMedium` over the
    grade scale;
  - the person profile hero: `ItmoHeroAvatar` under the nine-sided cookie and
    the name in emphasized `titleLarge`, revealed when the person arrives
    while the screen is open (`ProfileHeroReveal`).

  A hero that is already there on the first frame, and every hero under
  reduced motion, shows at once; scrolling it away and back never replays it.
  Onboarding, sign-in, settings, the links and reviews sheets and the schedule
  tab have no hero.
- **Loading.** `ItmoLoadingIndicator` for a wait shorter than about 5 s outside
  a button (a section, a sheet, a dialog: the lesson sheet's friends, the
  privacy audiences, the sign-in session check), the contained one in
  pull-to-refresh (`AppRefreshBox`). Both are the app's
  `ItmoMorphingIndicator`: Material's `LoadingIndicator` size and colours,
  morphing through `ItmoLoadingShapes` (Material's sequence without the
  odd-symmetric Cookie9Sided and Pentagon) about the shape's centroid, every
  650 ms one shape and 112.5 degrees on one eased curve `(0.38, 1.21, 0.22, 1)`
  (the web indicator's), without Material's spring snap. It opens resting on
  its first shape; under reduced motion it stays on it. In pull-to-refresh the
  pulled shape is drawn at the turning shape's size, so the handoff keeps size
  and angle. Never inside a button: `ProgressButton` keeps its small circular
  indicator, and an inline wait that must keep its slot (the services opt-in
  switch) keeps `ItmoActivityIndicator`. First loads keep the skeleton.
  Determinate progress in dense rows (recordbook scores, the people search
  load-more edge, the My ITMO page load) stays a flat linear bar.
- **Tabs and toggles.** In-screen tabs are a `SegmentedChoice` (`Моё` /
  `Запись`, the subject's tabs). A segmented toggle becomes
  `ItmoButtonGroup`, single-select with a selection required (the friend
  picker's scope); so may a single-select chip set of up to four options.
  Filter chips stay chips (link categories, review subjects),
  and a choice in a dialog or with second lines stays radios. Detail screens
  get no tabs.
- **Buttons and toolbars.** A split button only where a primary action has a
  menu of variants; none does today (the `.ics` sheet is a choice of ranges,
  each showing its days). A floating toolbar only for more than two actions,
  never docked together with the navigation bar; top bars with up to two
  actions stay top bars. The schedule's FAB pair stays two stacked FABs (below).
- **Motion in spring terms.** Components take `MotionScheme.standard()`;
  `MotionScheme.expressive()` only for hero moments; fixed tweens become
  springs (the schedule's scroll-to-top FAB, the sport week strip and score
  collapse, the onboarding step change); contextual screens and sheets follow
  predictive back. Under reduced motion the end state shows at once.
- **Shapes.** Connected groups already have the expressive connected-list shape
  (`largeIncreased` 20 dp outside, `extraSmall` 4 dp inside) and keep it.
- **Flexible bars.** `ShortNavigationBar` is the bar. Both headers are the
  kit's own, static (header lab variant 7): the large flexible bar puts actions
  in a row above the title, and the medium one truncates a long subject name. Expressive list items, the `SearchBar` state and
  `FlexibleBottomAppBar` are not used yet. The material3 alpha line
  (`1.12.0-alpha03` over Jetpack `1.5.0-alpha22`) is approved for production
  behind the wrappers.
- **Out of the look.** Widgets, the quick-settings tile, shortcut and
  notification icons keep their own palette; WebViews and the ITMO.ID page stay
  as they are.
- **Fixed choices.** Static colours below Android 12 and on iOS use the brand
  blue `#4984E2` (TonalSpot); widgets keep `#6750A4`. The schedule keeps two
  stacked FABs, not a FAB menu; Сегодня has the one extended `Пропуск` button.
  Navigation labels show on every tab.

## Verification

Screens and kit components are checked by JVM screenshot baselines in all
recorded appearances, with accessibility checks on every capture; an emulator
is used only for what the JVM cannot render (widgets, the tile, WebView,
notifications). Commands and re-recording: [testing.md](testing.md#screenshot-tests).
A look change (a token, a kit component) re-records the affected baselines in
one PR; the diff is the review. Before a release, check light, dark and a
dynamic palette, font scale 1.0 and 1.3 on a narrow width, long Russian names
and every state (loading, content, empty, error) on a device.

## System surfaces

The surfaces the system draws stay Views with their own look; the expressive
rules do not apply to them.

- Widgets: RemoteViews with their own launcher-adapted palette (the
  `widget_*` colours: the wallpaper's on Android 12+, the M3 baseline
  `#6750A4` below); on Android 12+ they follow the launcher's widget corner
  radius. Rules in [Widgets](features/widgets.md).
- The quick-settings tile icon `res/drawable/ic_tile_qr.xml` (Material Symbols
  `qr_code`, white, no theme tint: SystemUI colours tile icons and resolves no
  app theme attributes).
- The shortcut icons `res/drawable/ic_shortcut_qr.xml` and
  `ic_shortcut_today.xml`: adaptive icons without a tint (white background like
  the launcher icon, `qr_code` and `schedule` 36 dp in `#4984E2` in the safe
  zone of the 108 dp foreground); notification icons are system-tinted masks.
- The WebView slots of the ITMO.ID and BARS sign-in pages and the spoiler
  image crop screen keep the View theme around content the app does not draw.

## Reference implementations

Compose screens that show a rule of this guide best; the kit parts they use are
in [Shared components](#shared-components).

- Schedule day: the feed of `ui/feed/ScheduleFeedScreen.kt` in `:shared:feature-schedule`, a sticky day header over
  the kit's `LessonTimeline`.
- Sport: `SportScreen` in `feature/sport/ui/` of `:shared:feature-sport` on the M3 Expressive kit: `Моё` with the
  score ring card (the flow's hero), bookings as `LessonCard`s under day labels and the `Мои секции` row; `Запись`
  with `SearchField`, `FilterChips`, the paging `WeekStrip` and dense `ListRow`s with `OccupancyBar` and one action;
  `SportLessonSheetContent`, `SportFiltersSheetContent` and `SportJoinSheetContent` on `SheetScaffold`;
  `SportSectionsScreen` and `SportPointsScreen` on `ItmoDetailScreen`.
- Settings and profile groups: `SettingsScreen` in `:shared:feature-settings`
  (`feature/settings/ui/SettingsScreen.kt`) and `MeScreen` in
  `:shared:feature-account`, on the kit's `SettingsGroup` and `SettingsRow`.
- QR pass: the pass sheet `QrPassSheetContent` in `:shared:feature-qr`
  (`feature/qr/ui/`), the hero reveal of [Expressive components](#expressive-components).
- Recordbook: `RecordbookScreen` in `feature/recordbook/ui/` of `:shared:feature-recordbook` on the M3 Expressive
  kit: the root header with the period menu, `SegmentedChoice` of the moment, the session's hero, sections of
  `ListGroup` rows (name and reason or kind, the 8 dp new-mark dot, the number in `ValueWithSource`, a chevron), the
  quiet BARS row; `RecordbookSourcesSheetContent` on `SheetScaffold`.
- Subject page: `RecordbookSubjectScreen` (`ui/subject/`) on `ItmoDetailScreen`: the tabs as `SegmentedChoice`, the
  hero card with the kit's `GradeScale` and the connected sheet, checkpoint groups of `ListGroup` rows with a thin bar,
  teacher rows with an `Avatar`, link rows with the kit's `VotePill`, the gates as `GateCard`.
- The «Мои баллы» sheet: `SheetScoresSheet` of `:shared:feature-recordbook`
  (`ui/sheets/SheetScoresSheet.kt`) on the kit's `SheetScaffold` (handle, title,
  subject, one bounded area of at least 288 dp for loading, failures and the
  choices; choice rows with the title in `bodyLarge`, the caption in
  `bodySmall`, the value in `titleSmall` at the end, `ic_check` in `primary`
  and `selected` for the current total; the whole row is the target), hosted
  by `app/shell/entries/RecordbookEntries.kt`.
- The `.ics` sheet: `IcsExportSheetContent` (`:shared:feature-settings`,
  `feature/settings/ui/ics`) (handle, title,
  subtitle, one area of at least 288 dp for the ranges, loading, the file,
  an empty range and failures; the ranges are connected-group rows: title
  `bodyLarge`, days `bodySmall`, chevron), hosted by
  `app/shell/entries/SettingsEntries.kt`.
- Link sheets: `SubjectLinksSheet` (connected groups per category),
  `LinkEditorSheet` and `LinkActionsSheet` in `:shared:feature-resources`
  (`feature/resources/ui/`); rows are the kit's `LinkRow`.
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
  `primary` or a muted `Не подтверждён`; on the right the kit's vote pill
  centred on that column; 12 dp under the footer,
  16 dp under a row that ends with its text), `OwnTeacherReviewRow.kt` (`мой`,
  a status pill as a 12 % wash of its tone: content-based palettes make
  `…Container` colours too dark for the tone as text).
- AI summary: `feature/social/ui/reviews/TeacherSummaryCard.kt` (`Card.Content.Tonal`, a
  header with `auto_awesome` and a muted `ИИ`, the tone row with the dot, pros
  and cons as icon rows, tag chips and five scales
  collapsed behind the text button `Подробнее`/`Свернуть` whose 48 dp target
  reaches into the card padding).
- Tone dot: the kit's `ToneDot`, in the teacher row of the kit's
  `DetailsHeader` (`DetailsTeacher.tone`) in the lesson sheet (the subject
  page says the tone in words instead); its place is reserved (`reserveTone`) where a
  late dot would otherwise move the row.
- Review editor: Compose in `:shared:feature-reviews` (`feature/reviews/ui/ReviewEditorSheet.kt`),
  hosted by `app/shell/entries/ReviewsEntries.kt`. A form sheet without a handle
  that cannot be dragged or dismissed outside: Back and the close button ask
  `Не сохранять отзыв?` only when something changed. The title has a second line
  with the teacher's short name; subject suggestions are filter chips that mark
  the picked one.
- Clickable teacher fact in details headers: the kit's `DetailsHeader`; only a
  teacher with an id gets `onClick`, and with it the chevron, the touch target
  and the TalkBack action.
- Avatar image failure: the kit's `Avatar` shows the initials until the photo
  loads and whenever it fails; each URL is its own request, so a late failure
  of an earlier URL never replaces a newer photo.
- Расписание: Compose in `:shared:feature-schedule` (`ui/feed/ScheduleFeedScreen.kt`) on the M3 Expressive kit:
  `ItmoRootScreen` with the title menu (`Сентябрь ▾`), a pager of `WeekStrip`s, sticky day headers over the
  `LessonTimeline`, quiet `пар нет` and weekend rows on `surfaceContainerLow`; the month sheet, the friend picker
  (`ListGroup` rows with avatars, a hidden schedule at half opacity) and the lesson sheet (the big room in emphasized
  `displayMedium` in `primary`, action tiles with the first filled, `ListGroup`s) in `SheetScaffold`; see
  [Schedule](features/schedule.md).
- Сегодня: Compose in `:shared:feature-home` (`feature/home/ui/TodayScreen.kt`) on
  the M3 Expressive kit: the hero `ItmoCard(tone = Primary, hero = true)` (the
  label and the countdown in `labelLarge`, the subject in emphasized
  `headlineSmall`, the room in emphasized `displayMedium` beside the full
  address in `titleMedium`, `ItmoWavyProgress` for the lesson in progress, or
  emphasized `displaySmall` `Онлайн` with the extra-small `SmallButton`
  `Войти` next to it, at least a `content` gap apart), a
  `SectionHeader` with the count over the `LessonTimeline`, `ActionCard`s in
  `Tone.Warning` under `Нужно сделать`; see [Сегодня](features/home.md#сегодня).
- The bell's sheet: `BellSheetContent` (`feature/home/ui/BellSheet.kt`) on the
  `SheetScaffold`: `SectionHeader`s `Новое` (with the count) and `Раньше` over
  `ListGroup`s of event rows (a 40 dp lead: a mark's `TextTile` `+9` in
  `Success`, an `IconTile` of the change in `Warning` or of the queue in
  `Success`/`Error`, the person's `Avatar`; the title in `bodyLarge`, semibold
  with an 8 dp `primary` dot when new; the details in `bodyMedium`; `было`
  struck through in `onSurfaceVariant` over `стало` in `warning`; small
  `Принять` and outlined `Отклонить`), `Ничего нового` with `ic_check_circle`
  in `success` when nothing is new, and the `Настройки уведомлений` row last.
- Schedule changes in the schedule: on the lesson itself in the timeline (`изменена`, the old room struck through, the
  `Изменено вчера в 20:14` note); in the lesson sheet the `Изменения` group, one `было → стало` row per field (the old
  value struck through in `onSurfaceVariant`, the new one in `warning`), in `ui/lesson/LessonSheet.kt` of
  `:shared:feature-schedule`.
- Visual tests: every module's goldens under `<module>/screenshots/`, for
  example the `RecordbookScreen_*`, `RecordbookSubjectScreen_*`, `Subject*` and
  `SheetScoresSheet_*` goldens of `:shared:feature-recordbook`.
