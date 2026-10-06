package dev.alllexey.itmowidgets.feature.recordbook.domain.model

/** Preserve server hierarchy/order; never sum parent totals together with child scores. */
data class RecordbookControlRow(val control: RecordbookControl, val depth: Int)

fun recordbookControlOutline(controls: List<RecordbookControl>): List<RecordbookControlRow> {
    val emitted = mutableSetOf<Int>()
    val ids = controls.map { it.id }.toSet()
    return buildList {
        fun append(index: Int, depth: Int) {
            if (!emitted.add(index)) return
            val control = controls[index]
            add(RecordbookControlRow(control, depth))
            controls.indices.filter { controls[it].parentId == control.id }
                .forEach { append(it, depth + 1) }
        }
        controls.indices.filter { controls[it].parentId !in ids }.forEach { append(it, 0) }
        // Orphans, duplicate IDs and malformed cycles must not hide work from the student.
        controls.indices.forEach { append(it, 0) }
    }
}
