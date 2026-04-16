/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */
package com.xiaomo.hermes.util

import okhttp3.logging.HttpLoggingInterceptor

object AppConstants {
    // ============= HTTP Logging =============
    val HTTP_LOG_LEVEL: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.NONE

    // ============= API 配置说明 =============
    // 所有 API 配置现在从以下配置文件读取：
    // - /sdcard/.hermes/config/models.json (模型提供商配置)
    // - /sdcard/.hermes/openclaw.json (OpenClaw 主配置)
    //
    // 请勿在此文件中硬编码 API Key 和 Base URL
    // 使用 ConfigLoader.loadModelsConfig() 和 ConfigLoader.loadOpenClawConfig() 读取配置
    //
    // 参考文档：
    // - CLAUDE.md: Configuration System
    // - doc/OpenClaw架构深度分析.md: 配置系统说明

    // ============= 环境变量常量（用于配置文件的 ${VAR_NAME} 替换） =============
    // 这些常量会被 ConfigLoader 通过反射读取，用于替换配置文件中的环境变量占位符
    // 优先级：系统环境变量 > AppConstants 常量 > MMKV 存储
    //
    // ⚠️ 开源版本：请在配置文件中设置 API Key，不要在此处硬编码
    // 配置文件位置：/sdcard/.hermes/config/models.json
    const val OPENROUTER_API_KEY = ""  // 请在 /sdcard/.hermes/config/models.json 中配置

}
