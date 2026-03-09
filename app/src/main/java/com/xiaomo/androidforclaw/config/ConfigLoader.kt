package com.xiaomo.androidforclaw.config

import android.content.Context
import android.os.FileObserver
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

/**
 * 配置加载器 - 对齐 OpenClaw 的配置加载逻辑
 *
 * Features:
 * 1. Load config from JSON files
 * 2. Support environment variable substitution (${VAR_NAME})
 * 3. Support default config
 * 4. Config validation
 *
 * Reference: OpenClaw src/agents/models-config.ts
 */
class ConfigLoader(private val context: Context) {

    companion object {
        private const val TAG = "ConfigLoader"

        // Config file path - Use app private storage to avoid permission issues from UID changes
        // Note: Cannot use context.filesDir here because companion object is initialized at class load time
        // Actual path will be set dynamically in init block
        private var CONFIG_DIR = "/data/data/com.xiaomo.androidforclaw/files/config"
        private const val OPENCLAW_CONFIG_FILE = "openclaw.json"
    }

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    // Dynamic config directory (using app private storage)
    private val configDir: File
    private val openclawConfigFile: File

    init {
        // 使用应用私有存储空间（避免 UID 变化导致的权限问题）
        CONFIG_DIR = File(context.filesDir, "config").absolutePath
        configDir = File(CONFIG_DIR)
        openclawConfigFile = File(configDir, OPENCLAW_CONFIG_FILE)

        Log.d(TAG, "配置目录: ${configDir.absolutePath}")

        // 尝试从旧位置迁移配置文件（如果存在且可读）
        migrateConfigFromOldLocation()
    }

