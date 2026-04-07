package com.xiaomo.androidforclaw.routing

fun normalizeAccountId(accountId: String?): String =
    accountId?.trim()?.ifEmpty { null } ?: DEFAULT_ACCOUNT_ID

fun normalizeOptionalAccountId(accountId: String?): String? =
    accountId?.trim()?.ifEmpty { null }
