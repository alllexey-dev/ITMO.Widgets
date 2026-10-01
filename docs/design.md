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

The tone of a teacher's AI summary is such a stable colour of meaning:
`teacher_level_*` from red to green (`very_negative`, `negative`, `mixed`,
`positive`, `very_positive`, light `#D32F2F`, `#E06C00`, `#B58900`, `#689F38`,
`#2E7D32`, dark `#FF6E6E`, `#FFA24C`, `#FFD54F`, `#AED581`, `#4CAF50`),
only harmonized towards the primary colour (`core/ui/TeacherLevelTone.kt`), so
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
| `Card.Content.Tonal` | 20 dp radius, no stroke, `colorSurfaceContainerHigh` | The AI summary of a teacher's reviews above the reviews |
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
- The rows: one surface, `colorSurfaceContainerHigh`, 2 dp apart
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
- User-visible strings live in resources and are Russian. Remote names are
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
  and close with a trailing icon button. The lesson in progress is told
  by its time in `colorPrimary`, a filled badge and a thin progress line under
  the row, never by a filled row. Pull-to-refresh keeps the
  cards; one snackbar reports a partial failure. The `Мой ИТМО` FAB stays at the
  bottom end and the list reserves space under it.

## Shared components

Keep XML, Material components and the current architecture. The shared layer is
deliberately small: card variants, named dimensions, refresh helper, content-state
styles, the accessible selection row (`bindSelectionAccessibility`), the user row
(`item_user_row.xml`), the contextual screen header and the details-sheet header
(`view_details_header.xml`: title, kind, date with the time range and duration,
teacher, flow, place, map button) that every bottom sheet with a session starts with. Shared helpers belong to
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

By default the visual suites run in the light appearance only and write no
screenshots, which keeps the suite short. Always target the emulator explicitly
and preserve installed APKs/data with
`-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`; UTP otherwise
uninstalls its APKs after a run. Never use the user's phone or enable automatic
uninstallation of an incompatible APK. Two instrumentation arguments switch the
full checks on:

- `appearanceMatrix=full` runs each visual test in all four appearances: light;
  dark; font scale 1.3 with a dynamic seed on a 320 dp width; dark with font
  scale 1.3 and another seed on a 320 dp width. Every assertion runs for every
  appearance.
- `captureScreenshots=true` saves the PNGs under
  `/sdcard/Android/data/dev.alllexey.itmowidgets/cache/<suite>-screenshots/`
  (the profile and social suites use `files/` instead of `cache/`).

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Pandroid.testInstrumentationRunnerArguments.appearanceMatrix=full \
  -Pandroid.testInstrumentationRunnerArguments.captureScreenshots=true
```

The same run through `adb`, for one class:

```bash
adb -s emulator-5554 shell am instrument -w -e appearanceMatrix full -e captureScreenshots true \
  -e class dev.alllexey.itmowidgets.feature.onboarding.OnboardingVisualTest \
  dev.alllexey.itmowidgets.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 pull /sdcard/Android/data/dev.alllexey.itmowidgets/cache/onboarding-screenshots
