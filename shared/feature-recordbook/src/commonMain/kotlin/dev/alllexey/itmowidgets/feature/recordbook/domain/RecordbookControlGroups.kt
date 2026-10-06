package dev.alllexey.itmowidgets.feature.recordbook.domain

import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.recordbookControlOutline

/** One row of the subject's score list: a lone control or a group of related ones. */
sealed interface ControlEntry {
    data class Single(val control: RecordbookControl) : ControlEntry
}

/** Groups the UI names with its own string; any other group keeps [ControlGroup.title]. */
enum class ControlGroupKind { LABS, TESTS, PRACTICALS, HOMEWORK }

data class ControlGroup(
    /** The shared control name without its number, or the tree node's name. */
    val title: String,
    val controls: List<RecordbookControl>,
    /** Sum of known scores only; `null` while none of them is graded. */
    val score: Double?,
    val maximum: Double?,
    val belowMinimum: List<RecordbookControl>,
    val kind: ControlGroupKind? = null
) : ControlEntry

/** A graded control under its positive minimum; an ungraded one is not below anything yet. */
val RecordbookControl.isBelowMinimum: Boolean
    get() {
        val score = score ?: return false
        val minimum = minimum ?: return false
        return minimum > 0 && score < minimum
    }

object RecordbookControlGroups {
    private val trailingNumber = Regex("\\s*(№\\s*)?\\d+$")
    private val kinds = mapOf(
        "лабораторная работа" to ControlGroupKind.LABS,
        "контрольная работа" to ControlGroupKind.TESTS,
        "практическая работа" to ControlGroupKind.PRACTICALS,
        "домашнее задание" to ControlGroupKind.HOMEWORK
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
                    emitted.add(key) -> add(group(stripNumber(root.name), numbered.getValue(key), kinds[key]))
                }
            }
        }
    }

    private fun group(title: String, controls: List<RecordbookControl>, kind: ControlGroupKind? = null) = ControlGroup(
        title = title,
        controls = controls,
        score = controls.mapNotNull { it.score }.takeIf { it.isNotEmpty() }?.sum(),
        maximum = controls.mapNotNull { it.maximum }.takeIf { it.isNotEmpty() }?.sum(),
        belowMinimum = controls.filter { it.isBelowMinimum },
        kind = kind
    )

    private fun key(name: String): String = subjectNameKey(stripNumber(name))

    private fun stripNumber(name: String): String = name.trim().replace(trailingNumber, "").ifBlank { name.trim() }
}
