package dev.alllexey.itmowidgets.feature.recordbook.domain

import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.recordbookControlOutline

/** One row of the subject's score list: a lone control or a group of related ones. */
sealed interface ControlEntry {
    data class Single(val control: RecordbookControl) : ControlEntry
}

data class ControlGroup(
    val title: String,
    val controls: List<RecordbookControl>,
    /** Sum of known scores only; `null` while none of them is graded. */
    val score: Double?,
    val maximum: Double?,
    val belowMinimum: List<RecordbookControl>
) : ControlEntry

object RecordbookControlGroups {
    private val trailingNumber = Regex("\\s*(№\\s*)?\\d+$")
    private val titles = mapOf(
        "лабораторная работа" to "Лабораторные",
        "контрольная работа" to "Контрольные",
        "практическая работа" to "Практические",
        "домашнее задание" to "Домашние задания"
    )

    /** Keeps server order; a group takes the place of its first control. */
    fun groupControls(controls: List<RecordbookControl>): List<ControlEntry> {
        val trees = mutableListOf<Pair<RecordbookControl, MutableList<RecordbookControl>>>()
        recordbookControlOutline(controls).forEach { row ->
            if (row.depth == 0) {
                trees += row.control to mutableListOf()
            } else {
                val leaves = trees.last().second
                // Pre-order puts a parent right before its first child; a parent carries its
                // own total, so only leaves are listed and summed.
                if (leaves.lastOrNull()?.id == row.control.parentId) leaves.removeAt(leaves.lastIndex)
                leaves += row.control
            }
        }
        val numbered = trees.filter { it.second.isEmpty() }.map { it.first }
            .groupBy { key(it.name) }.filterValues { it.size >= 2 }
        val emitted = mutableSetOf<String>()
        return buildList {
            trees.forEach { (root, leaves) ->
                val key = key(root.name)
                when {
                    leaves.isNotEmpty() -> add(group(root.name, leaves))
                    key !in numbered -> add(ControlEntry.Single(root))
                    emitted.add(key) -> add(group(titles[key] ?: stripNumber(root.name), numbered.getValue(key)))
                }
            }
        }
    }

    private fun group(title: String, controls: List<RecordbookControl>) = ControlGroup(
        title = title,
        controls = controls,
        score = controls.mapNotNull { it.score }.takeIf { it.isNotEmpty() }?.sum(),
        maximum = controls.mapNotNull { it.maximum }.takeIf { it.isNotEmpty() }?.sum(),
        belowMinimum = controls.filter { control ->
            val score = control.score
            val minimum = control.minimum
            score != null && minimum != null && minimum > 0 && score < minimum
        }
    )

    private fun key(name: String): String = subjectNameKey(stripNumber(name))

    private fun stripNumber(name: String): String = name.trim().replace(trailingNumber, "").ifBlank { name.trim() }
}
