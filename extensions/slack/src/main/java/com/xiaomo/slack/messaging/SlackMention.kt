package com.xiaomo.slack.messaging

object SlackMention {

    private val USER_MENTION_REGEX = Regex("<@(\\w+)>")

    fun isMentioned(text: String, botId: String): Boolean {
        return text.contains("<@$botId>")
    }

    fun stripMention(text: String, botId: String): String {
        return text.replace("<@$botId>", "").trim()
    }

    fun parseMentions(text: String): List<String> {
        return USER_MENTION_REGEX.findAll(text).map { it.groupValues[1] }.toList()
    }

    fun formatMention(userId: String): String = "<@$userId>"
}
