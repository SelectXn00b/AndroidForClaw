/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */
package com.xiaomo.hermes.data.model

import android.graphics.Bitmap
import com.xiaomo.hermes.accessibility.service.ViewNode

data class ResultBean(
    val action: String? = null,
    val preImage: String? = null,
    val afterImage: String? = null
)

data class CheckResult(
    val lastScreenshot: Bitmap?,
    val newScreenshot: Bitmap,
    val lastPerceptionInfos: List<ViewNode>,
    val newPerceptionInfos: List<ViewNode>,
    val lastKeyboardActive: Boolean,
    val newKeyboardActive: Boolean,
    val summary: String,
    val action: String,
    // 反思评分是否为A
    val isA: Boolean
)
