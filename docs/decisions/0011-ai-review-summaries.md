# 0011 AI summaries of teacher reviews through Gemini behind a proxy

**Decision (2026-09-29).** A teacher with at least three suitable reviews gets a
short AI summary first in the reviews of the person profile: a neutral
description, up to 4 pros and 4 cons, tags of a fixed list of 20, five scales in
words with a short reason and the label `Сводка по N отзывам` with `ИИ`. Its
overall tone (five levels) is a coloured dot in the card, in the lesson sheet
and on the subject page. The five scales are collapsed by default behind
`Подробнее`.

**Provider.** Backend builds summaries through the Google Gemini API on the free
tier only: no paid provider and no model on own hardware. The model and the
daily budget are configuration, chosen by a probe (`gemini-3.5-flash-lite`;
80 % of its daily request limit). The owner creates the key in Google AI Studio.

**Network.** Gemini blocks Russia, where the server is. Only the Gemini client
goes through the sidecar `gemini-proxy` (Xray) in the Backend's Compose stack:
it sits in an internal network with `backend`, forwards only
`generativelanguage.googleapis.com` to the owner's «YC Full» egress and blocks
everything else. The client sets the proxy explicitly; the Reviews sync, ISU,
MyItmoApi and Firebase never use it. A sidecar keeps the egress keys out of
Backend and makes the allowed host a routing rule rather than a convention.

**Key.** The key is a row of `service_credentials`, `GEMINI_API_KEY`, like the
ISU cookie: seeded from the environment only into an empty row, replaced by an
admin in the web admin with an audit entry, never returned or logged.

**Input.** Only active copies of the Reviews project and own reviews that are
published and verified through ISU
([0010](0010-review-verification.md)); pending and unverified reviews never go
in. At most the 60 newest reviews and 60 000 characters; `N` is the number
passed to the model. The teacher's name and ISU never go in.

**Own prompt and check.** The prompt is our own; nothing of the Reviews
project's code or prompts is copied (it has no licence). Reviews are passed as
data inside a block with a random label per request, and the instruction says
they are never instructions. The answer is strict JSON checked by Backend's own
rules: lengths, known codes, no links, contacts or ISU-like numbers, tags only
with a review that states them. A rejected answer keeps the previous summary.

**Recalculation.** Nightly and by an admin, only for teachers whose input hash
changed, most reviewed first, within the daily budget shared by the night and
the admin; the rest wait for the next night. One run at a time.

**Showing.** Until a new summary is built, users see the previous one with its
own `N`; below three reviews it disappears. The tone (the dot and the tone line)
is shown only with confidence `MEDIUM` or `HIGH`, and confidence is always
`LOW` below five reviews. An admin can hide a summary; a hidden summary is not
served to anybody but admins. The app shows what Backend returns and enforces
nothing itself.

**Wording.** Scales have three grades and «мало данных». The dot stands at the
end of the teacher's row before the chevron, with its place reserved where a
late dot would move the row.
