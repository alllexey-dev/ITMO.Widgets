# Ownership

Each hot file has one writing lane per wave. Others hand in text; the owner
lane applies it. Lanes edit their own feature docs and modules directly.

## Hand-over points

A hand-over takes effect when the previous change is merged into `v2.3/next`:
kickoff (T5); the build barrier (build files stay with L04 until the module
skeleton merges); the strings split (merged: each `strings_<file>.xml` goes to
the lane of its module); the `core/`
extraction (frozen while it runs); the Konsist move; T11 (feature moves open);
`MainActivity.kt` → L17 after the last sport port; `di/` → L17 after the last
feature data move; T14 (shell files → L17); T15 (theme files, about ¼ day).

## Current wave (W0)

| File | Writer |
|---|---|
| `AGENTS.md` | L01 (kickoff) → L02 |
| `docs/README.md`, `docs/decisions/`, `docs/process/` | L02 (L01 adds its integration page) |
| `docs/architecture.md` | L02 → L06 → L02 |
| `docs/design.md` | L08 (L04: the visual-tests section) |
| `docs/product/` | L01 → L02 |
| `CHANGELOG.md` | L01 → L02 (preamble); then fragments only |
| `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, build-logic | L04 |
| `app/build.gradle.kts` | L01 (versions) → L04 → L05 (one line) |
| `scripts/verify.sh`, Android CI workflows | L04, one open PR at a time |
| MyItmoApi pin file | L04 creates → L01 bumps |
| `styles.xml`, `themes.xml`, `AndroidManifest.xml`, `ItmoWidgetsApplication.kt`, `scripts/strings-frozen-keys.txt` | L05 |
| `strings_<file>.xml` | The lane of the module `scripts/strings-owners.py --where <id or path>` names |
| `ArchitectureTest.kt` | L05 (one line) → L06 |
| `MainActivity.kt`, `core/navigation/`, debug hosts | L06 |
| `di/` | L07 |
| Navigation graphs | L17 |
| iOS: the iOS app project, `iosMain` source sets | L18 |
| Backend (`B/`), MyItmoApi (`M/`), Web (`W/`) | L21, L20, L22; Core 1.x (`C/`) frozen |

Two rules hold in every wave: a port that deletes a layout also deletes the
`tools:layout` attribute naming it, and a PR that deletes or moves a file fixes
the dangling path references to it in any doc in the same PR (one-line edits,
listed in the PR). Prose about a deleted class is a normal hand-in.

## Hand-ins

A PR that changes a fact stated in a doc another lane owns adds to its body:

```markdown
## Docs hand-in
<file>, <section>: <the text, at most 30 lines>
```

L02 applies hand-ins within 2 working days (at 3 pending, or every 2 working
days) and lists the applied PRs in its commit. Feature docs
(`docs/features/<x>.md`) are edited by their feature lane, not handed in.

## Module AGENTS.md

Every Gradle project in `settings.gradle.kts`, the build-logic build and the iOS
app project has
an `AGENTS.md` of at most 40 lines with exactly these sections and nothing the
root [`AGENTS.md`](../../AGENTS.md) already says:

```markdown
# <module>
## Owns
What lives here, one line per package or directory.
## Depends on
Modules and libraries, and what must not be added.
## Verify
The one command (a `scripts/verify.sh` mode).
## Hot files
Files with a single writer, and who it is.
## Docs
Links to the feature doc, recipes and decisions.
```

## Writing docs

`scripts/check-docs.sh` enforces:

- No path below `vibe/`; naming the bare directory is fine. Write "the
  integrator's board (local)" instead of a path.
- Files in sibling repositories are written as `../<repo>/…` or with the
  shorthands `B/` (Backend), `M/` (MyItmoApi), `W/` (Web), `C/` (Core 1.x).
- Every backticked path and relative link resolves.
- Every new doc is indexed: linked from `docs/README.md` or from a directory
  `README.md` it links.
- Current state only; history goes to `changelog.d/` fragments.
