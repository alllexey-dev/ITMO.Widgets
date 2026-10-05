# Docs and process

- ADRs 0016-0030 record the v2.3 decisions: the parity rewrite, Compose
  Multiplatform screens, the module graph, Koin, Navigation 3, Material 3
  Expressive, JVM screenshot tests, the iOS client, the pinned MyItmoApi
  build, Core 2.0, RemoteViews widgets, strings and icons, lanes and the
  integrator, release lines; 0001 and 0003 are superseded.
- `docs/process/` describes work tiers, the card format, parallel agents,
  file ownership, integration and the release checklist.
- `AGENTS.md` is rewritten for v2.3 at most 130 lines: all five
  repositories with their real remotes, Core 1.x frozen, the cross-repository
  order through OpenAPI and Core 2.0, `BackendGate`, the never-open secrets
  list, `scripts/verify.sh` as the one build entry point and a tiered
  definition of done.
- `scripts/check-docs.sh` guards docs against dangling paths, links and
  `vibe/` references, and unreleased changes go to `changelog.d/` fragments
  checked by `scripts/changelog.sh`.
- Owner rules that lived only in agent memory are in the docs: the v2.3
  standing approvals in `docs/process/workflow.md` and the one-process rule for
  instrumented tests in `docs/architecture.md`.
