package com.xiaomo.androidforclaw.pairing

/**
 * OpenClaw module: pairing
 * Source: OpenClaw/src/pairing/store.ts
 *
 * Persists pairing requests and paired devices.
 */
object PairingStore {

    suspend fun createPairingRequest(deviceName: String): PairingRequest {
        TODO("Generate code, persist request, return PairingRequest")
    }

    suspend fun getPairingRequest(id: String): PairingRequest? {
        TODO("Lookup pairing request by ID")
    }

    suspend fun upsertPairingRequest(request: PairingRequest) {
        TODO("Insert or update pairing request")
    }

    suspend fun acceptPairing(requestId: String, accountId: String): PairedDevice {
        TODO("Mark request as ACCEPTED, create PairedDevice record")
    }

    suspend fun rejectPairing(requestId: String) {
        TODO("Mark request as REJECTED")
    }

    suspend fun listPairedDevices(accountId: String): List<PairedDevice> {
        TODO("Return all devices paired to the given account")
    }

    suspend fun removePairedDevice(deviceId: String) {
        TODO("Unpair and delete device record")
    }

    suspend fun pruneExpiredRequests() {
        TODO("Delete all requests past expiresAt")
    }
}