    /**
     * 从旧的 /sdcard/.androidforclaw 迁移配置到新位置
     *
     * Migration strategy:
     * 1. Try to copy file directly (if readable)
     * 2. If copy fails (permission issue), read via shell command and write
     *
     * Old location: /sdcard/.androidforclaw/openclaw.json (note: not in config subdirectory)
     * New location: /data/user/0/com.xiaomo.androidforclaw/files/config/openclaw.json
     */
    private fun migrateConfigFromOldLocation() {
        try {
            // Old config file is directly under .androidforclaw directory, not in config subdirectory
            val oldConfigFile = File("/sdcard/.androidforclaw", OPENCLAW_CONFIG_FILE)

            // If old config file exists and new config file does not exist, try to migrate
            if (oldConfigFile.exists() && !openclawConfigFile.exists()) {
                Log.i(TAG, "检测到旧配置文件，开始迁移: ${oldConfigFile.absolutePath}")
                ensureConfigDir()

                // Try method 1: direct copy (if has permission)
                try {
                    if (oldConfigFile.canRead()) {
                        oldConfigFile.copyTo(openclawConfigFile, overwrite = true)
                        Log.i(TAG, "✅ 配置文件迁移成功 (方法1-直接复制): ${openclawConfigFile.absolutePath}")
                        return
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "方法1失败: ${e.message}，尝试方法2...")
                }

                // Try method 2: read via shell (bypass permission restrictions)
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("cat", oldConfigFile.absolutePath))
                    val content = process.inputStream.bufferedReader().use { it.readText() }
                    val exitCode = process.waitFor()

                    if (exitCode == 0 && content.isNotBlank()) {
                        openclawConfigFile.writeText(content)
                        Log.i(TAG, "✅ 配置文件迁移成功 (方法2-shell读取): ${openclawConfigFile.absolutePath}")
                        Log.i(TAG, "   迁移的配置文件大小: ${content.length} bytes")
                        return
                    } else {
                        Log.w(TAG, "方法2失败: exit code $exitCode")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "方法2失败: ${e.message}")
                }

                Log.w(TAG, "⚠️ 所有迁移方法均失败，将使用默认配置")
            } else if (!oldConfigFile.exists()) {
                Log.d(TAG, "旧配置文件不存在，无需迁移")
            } else {
                Log.d(TAG, "新配置文件已存在，跳过迁移")
            }
        } catch (e: Exception) {
            Log.w(TAG, "配置文件迁移失败（将使用默认配置）: ${e.message}")
        }
    }

    // Config cache
    private var cachedOpenClawConfig: OpenClawConfig? = null
    private var openclawConfigCacheValid = false

    // Hot reload support
    private var fileObserver: FileObserver? = null
    private var hotReloadEnabled = false
    private var reloadCallback: ((OpenClawConfig) -> Unit)? = null

    /**
     * 加载 OpenClaw 主配置（带自动备份和恢复）
     */
    fun loadOpenClawConfig(): OpenClawConfig {
        // If cache is valid, return directly
        if (openclawConfigCacheValid && cachedOpenClawConfig != null) {
            Log.d(TAG, "返回缓存的 OpenClaw 配置")
            return cachedOpenClawConfig!!
        }

        // Use ConfigBackupManager for safe loading
        val backupManager = ConfigBackupManager(context)
        val config = backupManager.loadConfigSafely {
            loadOpenClawConfigInternal()
        }

        if (config != null) {
            cachedOpenClawConfig = config
            openclawConfigCacheValid = true
            return config
        } else {
            // If all recovery fails, return default config
            Log.w(TAG, "使用默认配置")
            val defaultConfig = createDefaultOpenClawConfigObject()
            cachedOpenClawConfig = defaultConfig
            openclawConfigCacheValid = true
            return defaultConfig
        }
    }

    /**
     * 内部加载方法（不带容错）
     */
    private fun loadOpenClawConfigInternal(): OpenClawConfig {
        try {
            // Ensure config directory exists
            ensureConfigDir()

            // If config file does not exist, create default config
            if (!openclawConfigFile.exists()) {
                Log.i(TAG, "OpenClaw 配置文件不存在，创建默认配置: ${openclawConfigFile.absolutePath}")
                createDefaultOpenClawConfig()
            }

            // Read config file
            val configJson = openclawConfigFile.readText()
            Log.d(TAG, "读取 OpenClaw 配置文件: ${openclawConfigFile.absolutePath}")

            // Replace environment variables
            val processedJson = replaceEnvVars(configJson)

            // Parse JSON
            val config = gson.fromJson(processedJson, OpenClawConfig::class.java)

            // Validate config
            validateOpenClawConfig(config)

            Log.i(TAG, "✅ OpenClaw 配置加载成功")
            return config

        } catch (e: Exception) {
            Log.e(TAG, "❌ OpenClaw 配置加载失败: ${e.message}", e)
            throw e // Throw exception, handled by ConfigBackupManager
        }
    }

    /**
     * 创建默认配置对象
     * 从 assets 中的 openclaw.json.example 加载
     */
    private fun createDefaultOpenClawConfigObject(): OpenClawConfig {
        val defaultConfigJson = try {
            context.assets.open("openclaw.json.example").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "无法从 assets 读取 openclaw.json.example: ${e.message}")
            throw Exception("Missing openclaw.json.example in assets", e)
        }
        return gson.fromJson(defaultConfigJson, OpenClawConfig::class.java)
    }


    /**
     * 获取指定 provider 的配置
     */
    fun getProviderConfig(providerName: String): ProviderConfig? {
        val openClawConfig = loadOpenClawConfig()
        return openClawConfig.resolveProviders()[providerName]
    }

    /**
     * 获取指定模型的定义
     */
    fun getModelDefinition(providerName: String, modelId: String): ModelDefinition? {
        val provider = getProviderConfig(providerName) ?: return null
        return provider.models.find { it.id == modelId }
    }

    /**
     * 列出所有可用的模型
     */
    fun listAllModels(): List<Pair<String, ModelDefinition>> {
        val config = loadOpenClawConfig()
        val models = mutableListOf<Pair<String, ModelDefinition>>()

        config.resolveProviders().forEach { (providerName, provider) ->
            provider.models.forEach { model ->
                models.add(providerName to model)
            }
        }

        return models
    }

    /**
     * 根据模型 ID 查找对应的 provider 名称
     */
    fun findProviderByModelId(modelId: String): String? {
        val openClawConfig = loadOpenClawConfig()
        openClawConfig.resolveProviders().forEach { (providerName, provider) ->
            if (provider.models.any { it.id == modelId }) {
                return providerName
            }
        }
        return null
    }


    /**
     * 保存 OpenClaw 配置
     */
    fun saveOpenClawConfig(config: OpenClawConfig): Boolean {
        return try {
            ensureConfigDir()
            val json = gson.toJson(config)
            openclawConfigFile.writeText(json)
            Log.i(TAG, "✅ OpenClaw 配置保存成功: ${openclawConfigFile.absolutePath}")
            // Clear cache, will reload next time
            openclawConfigCacheValid = false
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ OpenClaw 配置保存失败: ${e.message}", e)
            false
        }
    }


    /**
     * 重新加载 OpenClaw 配置（用于热重载）
     */
    fun reloadOpenClawConfig(): OpenClawConfig {
        Log.i(TAG, "重新加载 OpenClaw 配置...")
        openclawConfigCacheValid = false
        return loadOpenClawConfig()
    }

    /**
     * 启用配置热重载
     * Monitor config files, auto-reload on changes
     *
     * @param callback Callback function after config reload
     */
    fun enableHotReload(callback: ((OpenClawConfig) -> Unit)? = null) {
        if (hotReloadEnabled) {
            Log.d(TAG, "配置热重载已启用")
            return
        }

        this.reloadCallback = callback

        try {
            // Ensure config directory exists
            ensureConfigDir()

            // Monitor config directory
            fileObserver = object : FileObserver(configDir, MODIFY or CREATE or DELETE) {
                override fun onEvent(event: Int, path: String?) {
                    when (path) {
                        OPENCLAW_CONFIG_FILE -> {
                            Log.i(TAG, "检测到 OpenClaw 配置文件变化: $path")
                            Log.i(TAG, "自动重新加载 OpenClaw 配置...")
                            val newConfig = reloadOpenClawConfig()
                            reloadCallback?.invoke(newConfig)
                        }
                    }
                }
            }

            fileObserver?.startWatching()
            hotReloadEnabled = true
            Log.i(TAG, "✅ 配置热重载已启用 - 监控: $CONFIG_DIR")

        } catch (e: Exception) {
            Log.e(TAG, "启用配置热重载失败", e)
        }
    }

    /**
     * 禁用配置热重载
     */
    fun disableHotReload() {
        fileObserver?.stopWatching()
        fileObserver = null
        reloadCallback = null
        hotReloadEnabled = false
        Log.i(TAG, "配置热重载已禁用")
    }

    /**
     * 是否启用了热重载
     */
    fun isHotReloadEnabled(): Boolean = hotReloadEnabled

    /**
     * 获取 Feishu Channel 配置（转换为 FeishuConfig）
     */
    fun getFeishuConfig(): com.xiaomo.feishu.FeishuConfig {
        val openClawConfig = loadOpenClawConfig()
        return FeishuConfigAdapter.toFeishuConfig(openClawConfig.gateway.feishu)
    }

    // ============ Private Methods ============

    /**
     * 确保配置目录存在
     */
    private fun ensureConfigDir() {
        if (!configDir.exists()) {
            configDir.mkdirs()
            Log.i(TAG, "创建配置目录: ${configDir.absolutePath}")
        }
    }


    /**
     * 创建默认 OpenClaw 配置文件
     * 从 assets 中的 openclaw.json.example 复制
     */
    private fun createDefaultOpenClawConfig() {
        try {
            // Read from openclaw.json.example in assets
            val defaultConfig = try {
                context.assets.open("openclaw.json.example").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                Log.e(TAG, "无法从 assets 读取 openclaw.json.example: ${e.message}")
                throw Exception("Missing openclaw.json.example in assets", e)
            }

            openclawConfigFile.writeText(defaultConfig)
            Log.i(TAG, "✅ 创建默认 OpenClaw 配置文件: ${openclawConfigFile.absolutePath}")
            Log.i(TAG, "📝 提示: 请编辑配置文件，填入你的 API Keys")
        } catch (e: Exception) {
            Log.e(TAG, "创建默认配置失败", e)
            throw e
        }
    }

    /**
     * 替换环境变量 (${VAR_NAME})
     *
     * Supported environment variable sources:
     * 1. System environment variables
     * 2. Constants in AppConstants
     * 3. Config stored in MMKV
     */
    private fun replaceEnvVars(json: String): String {
        var result = json
        val envVarPattern = Regex("""\$\{([A-Z_]+)\}""")

        envVarPattern.findAll(json).forEach { match ->
            val varName = match.groupValues[1]
            val value = getEnvVar(varName)

            if (value != null) {
                result = result.replace("\${$varName}", value)
                Log.d(TAG, "替换环境变量: \${$varName} -> ***")
            } else {
                Log.w(TAG, "⚠️ 环境变量未找到: \${$varName}")
            }
        }

        return result
    }

    /**
     * 获取环境变量值
     *
     * Lookup priority:
     * 1. System environment variables
     * 2. AppConstants 常量
     * 3. MMKV 配置
     */
    private fun getEnvVar(name: String): String? {
        // 1. Try to get from system environment variables
        val systemEnv = System.getenv(name)
        if (systemEnv != null) {
            Log.d(TAG, "从系统环境变量获取: $name")
            return systemEnv
        }

        // 2. Try to get from AppConstants
        try {
            val constantsClass = Class.forName("com.xiaomo.androidforclaw.util.AppConstants")
            val field = constantsClass.getDeclaredField(name)
            field.isAccessible = true
            val value = field.get(null) as? String
            if (value != null) {
                Log.d(TAG, "从 AppConstants 获取: $name")
                return value
            }
        } catch (e: Exception) {
            // Continue to next source
        }

        // 3. Try to get from MMKV
        try {
            val mmkv = com.tencent.mmkv.MMKV.defaultMMKV()
            val value = mmkv?.decodeString(name)
            if (value != null) {
                Log.d(TAG, "从 MMKV 获取: $name")
                return value
            }
        } catch (e: Exception) {
            Log.w(TAG, "从 MMKV 读取失败: $name", e)
        }

        return null
    }


    /**
     * 获取默认 OpenClaw 配置
     * 从 assets 中的 openclaw.json.example 加载
     */
    private fun getDefaultOpenClawConfig(): OpenClawConfig {
        return createDefaultOpenClawConfigObject()
    }

    /**
     * 验证 OpenClaw 配置
     */
    private fun validateOpenClawConfig(config: OpenClawConfig) {
        // Agent config validation
        require(config.agent.maxIterations in ConfigDefaults.MIN_MAX_ITERATIONS..ConfigDefaults.MAX_MAX_ITERATIONS) {
            "Agent maxIterations 必须在 ${ConfigDefaults.MIN_MAX_ITERATIONS} 到 ${ConfigDefaults.MAX_MAX_ITERATIONS} 之间"
        }

        require(config.agent.timeout in ConfigDefaults.MIN_TIMEOUT_MS..ConfigDefaults.MAX_TIMEOUT_MS) {
            "Agent timeout 必须在 ${ConfigDefaults.MIN_TIMEOUT_MS} 到 ${ConfigDefaults.MAX_TIMEOUT_MS} 之间"
        }

        require(config.agent.mode in listOf("exploration", "planning")) {
            "Agent mode 必须是 'exploration' 或 'planning'"
        }

        // Thinking config validation
        require(config.thinking.budgetTokens in ConfigDefaults.MIN_THINKING_BUDGET..ConfigDefaults.MAX_THINKING_BUDGET) {
            "Thinking budgetTokens 必须在 ${ConfigDefaults.MIN_THINKING_BUDGET} 到 ${ConfigDefaults.MAX_THINKING_BUDGET} 之间"
        }

        // Screenshot config validation
        require(config.tools.screenshot.quality in ConfigDefaults.MIN_SCREENSHOT_QUALITY..ConfigDefaults.MAX_SCREENSHOT_QUALITY) {
            "Screenshot quality 必须在 ${ConfigDefaults.MIN_SCREENSHOT_QUALITY} 到 ${ConfigDefaults.MAX_SCREENSHOT_QUALITY} 之间"
        }

        require(config.tools.screenshot.format in listOf("jpeg", "png", "webp")) {
            "Screenshot format 必须是 'jpeg', 'png' 或 'webp'"
        }

        // Gateway config validation
        require(config.gateway.port in ConfigDefaults.MIN_GATEWAY_PORT..ConfigDefaults.MAX_GATEWAY_PORT) {
            "Gateway port 必须在 ${ConfigDefaults.MIN_GATEWAY_PORT} 到 ${ConfigDefaults.MAX_GATEWAY_PORT} 之间"
        }

        // UI config validation
        require(config.ui.theme in listOf("light", "dark", "auto")) {
            "UI theme 必须是 'light', 'dark' 或 'auto'"
        }

        require(config.ui.language in listOf("zh", "en")) {
            "UI language 必须是 'zh' 或 'en'"
        }

        require(config.ui.floatingWindow.position in listOf("top-left", "top-right", "bottom-left", "bottom-right")) {
            "FloatingWindow position 必须是 'top-left', 'top-right', 'bottom-left' 或 'bottom-right'"
        }

        require(config.ui.floatingWindow.opacity in 0.0f..1.0f) {
            "FloatingWindow opacity 必须在 0.0 到 1.0 之间"
        }

        // Logging config validation
        require(config.logging.level in listOf("DEBUG", "INFO", "WARN", "ERROR")) {
            "Logging level 必须是 'DEBUG', 'INFO', 'WARN' 或 'ERROR'"
        }

        // Providers config validation (read from config.providers)
        if (config.providers.isNotEmpty()) {
            config.providers.forEach { (providerName, provider) ->
                require(provider.baseUrl.isNotBlank()) {
                    "Provider '$providerName' 的 baseUrl 不能为空"
                }

                require(provider.models.isNotEmpty()) {
                    "Provider '$providerName' 必须至少包含一个模型"
                }

                provider.models.forEach { model ->
                    require(model.id.isNotBlank()) {
                        "Provider '$providerName' 中的模型 id 不能为空"
                    }
                    require(model.name.isNotBlank()) {
                        "Provider '$providerName' 中的模型 '${model.id}' 的 name 不能为空"
                    }
                    require(model.contextWindow > 0) {
                        "模型 '${model.id}' 的 contextWindow 必须大于 0"
                    }
                    require(model.maxTokens > 0) {
                        "模型 '${model.id}' 的 maxTokens 必须大于 0"
                    }
                }
            }
        }

        // Feishu Channel config validation
        val feishu = config.gateway.feishu
        if (feishu.enabled) {
            require(feishu.appId.isNotBlank()) {
                "Feishu appId 不能为空（enabled=true 时）"
            }
            require(feishu.appSecret.isNotBlank()) {
                "Feishu appSecret 不能为空（enabled=true 时）"
            }
        }

        require(feishu.connectionMode in listOf("websocket", "webhook")) {
            "Feishu connectionMode 必须是 'websocket' 或 'webhook'"
        }

        require(feishu.dmPolicy in listOf("open", "pairing", "allowlist")) {
            "Feishu dmPolicy 必须是 'open', 'pairing' 或 'allowlist'"
        }

        require(feishu.groupPolicy in listOf("open", "allowlist", "disabled")) {
            "Feishu groupPolicy 必须是 'open', 'allowlist' 或 'disabled'"
        }

        require(feishu.groupCommandMentionBypass in listOf("never", "single_bot", "always")) {
            "Feishu groupCommandMentionBypass 必须是 'never', 'single_bot' 或 'always'"
        }

        require(feishu.topicSessionMode in listOf("disabled", "enabled")) {
            "Feishu topicSessionMode 必须是 'disabled' 或 'enabled'"
        }

        require(feishu.chunkMode in listOf("length", "newline")) {
            "Feishu chunkMode 必须是 'length' 或 'newline'"
        }

        Log.i(TAG, "✅ OpenClaw 配置验证通过")
    }
}
