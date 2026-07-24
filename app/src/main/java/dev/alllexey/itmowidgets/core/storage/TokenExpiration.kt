package dev.alllexey.itmowidgets.core.storage

internal fun calculateTokenExpiration(
    nowMillis: Long,
    lifetimeSeconds: Long
): Long {
    return runCatching {
        Math.addExact(
            nowMillis,
            Math.multiplyExact(lifetimeSeconds, MILLIS_PER_SECOND)
        )
    }.getOrDefault(Long.MAX_VALUE)
}

private const val MILLIS_PER_SECOND = 1000L
