package com.megasmsrouter.network

enum class FailureKind { OFFLINE, TIMEOUT, TLS, IO, BLOCKED, BUILD }

sealed interface NetworkResult {
    val durationMs: Long

    data class Success(val code: Int, override val durationMs: Long) : NetworkResult
    data class HttpError(val code: Int, override val durationMs: Long, val bodyMismatch: Boolean = false) : NetworkResult
    data class Failure(val kind: FailureKind, val detail: String, override val durationMs: Long) : NetworkResult
}

val NetworkResult.statusCode: Int?
    get() = when (this) {
        is NetworkResult.Success -> code
        is NetworkResult.HttpError -> code
        is NetworkResult.Failure -> null
    }

/** Short, secret-free description (safe for history and UI). */
fun NetworkResult.summary(): String = when (this) {
    is NetworkResult.Success -> "HTTP $code"
    is NetworkResult.HttpError -> if (bodyMismatch) "HTTP $code (body)" else "HTTP $code"
    is NetworkResult.Failure -> kind.name
}
