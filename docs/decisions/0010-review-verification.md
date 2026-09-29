# 0010 Own teacher reviews verified through ISU flows

**Decision (2026-09-29).** Users with `Подключение к ITMO.Widgets` write one
review per teacher in the person profile. Backend checks whether the teacher
taught the author through ISU flows: the author is verified for the teacher
when some ISU flow has the teacher in its schedule and the author among its
members. Backend does not trust the client for it; the app only sends candidate
flows.

**ISU session.** Backend holds an ISU session with the technical account's
`KEYCLOAK_IDENTITY` cookie. The cookie and the service tokens of the technical
My ITMO account live in one Backend table, `service_credentials`: a seed from
the environment is written only into a row without a value, the server rotates
the values itself (a new cookie after each ISU login, new tokens after each My
ITMO refresh), and an admin sees everything about a value except the value and
replaces it through one admin API with an audit entry. There is no separate
ISU session store.

**Cache.** Backend caches only numbers: flow teachers for 30 days, flow members
for one day. Names, groups and photos from ISU are never read or stored.

**No rejection by ISU.** A review is never rejected because of ISU. Without a
session it waits, a failed request is retried later, and an author ISU cannot
find stays unverified. Unverified reviews are visible with the muted mark `Не
подтверждён` next to verified ones (`Вёл у автора`) and will not enter an AI
summary. ISU lists at most 250 members per flow, so an author beyond them in a
larger flow stays unverified.

**Candidates.** The app sends the flows of its own academic lessons with the
teacher from the personal schedule of the last 8 study periods; Backend adds the
author's uploaded lessons with the teacher and schedule flows. The recordbook is
not used: its teachers carry no ISU, and an ISU is never looked up by name
([0009](0009-person-profile.md)).

**Anonymity.** Anonymity is stored on the review, not in a version: switching
it applies at once without moderation and never exposes the author of an
anonymous review to other users. Moderators see the author.

**Votes and order.** Own reviews and the Reviews copies get +1/−1 votes and
form one list: by score, then date, own reviews before copies. Copies are not
reported or moderated.

**Writing.** `Написать` appears when Backend knows the person as a teacher
(`knownTeacher`) or the My ITMO person has a position, the viewer may write and
has no review of this person yet.

**No «My reviews».** The own review is visible only in the teacher's profile;
there is no separate list of one's reviews.
