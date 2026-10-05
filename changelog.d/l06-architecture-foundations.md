# Architecture

- `StableIdentifiersTest` freezes what an installed 2.2 depends on: manifest
  class names, workers and work names, shortcuts, notification channels,
  preference and token files, the widget snapshot, data directories, the
  FileProvider authority and the distribution flavors.
- `UpgradeFrom22Test` reads a data directory captured from 2.2 with the
  current stores: token files, preferences, debug overrides, file stores,
  caches and the widget snapshot.
- Launch intents live in `core/navigation/AppEntryIntents.kt`, so features
  no longer import the app shell.
- The presentation kit (`core/presentation`, `LoadState`) and one ViewModel
  contract: a single `uiState` flow and at most one `events` flow;
  `AppResult` replaces the per-feature result and state wrappers, and
  `core/util` is gone.
- Debug hosts share one preview appearance and per-feature appearance files;
  notification permission, spoiler pick and crop, clipboard and in-button
  progress each have one shared helper; shared test fakes live in
  `core/testing`.
- The architecture rules run from the `:konsist` module over `app/` and every
  shared module, split by topic, with floors and per-feature ratchets that
  only shrink. They also check that common and iOS code imports no Android,
  Java or Dagger API and only multiplatform `androidx` artifacts, that
  screens, sheets and dialogs stay stateless with a preview, that Compose
  code outside the design system takes colours, font sizes and spacing from
  it, that network clients are matched by their full name, that shared
  feature modules never depend on each other, and that a type has one DI
  graph.
