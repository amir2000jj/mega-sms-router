package com.megasmsrouter.network

import com.megasmsrouter.domain.model.HttpMethod

object RetryPolicy {
    private val RETRYABLE_HTTP = setOf(408, 425, 429, 500, 502, 503, 504)

    /**
     * @param retriesDone how many retries were already made (0 on the first attempt)
     * Non-idempotent methods (POST/PATCH) are retried only if the user opted in (an Idempotency-Key
     * header is then sent) or if the request provably never left the device (OFFLINE).
     */
    fun shouldRetry(
        result: NetworkResult,
        method: HttpMethod,
        retryNonIdempotent: Boolean,
        retriesDone: Int,
        maxRetries: Int
    ): Boolean {
        if (retriesDone >= maxRetries) return false
        return when (result) {
            is NetworkResult.Success -> false
            is NetworkResult.HttpError ->
                !result.bodyMismatch && result.code in RETRYABLE_HTTP && (method.isIdempotent || retryNonIdempotent)
            is NetworkResult.Failure -> when (result.kind) {
                FailureKind.OFFLINE -> true
                FailureKind.TIMEOUT, FailureKind.IO -> method.isIdempotent || retryNonIdempotent
                FailureKind.TLS, FailureKind.BLOCKED, FailureKind.BUILD -> false
            }
        }
    }
}
