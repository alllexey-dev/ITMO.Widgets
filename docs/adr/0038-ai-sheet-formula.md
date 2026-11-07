# 0038 Backend analyses a flow-shared sheet; the device computes the own total

**Status.** Accepted (2026-11-05). Amends [0014](0014-sheet-scores-on-device.md).

**Decision.** A connected sheet's total is a `SheetFormula`: one column of the own row shown as the sheet shows it
(as before), or arithmetic over cells of the own row (`sum`, `multiply`, `min`, `max`, `subtract`, `divide`,
`round`, numbers). A formula comes from one of two places:

1. **By hand, every sheet.** The review's column list picks one column or adds several (`Сложить столбцы`).
2. **AI, only for a sheet a subject link shares with a flow or everybody.** `Разобрать с ИИ` asks Backend
   (`POST`/`GET /api/links/{id}/sheet-rule`, Backend's
   [sheet rules contract](https://github.com/alllexey-dev/itmo-widgets-backend/blob/dev/docs/contracts/sheet-rules.md)
   and its ADR 0038). Backend's analyzer reads the workbook once, with names and ISU pseudonymised before the model,
   and returns a rule: tabs, columns and a closed set of nodes, no cell values, names or ISU. The device applies
   the rule to the tabs it already downloads; nothing is saved before `Использовать`.

The rule's `current_accumulated` route is the number shown everywhere; its `official_total` route (the teacher's
total, which may be 0 before admission) is a second line on the subject page and in the review, never compared
by the mark check.

**What changes against 0014.** 0014 kept the sheet away from Backend entirely. For a sheet that is already shared
with a flow or everybody through a subject link, Backend reading it adds no new reader: the link's audience can
open it anyway, and Backend checks the approved revision on every call. The connection, the own row and the
downloaded tabs still stay on the device; the device never uploads the sheet and Backend never sends other
students' data. A private or local link never offers the AI, and every call passes the services opt-in
(`BackendGate.mayCallBackend()`); the demo session answers a fixture rule.

0014's "values as the sheet shows them" stays for a single column. A computed formula parses numbers (`7,5`,
`-3`, spaces) and follows the rule's `blank_policy` exactly as the analyzer checked it
(`sheet-analyzer/evidence.py`): an empty input is skipped by a sum under `ignore_blank_inputs` and makes the
result unavailable under `preserve`; text, an error cell, a missing operand or a division by zero makes it
unavailable (a dash), never a guessed 0. A computed value is written as the app writes numbers (`66,3`).

**Columns are kept by header path.** A rule names columns by letter; when the viewer accepts it, each cell takes
the header path it has in the downloaded tab, as a hand-picked column does, so a later reading finds it again when
columns move. A rule naming a tab, row or column the device does not see is no proposal.

**Storage.** `SheetScoresFileStore` format 2 stores the formula, the official formula and its last value, the
rule's summary and the link id; format 1 is read and migrated to a direct formula of its tab. 2.2 reads only
format 1 and deletes another format, so going back to 2.2 after 2.3 has written the file loses the connections
(they are connected again from the subject's links).

**Why not keep the heuristics.** The candidates of `SheetTotals` find a total column; many sheets have none or a
total the teacher fills only at the end. The experiment of 2026-11-04 showed a model reading the formulas of the
XLSX finds the accumulated sum reliably when its answer is checked against the workbook; the device cannot read
formulas from the CSV it downloads.

**Revisit when.** Backend serves rules for private sheets (it must not), the rule schema changes version, or a
sheet needs rows other than the own one.
