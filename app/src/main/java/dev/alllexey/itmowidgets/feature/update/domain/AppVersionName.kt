package dev.alllexey.itmowidgets.feature.update.domain

/**
 * A release name such as `2.1` or `2.1.3-rc2`, ordered by its numeric segments.
 *
 * Plain string order is wrong here: it puts `2.10` before `2.9`. A pre-release
 * suffix carries the same numbers as the finished release but sorts below it, so
 * a `2.1-SNAPSHOT` build is still offered `2.1`.
 */
data class AppVersionName(val raw: String) : Comparable<AppVersionName> {

    private val numbers: List<Long> = raw.substringBefore(PRE_RELEASE_SEPARATOR)
        .split('.')
        .map { segment -> segment.takeWhile(Char::isDigit).toLongOrNull() ?: 0L }

    private val preRelease: String = raw.substringAfter(PRE_RELEASE_SEPARATOR, "")

    override fun compareTo(other: AppVersionName): Int {
        repeat(maxOf(numbers.size, other.numbers.size)) { index ->
            val difference = numbers.segment(index).compareTo(other.numbers.segment(index))
            if (difference != 0) return difference
        }
        return when {
            preRelease == other.preRelease -> 0
            // A missing suffix is the released build and wins over any pre-release of it.
            preRelease.isEmpty() -> 1
            other.preRelease.isEmpty() -> -1
            else -> preRelease.compareTo(other.preRelease)
        }
    }

    override fun toString(): String = raw

    private fun List<Long>.segment(index: Int): Long = getOrElse(index) { 0L }

    private companion object {
        const val PRE_RELEASE_SEPARATOR = '-'
    }
}
