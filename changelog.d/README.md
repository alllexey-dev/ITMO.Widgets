# Changelog fragments

Unreleased changes are written here, one file per change, instead of into
`CHANGELOG.md`, so parallel lanes never edit the same lines. At release the
fragments are folded into `CHANGELOG.md` and deleted.

## Name

`changelog.d/<lane-id-lowercase>[-<topic>].md`, for example
`l10-schedule-widgets.md` or `l12-recordbook.md`. Names are lowercase only:
APFS is case-insensitive, so `L18-ios.md` and `l18-ios.md` would collide.
`scripts/changelog.sh check` accepts `^l[0-9]{2}-[a-z0-9][a-z0-9-]*\.md$`;
any other file here except this `README.md` fails.

- One fragment per feature per lane, written by the card that closes the
  feature, and at most one per infrastructure lane, written at its close.
- Only the lane in the name edits its fragment. Other lanes write their own.

## Content

An optional first line `# <Area>`, then one tight list of `- ` bullets in the
voice of `CHANGELOG.md`: what changed for the user or the developer, the UI
text quoted exactly («…»), the class or file named where it helps. Wrap
continuation lines with two spaces. No other headings, no blank lines inside
the list, no trailing whitespace, a final newline.

```markdown
# Schedule widgets

- The schedule widgets read their state from the shared presentation
  layer.
```

## Commands

```bash
scripts/changelog.sh check                            # also run by scripts/check-docs.sh
scripts/changelog.sh collect <version> <date> --dry-run
scripts/changelog.sh collect <version> <date>         # at release only
scripts/changelog.sh --self-test
```

`collect` turns `## <version> — development` into `## <version> — <date>`,
or inserts that heading below the preamble when there is no development
heading. Bullets without an area join the list right below the version
heading; each `# <Area>` goes under `### <Area>`, merged with an existing
group of that name. It then deletes the fragments; commit `CHANGELOG.md` and
the deletions together. The script finds the repository with
`git rev-parse --show-toplevel`, so other repositories copy it unchanged.
