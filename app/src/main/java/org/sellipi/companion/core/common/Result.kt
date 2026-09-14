package org.sellipi.companion.core.common

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Error(val exception: Throwable, val message: String? = exception.message) : AppResult<Nothing>
    data object Loading : AppResult<Nothing>
}
