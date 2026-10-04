# Plan template

Plans are lane files of cards, not stage plans. The numbered-stage format of
the user-global plan rules (separate test and mock stages, `*-track.md`
trackers) does not apply in these repositories (see [`AGENTS.md`](../../AGENTS.md)).
Light work has no plan at all ([workflow](workflow.md#tiers)).

## Lane file

A header, then `## Cards` in dependency order:

- **Goal**: what the lane delivers, 2–4 sentences.
- **Exit**: checkable bullets that close the lane.
- One line `Repos | Entry gate | Max parallel agents | Branch prefix`.
- **Owns**: files and directories the lane alone writes ([ownership](ownership.md)).
- **Invariants**: rules every card of the lane keeps.
- **Context**: reports and decisions the cards cite.

## Card

One card is one agent session, one branch and one reviewable PR of 0.25–2
agent-days. It starts with machine-readable lines:

```text
### <ID> · <title>
- size: S|M|L <days>
- needs: <card IDs, gates, owner:<item>, recipe:<name>> or -
- touches: <paths or globs; new files marked (new)>
- branch: v2.3/<lane-id-lowercase>/<id-lowercase>-<slug> (or none)
- verify: <exact commands>
- produces: recipe:<name>   (only on the card that writes the recipe)
Do: 2–6 bullets: decisions, invariants, traps, exact names
Done when: 2–5 checkable bullets: tests at the layer of the behaviour,
  baselines, stable identifiers, the docs line
Refs: report sections, code paths, URLs
```

Sizes: S ≤ 0.5, M ≤ 1.5, L ≤ 2.0 agent-days; a larger item is split by a letter
or digit suffix (`KM-01a`, `SH-1b9`).

Rules:

- Tests are part of "Done when", never a separate card.
- Docs and the changelog fragment come once per feature, in its close card
  ([closing a feature](workflow.md#closing-a-feature)).
- Repeated work is a recipe: the first card doing it has `produces:` and writes
  the recipe (at most two pages: steps that worked, traps, files to copy) in the
  same session; later cards list it in `needs`.
- "Do" holds decisions and traps, not code that the card will write.
- A card never holds its status. Status lives only in the integrator's board
  (local, in the ignored `vibe/` directory of the main checkout).

## Executing a card

1. **Pick** a `ready` card whose lane has a free slot and whose `touches`
   overlap no running card and no frozen hot file.
2. **Read** the decisions and the integration protocol, the ownership rows of
   the touched files, the lane header, the card, its recipes, and only the
   report sections its Refs cite. The code is the truth.
3. **Claim**: board row `in-progress` with the branch name.
4. **Worktree**: `~/proj/.wt/bin/lane new <repo> <lane-id> <card-id>-<slug>`
   (the integrator's tool, outside the repository);
   cards with `branch: none` work only in the paths they name.
5. **Implement** inside `touches`; an edit outside needs the integrator's OK.
6. **Verify** with the card's `verify` lines through `scripts/verify.sh`.
7. **Commit and push** (`<CARD-ID>: <summary>`), open the PR into `v2.3/next`,
   set the board row to `review`.
8. **Hand off**: rebase when asked; after the merge comment run `lane done`.
9. **Stuck**: set `blocked` with the reason, comment in the PR and stop; never
   widen the scope or bypass a guard.
