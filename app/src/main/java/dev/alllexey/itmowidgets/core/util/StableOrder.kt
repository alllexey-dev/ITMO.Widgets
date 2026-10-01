package dev.alllexey.itmowidgets.core.util

/**
 * Keeps a ranked list in the order the viewer first saw while a screen is open: a vote changes a score but must not
 * move its row under the finger. [arrange] sorts what it gets by the ids it has shown before; ids it has not shown
 * yet follow in the order they come in, that is by their ranking. [reset] lets the next list take its own ranking
 * on an explicit refresh; a new screen gets a new instance and ranks afresh.
 */
class StableOrder {
    private var shown: Map<String, Int>? = null

    fun <T> arrange(items: List<T>, id: (T) -> String): List<T> {
        val order = shown
        val arranged = if (order == null) items else items.sortedBy { order[id(it)] ?: Int.MAX_VALUE }
        shown = arranged.withIndex().associate { (index, item) -> id(item) to index }
        return arranged
    }

    fun reset() {
        shown = null
    }
}
