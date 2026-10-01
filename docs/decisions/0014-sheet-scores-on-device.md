# 0014 Own totals from public Google Sheets are read on the device

**Decision (2026-10-01).** A student connects the own total of a subject from
a teacher's public Google Sheet behind any link of the subject (`Мои баллы`).
The application downloads the sheet, finds the own row and the total column by
itself and shows that one value on the subject page and, while My ITMO and
BARS have no points, in the recordbook list. The existing mark check reads the
connected totals in the background. Everything happens on the device: no
request to Backend, no dependency on `Подключение к ITMO.Widgets`.

**Why on the device.** A sheet holds every student's marks and names. Reading
it on Backend would put other people's personal data and the own total on the
server, and the project server has no need for either. The device reads only
public sheets, without cookies or an account, and keeps only the chosen row key
and column; other names are seen only while choosing a row and are never stored.

**Not the Google Sheets API.** The API needs a key or OAuth, has quotas per
project and would tie every installation to one Google Cloud project. The public
export of a tab as CSV (`/export?format=csv&gid=…`) and the HTML view
(`/htmlview/sheet`, `/htmlview` for the list of tabs) work without credentials.
`gviz/tq` loses cells of mixed columns and `pubhtml` answers 401 for sheets that
are shared but not published, so neither is used. A sheet that forbids the
export still renders its HTML view, which is why the HTML tab is the fallback.

**Automatic search instead of manual markup.** The roadmap's Stage 33 planned a
manual mapping: a header row, a key column and a formula for the total. Real
sheets have one to six header rows, a teacher's name above them, merged group
titles and the ISU or only the name as the key, so manual markup would be
needed for almost every sheet and break with every edit. Instead the own row is
found by the ISU and the forms of the ITMO.ID name, the header path of every
column is rebuilt from the merged titles, and the total is the column whose
header holds the strongest keyword (`итог`, `σ`, `сумма`, `total`, …). When the
search is ambiguous or finds nothing, the student picks the row, the tab or the
total from a list.

**A key, not a row number.** The connection stores the row key (the ISU or the
normalised name) and the column as the tab's `gid` plus the header path, with the
column index only as a fallback. Teachers sort, insert and delete rows and
columns; the next reading finds the same row and column again.

**Values as the sheet shows them.** The total is the cell text (`66,3`, `80%`,
`5A`), never recomputed or parsed into a number: the formats differ between
sheets and a wrong conversion would show a wrong mark.

**News through the mark check.** A changed total is an unread subject of the
existing `marks-check` (`MarkSource.SHEETS`), with its own switch, so the
notification, the home card and the recordbook dot stay one mechanism. Any
reading the student saw (connecting, opening the page, a pull) moves the
baseline, so a total already seen in the app is not notified later.
