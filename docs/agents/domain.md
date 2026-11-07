# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

## Before exploring, read these

- **`GLOSSARY.md`** at the repo root.
- **`docs/adr/`**: the ADRs of this repo (`NNNN-<slug>.md`, index in `docs/adr/README.md`). Read the ones that touch
  the area you're about to work in.
- **`docs/README.md`**: the map of the rest of the docs (architecture, design, settings, per-feature docs).

If any of these files don't exist, **proceed silently**. Don't flag their absence; don't suggest creating them upfront.
The `/domain-modeling` skill (reached via `/grill-with-docs` and `/improve-codebase-architecture`) creates them lazily
when terms or decisions actually get resolved. New ADRs go into `docs/adr/` with the next free number.

## File structure

Single-context repo:

```text
/
+-- GLOSSARY.md
+-- docs/adr/
|   +-- README.md
|   +-- 0031-swiftui-ios-screens.md
|   +-- 0035-ios-without-compose.md
+-- shared/
+-- app/
+-- iosApp/
```

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the
term as defined in `GLOSSARY.md`. Don't drift to synonyms the glossary explicitly avoids.

If the concept you need isn't in the glossary yet, that's a signal: either you're inventing language the project
doesn't use (reconsider) or there's a real gap (note it for `/domain-modeling`).

## Flag ADR conflicts

If your output contradicts an existing ADR, surface it explicitly rather than silently overriding:

> _Contradicts ADR 0031 (SwiftUI iOS screens), but worth reopening because..._
