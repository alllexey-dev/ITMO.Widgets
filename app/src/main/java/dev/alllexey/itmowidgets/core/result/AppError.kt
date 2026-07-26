package dev.alllexey.itmowidgets.core.result

sealed interface AppError {

    data object Network : AppError

    data object Unauthorized : AppError

    data object Forbidden : AppError

    data object NotFound : AppError

    /** Requested data lives on the project backend, which the user has not opted into. */
    data object CustomServicesDisabled : AppError

    data class Unknown(val cause: Throwable? = null) : AppError
}

sealed interface AppResult<out T> {

    data class Success<out T>(val value: T) : AppResult<T>

    data class Failure(val error: AppError) : AppResult<Nothing>
}
