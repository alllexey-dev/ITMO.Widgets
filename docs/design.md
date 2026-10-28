# Design guide

One visual language across the application, grown from the screens the user
already likes: the schedule and the sport cards. Quiet surfaces, compact layout,
clear hierarchy, small meaningful accents. This is the contract for every
screen. Android screens are Compose (in each feature's `androidMain`): they take
every value from `ItmoTheme` in `:shared:designsystem` ([Tokens](#tokens)) and
their parts from its kits ([Shared components](#shared-components),
[The M3 Expressive kit](#the-m3-expressive-kit)) inside the [shell](#shell). iOS
draws its own SwiftUI screens on the same tokens ([iOS app](ios.md#design-system)).
Views remain only where the system or a library draws ([System surfaces](#system-surfaces)).

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
is the one source of design values for Compose: `colorScheme`, `extendedColors`
(lesson types, recordbook, sport, teacher levels, the kit's `success` and
`warning` roles), `shapes`, `spacing`, `typography` with `emphasizedTypography`,
and `motion`. The schema has the Material 3 Expressive shape from the start, so
component APIs do not change when the values do. The values themselves are in
the code and in the export below.

- Components and screens read tokens only through `ItmoTheme.*`. No raw colour,
  `.dp` spacing or font size outside `shared/designsystem`; experimental and
  expressive Material APIs only inside it, behind `Itmo*` wrappers.
- The type scale is pinned to MDC 1.13's `TextAppearance.Material3.*` values, so a
  material3 bump cannot change a screen's text. Components move on
  `MotionScheme.standard()`; `MotionScheme.expressive()` only for heroes
  (`ItmoTheme.heroMotionScheme`).
- `cardSummary` is 28 dp like `cardHero` while the PE sport card is its page's
  hero; if it stops being the hero it becomes 20 dp.
- `ItmoTheme(expressive = true)` is the default: the expressive variants of
  [Expressive components](#expressive-components) are on for every screen.
- Reduced motion: `rememberReducedMotion()` is true when the system animator
  duration scale is 0; a kit animation then shows its end state at once.
- Equality with the Android resources the widgets and system surfaces use is
  tested: `DesignTokensParityTest` (`:app`) keeps extended colours, spacing and
  shapes equal to `res/values` except its listed divergences, `ThemeParityTest`
  the dynamic and seeded schemes and the M3 baseline the widgets keep on API
  26-30, and `TypographyParityTest` the type scale.
- `shared/designsystem/tokens/itmo-tokens.json` exports the tokens (schemes by
  role, extended colours with their derivation rules, shapes, spacing, type,
  motion; `schemaVersion` grows with a breaking change). The iOS client reads it
  instead of copying values. It is generated, never edited or merged by hand:
  after a token change or a rebase run
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
black background, light or dark mode). `resolveColorScheme(spec, dark, wallpaper)` in
`designsystem/theme/ThemeSchemes.kt` is the one way from it to a scheme:
`ItmoTheme` without a `colorSource` reads `AppColorSource.current`, which the
Android app feeds from `AppearancePreferences.observeTheme()` at start. The
widgets take the same roles from core's `ThemeSpec.widgetPalette()` (the seeds
live in `core/settings/ThemeSeeds.kt`), which builds the scheme without
Compose; Android's `widgetPalette(context)` keeps the wallpaper's own scheme
where the app does ([widgets](features/widgets.md#theme-colours)).

`ThemeSpec.mode` (`Системная`, `Светлая`, `Тёмная`) is not part of the scheme:
it decides `dark`. Android applies it as AppCompat's night mode in
`ItmoWidgetsApplication` (read before the first activity starts, then followed),
so the activity's configuration carries it: `isSystemInDarkTheme()`, the
default of `ItmoTheme(dark = ...)`, the Views, the system bars and the WebViews
all read it; a change recreates the activities. iOS sets `preferredColorScheme`
on `ShellView`. Never read the phone's own mode for an app screen; the widgets
(`WidgetColors`, `observeWidgetTheme()` drops the mode) keep the launcher's,
and so do their previews in the gallery (`Context.inSystemNightMode()`,
iOS `systemColorScheme`; [widgets](features/widgets.md#previews)).

- The seed: `Как обои` the wallpaper's (`system_accent1_500`, the brand blue
  without one), `Фирменный` the brand blue `#4984E2`, `Свой цвет` the user's,
  and six presets around the brand blue (`ThemeSeeds.kt`). TonalSpot keeps only
  a seed's hue, so no grey preset is offered; `Монохром` is the grey look.
- The scheme: MaterialKolor's `dynamicColorScheme` from the seed with the
  style's `PaletteStyle` and the contrast level, 2021 spec. `Спокойная` at
  `Обычный` is the brand scheme's recipe; `Как обои` at `Спокойная` and
  `Обычный` is the system's dynamic scheme unchanged, any other style or
  contrast rebuilds it from the wallpaper's seed.
- `Чёрный фон` sets `background`, `surface`, `surfaceDim` and
  `surfaceContainerLowest` to black in the dark scheme only; the containers
  keep their tones, so cards stay apart from the page.

Every style and contrast keeps `onSurface` on `surface` at 4.5:1 or more (7:1
at `Высокий`) and the on-primary pairs at 3:1 or more (`ThemeSchemesTest`).
The View screens that remain, the QR tile, notifications and the launcher
icon keep their colours. `AccentSamplesPreview`, `ThemeStylesPreview` and
`ThemeContrastPreview` show a sample screen across seeds, styles and contrasts.

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
`positive`, `very_positive`), only harmonized towards the primary colour (`teacherLevel*` in
`ItmoTheme.extendedColors`, drawn by the kit's `ToneDot`), so every palette
keeps five distinct tones. The dot is decorative; its row says the tone in
words.

## Geometry and the card family

The grid is 4 dp; the margins, gaps and paddings are `ItmoTheme.spacing`, cards
have elevation 0 dp. Compactness never shrinks the 48 x 48 dp touch target.

The card family below names the variants the docs and the kit's KDoc refer to;
their shapes are `ItmoTheme.shapes` tokens.

| Variant | Shape | Use |
|---|---|---|
| `Card.Content` | `cardContent` 20 dp, `surfaceContainerLow`, no stroke | Subject, control and user rows |
| `Card.Content.Outlined` | 20 dp, 1 dp `outlineVariant` | Sport lesson and booking cards, debug cards |
| `Card.Content.Tonal` | 20 dp, `surfaceContainerLow`, no stroke | The AI summary of a teacher's reviews above the reviews |
| `Card.CompactSummary` | `cardContent` 20 dp | The recordbook summary row |
| `Card.Summary` | `cardSummary` 28 dp, 20 dp padding | The sport score card of `Моё` and the formula of `Откуда баллы` (`ScoreCard`, `ItmoCard(hero = true)`) |
| `Card.Hero` | `cardHero` 28 dp, `surfaceContainer`, no stroke | The subject result and the person on a profile, above the page's connected groups |
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
  outline. Others' links and reviews carry the one vote pill, like and dislike
  thumbs around the score (`VotePill`: a 32 dp pill inside 48 dp targets).
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
  file (`composeResources/values/strings_<file>.xml`; shared ones in
  `:shared:core`); one key names a string on every platform. Remote names are
  trimmed at the mapper.
- Copy is short and says what the user needs, not how the app works: no
  instructions for standard gestures (pull to refresh, tap), no descriptions or
  footers that repeat the title, no internals (matching rules, token handling,
  sync mechanics) outside the consent and privacy screens.
- Fixed names: the university site is `My ITMO`; the project server and its
  settings page are `Сервер ITMO.Widgets` (not "сервисы").
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
  the root header ([Shell](#shell)); pushed screens use `ItmoDetailScreen`
  (back, the title and its subtitle). `AppTopBar` stays only on the WebView
  screens with a close button (My ITMO, the web sign-in).

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
- A value on screen that a slower source is about to replace (My ITMO's points
  while the first BARS answer of the period runs) pulses like the skeleton in
  place (`LoadingPulse`, iOS `itmoLoadingPulse`) and keeps its geometry, so the
  answer changes the number without moving anything.
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
- Snackbars go through the kit's `ItmoSnackbarHost`: 2 s for a plain confirmation, 5 s with an action,
  whatever the call asks; an indefinite one waits for its answer.
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
  `Запись` of sport switch by their `SegmentedChoice` and by a swipe (a
  `HorizontalPager`); the sport week strip pages by weeks.
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
   between tabs on either platform; horizontal content in a screen (the sport
   pager, chip rows) scrolls or pages inside the screen.
2. A tab switch is an M3 fade through: the old root fades out in 90 ms, the new
   one fades in over 210 ms after it, growing from 92 % (`ShellMotion`). Each
   tab keeps its scroll, saveable state and view models across switches.
3. A pushed screen comes and goes on the shared X axis: both screens move a
   tenth of the width and fade through, on the standard scheme's spatial spring.
   It covers the bar, which stays in place under it.
4. Back on a pushed screen is Material's back preview (`ShellBack`, as MDC's
   main container draws it): under the finger the top screen shrinks to 90 %,
   moves towards the far side and rounds its corners to 28 dp over the already
   composed screen under it. On release it dissolves from where the finger left
   it (300 ms, 5 % smaller), whatever the progress, so a fling never ends in a
   blink; nothing fades while the finger is down. Back on another tab's root has
   no preview and fades through to Сегодня; Back on Сегодня's root leaves the
   app with the system's back-to-home animation.
5. Sheets and dialogs are their own windows with the system's predictive Back.
6. System gestures win: no gesture exclusion, the back edges stay the system's
   (`GestureRulesTest` in `konsist/`).
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
and baselines in all four Material appearances under
`shared/designsystem/screenshots/`. A screen uses the kit and grows it instead of
drawing its own variant.

| Component | Use |
|---|---|
| `AppTopBar`, `AppTopBarAction` | The WebView screens with a close button (My ITMO, the web sign-in): close, a title of up to two lines (one over an optional one-line subtitle, the page's host), trailing actions; every other pushed screen is `ItmoDetailScreen` |
| `ItmoNavigationBar`, `ItmoNavigationBarItem` | Root tabs: M3E's `ShortNavigationBar`, a label on every tab, the FILL 1 icon on the selected one |
| `ContentState`, `ContentStateLoading` | Full and compact loading, empty and error states with an optional action |
| `Skeleton` | First load without a cache, list or cards |
| `LoadingPulse` | A shown value that a slower source is about to replace |
| `AppRefreshBox` | Pull-to-refresh that the user asked for |
| `ProgressButton` | A button with its own progress, same size, second tap ignored |
| `ItmoSwitch` | An on/off switch; with no callback it only shows the state and the row carries the toggle |
| `ItmoActivityIndicator` | A small inline wait that must not change its slot (a switch's slot, a row) |
| `ButtonRow` | Two buttons side by side, stacked at full width when the labels do not fit |
| `Pill` | The `моя` badge, a count, a status in its tone over a 12 % wash |
| `ToneDot` | The review tone dot, an empty slot until the tone arrives |
| `Avatar` | A photo from the given URL through the host's image loader, initials otherwise: surname and given name by `personInitials`, from ITMO.ID's name parts when the caller passes `initials`; M3 Expressive's nine-sided cookie with the expressive switch, a circle otherwise |
| `Modifier.connectedGroupItem`, `GroupPosition`, `GroupSurface` | A row of a connected group, drawn by its position, on a screen or in a sheet |
| `SectionHeading`, `SectionSubheading` | The heading over a group; a sub-heading with its value |
| `GroupActionRow` | The last row of a group that leads further |
| `LinkRow`, `VotePill` | A link with its own badge; the one vote pill of links and reviews: like and dislike thumbs around the score, the own vote filled and in `primary`, only the score when voting is not allowed |
| `DetailsHeader` | The head of every details sheet |
| `SheetScaffold`, `SheetHandle` | A bottom sheet body: handle, header, one bounded content area (288 dp minimum where states switch), footer |
| `ConfirmDialog`, `ChoiceDialog`, `ReportDialog` | A confirmation, a single choice, reporting a review or a link |
| `InfoDialog` | A message with one button |
| `ItmoMenu`, `ItmoMenuGroup`, `ItmoMenuItem` | The one Android menu anchored to its parent, for actions and pickers: groups with separators and an optional label, two-line items with supporting text and Material's 12 dp around them, the current choice checked, destructive items in the error colour; no screen draws `DropdownMenu` itself |
| `ScoreRing` | The sport score ring |
| `GradeScale` | A 0-100 bar with grade ticks |
| `TimelineMarker`, `TimelineLine` | The schedule timeline |
| `StepsIndicator` | Progress dots of a flow |

Experimental and expressive Material components reach screens only through
`Itmo*` wrappers in `shared/designsystem` (`designsystem/components/expressive/`),
so a material3 line switch stays in the module. `ItmoTheme(expressive)` is the
one switch; heroes read `ItmoTheme.heroMotionScheme`. Where each is used:
[Expressive components](#expressive-components).

| Wrapper | Use |
|---|---|
| `ItmoLoadingIndicator` | A wait shorter than about 5 s outside a button; `contained` in pull-to-refresh |
| `ItmoButtonGroup` | A single choice of two to four short options with a selection required; under expressive the kit's `SegmentedChoice` |
| `ItmoWavyProgress` | Determinate progress of a hero only, linear or circular |
| `ItmoMarkTile` | The app mark's tile on the sign-in screen, the twelve-sided cookie |
| `ServicesConsentSheet`, `StoredDataGroup` | The consent before connecting to ITMO.Widgets and the list of what the server keeps (`ServicesStoredData`), shared by «Сервер ITMO.Widgets» and the first-run flow |
| `ItmoSplitButton` | A primary action with a menu of its variants |
| `ItmoFabMenu` | A FAB that opens a menu of actions above it |
| `ItmoFloatingToolbar` | More than two icon actions floating over the content, never docked with the navigation bar |

Sheets and dialogs are overlay scenes of the Navigation 3 shell that render a
`SheetScaffold` body. Shared helpers that are not
drawing (texts, formatters) belong to `:shared:core`; screen-specific parts to
the feature's `ui/`. Extract only rules that genuinely repeat; do not build a
universal renderer.

## Shell

The Android shell (`app/shell/`, [architecture](architecture.md#navigation)) is the frame every screen sits in.

- Five tabs in this order, close to My ITMO: Зачётка, Расписание, Сегодня (the centre and the start tab), Спорт,
  Профиль (`AppTab`, which also names each tab's label key and its outlined and FILL 1 icons: `book_2`,
  `calendar_month`, `today`, `sprint`, `person`). The bar is `ItmoNavigationBar` (M3E `ShortNavigationBar`) with
  labels.
- Every tab root has the root header (`ItmoRootScreen`, `app/shell/entries/TabRootFrames.kt`), one line: the title (`titleLarge`) and its subtitle (`bodyMedium`) on one baseline on the
  left, the actions in the same row, centred on the title block; 56 dp. When the subtitle does not fit next to the
  title (a narrow window, a large font) it goes under it; the title never truncates, the header grows instead. It is
  static: it never collapses, the content scrolls under it. Сегодня shows the date
  (`понедельник, 7 сентября`, `TodayHeaderViewModel`) and the bell with the count of unseen events, which is in this
  header only and opens the bell's sheet ([the bell](features/home.md#the-bell)). Every other tab draws its own root
  header (Расписание the month `Сентябрь ▾` over the week, Зачётка the period, Спорт and Профиль their actions).
- The `Пропуск` extended FAB is on Сегодня only and opens the pass sheet; the widgets, the tile and the shortcut open
  the same sheet.
- Pushed screens cover the bar: back, a title, no navigation bar. The bar stays in place under them, so the
  predictive Back uncovers the root with its bar. The title and its subtitle flow as one paragraph
  (`ItmoDetailScreen`, 64 dp in one line) and a long title wraps in full, never truncated.
- Sheets come in three sizes (`SheetPolicy.Height`): content height (`FIT_CONTENT`, the pass, the links, the month
  sheet, the friend picker), 82 % of the window (`TALL`, the bell's sheet, the sport lesson sheet) or the whole height
  below the status bar (`FULL`, the lesson sheet, so the friends arriving later never resize it); none goes under the
  status bar.

## The M3 Expressive kit

`designsystem.kit` (`shared/designsystem/.../kit/`) is the kit of the redesigned Android screens, ported from the
design lab: Material 3 Expressive; iOS draws the same screens in SwiftUI ([ADR 0031](decisions/0031-swiftui-ios-screens.md)). Its components take formatted text, icons and colours;
what a lesson says and which pills it gets is decided by shared presentation: `core/lesson/LessonTimelines` builds
`LessonUi` and `TimelineItem` (states, pills, gaps of an hour or more, progress, change notes) for every lesson list;
a lesson is online by MyITMO's remote format or by a placeholder room (`дистант`, `онлайн`, `DayLesson.isOnline`),
and the kit's `LessonMapping.kt` draws them. Both kits are current: a screen takes a part from `kit` where it has
one, otherwise from [Shared components](#shared-components).

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
| `ListGroup`, `ListRow`, `SwitchListRow`, `PersonListRow`, `ListGroupNote` | Connected groups of rows: icon tile or avatar, title, subtitle, lines below it, count, value, accessory, chevron, destructive; a switch row; a person with a 40 dp avatar, the one row for people in every list; a caption |
| `GateCard`, `ActionCard`, `LockNote`, `StaleContent` | Gated (what, why, one fix), worth doing, locked without a fix, old data dimmed under its date; empty and error stay `ContentState` |
| `LessonView`, `LessonCard` | One lesson outside a day list: subject; `тип, преподаватель`; `аудитория, корпус` (none online); note; pills; friends; with `onClick` the card opens the lesson |
| `TimelineRow`, `LessonTimeline` | A day's lessons on the rail: a dot per lesson in its type's colour, the elapsed part in `primary` (one clock: it grows monotonically, so overlapping lessons get one `сейчас` marker), `Окно 2 ч 10 мин` rows, a change note and an `одновременно с «…»` note under a lesson; with `onLessonClick` each lesson is a target |
| `toLessonView`, `toTimelineRows`, `lessonTypeAccent` | The shared lesson presentation (`core/lesson`) drawn by the kit: texts resolved, pills toned (`идёт` solid `Primary`, `далее` `Neutral`, `изменена` and `очередь` `Warning`, `отменена` `Error`, `записаны` `Success`, `онлайн` `Neutral` with the camera), lesson types coloured |
| `SectionHeader`, `TextTile` | A section's title with a neutral count at the end; a short text (`+9`) in a tone's tile, the size of an `IconTile` |
| `ValueWithSource`, `PointsMeter`, `SportRing`, `Tone.ink()` | A row's number over its quiet source (`БАРС`, `My ITMO`, `таблица`; a warning colour when stale, tappable to its sources); points over a 0-100 bar in a tone; the sport points in a small ring of visits and bonus; text in a tone on a plain surface |
| `SearchField`, `FilterChips`, `ChipSpec`, `OccupancyBar` | The search of a list as a pill with a clear button and a trailing action with a badge (more filters); filter chips in a sideways row or wrapped; places taken as a bar (88 dp, down to 44 dp in a tight row; the text never wraps) and `22 из 24` in a tone |
| `WeekStrip`, `WeekStripDay` | Past weekday/date labels are dimmed on both platforms; today and the selected day (including a past selection) keep full emphasis. One week as seven targets: weekday, date, a dot per lesson, today in `primary`, the chosen day on a `primary` indicator that slides on the scheme's default spatial spring while the colours crossfade (a week gaining or losing the choice fades it in place; snaps under reduced motion); a pager of these pages by weeks |

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

  A hero that is already there on the first frame, and every hero under
  reduced motion, shows at once; scrolling it away and back never replays it.
  Onboarding, sign-in, settings, the links and reviews sheets and the schedule
  tab have no hero.
- **Loading.** `ItmoLoadingIndicator` for a wait shorter than about 5 s outside
  a button (a section, a sheet, a dialog), the contained one in pull-to-refresh
  (`AppRefreshBox`). Both are `ItmoMorphingIndicator`: Material's
  `LoadingIndicator` size and colours, morphing through `ItmoLoadingShapes` on
  one eased curve without Material's spring snap; under reduced motion it stays
  on its first shape. Never inside a button (`ProgressButton` keeps its small
  circular indicator) and not where an inline wait must keep its slot
  (`ItmoActivityIndicator`). First loads keep the skeleton; determinate progress
  in dense rows stays a flat linear bar.
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
- **Motion in spring terms.** Fixed tweens become springs (the schedule's
  scroll-to-top FAB, the sport week strip and score collapse, the onboarding
  step change); connected groups keep the expressive connected-list shape.
- **Flexible bars.** `ShortNavigationBar` is the bar. Both headers are the
  kit's own and static: the large flexible bar puts actions above the title,
  and the medium one truncates a long subject name. Expressive list items, the
  `SearchBar` state and `FlexibleBottomAppBar` are not used. The material3
  alpha line is approved for production behind the wrappers.
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
  `widget_*` colours with the containers the new widgets use: the wallpaper's
  on Android 12+, the M3 baseline `#6750A4` below; the status tones of
  `очередь` and `записаны` are fixed); on Android 12+ they follow the launcher's widget corner
  radius. The cards widget adds lesson type chips (the type colour faint behind its name, the name darkened on
  light and lightened on dark) and M3 Expressive's wavy progress on the current lesson (ADR 0021, amended).
  Rules in [Widgets](features/widgets.md).
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

The screens that show a rule of this guide best; read them before building a similar one.

- Tab roots on the M3 Expressive kit: `TodayScreen` (`:shared:feature-home`, the hero and `Нужно сделать`),
  `ScheduleFeedScreen` (`:shared:feature-schedule`, the title menu, `WeekStrip` pager, sticky days over
  `LessonTimeline`), `SportScreen` (`:shared:feature-sport`, `Моё` / `Запись`, `SearchField`, `FilterChips`,
  `OccupancyBar`), `RecordbookScreen` (`:shared:feature-recordbook`, `ValueWithSource` rows).
- Pushed screens on `ItmoDetailScreen`: `RecordbookSubjectScreen` (tabs, the hero with `GradeScale`, `GateCard`),
  `UserProfileScreen` (`:shared:feature-social`, an empty title under a centred contact header), `SportPointsScreen`.
- Sheets on `SheetScaffold`: `BellSheetContent`, `SheetScoresSheet` (one bounded area of at least 288 dp for loading,
  failures and the choices), `IcsExportSheetContent`, `SubjectLinksSheetContent`, the lesson sheet (`ui/lesson/LessonSheet.kt`,
  the `Изменения` group with `было -> стало` rows).
- Settings: `SettingsScreen` (every page of `SettingsViewModel`, its rows through `SettingSectionView`: `ListGroup`,
  `ListRow` / `SwitchListRow` with the row's icon in a neutral tile, `SegmentedChoice`, `ListGroupNote`),
  `AppearanceScreen` (the light and dark sample, named colour circles, the palettes as a sideways row),
  `ConnectionScreen`, `NotificationSettingsScreen` and `DiagnosticsScreen` (`:shared:feature-settings`), all on
  `ItmoDetailScreen`.
- Reviews: `TeacherReviewRow`, `OwnTeacherReviewRow` (a status pill as a 12 % wash of its tone: content-based palettes
  make `...Container` colours too dark as text), `TeacherSummaryCard` (`Card.Content.Tonal`), `ReviewEditorSheet`
  (asks `Не сохранять отзыв?` only when something changed).
- `Avatar` shows the initials until the photo loads and whenever it fails; each URL is its own request, so a late
  failure of an earlier URL never replaces a newer photo.
