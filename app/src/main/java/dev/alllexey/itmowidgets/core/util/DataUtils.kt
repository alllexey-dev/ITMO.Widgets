package dev.alllexey.itmowidgets.core.util

import dev.alllexey.itmowidgets.core.result.AppError

sealed interface DataState<out T> {
    data class Success<out T>(val data: T) : DataState<T>
    data class Error(val error: AppError) : DataState<Nothing>
}

sealed interface CustomDataState<out T> {
    data object Disabled : CustomDataState<Nothing>
    data class Success<out T>(val data: T) : CustomDataState<T>
    data class Error(val error: AppError) : CustomDataState<Nothing>
}

sealed interface MergedDataState<out T> {
    data class Success<out T>(val data: T) : MergedDataState<T>
    data class PartialSuccess<out T>(
        val data: T,
        val error: AppError
    ) : MergedDataState<T>
    data class Error(val error: AppError) : MergedDataState<Nothing>

    companion object {
        fun <T> of(data: T, error: AppError?): MergedDataState<T> {
            return error?.let { PartialSuccess(data, it) } ?: Success(data)
        }
    }
}

inline fun <T, R> DataState<T>.fold(
    onSuccess: (T) -> R,
    onError: (AppError) -> R
): R = when (this) {
    is DataState.Success -> onSuccess(data)
    is DataState.Error -> onError(error)
}

inline fun <T, R> CustomDataState<T>.fold(
    onDisabled: () -> R,
    onSuccess: (T) -> R,
    onError: (AppError) -> R
): R = when (this) {
    CustomDataState.Disabled -> onDisabled()
    is CustomDataState.Success -> onSuccess(data)
    is CustomDataState.Error -> onError(error)
}

inline fun <T, R> MergedDataState<T>.fold(
    onSuccess: (T) -> R,
    onPartialSuccess: (T, AppError) -> R,
    onError: (AppError) -> R
): R = when (this) {
    is MergedDataState.Success -> onSuccess(data)
    is MergedDataState.PartialSuccess -> onPartialSuccess(data, error)
    is MergedDataState.Error -> onError(error)
}

fun <T> DataState<T>.dataOrNull(): T? =
    (this as? DataState.Success)?.data

fun DataState<*>.errorOrNull(): AppError? =
    (this as? DataState.Error)?.error

fun <T> CustomDataState<T>.dataOrNull(): T? =
    (this as? CustomDataState.Success)?.data

fun CustomDataState<*>.errorOrNull(): AppError? =
    (this as? CustomDataState.Error)?.error

fun <T> MergedDataState<T>.dataOrNull(): T? = when (this) {
    is MergedDataState.Success -> data
    is MergedDataState.PartialSuccess -> data
    is MergedDataState.Error -> null
}

fun MergedDataState<*>.errorOrNull(): AppError? = when (this) {
    is MergedDataState.Success -> null
    is MergedDataState.PartialSuccess -> error
    is MergedDataState.Error -> error
}

val DataState<*>.isSuccess get() = this is DataState.Success
val DataState<*>.isError get() = this is DataState.Error

val CustomDataState<*>.isDisabled get() = this is CustomDataState.Disabled
val CustomDataState<*>.isSuccess get() = this is CustomDataState.Success
val CustomDataState<*>.isError get() = this is CustomDataState.Error

val MergedDataState<*>.isPartialSuccess get() = this is MergedDataState.PartialSuccess
val MergedDataState<*>.isSuccess get() = this is MergedDataState.Success
val MergedDataState<*>.isError get() = this is MergedDataState.Error
