package dev.alllexey.itmowidgets.core.result

/**
 * A cached collection as a repository or a screen sees it: unknown until the first answer, then content or a typed
 * failure.
 */
sealed interface LoadState<out T> {

    data object Loading : LoadState<Nothing>

    /** Custom services are switched off, so nothing was requested. */
    data object Disabled : LoadState<Nothing>

    /** [value] to show; [error] is set when part of it could not be refreshed (a partial success). */
    data class Content<out T>(val value: T, val error: AppError? = null) : LoadState<T>

    data class Error(val error: AppError) : LoadState<Nothing>
}

fun <T> LoadState<T>.valueOrNull(): T? = (this as? LoadState.Content)?.value

fun LoadState<*>.errorOrNull(): AppError? = when (this) {
    is LoadState.Content -> error
    is LoadState.Error -> error
    LoadState.Loading, LoadState.Disabled -> null
}

inline fun <T, R> LoadState<T>.map(transform: (T) -> R): LoadState<R> = when (this) {
    is LoadState.Content -> LoadState.Content(transform(value), error)
    is LoadState.Error -> this
    LoadState.Loading -> LoadState.Loading
    LoadState.Disabled -> LoadState.Disabled
}
