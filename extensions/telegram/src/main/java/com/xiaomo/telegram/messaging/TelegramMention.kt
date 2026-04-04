package com.xiaomo.telegram.messaging

object TelegramMention {

    fun isMentioned(mentions: List<String>, botUsername: String): Boolean {
        return mentions.any { it.equals("@$botUsername", ignoreCase = true) }
    }

    fun stripMention(text: String, botUsername: String): String {
        return text.replace("@$botUsername", "", ignoreCase = true).trim()
    }

    fun parseMentionsFromEntities(text: String, entities: List<MentionEntity>): List<String> {
        return entities.filter { it.type == "mention" }
            .mapNotNull { entity ->
                if (entity.offset + entity.length <= text.length) {
                    text.substring(entity.offset, entity.offset + entity.length)
                } else null
            }
    }

    data class MentionEntity(
        val type: String,
        val offset: Int,
        val length: Int
    )
}
