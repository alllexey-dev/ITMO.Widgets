# 0008 Subject links: schedule-flow audiences, premoderation only for everybody

**Decision (2026-09-23).** A subject link has a category and a visibility:
`PRIVATE`, `GROUP`, `FLOW` or `ALL`. Group and flow links are published to
their audience at once; only links for everybody (`ALL`) go through
premoderation, and premoderation is a policy that can be switched off without a
deployment. A chat is an ordinary link with category `CHAT`.

**Audiences.** Audiences are MyITMO schedule `flow_id`s. A flow id is unique per
cohort year and uses the same ids as ISU potok ids, so group P3119 of this year
and of last year never share links, which a group name could not guarantee.
`GROUP` is every flow of the author in the subject and period with
`type_id != 1` (practice, labs), `FLOW` every flow with `type_id == 1`
(lectures). Saving a group or flow link snapshots the author's matching flows;
a viewer sees it when they share one of them.

Membership is behind the Backend interface `FlowMembership`. Its only
implementation, `ScheduleFlowMembership`, trusts the schedule the user
uploaded: `LessonService.syncLessons` records each lesson's flow in
`user_subject_flows`, and the rows outlive the lessons.

**Strict verification is deferred.** Researched on 2026-09-23:

- ISU flow member lists would need Backend to hold a technical ISU session;
  the ids would match because schedule flows are ISU potoks.
- zkTLS through TLSNotary would let the client prove its my.itmo.ru schedule
  response without handing over its token. `tlsn` is still alpha and supports
  only TLS 1.2, which my.itmo.ru offers today but is not obliged to keep.

Neither is part of this iteration. Either would replace only the
`FlowMembership` implementation; the link model, the API and the app do not
change. Until then a modified client could claim a flow it does not attend;
the reach of such a link is one subject's audience, and reports, votes and
restrictions apply to it.

**Why premoderation only for everybody.** A group or flow link reaches students
who attend the same classes as the author, and chats or score tables are useful
the moment they are shared; a review queue would only delay them. A link for
everybody reaches strangers of every intake, and approved `ALL` links of
lasting categories are also shown to later years (`С прошлых лет`).

**Moderation.** Roles, cases, append-only decisions, reports, capability
restrictions and typed policies with code defaults are shared machinery that
teacher reviews will reuse. Every change of a non-private link creates an
immutable revision; others always see the latest approved revision, so a group
link widened to `ALL` is not public before a moderator approves it. Automatic
approval of group, flow and (with premoderation off) public links is a decision
by `actor=POLICY`, never a fictional moderator. Switching premoderation off
approves the pending queue. Reports and a falling vote score open a case once
their thresholds are reached; neither hides a link by itself. Restrictions are
manual and never block private links, deletion or pins.

**Consequences.** The owner sees a review state only as a short badge on the
link and the reason in its actions; the app has no screen of one's own links
across subjects, no home card for moderation results and no moderator UI
(operators use the Backend moderation API). Without `Подключение к ITMO.Widgets` links stay private on the
device and are uploaded as private links on the first refresh with the
connection. Any HTTPS host is accepted and Backend never fetches a submitted URL.
