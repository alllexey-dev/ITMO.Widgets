package dev.alllexey.itmowidgets.core.result

import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [block] and wraps its value. Cancellation is rethrown so structured concurrency keeps working; any other
 * [Exception] becomes a [AppResult.Failure] through [mapError]. Errors (`OutOfMemoryError` and the like) propagate.
 */
inline fun <T> appResultOf(mapError: (Exception) -> AppError, block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Exception) {
    AppResult.Failure(mapError(failure))
}
