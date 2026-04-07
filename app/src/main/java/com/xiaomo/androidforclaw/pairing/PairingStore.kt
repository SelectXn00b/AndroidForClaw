package com.xiaomo.androidforclaw.pairing

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * OpenClaw module: pairing
 * Source: OpenClaw/src/pairing/store.ts
 *
 * Persists pairing requests and paired devices.
 */
object PairingStore {

    private val requests = ConcurrentHashMap<String, PairingRequest>()
    private val devices = ConcurrentHashMap<String, PairedDevice>()
    /** Maps accountId -> set of deviceIds */
    private val accountDevices = ConcurrentHashMap<String, MutableSet<String>>()

    private fun generatePairingCode(): String {
        // 6-digit numeric code
        return (100_000..999_999).random().toString()
    }

    suspend fun createPairingRequest(deviceName: String): PairingRequest {
        val request = PairingRequest(
            id = UUID.randomUUID().toString(),
            code = generatePairingCode(),
            deviceName = deviceName
        )
        requests[request.id] = request
        return request
    }

    suspend fun getPairingRequest(id: String): PairingRequest? {
        return requests[id]
    }

    suspend fun upsertPairingRequest(request: PairingRequest) {
        requests[request.id] = request
    }

    suspend fun acceptPairing(requestId: String, accountId: String): PairedDevice {
        val request = requests[requestId]
            ?: throw IllegalArgumentException("Pairing request not found: $requestId")
        requests[requestId] = request.copy(
            status = PairingStatus.ACCEPTED,
            accountId = accountId
        )
        val now = System.currentTimeMillis()
        val device = PairedDevice(
            deviceId = UUID.randomUUID().toString(),
            deviceName = request.deviceName,
            pairedAt = now,
            lastSeenAt = now
        )
        devices[device.deviceId] = device
        accountDevices.getOrPut(accountId) { ConcurrentHashMap.newKeySet() }.add(device.deviceId)
        return device
    }

    suspend fun rejectPairing(requestId: String) {
        val request = requests[requestId] ?: return
        requests[requestId] = request.copy(status = PairingStatus.REJECTED)
    }

    suspend fun listPairedDevices(accountId: String): List<PairedDevice> {
        val deviceIds = accountDevices[accountId] ?: return emptyList()
        return deviceIds.mapNotNull { devices[it] }
    }

    suspend fun removePairedDevice(deviceId: String) {
        devices.remove(deviceId)
        // Remove from all account mappings
        accountDevices.values.forEach { it.remove(deviceId) }
    }

    suspend fun pruneExpiredRequests() {
        val now = System.currentTimeMillis()
        val iter = requests.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (entry.value.expiresAt < now && entry.value.status == PairingStatus.PENDING) {
                iter.remove()
            }
        }
    }
}
