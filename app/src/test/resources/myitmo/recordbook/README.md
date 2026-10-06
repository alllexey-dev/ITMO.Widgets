# Vendored MyItmoApi recordbook fixtures

Synthetic answers, not captured university responses or account exports. They are
copied unchanged from MyItmoApi (`M/`) commit `3a85bd82a9c9a6101ae0f8aeb85cc3ed12f7bf5e`
(`gradle/myitmoapi.ref` at the time of KM-10b1) and read by
`feature/recordbook/data/RecordbookRepositoryImplTest.kt`.

| File | Source |
|---|---|
| `specializations.json` | `M/kmp/fixtures/recordbook/specializations.json` |
| `record-book.json` | `M/kmp/fixtures/recordbook/record-book.json` |
| `controls.json` | `M/kmp/fixtures/recordbook/controls.json` |
| `absence.json` | `M/kmp/fixtures/recordbook/absence.json` |
| `empty.json` | `M/kmp/fixtures/recordbook/empty.json` |

Refresh the commit above whenever a fixture is updated from a newer pin.
