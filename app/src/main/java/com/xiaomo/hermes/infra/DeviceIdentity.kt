package com.xiaomo.hermes.infra

import java.io.File
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import org.json.JSONObject

/**
 * OpenClaw module: infra
 * Source: OpenClaw/src/infra/device-identity.ts
 *
 * Device identity management using Ed25519 keys.
 * Generates a persistent device identity (keypair + deviceId derived from public key hash).
 */

data class DeviceIdentity(
    val deviceId: String,
    val publicKeyRaw: ByteArray,
    val privateKeyRaw: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceIdentity) return false
        return deviceId == other.deviceId
    }

    override fun hashCode(): Int = deviceId.hashCode()
}

object DeviceIdentityManager {

    private const val STORED_VERSION = 1

    /**
     * Load or create device identity from the given file.
     * Aligned with TS loadOrCreateDeviceIdentity().
     */
    fun loadOrCreateDeviceIdentity(file: File): DeviceIdentity {
        // Try loading existing
        try {
            if (file.exists()) {
                val raw = file.readText(Charsets.UTF_8)
                val json = JSONObject(raw)
                if (json.optInt("version") == STORED_VERSION) {
                    val publicKeyB64 = json.getString("publicKeyBase64")
                    val privateKeyB64 = json.getString("privateKeyBase64")
                    val publicKeyRaw = base64UrlDecode(publicKeyB64)
                    val privateKeyRaw = base64UrlDecode(privateKeyB64)
                    val derivedId = fingerprintPublicKey(publicKeyRaw)
                    val storedId = json.optString("deviceId", "")
                    // Re-derive if mismatch (aligned with TS identity migration)
                    if (derivedId != storedId) {
                        json.put("deviceId", derivedId)
                        saveIdentityFile(file, json.toString(2))
                    }
                    return DeviceIdentity(derivedId, publicKeyRaw, privateKeyRaw)
                }
            }
        } catch (_: Exception) {
            // fall through to regenerate
        }

        // Generate new identity
        val identity = generateIdentity()
        val json = JSONObject().apply {
            put("version", STORED_VERSION)
            put("deviceId", identity.deviceId)
            put("publicKeyBase64", base64UrlEncode(identity.publicKeyRaw))
            put("privateKeyBase64", base64UrlEncode(identity.privateKeyRaw))
            put("createdAtMs", System.currentTimeMillis())
        }
        file.parentFile?.mkdirs()
        saveIdentityFile(file, json.toString(2))
        return identity
    }

    /** Sign a payload string with the device's private key. Returns base64url signature. */
    fun signDevicePayload(privateKeyRaw: ByteArray, payload: String): String {
        val keySpec = java.security.spec.PKCS8EncodedKeySpec(privateKeyRaw)
        val keyFactory = java.security.KeyFactory.getInstance("Ed25519")
        val privateKey = keyFactory.generatePrivate(keySpec)
        val sig = Signature.getInstance("Ed25519")
        sig.initSign(privateKey)
        sig.update(payload.toByteArray(Charsets.UTF_8))
        return base64UrlEncode(sig.sign())
    }

    /** Verify a device signature. Returns true if valid. */
    fun verifyDeviceSignature(publicKeyRaw: ByteArray, payload: String, signatureBase64Url: String): Boolean {
        return try {
            val keySpec = java.security.spec.X509EncodedKeySpec(publicKeyRaw)
            val keyFactory = java.security.KeyFactory.getInstance("Ed25519")
            val publicKey = keyFactory.generatePublic(keySpec)
            val sig = Signature.getInstance("Ed25519")
            sig.initVerify(publicKey)
            sig.update(payload.toByteArray(Charsets.UTF_8))
            val sigBytes = try {
                base64UrlDecode(signatureBase64Url)
            } catch (_: Exception) {
                java.util.Base64.getDecoder().decode(signatureBase64Url)
            }
            sig.verify(sigBytes)
        } catch (_: Exception) {
            false
        }
    }

    /** Derive deviceId (SHA-256 hex of raw public key) from public key bytes. */
    fun deriveDeviceIdFromPublicKey(publicKeyRaw: ByteArray): String? {
        return try {
            if (publicKeyRaw.isEmpty()) null else sha256Hex(publicKeyRaw)
        } catch (_: Exception) {
            null
        }
    }

    /** Get base64url of raw public key bytes. */
    fun publicKeyRawBase64Url(publicKeyRaw: ByteArray): String = base64UrlEncode(publicKeyRaw)

    // --- Internal ---

    private fun generateIdentity(): DeviceIdentity {
        val kpg = KeyPairGenerator.getInstance("Ed25519")
        val keyPair = kpg.generateKeyPair()
        val publicKeyRaw = keyPair.public.encoded
        val privateKeyRaw = keyPair.private.encoded
        val deviceId = fingerprintPublicKey(publicKeyRaw)
        return DeviceIdentity(deviceId, publicKeyRaw, privateKeyRaw)
    }

    private fun fingerprintPublicKey(publicKeyRaw: ByteArray): String = sha256Hex(publicKeyRaw)

    private fun saveIdentityFile(file: File, content: String) {
        val tmpFile = File(file.parent, "${file.name}.${java.util.UUID.randomUUID()}.tmp")
        try {
            tmpFile.writeText("$content\n", Charsets.UTF_8)
            if (!tmpFile.renameTo(file)) {
                tmpFile.copyTo(file, overwrite = true)
                tmpFile.delete()
            }
        } finally {
            tmpFile.delete()
        }
    }
}
