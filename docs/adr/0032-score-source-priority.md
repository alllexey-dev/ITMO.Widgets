# 0032 The most current score source with a value wins

**Status.** Accepted (2026-10-13). Amends [0014](0014-sheet-scores-on-device.md).

**Decision.** A subject in progress shows one number, picked by one rule
(`RecordbookPointsRule` in `:shared:feature-recordbook`) for the list,
`Откуда баллы` and the subject page:

1. a connected sheet whose last reading succeeded and whose cell is not empty;
2. BARS, when the subject's journal has a score;
3. the last value of a sheet whose last reading failed;
4. My ITMO's score.

A source without a value never hides one with a value. With no value anywhere
the dash is labelled by the first source present in the same order. A final
result or a no-show ignores the sheet.

**Why.** 0014 showed a sheet's total only while My ITMO and BARS had no points,
and the code took BARS whenever the subject had a journal, even an empty one. On
the owner's phone an exam with an untouched BARS journal showed a dash under
`БАРС` while the connected sheet had the total. Teachers fill their sheets
first, then copy the points to BARS and at the end to My ITMO, so the sheet is
the most current number when it is connected and was read. A failed reading
keeps an old value, so live BARS goes first then.
