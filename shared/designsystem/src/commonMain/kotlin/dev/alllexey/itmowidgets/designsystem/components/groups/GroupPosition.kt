package dev.alllexey.itmowidgets.designsystem.components.groups

/** Where a row stands in its connected group; the outer corners belong to the first and the last row. */
enum class GroupPosition {
    Single, First, Middle, Last;

    /** The row rounds its top corners outward and has no gap above it. */
    val isFirst: Boolean get() = this == Single || this == First

    /** The row rounds its bottom corners outward. */
    val isLast: Boolean get() = this == Single || this == Last

    companion object {
        /** The position of the row at [index] in a group of [size] rows. */
        fun of(index: Int, size: Int): GroupPosition = when {
            size <= 1 -> Single
            index == 0 -> First
            index == size - 1 -> Last
            else -> Middle
        }
    }
}
