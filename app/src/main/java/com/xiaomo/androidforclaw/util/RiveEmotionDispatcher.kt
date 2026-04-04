package com.xiaomo.androidforclaw.util

import android.content.Context
import ai.openclaw.app.rive.RiveStateHolder

object RiveEmotionDispatcher {
    fun processAndDispatch(context: Context, text: String): String {
        val enabled = context.getSharedPreferences("forclaw_rive_avatar", Context.MODE_PRIVATE)
            .getBoolean("enabled", false)
        if (!enabled) return text

        val result = RiveEmotionTagParser.parse(text)
        if (result.emotion != null) {
            RiveStateHolder.fireTrigger(result.emotion)
        }
        return result.cleanText
    }
}
