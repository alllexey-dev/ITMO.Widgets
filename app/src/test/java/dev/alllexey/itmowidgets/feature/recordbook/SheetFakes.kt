package dev.alllexey.itmowidgets.feature.recordbook

import dev.alllexey.itmowidgets.core.resources.GoogleSheetUrl
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.StudyHalf
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.KeyKind
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.OwnIdentity
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCell
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCheck
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetColumnRef
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetFixtures
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetInspection
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRowMatch
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScore
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScoresRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTab
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTabGrid
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetWorkbook
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

const val TEST_SHEET_ID = "1TestSheetIdForUnitTests_0123456789-abc"
const val TEST_SHEET_URL = "https://docs.google.com/spreadsheets/d/$TEST_SHEET_ID/edit#gid=22"
val TEST_IDENTITY = OwnIdentity(123456, "Тестов Тест Тестович")

/** Connections in [scores]; inspections come from [inspections] in order, every call is recorded. */
class FakeSheetScoresRepository(vararg initial: SheetScore) : SheetScoresRepository {
    val scores = MutableStateFlow(initial.toList())
    val inspections = ArrayDeque<SheetInspection>()
    var checkResult = SheetCheck(emptyList(), emptyList())
    /** Holds [check] until completed. */
    var checkGate: CompletableDeferred<Unit>? = null
    /** Holds [inspect] until completed. */
    var inspectGate: CompletableDeferred<Unit>? = null
    /** Holds [connect] and [changeTotal] until completed. */
    var connectGate: CompletableDeferred<Unit>? = null
    var connectResult: AppResult<Unit> = AppResult.Success(Unit)
    val inspected = mutableListOf<String>()
    val refreshes = mutableListOf<ResourceScope>()
    val connected = mutableListOf<Connected>()
    val totals = mutableListOf<Connected>()
    val disconnected = mutableListOf<ResourceScope>()
    val checks = mutableListOf<StudyHalf>()
    var untrackCalls = 0

    data class Connected(val scope: ResourceScope, val url: String, val row: SheetRowMatch, val total: SheetCell)

    override fun observe(): Flow<List<SheetScore>> = scores

    override suspend fun refresh(scope: ResourceScope) {
        refreshes += scope
    }

    override suspend fun inspect(url: String): SheetInspection {
        inspected += url
        inspectGate?.await()
        return inspections.removeFirstOrNull() ?: SheetInspection.Failed(SheetStatus.NETWORK)
    }

    override suspend fun connect(scope: ResourceScope, url: String, row: SheetRowMatch, total: SheetCell): AppResult<Unit> {
        connected += Connected(scope, url, row, total)
        connectGate?.await()
        if (connectResult is AppResult.Success) {
            scores.value = scores.value.filterNot { it.scope.key == scope.key } + sheetScore(
                scope = scope, url = url, tabGid = row.tab.gid, tabName = row.tab.name, rowKey = row.key,
                column = SheetColumnRef(total.headerPath, total.column), value = total.value.ifEmpty { null },
            )
        }
        return connectResult
    }

    override suspend fun changeTotal(scope: ResourceScope, row: SheetRowMatch, total: SheetCell): AppResult<Unit> {
        totals += Connected(scope, "", row, total)
        connectGate?.await()
        if (connectResult is AppResult.Success) {
            scores.value = scores.value.map {
                if (it.scope.key == scope.key) {
                    it.copy(column = SheetColumnRef(total.headerPath, total.column), value = total.value.ifEmpty { null })
                } else {
                    it
                }
            }
        }
        return connectResult
    }

    override suspend fun disconnect(scope: ResourceScope) {
        disconnected += scope
        scores.value = scores.value.filterNot { it.scope.key == scope.key }
    }

    override suspend fun check(half: StudyHalf): SheetCheck {
        checks += half
        checkGate?.await()
        return checkResult
    }

    override suspend fun untrack() {
        untrackCalls++
        scores.value = scores.value.map { it.copy(tracked = false) }
    }
}

fun sheetScore(
    scope: ResourceScope = ResourceScope(1, "Тестовый предмет", "2026-1"),
    value: String? = "66,3",
    status: SheetStatus = SheetStatus.OK,
    url: String = TEST_SHEET_URL,
    tabGid: Long = 22,
    tabName: String = "P3110",
    rowKey: String = "123456",
    column: SheetColumnRef = SheetColumnRef("ИТОГО баллов", 11),
    baseline: String? = value,
    tracked: Boolean = true,
    updatedAt: Instant? = Instant.parse("2026-09-07T09:00:00Z"),
    connectedAt: Instant = Instant.parse("2026-09-01T09:00:00Z"),
) = SheetScore(
    scope = scope, url = url, tabGid = tabGid, tabName = tabName, rowKey = rowKey, keyColumn = 0,
    keyKind = KeyKind.ISU, column = column, value = value, baseline = baseline, tracked = tracked, status = status,
    updatedAt = updatedAt, connectedAt = connectedAt,
)

/** The synthetic grades tab (gid 22) first, then the roster (11) and a tab without the viewer (33). */
fun testWorkbook(vararg tabs: Pair<SheetTab, String> = arrayOf(
    SheetTab(22, "P3110") to "grades_multiheader.csv",
    SheetTab(11, "All") to "roster.csv",
    SheetTab(33, "BARS (Fall semester 2026)") to "name_only.csv",
)): SheetWorkbook = SheetWorkbook(
    GoogleSheetUrl.parse(TEST_SHEET_URL)!!,
    tabs.map { (tab, file) -> SheetTabGrid(tab, SheetFixtures.csv(file)) },
)
