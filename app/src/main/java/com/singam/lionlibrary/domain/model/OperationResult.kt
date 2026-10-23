package com.singam.lionlibrary.domain.model

interface AppError

sealed interface OperationResult<out T, out E : AppError> {
    data class Success<T>(val data: T) : OperationResult<T, Nothing>

    data class Failure<E : AppError>(val error: E) : OperationResult<Nothing, E>
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
