package com.megasmsrouter.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "api_destinations")
data class ApiDestinationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val enabled: Boolean,
    val url: String,
    val method: String,
    /** AES-GCM encrypted JSON array of headers. */
    val headersEnc: String,
    val bodyType: String,
    val bodyTemplate: String,
    val authType: String,
    val authUser: String,
    /** Encrypted token / API key / password. */
    val authSecretEnc: String,
    val apiKeyHeader: String,
    val allowHttp: Boolean,
    val connectTimeoutSec: Int,
    val readTimeoutSec: Int,
    val maxRetries: Int,
    val retryNonIdempotent: Boolean,
    val successContains: String,
    val priority: Int,
    val lastStatus: String?,
    val lastStatusAt: Long?
)

@Entity(tableName = "routing_rules")
data class RoutingRuleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val enabled: Boolean,
    val priority: Int,
    /** One sender per line. */
    val senders: String,
    val normalizePhone: Boolean,
    /** One keyword per line. */
    val keywords: String,
    val pattern: String,
    val captureGroup: Int,
    val minDigits: Int,
    val maxDigits: Int
)

@Entity(
    tableName = "rule_destinations",
    primaryKeys = ["ruleId", "destinationId"],
    foreignKeys = [
        ForeignKey(
            entity = RoutingRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["ruleId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ApiDestinationEntity::class,
            parentColumns = ["id"],
            childColumns = ["destinationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("destinationId")]
)
data class RuleDestinationCrossRef(val ruleId: String, val destinationId: String)

/** No SMS body, code or auth header is stored here. */
@Entity(tableName = "request_history")
data class RequestHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val destinationId: String,
    val destinationName: String,
    val method: String,
    val host: String,
    val statusCode: Int?,
    val durationMs: Long,
    val success: Boolean,
    val retryCount: Int,
    val note: String
)

/** Work item waiting for delivery. Payload is encrypted and the row is deleted when finished. */
@Entity(tableName = "pending_deliveries")
data class PendingDeliveryEntity(
    @PrimaryKey val id: String,
    val destinationId: String,
    val messageId: String,
    val payloadEnc: String,
    val createdAt: Long
)

/** Only a SHA-256 hash of (sender, timestamp, body) for duplicate detection. */
@Entity(tableName = "processed_messages")
data class ProcessedMessageEntity(
    @PrimaryKey val msgKey: String,
    val processedAt: Long
)
