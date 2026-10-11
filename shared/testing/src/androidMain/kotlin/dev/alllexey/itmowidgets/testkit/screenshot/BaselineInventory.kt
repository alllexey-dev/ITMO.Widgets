package dev.alllexey.itmowidgets.testkit.screenshot

/**
 * A module's baselines against its previews. A baseline whose base is a preview is captured and compared by its
 * case; one listed in [BaselineDirectory.REFERENCES] is a reference that `:app` records; anything else is stale.
 * A reference whose preview exists means its reference test or line is still there.
 */
class BaselineInventory(previewBases: Set<String>, directory: BaselineDirectory) {

    private val references = directory.references()
    private val baselines = directory.baselines()

    /** Reference baselines, by file. */
    val referenceFiles: List<String> = baselines
        .filter { it.base != null && it.base !in previewBases && it.base in references }
        .map { it.fileName }

    /** Files that are neither a preview's baseline nor a listed reference. */
    val stale: List<String> = baselines
        .filter { it.base == null || (it.base !in previewBases && it.base !in references) }
        .map { it.fileName }

    /** Listed references whose preview exists: the reference test and the line must go. */
    val previewedReferences: List<String> = references.filter { it in previewBases }.sorted()

    /** Listed references without any baseline file. */
    val missingReferences: List<String> = (references - baselines.mapNotNull { it.base }.toSet()).sorted()

    val problems: List<String>
        get() = stale.map { "stale baseline $it: no preview and not in ${BaselineDirectory.REFERENCES}; delete it" } +
            previewedReferences.map {
                "reference $it also has a preview: delete its reference test in :app and its " +
                    "line in ${BaselineDirectory.REFERENCES}"
            } +
            missingReferences.map { "reference $it is listed but has no baseline; record it with `shots app --record`" }

    fun report(): String = buildString {
        referenceFiles.forEach { appendLine("reference: $it") }
        problems.forEach { appendLine(it) }
    }
}
