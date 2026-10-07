# 2.2 store goldens of the recordbook

Files 2.2's Gson stores wrote, read by the JVM golden tests of `MarksFileStore` and `SheetScoresFileStore` (recipe
`kotlinx-file-store`). Bytes are kept as written: never reformat them. The other stores' goldens are in
`app/src/test/resources/stores/`.

| File | Source |
|---|---|
| `marks/state.json` | G-04 capture, `androidTest/assets/upgrade-2.2/files/marks/state.json` |
| `marks/state-sp08.json` | SP-08 fixture `files/marks/state.json` (2.2 on the host JVM): null scores, rates and marks absent, `60.0` kept as a double, a plan with empty `marks` |
| `sheet_scores/state.json` | G-04 capture, `androidTest/assets/upgrade-2.2/files/sheet_scores/state.json` |
| `sheet_scores/state-sp08.json` | SP-08 fixture `files/sheet_scores/state.json` (2.2 on the host JVM): a connection with every nullable field absent, an empty `tabName` and `headerPath` |
