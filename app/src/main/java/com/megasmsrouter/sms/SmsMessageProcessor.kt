package com.megasmsrouter.sms

import com.megasmsrouter.background.DeliveryScheduler
import com.megasmsrouter.data.AppDatabase
import com.megasmsrouter.data.entities.PendingDeliveryEntity
import com.megasmsrouter.data.entities.ProcessedMessageEntity
import com.megasmsrouter.data.repository.PayloadCodec
import com.megasmsrouter.data.repository.toDomain
import com.megasmsrouter.domain.model.IncomingSms
import com.megasmsrouter.domain.model.MessagePayload
import com.megasmsrouter.domain.model.Placeholders
import com.megasmsrouter.domain.parsing.DigitNormalizer
import com.megasmsrouter.domain.routing.MessageKey
import com.megasmsrouter.domain.routing.Router
import com.megasmsrouter.security.SecretCipher
import java.util.UUID

/**
 * Called from the receiver. Does only local, fast work: match -> dedupe -> queue encrypted
 * payload -> schedule delivery. No network I/O happens here.
 * A message that matches no enabled rule leaves NO trace at all.
 */
class SmsMessageProcessor(
    private val db: AppDatabase,
    private val cipher: SecretCipher,
    private val isForwardingEnabled: () -> Boolean,
    private val scheduler: DeliveryScheduler,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    /** @return number of deliveries queued */
    suspend fun process(sms: IncomingSms): Int {
        if (!isForwardingEnabled()) return 0

        val refs = db.ruleDao().getRefs().groupBy { it.ruleId }
        val rules = db.ruleDao().getAll().map { r -> r.toDomain(refs[r.id].orEmpty().map { it.destinationId }) }
        if (rules.none { it.enabled }) return 0
        val destinations = db.destinationDao().getAll().filter { it.enabled }.associateBy { it.id }
        if (destinations.isEmpty()) return 0

        val planned = Router.route(sms.sender, sms.body, rules, destinations.keys)
        if (planned.isEmpty()) return 0

        val now = clock()
        val key = MessageKey.compute(sms.sender, sms.timestamp, sms.body)
        if (db.processedDao().insert(ProcessedMessageEntity(key, now)) == -1L) return 0 // duplicate

        val messageId = UUID.randomUUID().toString()
        var queued = 0
        for (p in planned) {
            val dest = destinations[p.destinationId]?.toDomain(cipher) ?: continue
            val used = Placeholders.usedBy(dest)
            // Data minimisation: keep only the fields this destination's template really uses.
            val payload = MessagePayload(
                code = if ("code" in used) DigitNormalizer.normalize(p.code) else "",
                sender = if ("sender" in used) sms.sender else "",
                message = if ("message" in used) sms.body else "",
                timestamp = sms.timestamp,
                destinationId = dest.id,
                messageId = messageId
            )
            val pendingId = UUID.randomUUID().toString()
            db.pendingDao().insert(
                PendingDeliveryEntity(
                    id = pendingId,
                    destinationId = dest.id,
                    messageId = messageId,
                    payloadEnc = cipher.encrypt(PayloadCodec.encode(payload)),
                    createdAt = now
                )
            )
            scheduler.enqueue(pendingId)
            queued++
        }

        db.processedDao().deleteOlderThan(now - SEVEN_DAYS)
        db.pendingDao().deleteOlderThan(now - TWO_DAYS)
        return queued
    }

    private companion object {
        const val SEVEN_DAYS = 7L * 24 * 3600 * 1000
        const val TWO_DAYS = 2L * 24 * 3600 * 1000
    }
}
