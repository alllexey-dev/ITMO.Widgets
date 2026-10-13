# 0020 One Navigation 3 shell with `@Serializable` routes

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
- Routes are `@Serializable` keys on `navigation3-runtime` in `commonMain`; a key
  declares its kind (tab root, overlay screen, sheet, dialog) and its sheet policy.
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
