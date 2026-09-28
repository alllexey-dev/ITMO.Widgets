# 0009 One person profile, direct My ITMO identity and copied reviews

**Decision (2026-09-28).** `USER_PROFILE` opens for any positive ISU, including
people who do not use ITMO.Widgets. Teachers have the same person profile, not
another screen. Identity and facts come from My ITMO through the existing
MyItmoApi client on the device; registered users additionally have a Backend
social block, and copied Reviews text appears as the last section.

**Source boundary.** Backend neither proxies the profile header/facts nor calls
My ITMO with the viewer's token. Its own service account still resolves the
current study groups in `UserData`, as described in
[Current study groups on read](../../../itmo-widgets-backend/docs/contracts/friendships.md#current-study-groups-on-read).
The user's refresh token remains on the device; the access token is sent to
Backend only for authentication and only with the custom-services opt-in.
The existing ID-token identity publication remains a separate Backend request.
Contacts are discarded at the data boundary; gender and exchange status are
not part of the displayed person model.

**Reviews.** Older reviews are anonymous text copies from the Reviews project,
read through Backend only with `Подключение к ITMO.Widgets`. The route
`GET /api/teachers/{isu}/reviews` requires authentication, has no rate limiter
or pagination, and uses the existing V7 tables without a new migration.
Subject is free text; the source link falls back to the provider's teacher
page. No author, rating, writing form, eligibility proof or moderation action is
added by this decision. The response object and section heading leave room for
those later additions.

**Presentation.** One RecyclerView keeps identity, relationship, sharing,
facts and reviews in a fixed order. Initial loading waits for the three parts,
with a 3-second deadline once either identity is ready. Late reviews append;
late identity blocks wait for explicit retry instead of moving the page. The
[profile contract](../features/social.md) specifies cached and failed replies,
quiet missing/disabled parts and the single partial-failure snackbar.

**Navigation.** Teacher rows with a usable ISU and both search sections open
this profile through the shared navigation helper. Sheets dismiss first.
Unavailable identifiers are never guessed from names, and informational rows
do not look actionable. Friend-sport cards remain read-only.
