package com.xiaomo.telegram.policy

import com.xiaomo.telegram.TelegramConfig

class TelegramPolicy(private val config: TelegramConfig) {

    enum class DmPolicyType { OPEN, ALLOWLIST, DENYLIST }
    enum class GroupPolicyType { OPEN, ALLOWLIST, DENYLIST }

    fun resolveDmPolicy(): DmPolicyType {
        return when (config.dmPolicy.lowercase()) {
            "open" -> DmPolicyType.OPEN
            "allowlist" -> DmPolicyType.ALLOWLIST
            "denylist" -> DmPolicyType.DENYLIST
            else -> DmPolicyType.OPEN
        }
    }

    fun resolveGroupPolicy(): GroupPolicyType {
        return when (config.groupPolicy.lowercase()) {
            "open" -> GroupPolicyType.OPEN
            "allowlist" -> GroupPolicyType.ALLOWLIST
            "denylist" -> GroupPolicyType.DENYLIST
            else -> GroupPolicyType.OPEN
        }
    }

    fun isDmAllowed(senderId: String): Boolean {
        return when (resolveDmPolicy()) {
            DmPolicyType.OPEN -> true
            DmPolicyType.ALLOWLIST -> false
            DmPolicyType.DENYLIST -> true
        }
    }

    fun isGroupAllowed(groupId: String): Boolean {
        return when (resolveGroupPolicy()) {
            GroupPolicyType.OPEN -> true
            GroupPolicyType.ALLOWLIST -> false
            GroupPolicyType.DENYLIST -> true
        }
    }

    fun requiresMention(): Boolean = config.requireMention
}
