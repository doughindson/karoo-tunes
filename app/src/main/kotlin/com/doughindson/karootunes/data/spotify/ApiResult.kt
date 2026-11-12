package com.doughindson.karootunes.data.spotify

sealed class ApiError(val message: String) {
    /** No response at all: no signal, companion app unreachable, timeout. */
    class Offline(message: String) : ApiError(message)
    class NotLoggedIn(message: String) : ApiError(message)
    /** [reason] is Spotify's machine-readable code, e.g. NO_ACTIVE_DEVICE or PREMIUM_REQUIRED. */
    class Http(val status: Int, message: String, val reason: String? = null) : ApiError(message)
    class Unexpected(message: String) : ApiError(message)

    override fun toString(): String = "${this::class.simpleName}($message)"
}

class ApiException(val error: ApiError) : Exception(error.message)

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>

    /** Spotify answered 204, e.g. "no active player". */
    data object NoContent : ApiResult<Nothing>

    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Ok -> ApiResult.Ok(transform(value))
    ApiResult.NoContent -> ApiResult.NoContent
    is ApiResult.Failure -> this
}

fun <T> ApiResult<T>.valueOrNull(): T? = (this as? ApiResult.Ok)?.value

fun <T> ApiResult<T>.errorOrNull(): ApiError? = (this as? ApiResult.Failure)?.error

/** For callers like paging sources that report failures as exceptions. NoContent becomes [default]. */
fun <T> ApiResult<T>.getOrThrow(default: () -> T): T = when (this) {
    is ApiResult.Ok -> value
    ApiResult.NoContent -> default()
    is ApiResult.Failure -> throw ApiException(error)
}
