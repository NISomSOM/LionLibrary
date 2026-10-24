package com.singam.lionlibrary.domain.model

interface AppError

sealed interface OperationResult<out T, out E : AppError> {
    data class Success<T>(val data: T) : OperationResult<T, Nothing>

    data class Failure<E : AppError>(val error: E) : OperationResult<Nothing, E>
}

inline fun <T, E : AppError, R> OperationResult<T, E>.map(
    transform: (T) -> R
): OperationResult<R, E> =
    when (this) {
        is OperationResult.Success -> OperationResult.Success(transform(data))
        is OperationResult.Failure -> this
    }

enum class DataError : AppError {
    UNAUTHORIZED,
    OFFLINE,
    SERVER,
    NOT_FOUND,
    PERMISSION,
    STORAGE,
    UNKNOWN,
}
