package com.xiaomo.androidforclaw.pairing

/**
 * OpenClaw module: pairing
 * Source: OpenClaw/src/pairing/types.ts
 */

enum class PairingStatus { PENDING, ACCEPTED, REJECTED, EXPIRED }

data class PairingRequest(
    val id: String,
    val code: String,
    val deviceName: String,
    val status: PairingStatus = PairingStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = createdAt + 600_000,
    val accountId: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class PairedDevice(
    val deviceId: String,
    val deviceName: String,
    val pairedAt: Long,
    val lastSeenAt: Long? = null,
    val platform: String? = null
)
