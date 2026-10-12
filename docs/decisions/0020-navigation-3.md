# 0020 One Navigation 3 shell with `@Serializable` routes

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
