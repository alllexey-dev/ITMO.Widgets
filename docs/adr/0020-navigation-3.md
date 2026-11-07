# 0020 One Navigation 3 shell with `@Serializable` routes

**Amended (2026-10-14).** The predictive Back is the shell's own (`ShellBack`): `NavDisplay` 1.2 plays a predictive
pop's release as the rest of the gesture's seek timeline, so a long drag or a fling ended in a blink. The shell takes
the system gesture, draws Material's back preview on pushed screens only (tab roots pop with their fade through on
release, Сегодня's root goes home, sheets keep their own), and feeds `NavDisplay` the gesture held at 0, so the
release dissolve always runs its full 300 ms. Nav3 1.3's gesture and release handoff replaces this once stable.

**Amended (2026-10-13).** `NavDisplay` owns every screen of the tabs surface: its entries are the start tab's root,
the selected tab's root above it, the pushed screens, then the sheets and dialogs (`ShellBackStack.entries`). It does
the transitions (fade through between tab roots from their entry metadata, M3 shared axis X for pushed screens), the
predictive Back (the previous entry composed under the top one) and the entries' lifecycles; the shell has no scene,
layer or Back handler of its own. Per-tab state follows the Navigation 3 `multiplestacks` recipe: each tab's stack
and the contextual stack are decorated on their own, so a hidden tab keeps its saveable state and view models. A
tab's stack is its root: pushed screens cover the bar and a tab switch closes them, so "each tab keeps its own back
stack" below means its root's state. Tabs switch by the bar only; the tab swipe is gone (owner, 2026-10-13). The bar
is laid out under `NavDisplay`, so pushed screens cover it and the predictive Back uncovers it.

**Decision (2026-10-03).** The Android app has one Navigation 3 Compose shell in
`MainActivity` (`app/shell/AppShell.kt`).
- Routes are `@Serializable` `AppRoute` keys in `commonMain`; a key
  declares its kind (tab root, overlay screen, sheet, dialog) and its sheet policy.
  Since ADR 0035 they extend no Nav3 type: the shell shows them as
  `NavEntry<AppRoute>`, so `navigation3-runtime` stays in `:app`.
  Feature keys join one polymorphic `SerializersModule`.
- Sheets and dialogs are overlay scenes: a bottom-sheet scene strategy on
  `ModalBottomSheet` and `DialogSceneStrategy`. Results go back through the
  Navigation 3 result API.
- Each tab keeps its own back stack and saveable state; overlay screens draw above
  the bar; sheets and dialogs above everything.
- Entry intents, App Links and shortcuts become routes through one parser
  (`EntryRouteParser`). A `RouteQueue` feeds the shell: one pending route, newer
  replaces older, handed out once its tab is selected and the session is ready.
- Screens stay stateless and take callbacks (ADR 0017); only `<Name>Route` reads the
  ViewModel and turns callbacks into navigation.
- Activities for WebView screens stay Activities. `navigation3-ui` has no iOS
  artifact, so scenes live in `app/`; the iOS shell is SwiftUI and uses the same
  route keys, parser and `RouteQueue` (ADR 0023).

**Why.** Navigation 3 is Google's recommendation for Compose-only apps, keeps the
back stack as plain state, and has multiplatform keys and deep links.

**Consequence.** Predictive back on sheets needs `android:enableOnBackInvokedCallback`.

**Supersedes.** The Navigation part of the rule that Android stays on XML,
Fragments, ViewBinding, Navigation, Hilt and WorkManager (with ADR 0017).

**Revisit when.** Predictive back on a sheet scene fails on a device, or a sheet
case cannot be expressed as a scene.
