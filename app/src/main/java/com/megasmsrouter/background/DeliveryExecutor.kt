package com.megasmsrouter.background

import com.megasmsrouter.data.AppDatabase
import com.megasmsrouter.data.entities.RequestHistoryEntity
import com.megasmsrouter.data.repository.PayloadCodec
import com.megasmsrouter.data.repository.toDomain
import com.megasmsrouter.domain.model.ApiDestination
import com.megasmsrouter.network.*
import com.megasmsrouter.security.SecretCipher
import com.megasmsrouter.util.hostOf

/**
 * Delivers one queued request. Delivery is AT-LEAST-ONCE: if the app dies after the server
 * answered but before the row is deleted, the request may be sent again. Exactly-once is only
 * possible if the destination honours the Idempotency-Key header (sent when retries of
 * POST/PATCH are enabled).
 */
class DeliveryExecutor(
    private val db: AppDatabase,
    private val cipher: SecretCipher,
    private val client: ApiClient,
    private val isForwardingEnabled: () -> Boolean,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    enum class Outcome { DONE, RETRY }

    /** @param attempt WorkManager runAttemptCount (0 for the first attempt) */
    suspend fun run(pendingId: String, attempt: Int): Outcome {
        val pendingDao = db.pendingDao()
        val pending = pendingDao.get(pendingId) ?: return Outcome.DONE
        val destEntity = db.destinationDao().getById(pending.destinationId)
        if (destEntity == null || !destEntity.enabled || !isForwardingEnabled()) {
            pendingDao.delete(pendingId) // paused / disabled / deleted: drop the queued data
            return Outcome.DONE
        }
        val dest = destEntity.toDomain(cipher)
        val payload = PayloadCodec.decode(cipher.decrypt(pending.payloadEnc), dest.id, pending.messageId)
        if (payload == null) {
            pendingDao.delete(pendingId)
            return Outcome.DONE
        }

        val idempotencyKey =
            if (!dest.method.isIdempotent && dest.retryNonIdempotent) "${pending.messageId}:${dest.id}" else null

        val request = try {
            RequestBuilder.build(dest, payload, idempotencyKey)
        } catch (e: Exception) {
            record(dest, NetworkResult.Failure(FailureKind.BUILD, e.javaClass.simpleName, 0), attempt)
            pendingDao.delete(pendingId)
            return Outcome.DONE
        }

        val result = client.send(request, dest)
        record(dest, result, attempt)

        val retry = RetryPolicy.shouldRetry(result, dest.method, dest.retryNonIdempotent, attempt, dest.maxRetries)
        if (result is NetworkResult.Success || !retry) {
            pendingDao.delete(pendingId)
            return Outcome.DONE
        }
        return Outcome.RETRY
    }

    private suspend fun record(dest: ApiDestination, r: NetworkResult, attempt: Int) {
        val now = clock()
        db.historyDao().insert(
            RequestHistoryEntity(
                timestamp = now,
                destinationId = dest.id,
                destinationName = dest.name,
                method = dest.method.name,
                host = hostOf(dest.url),
                statusCode = r.statusCode,
                durationMs = r.durationMs,
                success = r is NetworkResult.Success,
                retryCount = attempt,
                note = r.summary()
            )
        )
        db.destinationDao().setLastStatus(dest.id, r.summary(), now)
        db.historyDao().prune()
    }
}
