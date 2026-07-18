package dev.alllexey.itmowidgets.core.util

sealed interface DataState<out T> {
    data class Success<out T>(val data: T) : DataState<T>
    data class Error(val throwable: Throwable) : DataState<Nothing>
}

sealed interface CustomDataState<out T> {
    data object Disabled : CustomDataState<Nothing>
    data class Success<out T>(val data: T) : CustomDataState<T>
    data class Error(val throwable: Throwable) : CustomDataState<Nothing>
}

sealed interface MergedDataState<out T> {
    data class Success<out T>(val data: T) : MergedDataState<T>
    data class PartialSuccess<out T>(
        val data: T,
        val throwable: Throwable
    ) : MergedDataState<T>
    data class Error(val throwable: Throwable) : MergedDataState<Nothing>


    companion object {
        fun <T> of(data: T, throwable: Throwable?): MergedDataState<T> {
            return throwable?.let { PartialSuccess(data, it) } ?: Success(data)
        }
    }
}

inline fun <T, R> DataState<T>.fold(
    onSuccess: (T) -> R,
    onError: (Throwable) -> R
): R = when (this) {
    is DataState.Success -> onSuccess(data)
    is DataState.Error -> onError(throwable)
}

inline fun <T, R> CustomDataState<T>.fold(
    onDisabled: () -> R,
    onSuccess: (T) -> R,
    onError: (Throwable) -> R
): R = when (this) {
    CustomDataState.Disabled -> onDisabled()
    is CustomDataState.Success -> onSuccess(data)
    is CustomDataState.Error -> onError(throwable)
}

inline fun <T, R> MergedDataState<T>.fold(
    onSuccess: (T) -> R,
    onPartialSuccess: (T, Throwable) -> R,
    onError: (Throwable) -> R
): R = when (this) {
    is MergedDataState.Success -> onSuccess(data)
    is MergedDataState.PartialSuccess -> onPartialSuccess(data, throwable)
    is MergedDataState.Error -> onError(throwable)
}

fun <T> DataState<T>.dataOrNull(): T? =
    (this as? DataState.Success)?.data

fun DataState<*>.throwableOrNull(): Throwable? =
    (this as? DataState.Error)?.throwable

fun <T> CustomDataState<T>.dataOrNull(): T? =
    (this as? CustomDataState.Success)?.data

fun CustomDataState<*>.throwableOrNull(): Throwable? =
    (this as? CustomDataState.Error)?.throwable

fun <T> MergedDataState<T>.dataOrNull(): T? = when (this) {
    is MergedDataState.Success -> data
    is MergedDataState.PartialSuccess -> data
    is MergedDataState.Error -> null
}

fun MergedDataState<*>.throwableOrNull(): Throwable? = when (this) {
    is MergedDataState.Success -> null
    is MergedDataState.PartialSuccess -> throwable
    is MergedDataState.Error -> throwable
}

val DataState<*>.isSuccess get() = this is DataState.Success
val DataState<*>.isError get() = this is DataState.Error

val CustomDataState<*>.isDisabled get() = this is CustomDataState.Disabled
val CustomDataState<*>.isSuccess get() = this is CustomDataState.Success
val CustomDataState<*>.isError get() = this is CustomDataState.Error

val MergedDataState<*>.isPartialSuccess get() = this is MergedDataState.PartialSuccess
val MergedDataState<*>.isSuccess get() = this is MergedDataState.Success
val MergedDataState<*>.isError get() = this is MergedDataState.Error
