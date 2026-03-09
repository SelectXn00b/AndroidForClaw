package com.xiaomo.androidforclaw.gateway.protocol

/**
 * Gateway error exception
 */
class GatewayError(
    val code: String,
    message: String,
    val details: Any? = null
) : Exception(message)
