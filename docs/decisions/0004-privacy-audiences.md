# 0004 Independent privacy audiences without reciprocity

**Decision.** Schedule, sport and friends each have an owner-chosen audience: `ALL`
(any authenticated user), `FRIENDS` (accepted mutual friends) or
`NOBODY`. Schedule and sport default to `FRIENDS`; friends default to `ALL` for new and
existing accounts, as explicitly chosen for this feature. Name, group and ISU are always visible. The viewer's own audiences do
not restrict what the viewer can see.

**Why.** Identity is already public through official university services, so
hiding it would be a fake control. Reciprocity punished users who share little
without protecting anyone.

**Consequence.** Backend returns viewer-scoped capabilities, never another
user's audiences. Legacy enabled settings map to `FRIENDS`, disabled ones to
`NOBODY`; an upgrade never broadens an audience. Blocking is not implemented;
a rejected request is not a block.
