# 0005 Explicit friendships; a crossed request accepts

**Decision (2026-09-15).** One row per unordered user pair with `PENDING` or
`ACCEPTED`. Actions are request, accept, reject, cancel, remove. A request sent
to someone who already sent one becomes a friendship immediately. Reject, cancel
and remove delete the row; nothing is notified about them. `BLOCKED` is reserved
in the contract for a later block model.

**Why.** The legacy reciprocal-request model had no explicit states, so clients
had to infer them from two lists. A crossed request expresses mutual intent and
should not require a second tap.

**Consequence.** Backend locks both user rows in ISU order for every mutation,
returns the fresh viewer-scoped profile from each action, and notifies only
`REQUEST_RECEIVED` and `REQUEST_ACCEPTED` after commit.
