# 0006 People search through MyITMO plus Backend lookup

**Decision (2026-09-15).** Searching people by name uses MyITMO's personality
search on the device. Backend only answers `POST /api/users/lookup` with the
registered subset of up to fifty ISUs. Backend has no name search and no rate
limiter for lookup.

**Why.** MyITMO already indexes every student and employee; duplicating that
index on Backend would be worse and stale. Lookup is authenticated, bounded and
served by one indexed query, so a limiter added state and a 429 path without a
real threat.

**Consequence.** The schedule picker's `Все` scope and the people-search screen
share `PeopleSearchRepository`; unregistered people get an invite share sheet;
phone and e-mail from the directory never leave the data layer.
