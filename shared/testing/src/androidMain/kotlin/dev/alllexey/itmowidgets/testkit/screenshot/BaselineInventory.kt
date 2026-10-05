package dev.alllexey.itmowidgets.testkit.screenshot

/**
 * A module's baselines against its previews. A baseline whose base is a preview is captured and compared by its
 * case; one listed in [BaselineDirectory.REFERENCES] is an XML reference pending its port; anything else is stale.
 * A reference whose preview exists means the port merged but its reference test or line is still there.
 */
class BaselineInventory(previewBases: Set<String>, directory: BaselineDirectory) {

    private val references = directory.references()
    private val baselines = directory.baselines()

    /** References pending their port, by file. */
    val pendingReferences: List<String> = baselines
        .filter { it.base != null && it.base !in previewBases && it.base in references }
        .map { it.fileName }

    /** Files that are neither a preview's baseline nor a listed reference. */
    val stale: List<String> = baselines
        .filter { it.base == null || (it.base !in previewBases && it.base !in references) }
        .map { it.fileName }

    /** Listed references whose preview exists: the port must delete the reference test and the line. */
    val portedReferences: List<String> = references.filter { it in previewBases }.sorted()

    /** Listed references without any baseline file. */
    val missingReferences: List<String> = (references - baselines.mapNotNull { it.base }.toSet()).sorted()

    val problems: List<String>
        get() = stale.map { "stale baseline $it: no preview and not in ${BaselineDirectory.REFERENCES}; delete it" } +
            portedReferences.map {
                "reference $it still listed after its port: delete its XmlReferenceCapture test in :app and its " +
                    "line in ${BaselineDirectory.REFERENCES}"
            } +
            missingReferences.map { "reference $it is listed but has no baseline; record it with `shots app --record`" }

    fun report(): String = buildString {
        pendingReferences.forEach { appendLine("reference (pending port): $it") }
        problems.forEach { appendLine(it) }
    }
}
