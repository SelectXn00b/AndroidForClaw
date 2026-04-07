package com.xiaomo.androidforclaw.pairing

import com.xiaomo.androidforclaw.infra.generateNumericCode
import com.xiaomo.androidforclaw.infra.generateSecureToken

/**
 * OpenClaw module: pairing
 * Source: OpenClaw/src/pairing/challenge.ts
 *
 * Generates and validates pairing challenge tokens.
 */
object PairingChallenge {

    fun generatePairingCode(digits: Int = 6): String = generateNumericCode(digits)

    fun generateChallengeToken(): String = generateSecureToken(32)

    fun isCodeExpired(request: PairingRequest): Boolean =
        System.currentTimeMillis() > request.expiresAt

    fun validateCode(input: String, request: PairingRequest): Boolean {
        if (isCodeExpired(request)) return false
        return input.trim() == request.code
    }
}