```

Before calling a UI change done, run the affected suites with both arguments
and look at the PNGs; the default run only proves the layout holds in light.
`Screenshots` exports PNGs to Gradle's
`app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected/`.
Copy a completed suite's screenshots into ignored `vibe/` before another
connected run can replace that output directory.

Preview hosts set `delegate.localNightMode` before attaching their base context;
setting it in `onCreate` can trigger an extra recreation and consume one-shot
feedback before a test observes it. Assert the effective font scale, night mode,
width and seeded palette, not just fixture values. Capture after layout/frame
commit and completed snackbar animations; allow the SystemUI compositor to
settle when switching appearances. Wait for feedback dismissal after retry.
These are local host overrides: preserve the emulator's global theme/font
settings, and restore them if a separate test explicitly changes them.

## Reference implementations

- Schedule day: `res/layout/item_day_schedule.xml`, `feature/schedule/ui/DayScheduleAdapter.kt`.
- Sport cards: `res/layout/item_sport_lesson.xml`, `res/layout/item_sport_booking.xml`,
  details sheet `feature/sport/ui/common/SportCommonDetailsBottomSheet.kt`.
- Settings and profile groups: `res/values/styles.xml`, `feature/settings/ui/SettingsRenderer.kt`.
- Recordbook row: `res/layout/item_recordbook_subject.xml` (name, metadata, a number
  with a thin bar or a grade badge), `feature/recordbook/ui/RecordbookAdapter.kt`.
  The name and the dot `new_mark` for unread marks share a horizontal row
  centred vertically: the name takes the remaining width and wraps, the dot
  (8 dp `shape_circle_filled`, `colorPrimary`, `design_spacing_compact` before
  it) follows it like the new-change dot of `item_schedule_change.xml`, is set
  on every bind and adds `Новое` at the start of the row's TalkBack description.
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
- The «Мои баллы» sheet: `res/layout/sheet_scores_setup.xml` (handle, title,
  subject, one bounded area of at least 288 dp for loading, failures and the
  choices) with `res/layout/item_sheet_scores_option.xml` (title `bodyLarge`,
  caption `bodySmall`, value `titleSmall` at the end, `ic_check` in
  `colorPrimary` and `selected` for the current total; the whole row is the
  target), `feature/recordbook/ui/sheets/SheetScoresBottomSheet.kt`.
- Link sheets: `feature/resources/ui/SubjectLinksBottomSheet.kt` (connected
  groups per category), `LinkEditorBottomSheet.kt`, `LinkActionsBottomSheet.kt`,
  `res/layout/item_subject_link.xml`.
- User row: `res/layout/item_user_row.xml`.
- Person profile: `res/layout/fragment_user_profile.xml`,
  `feature/social/ui/UserProfileAdapter.kt`: the hero `res/layout/item_profile_header.xml`
  (`Card.Hero`: avatar, name, one short line, the ISU number with a copy symbol
  in a 48 dp target, the friendship badge or status and buttons), fact sections
  `res/layout/item_profile_facts.xml` with rows `item_profile_fact.xml`, the
  `ITMO.Widgets` group `res/layout/item_profile_sharing.xml` with rows
  `item_profile_entry.xml`, and the reviews heading `res/layout/item_profile_section.xml`.
- Teacher reviews: the own review as a group of its own, the others as one
  connected group, `res/layout/item_teacher_review.xml` (`subject, date` with
  `⋮`; the full text; a footer in `feature/social/ui/ReviewFooterLayout.kt`:
  who wrote it, a named author or the source as a link, otherwise
  `Анонимный отзыв`, then the verification pill `view_review_verified.xml` or a
  muted `Не подтверждён`, and the vote pill `view_link_vote_pill.xml` flush at
  the end on the first line), `res/layout/item_own_teacher_review.xml` (`мой`,
  a status pill as a 12 % wash of its tone: content-based palettes make
  `…Container` colours too dark for the tone as text), bound in `feature/social/ui/ReviewViews.kt`.
- AI summary: `res/layout/item_teacher_summary.xml` (`Card.Content.Tonal`, a
  header with `auto_awesome` and a muted `ИИ`, the tone row with the dot, pros
  and cons as icon rows `view_summary_point.xml`, tag chips
  `item_summary_tag_chip.xml` and five scales `view_summary_scale.xml`
  collapsed behind the text button `Подробнее`/`Свернуть` whose 48 dp target
  reaches into the card padding), bound in `feature/social/ui/SummaryViews.kt`.
- Tone dot: `res/drawable/bg_teacher_level_dot.xml` tinted by
  `core/ui/TeacherLevelTone.kt` (`ImageView.bindLevel`), in the lesson sheet's
  teacher fact (`fact_mark` in `res/layout/item_sport_detail_fact.xml`) and the
  subject page's teacher rows (`level_dot` in `res/layout/item_subject_teacher.xml`);
  its place is reserved where a late dot would otherwise move the row.
- Review editor: `res/layout/sheet_review_editor.xml`,
  `feature/reviews/ui/ReviewEditorBottomSheet.kt`. A form sheet without a handle
  that cannot be dragged or dismissed outside: Back and the close button ask
  `Не сохранять отзыв?` only when something changed. The title has a second line
  with the teacher's short name; subject suggestions are filter chips
  (`item_review_subject_chip.xml`) that mark the picked one.
- Clickable teacher fact in details headers: `res/layout/item_sport_detail_fact.xml`,
  `core/ui/DetailsHeader.kt` (`bindAction` resets chevron, ripple, touch target
  and accessibility action when the identifier is absent).
- Avatar image failure: `core/ui/AvatarView.kt` keeps current initials ready and
  guards the posted Glide fallback against a newer binding or successful load.
- Friend picker: `res/layout/dialog_friend_selector.xml`.
- Home feed: `res/layout/fragment_home.xml`, `res/layout/item_home_*.xml`,
  `feature/home/ui/HomeFeedAdapter.kt`, `feature/home/HomeFeedVisualTest.kt`.
  `res/layout/item_home_schedule_changes.xml` is a `Card.Content` that closes:
  a header with `ic_edit_calendar` in `colorPrimary`, the title, the count
  badge (`bg_home_badge`) and a trailing 48 dp close button, then the headline
  of the latest change wrapping without truncation; the whole card opens the
  history. `res/layout/item_home_marks.xml` has the same geometry with
  `ic_menu_book`, `Новые оценки`, the number of subjects and `Прочитано`; its
  body is the list of names, wrapping without truncation, and the whole card
  opens the recordbook.
- Schedule changes: the history row `res/layout/item_schedule_change.xml`
  (`Card.Content`, not clickable: subject with an 8 dp `colorPrimary` dot for a
  new change, the main line, one `item_schedule_change_line.xml` per changed
  field so a wrapped field never runs into the next, `вид · поток`; one TalkBack
  node), day titles `res/layout/item_schedule_change_day.xml`, the screen
  `res/layout/fragment_schedule_changes.xml` with
  `feature/schedule/ui/changes/ScheduleChangesAdapter.kt`. On lesson cards the
  mark `change_indicator` (`ic_edit_calendar`, 16 dp, `colorPrimary`) after the
  video-call icon in `res/layout/item_schedule_lesson.xml`; in the lesson sheet
  the block `changes_card` (divider, `Изменения`, `было → стало` lines) in
  `res/layout/fragment_lesson_details.xml` and the informational `flow_fact` row
  between teacher and place in `res/layout/view_details_header.xml`.
- Visual tests: `feature/sport/cards/SportCardsVisualTest.kt`,
  `feature/recordbook/RecordbookVisualTest.kt`, `feature/resources/SubjectLinksVisualTest.kt`,
  `feature/friendselector/SelectionRowsTest.kt`, `feature/social/UserProfileVisualTest.kt`,
  `feature/reviews/ReviewEditorVisualTest.kt`, `feature/schedule/ScheduleChangesVisualTest.kt`,
  `feature/recordbook/SheetScoresVisualTest.kt`.
