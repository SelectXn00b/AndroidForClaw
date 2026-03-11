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
        // 使用 /sdcard/.androidforclaw 目录（对齐 OpenClaw ~/.openclaw/）
        CONFIG_DIR = "/sdcard/.androidforclaw"
        configDir = File(CONFIG_DIR)
        openclawConfigFile = File(configDir, OPENCLAW_CONFIG_FILE)

        Log.d(TAG, "配置目录: ${configDir.absolutePath}")
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

            // Parse JSON using JSONObject (avoid GSON default value issues)
            val config = parseOpenClawConfigFromJson(processedJson)

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
     * 从 assets 中的 openclaw.json.default.txt 加载
     */
    private fun createDefaultOpenClawConfigObject(): OpenClawConfig {
        val defaultConfigJson = try {
            context.assets.open("openclaw.json.default.txt").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "无法从 assets 读取 openclaw.json.default.txt: ${e.message}")
            throw Exception("Missing openclaw.json.default.txt in assets", e)
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
     * 从 JSON 字符串手动解析 OpenClawConfig
     * 避免 GSON 的默认值问题 (boolean 字段缺失时会用 false 而非代码默认值)
     */
    private fun parseOpenClawConfigFromJson(json: String): OpenClawConfig {
        val jsonObj = org.json.JSONObject(json)

        // 先用 GSON 解析大部分配置
        val config = gson.fromJson(json, OpenClawConfig::class.java)

        // 手动解析 providers,确保 authHeader 默认为 true
        val providersMap = mutableMapOf<String, ProviderConfig>()

        // 优先从 models.providers 读取
        if (jsonObj.has("models") && jsonObj.getJSONObject("models").has("providers")) {
            val providersJson = jsonObj.getJSONObject("models").getJSONObject("providers")
            providersJson.keys().forEach { providerName ->
                val providerJson = providersJson.getJSONObject(providerName)
                val provider = parseProviderConfig(providerJson)
                providersMap[providerName] = provider
            }
        }
        // 否则从 providers 读取
        else if (jsonObj.has("providers")) {
            val providersJson = jsonObj.getJSONObject("providers")
            providersJson.keys().forEach { providerName ->
                val providerJson = providersJson.getJSONObject(providerName)
                val provider = parseProviderConfig(providerJson)
                providersMap[providerName] = provider
            }
        }

        // 更新 config 的 models.providers
        if (config.models != null && providersMap.isNotEmpty()) {
            return config.copy(
                models = config.models.copy(providers = providersMap)
            )
        }

        return config
    }

    /**
     * 手动解析 ProviderConfig,给 authHeader 默认值 true
     */
    private fun parseProviderConfig(json: org.json.JSONObject): ProviderConfig {
        val baseUrl = json.getString("baseUrl")
        val apiKey = json.optString("apiKey", null)
        val api = json.optString("api", "openai-completions")
        val auth = json.optString("auth", null)

        // 关键: authHeader 默认为 true (GSON 缺失时会用 false)
        val authHeader = json.optBoolean("authHeader", true)

        val headers = if (json.has("headers")) {
            val headersJson = json.getJSONObject("headers")
            headersJson.keys().asSequence().associateWith { headersJson.getString(it) }
        } else null

        val injectNumCtxForOpenAICompat = json.optBoolean("injectNumCtxForOpenAICompat", false)

        // 解析 models 数组
        val modelsArray = json.getJSONArray("models")
        val models = mutableListOf<ModelDefinition>()
        for (i in 0 until modelsArray.length()) {
            val modelJson = modelsArray.getJSONObject(i)
            val model = parseModelDefinition(modelJson, api)
            models.add(model)
        }

        return ProviderConfig(
            baseUrl = baseUrl,
            apiKey = apiKey,
            api = api,
            auth = auth,
            authHeader = authHeader,
            headers = headers,
            injectNumCtxForOpenAICompat = if (injectNumCtxForOpenAICompat) true else null,
            models = models
        )
    }

    /**
     * 手动解析 ModelDefinition
     */
    private fun parseModelDefinition(json: org.json.JSONObject, defaultApi: String): ModelDefinition {
        val id = json.getString("id")
        val name = json.getString("name")
        val reasoning = json.optBoolean("reasoning", false)
        val contextWindow = json.optInt("contextWindow", 4096)
        val maxTokens = json.optInt("maxTokens", 2048)
        val api = json.optString("api", defaultApi)

        val input: List<String> = if (json.has("input")) {
            val inputArray = json.getJSONArray("input")
            List(inputArray.length()) { inputArray.getString(it) }
        } else listOf("text")

        val headers = if (json.has("headers")) {
            val headersJson = json.getJSONObject("headers")
            headersJson.keys().asSequence().associateWith { headersJson.getString(it) }
        } else null

        return ModelDefinition(
            id = id,
            name = name,
            reasoning = reasoning,
            input = input,
            contextWindow = contextWindow,
            maxTokens = maxTokens,
            api = api,
            headers = headers
        )
    }

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
     * 从 assets 中的 openclaw.json.default.txt 复制
     */
    private fun createDefaultOpenClawConfig() {
        try {
            // Read from openclaw.json.default.txt in assets
            val defaultConfig = try {
                context.assets.open("openclaw.json.default.txt").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                Log.e(TAG, "无法从 assets 读取 openclaw.json.default.txt: ${e.message}")
                throw Exception("Missing openclaw.json.default.txt in assets", e)
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
     *
     * Note: Removed MMKV as source to prevent config loss on app data clear
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

        // Note: Do not read from MMKV to avoid config loss on app data clear
        // User should write actual values in openclaw.json instead of using ${VAR_NAME}

        return null
    }


    /**
     * 获取默认 OpenClaw 配置
     * 从 assets 中的 openclaw.json.default.txt 加载
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
