/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */
package com.xiaomo.hermes.ui.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.xiaomo.hermes.ui.skills.SkillsMarketScreen
import ai.openclaw.app.ui.OpenClawTheme

/**
 * Skills 市场页面
 *
 * 布局：
 * 1. 搜索栏
 * 2. 分类筛选（全部/自动化/效率/开发工具...）
 * 3. 热门 Skills 列表（来自 awesome-openclaw-skills）
 * 4. 精选合集卡片（VoltAgent/中文精选/阿里云榜等）
 * 5. 底部聚合资源列表（ClawHub/AI Agent Store 等）
 */
class SkillsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            OpenClawTheme {
                SkillsMarketScreen()
            }
        }
    }
}
