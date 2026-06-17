package com.ai.assistance.operit.core.application

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.system.Os
import com.ai.assistance.operit.util.AppLogger
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.work.Configuration as WorkConfiguration
import androidx.work.WorkManager
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.request.CachePolicy
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import com.ai.assistance.operit.BuildConfig
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.chat.AIMessageManager
import com.ai.assistance.operit.api.chat.AIForegroundService
import com.ai.assistance.operit.plugins.PluginRegistry
import com.ai.assistance.operit.plugins.lifecycle.AppLifecycleEvent
import com.ai.assistance.operit.plugins.lifecycle.AppLifecycleHookParams
import com.ai.assistance.operit.plugins.lifecycle.AppLifecycleHookPluginRegistry
import com.ai.assistance.operit.core.config.SystemPromptConfig
import com.ai.assistance.operit.core.cron.CronTickWorker
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import com.ai.assistance.operit.core.tools.system.Terminal
import com.ai.assistance.operit.core.workflow.WorkflowSchedulerInitializer
import com.ai.assistance.operit.data.backup.RoomDatabaseBackupPreferences
import com.ai.assistance.operit.data.backup.RoomDatabaseBackupScheduler
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.ExternalHttpApiPreferences
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.WakeWordPreferences
import com.ai.assistance.operit.data.preferences.initAndroidPermissionPreferences
import com.ai.assistance.operit.data.preferences.initUserPreferencesManager
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.data.repository.CustomEmojiRepository
import com.ai.assistance.operit.ui.features.chat.webview.LocalWebServer
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.language.LanguageFactory
import com.ai.assistance.operit.util.GlobalExceptionHandler
import com.ai.assistance.operit.util.ImagePoolManager
import com.ai.assistance.operit.util.LocaleUtils
import com.ai.assistance.operit.util.MediaPoolManager
import com.ai.assistance.operit.util.AppIconManager
import com.ai.assistance.operit.util.CrashRecoveryState
import com.ai.assistance.operit.util.OperitPaths
import com.ai.assistance.operit.util.SkillRepoZipPoolManager
import com.ai.assistance.operit.util.SerializationSetup
import com.ai.assistance.operit.util.TextSegmenter
import com.ai.assistance.operit.util.WaifuMessageProcessor
import com.ai.assistance.operit.core.tools.agent.ShowerController
import com.ai.assistance.operit.ui.common.displays.VirtualDisplayOverlay
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.ai.assistance.operit.core.tools.system.shower.OperitShowerShellRunner
import com.ai.assistance.showerclient.ShowerEnvironment
import com.ai.assistance.showerclient.ShowerLogSink
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Application class for Operit */
class OperitApplication : Application(), ImageLoaderFactory, WorkConfiguration.Provider {

    companion object {
        /** Global JSON instance with custom serializers */
        lateinit var json: Json
            private set

        @Volatile
        var appStartupTimeMs: Long = 0L
            private set

        // 全局应用实例
        lateinit var instance: OperitApplication
            private set

        // 全局ImageLoader实例，用于高效缓存图片
        lateinit var globalImageLoader: ImageLoader
            private set

        private const val TAG = "OperitApplication"
    }

    // 应用级协程作用域
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 懒加载数据库实例
    private val database by lazy { AppDatabase.getDatabase(this) }

    private fun configureOpenMpEnvironment() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Os.setenv("KMP_AFFINITY", "disabled", true)
                Os.setenv("OMP_PROC_BIND", "false", true)
            }
        } catch (_: Throwable) {
        }
    }

    override fun onCreate() {
        super.onCreate()
        val startTime = System.currentTimeMillis()
        appStartupTimeMs = startTime
        instance = this

        com.xiaomo.hermes.hermes.initHermesConstants(this)

        configureOpenMpEnvironment()
        AppIconManager.ensureComponentState(this)

        // 每次应用冷启动时重置上一轮日志，避免日志无限增长
        val isCrashReportRecoveryStartup = CrashRecoveryState.consumePendingCrashReportLaunch(this)
        if (!isCrashReportRecoveryStartup) {
            AppLogger.resetLogFile()
        }

        // Install frame-skip logger + StrictMode (debug only) before any heavy work,
        // so that "main thread blocked Xms" events at startup are captured in operit.log
        // and shipped to the feedback server.
        com.ai.assistance.operit.util.MainThreadHealthInstaller.install()

        ensureWorkManagerInitialized()

        if (isCrashReportRecoveryStartup) {
            AppLogger.w(TAG, "检测到崩溃报告启动，保留上一轮日志供崩溃页导出")
        }

        AppLogger.d(TAG, "【启动计时】应用启动开始")
        AppLogger.d(TAG, "【启动计时】实例初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        launchCleanOnExitCleanup()
        AppLogger.d(TAG, "【启动计时】cleanOnExit 清理任务已提交（异步IO） - ${System.currentTimeMillis() - startTime}ms")

        launchOrphanTagMigrationsIfNeeded()
        AppLogger.d(TAG, "【启动计时】R-AGENT-029 孤儿 tag 迁移任务已提交（异步IO） - ${System.currentTimeMillis() - startTime}ms")

        launchAutoNodeArchiverMigrationIfNeeded()
        AppLogger.d(TAG, "【启动计时】R-AGENT-040 自动节点归档迁移任务已提交（异步IO） - ${System.currentTimeMillis() - startTime}ms")

        launchLegacyAutoNodeDeletionIfNeeded()
        AppLogger.d(TAG, "【启动计时】R-AGENT-041-a 散节点删除迁移任务已提交（异步IO） - ${System.currentTimeMillis() - startTime}ms")

        // R-AGENT-031: enqueue the WorkManager-driven cron tick (15-minute period, KEEP policy).
        // Must be after launchOrphanTagMigrationsIfNeeded so memory-side maintenance runs first.
        CronTickWorker.enqueue(this)
        AppLogger.d(TAG, "【启动计时】R-AGENT-031 CronTickWorker 已入队（PeriodicWork 15m KEEP） - ${System.currentTimeMillis() - startTime}ms")

        // R-AGENT-043: inject the immediate-trigger runner so `cronjob(action="run")`
        // and the sidebar Cron Jobs screen can fire jobs in-process, bypassing
        // WorkManager's 15-minute periodic tick. The lambda lives in `app` module
        // (where `CronAgentRunner` lives) and is plumbed into `Scheduler.kt`'s
        // injection slot, mirroring the R-AGENT-033 `cronOutboundDispatcher` pattern.
        com.xiaomo.hermes.hermes.cron.cronImmediateRunner = { job ->
            com.ai.assistance.operit.core.cron.CronAgentRunner.run(applicationContext, job)
        }
        AppLogger.d(TAG, "【启动计时】R-AGENT-043 cronImmediateRunner 已注入 - ${System.currentTimeMillis() - startTime}ms")

        // Initialize ActivityLifecycleManager to track the current activity
        ActivityLifecycleManager.initialize(this)
        AppLogger.d(TAG, "【启动计时】ActivityLifecycleManager初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // Initialize AIMessageManager
        AIMessageManager.initialize(this)
        PluginRegistry.initializeBuiltins()
        AppLifecycleHookPluginRegistry.dispatchAsync(
            event = AppLifecycleEvent.APPLICATION_CREATE,
            params =
                AppLifecycleHookParams(
                    context = applicationContext,
                    extras =
                        mapOf(
                            "startupTimeMs" to startTime
                        )
                )
        )
        AppLogger.d(TAG, "【启动计时】AIMessageManager初始化完成 - ${System.currentTimeMillis() - startTime}ms")
        startGlobalAIForegroundServiceIfNeeded()
        AppLogger.d(TAG, "【启动计时】AIForegroundService 持久后台职责检查完成 - ${System.currentTimeMillis() - startTime}ms")

        // Initialize ANR monitor
        // AnrMonitor.start()

        // 在所有其他初始化之前设置全局异常处理器
        Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(this))
        AppLogger.d(TAG, "【启动计时】全局异常处理器设置完成 - ${System.currentTimeMillis() - startTime}ms")

        // Initialize the JSON serializer with our custom module
        json = Json {
            serializersModule = SerializationSetup.module
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = false
            encodeDefaults = true
        }
        AppLogger.d(TAG, "【启动计时】JSON序列化器初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 在最早时机初始化并应用语言设置（必须在获取字符串资源之前）
        initializeAppLanguage()
        AppLogger.d(TAG, "【启动计时】语言设置初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 初始化用户偏好管理器
        val defaultProfileName = applicationContext.getString(R.string.default_profile)
        initUserPreferencesManager(applicationContext, defaultProfileName)
        AppLogger.d(TAG, "【启动计时】用户偏好管理器初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 初始化Android权限偏好管理器
        initAndroidPermissionPreferences(applicationContext)
        AppLogger.d(TAG, "【启动计时】Android权限偏好管理器初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 初始化功能提示词管理器
        applicationScope.launch {
            val characterStartTime = System.currentTimeMillis()
            CharacterCardManager.getInstance(applicationContext).initializeIfNeeded()
            AppLogger.d(TAG, "【启动计时】功能提示词管理器初始化完成（异步） - ${System.currentTimeMillis() - characterStartTime}ms")
        }

        // 初始化当前活跃角色目标的自定义表情
        applicationScope.launch {
            val emojiStartTime = System.currentTimeMillis()
            CustomEmojiRepository.getInstance(applicationContext).initializeBuiltinEmojis()
            AppLogger.d(TAG, "【启动计时】当前角色自定义表情初始化完成（异步） - ${System.currentTimeMillis() - emojiStartTime}ms")
        }

        // 初始化AndroidShellExecutor上下文
        AndroidShellExecutor.setContext(applicationContext)
        AppLogger.d(TAG, "【启动计时】AndroidShellExecutor初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 初始化 Shower 虚拟屏客户端的 ShellRunner 环境
        ShowerEnvironment.shellRunner = OperitShowerShellRunner
        ShowerEnvironment.logSink =
            ShowerLogSink { priority, tag, message, throwable ->
                when (priority) {
                    AppLogger.VERBOSE ->
                        if (throwable != null) AppLogger.v(tag, message, throwable) else AppLogger.v(tag, message)
                    AppLogger.DEBUG ->
                        if (throwable != null) AppLogger.d(tag, message, throwable) else AppLogger.d(tag, message)
                    AppLogger.INFO ->
                        if (throwable != null) AppLogger.i(tag, message, throwable) else AppLogger.i(tag, message)
                    AppLogger.WARN ->
                        if (throwable != null) AppLogger.w(tag, message, throwable) else AppLogger.w(tag, message)
                    AppLogger.ERROR ->
                        if (throwable != null) AppLogger.e(tag, message, throwable) else AppLogger.e(tag, message)
                    AppLogger.ASSERT ->
                        if (throwable != null) AppLogger.wtf(tag, message, throwable) else AppLogger.wtf(tag, message)
                    else ->
                        if (throwable != null) {
                            AppLogger.println(priority, tag, "$message\n${AppLogger.getStackTraceString(throwable)}")
                        } else {
                            AppLogger.println(priority, tag, message)
                        }
                }
            }
        // Shower logs are already mirrored to AppLogger; avoid duplicate system log entries.
        ShowerEnvironment.emitToSystemLog = false
        AppLogger.d(TAG, "【启动计时】ShowerEnvironment.shellRunner 已配置 - ${System.currentTimeMillis() - startTime}ms")
        AppLogger.d(TAG, "【启动计时】ShowerEnvironment.logSink 已配置 - ${System.currentTimeMillis() - startTime}ms")

        // 初始化PDFBox资源加载器
        PDFBoxResourceLoader.init(getApplicationContext());
        AppLogger.d(TAG, "【启动计时】PDFBox资源加载器初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 初始化语言支持
        LanguageFactory.init()
        AppLogger.d(TAG, "【启动计时】语言工厂初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 后台预热 TextSegmenter，避免首次搜索记忆时才触发 Jieba 初始化
        applicationScope.launch {
            val segmenterStartTime = System.currentTimeMillis()
            TextSegmenter.initialize(applicationContext)
            AppLogger.d(TAG, "【启动计时】TextSegmenter预热完成（异步） - ${System.currentTimeMillis() - segmenterStartTime}ms")
        }
        
        // Initialize WaifuMessageProcessor
        WaifuMessageProcessor.initialize(applicationContext)
        AppLogger.d(TAG, "【启动计时】WaifuMessageProcessor初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 预加载数据库
        applicationScope.launch {
            val dbStartTime = System.currentTimeMillis()
            // 简单访问数据库以触发初始化
            database.openHelper.writableDatabase
            AppLogger.d(TAG, "【启动计时】数据库预加载完成（异步） - ${System.currentTimeMillis() - dbStartTime}ms")
        }

        // 初始化全局图片加载器，设置强大的缓存策略
        // 创建自定义 OkHttp 客户端，增加超时时间以支持慢速图片服务器
        val imageOkHttpClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS) // 连接超时：30秒（默认10秒）
                .readTimeout(60, TimeUnit.SECONDS)    // 读取超时：60秒（默认10秒）
                .writeTimeout(30, TimeUnit.SECONDS)   // 写入超时：30秒（默认10秒）
                .retryOnConnectionFailure(true)       // 连接失败时自动重试
                .build()
        
        globalImageLoader =
                ImageLoader.Builder(this)
                        .okHttpClient(imageOkHttpClient) // 使用自定义 OkHttp 客户端
                        .components {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                add(ImageDecoderDecoder.Factory())
                            } else {
                                add(GifDecoder.Factory())
                            }
                        }
                        .crossfade(true)
                        .respectCacheHeaders(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .diskCache {
                            DiskCache.Builder()
                                    .directory(filesDir.resolve("image_cache"))
                                    .maxSizeBytes(50 * 1024 * 1024) // 50MB磁盘缓存上限，比百分比更精确
                                    .build()
                        }
                        .memoryCache {
                            // 设置内存缓存最大大小为应用可用内存的15%
                            coil.memory.MemoryCache.Builder(this).maxSizePercent(0.15).build()
                        }
                        .build()
        AppLogger.d(TAG, "【启动计时】全局图片加载器初始化完成（超时配置：连接30s/读取60s） - ${System.currentTimeMillis() - startTime}ms")
        
        // 初始化图片池管理器，支持本地持久化缓存
        ImagePoolManager.initialize(filesDir, preloadNow = false)
        AppLogger.d(TAG, "【启动计时】图片池管理器初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 初始化媒体池管理器（音频/视频），支持本地持久化缓存
        MediaPoolManager.initialize(filesDir, preloadNow = false)
        AppLogger.d(TAG, "【启动计时】媒体池管理器初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        SkillRepoZipPoolManager.initialize(filesDir)

        // 启动后重任务统一后台串行执行，避免多个大任务同时跑导致首屏掉帧
        applicationScope.launch(Dispatchers.Default) {
            // 给 UI 一个缓冲时间先完成首屏渲染
            kotlinx.coroutines.delay(800)

            val imagePreloadStartTime = System.currentTimeMillis()
            withContext(Dispatchers.IO) { ImagePoolManager.preloadFromDisk() }
            AppLogger.d(TAG, "【启动计时】图片池磁盘预加载完成（异步/串行） - ${System.currentTimeMillis() - imagePreloadStartTime}ms")

            val mediaPreloadStartTime = System.currentTimeMillis()
            withContext(Dispatchers.IO) { MediaPoolManager.preloadFromDisk() }
            AppLogger.d(TAG, "【启动计时】媒体池磁盘预加载完成（异步/串行） - ${System.currentTimeMillis() - mediaPreloadStartTime}ms")

            val toolStartTime = System.currentTimeMillis()
            val toolHandler = AIToolHandler.getInstance(this@OperitApplication)
            toolHandler.registerDefaultTools()
            AppLogger.d(TAG, "【启动计时】AIToolHandler初始化并注册工具完成（异步/串行） - ${System.currentTimeMillis() - toolStartTime}ms")
        }
        
        // 初始化工作流调度器（异步）
        applicationScope.launch {
            val schedulerStartTime = System.currentTimeMillis()
            WorkflowSchedulerInitializer.initialize(applicationContext)
            AppLogger.d(TAG, "【启动计时】WorkflowScheduler初始化完成（异步） - ${System.currentTimeMillis() - schedulerStartTime}ms")
        }

        applicationScope.launch {
            try {
                val prefs = RoomDatabaseBackupPreferences.getInstance(applicationContext)
                if (prefs.isDailyBackupEnabled()) {
                    RoomDatabaseBackupScheduler.ensureScheduled(applicationContext)
                } else {
                    RoomDatabaseBackupScheduler.cancelScheduled(applicationContext)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Room DB backup schedule init failed", e)
            }
        }

        // 在应用启动时尝试绑定无障碍服务提供者（解决后台绑定限制问题）
        applicationScope.launch {
            AppLogger.d(TAG, "【启动计时】开始预绑定无障碍服务提供者...")
            val bindStartTime = System.currentTimeMillis()
            try {
                val bound = com.ai.assistance.operit.data.repository.UIHierarchyManager.bindToService(this@OperitApplication)
                AppLogger.d(TAG, "【启动计时】无障碍服务预绑定完成（异步） - 结果: $bound, 耗时: ${System.currentTimeMillis() - bindStartTime}ms")
            } catch (e: Exception) {
                AppLogger.e(TAG, "无障碍服务预绑定失败", e)
            }
        }
        
        val totalTime = System.currentTimeMillis() - startTime
        AppLogger.d(TAG, "【启动计时】应用启动全部完成 - 总耗时: ${totalTime}ms")
    }

    /**
     * 实现 ImageLoaderFactory 接口
     * 让 Coil 使用我们配置的全局 ImageLoader（带有自定义超时设置）
     */
    override fun newImageLoader(): ImageLoader {
        return globalImageLoader
    }

    /**
     * 实现 WorkConfiguration.Provider 接口
     * 提供 WorkManager 的配置，确保 WorkManager 被正确初始化
     */
    override val workManagerConfiguration: WorkConfiguration
        get() = WorkConfiguration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) AppLogger.DEBUG else AppLogger.INFO)
            .build()

    private fun ensureWorkManagerInitialized() {
        try {
            WorkManager.getInstance(applicationContext)
        } catch (_: IllegalStateException) {
            try {
                WorkManager.initialize(applicationContext, workManagerConfiguration)
            } catch (_: IllegalStateException) {

            }
        }
    }

    private fun launchCleanOnExitCleanup() {
        applicationScope.launch {
            val cleanupStartTime = System.currentTimeMillis()
            try {
                val deletedFiles =
                    cleanDirectory(File(OperitPaths.cleanOnExitPathSdcard()), preserveRootNoMedia = true) +
                        cleanDirectory(File(cacheDir, "Operit/cleanOnExit"), preserveRootNoMedia = false)
                AppLogger.d(
                    TAG,
                    "cleanOnExit 清理完成，总计删除${deletedFiles}个文件，耗时${System.currentTimeMillis() - cleanupStartTime}ms"
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "清理临时文件失败", e)
            }
        }
    }

    /**
     * R-AGENT-029: 一次性清理旧库 `#auto_summary_id:*` 孤儿 tag。
     *
     * R-AGENT-027 已堵住生产源（fact 抽取不再写该 tag），但历史 APK（含 r026-aikeep `ba814a70`）装机的
     * ObjectBox 里仍有残留 + R-AGENT-026 keepDecision=false 路径产生的 `#auto_summary_id:-1` 孤儿。
     * 用 SharedPreferences 防重入键 `R_AGENT_029_auto_summary_id_orphan_cleanup_done`，
     * 任一 profile 失败不写完成标记，下次启动重试。
     *
     * 仿 [launchCleanOnExitCleanup] 范式：applicationScope + Dispatchers.IO + try/catch，
     * 不阻塞主线程，不影响其它启动任务。
     */
    private fun launchOrphanTagMigrationsIfNeeded() {
        applicationScope.launch {
            val migrationStartTime = System.currentTimeMillis()
            val prefs = getSharedPreferences("hermes_data_migrations", Context.MODE_PRIVATE)
            val key = "R_AGENT_029_auto_summary_id_orphan_cleanup_done"
            if (prefs.getBoolean(key, false)) {
                return@launch
            }
            try {
                val profiles = preferencesManager.profileListFlow.first()
                if (profiles.isEmpty()) {
                    AppLogger.d(TAG, "R-AGENT-029: no profiles yet, skip migration this run")
                    return@launch
                }
                var totalCleaned = 0
                for (profileId in profiles) {
                    val repo = com.ai.assistance.operit.data.repository.MemoryRepository(
                        applicationContext,
                        profileId
                    )
                    val cleaned = repo.cleanupOrphanTagsByPrefix("#auto_summary_id:")
                    totalCleaned += cleaned
                    AppLogger.d(TAG, "R-AGENT-029: profile=$profileId cleaned=$cleaned orphan tag(s)")
                }
                prefs.edit().putBoolean(key, true).apply()
                AppLogger.d(
                    TAG,
                    "R-AGENT-029: migration done, totalCleaned=$totalCleaned orphan tag(s) across ${profiles.size} profile(s), 耗时${System.currentTimeMillis() - migrationStartTime}ms"
                )
            } catch (e: Exception) {
                AppLogger.w(TAG, "R-AGENT-029: orphan tag migration failed, will retry next launch: ${e.message}")
            }
        }
    }

    /**
     * R-AGENT-040: 一次性把 phase 1 之前装的旧 APK 写下的散 `#auto_summary` / `#auto_extracted` /
     * `#auto_summary_id:NNN` 节点合并到 R-AGENT-038 的 3 个 root 节点 (`#auto_summary_root` /
     * `#auto_extracted_root` / `#auto_summary_id_root`)。
     *
     * 仿 [launchOrphanTagMigrationsIfNeeded] 范式：applicationScope + Dispatchers.IO + try/catch，
     * 不阻塞主线程。SharedPreferences 防重入键 `R_AGENT_040_auto_node_consolidation_done`（位于
     * `hermes_data_migrations` 文件，与 R-AGENT-029 共用）。失败不写完成标记，下次启动重试。
     *
     * **不删旧节点**：保险起见 phase 2 留着，R-AGENT-041-a 才删。
     * **chatId**：Memory 实体无 chatId 字段，旧节点的 chatId 是作为 `#chat:<id>` tag 挂在节点上的，
     * 迁移时从 tags ToMany 找 `#chat:` 前缀的 tag 取后缀；找不到传 `""`。
     */
    private fun launchAutoNodeArchiverMigrationIfNeeded() {
        applicationScope.launch {
            val migrationStartTime = System.currentTimeMillis()
            val prefs = getSharedPreferences("hermes_data_migrations", Context.MODE_PRIVATE)
            val key = "R_AGENT_040_auto_node_consolidation_done"
            if (prefs.getBoolean(key, false)) {
                return@launch
            }
            // Memory 实体无 chatId 字段，旧节点的 chatId 是作为 `#chat:<id>` tag 挂上的；
            // 从 tags ToMany 找 `#chat:` 前缀 tag 取后缀作为 chatId，找不到传 ""。
            val extractChatId: (com.ai.assistance.operit.data.model.Memory) -> String = { memory ->
                try {
                    memory.tags
                        .firstOrNull { it.name.startsWith("#chat:") }
                        ?.name
                        ?.removePrefix("#chat:")
                        ?: ""
                } catch (_: Exception) {
                    ""
                }
            }
            try {
                val profiles = preferencesManager.profileListFlow.first()
                if (profiles.isEmpty()) {
                    AppLogger.d(TAG, "R-AGENT-040: no profiles yet, skip migration this run")
                    return@launch
                }
                var totalAppended = 0
                for (profileId in profiles) {
                    val repo = com.ai.assistance.operit.data.repository.MemoryRepository(
                        applicationContext,
                        profileId
                    )
                    val archiver = com.ai.assistance.operit.data.repository.MemoryArchiver(
                        applicationContext,
                        repo
                    )

                    // SUMMARY bucket：扫 `#auto_summary` exact-match
                    val summaryNodes = repo.findMemoriesByTag("#auto_summary")
                        .sortedBy { it.createdAt.time }
                    for (node in summaryNodes) {
                        if (node.content.isBlank()) continue
                        val chatId = extractChatId(node)
                        try {
                            archiver.appendToRoot(
                                bucket = com.ai.assistance.operit.data.repository.MemoryArchiver.ArchiveBucket.SUMMARY,
                                chatId = chatId,
                                content = node.content,
                                timestamp = node.createdAt.time
                            )
                            totalAppended++
                        } catch (e: Exception) {
                            AppLogger.w(TAG, "R-AGENT-040: profile=$profileId SUMMARY append failed nodeId=${node.id}: ${e.message}")
                        }
                    }

                    // EXTRACTED bucket：扫 `#auto_extracted` exact-match
                    val extractedNodes = repo.findMemoriesByTag("#auto_extracted")
                        .sortedBy { it.createdAt.time }
                    for (node in extractedNodes) {
                        if (node.content.isBlank()) continue
                        val chatId = extractChatId(node)
                        try {
                            archiver.appendToRoot(
                                bucket = com.ai.assistance.operit.data.repository.MemoryArchiver.ArchiveBucket.EXTRACTED,
                                chatId = chatId,
                                content = node.content,
                                timestamp = node.createdAt.time
                            )
                            totalAppended++
                        } catch (e: Exception) {
                            AppLogger.w(TAG, "R-AGENT-040: profile=$profileId EXTRACTED append failed nodeId=${node.id}: ${e.message}")
                        }
                    }

                    // SUMMARY_ID bucket：变长后缀，走 prefix 扫
                    val summaryIdTags = repo.findTagsByNamePrefix("#auto_summary_id:")
                    val summaryIdNodes = mutableListOf<com.ai.assistance.operit.data.model.Memory>()
                    for (tag in summaryIdTags) {
                        summaryIdNodes.addAll(tag.memories.toList())
                    }
                    val uniqueSummaryIdNodes = summaryIdNodes
                        .distinctBy { it.id }
                        .sortedBy { it.createdAt.time }
                    for (node in uniqueSummaryIdNodes) {
                        if (node.content.isBlank()) continue
                        val chatId = extractChatId(node)
                        try {
                            archiver.appendToRoot(
                                bucket = com.ai.assistance.operit.data.repository.MemoryArchiver.ArchiveBucket.SUMMARY_ID,
                                chatId = chatId,
                                content = node.content,
                                timestamp = node.createdAt.time
                            )
                            totalAppended++
                        } catch (e: Exception) {
                            AppLogger.w(TAG, "R-AGENT-040: profile=$profileId SUMMARY_ID append failed nodeId=${node.id}: ${e.message}")
                        }
                    }

                    AppLogger.d(
                        TAG,
                        "R-AGENT-040: profile=$profileId scanned summary=${summaryNodes.size} extracted=${extractedNodes.size} summaryId=${uniqueSummaryIdNodes.size}"
                    )
                }
                prefs.edit().putBoolean(key, true).apply()
                AppLogger.d(
                    TAG,
                    "R-AGENT-040: migration done, totalAppended=$totalAppended across ${profiles.size} profile(s), 耗时${System.currentTimeMillis() - migrationStartTime}ms"
                )
            } catch (e: Exception) {
                AppLogger.w(TAG, "R-AGENT-040: auto node consolidation failed, will retry next launch: ${e.message}")
            }
        }
    }

    /**
     * R-AGENT-041-a: 一次性把已被 R-AGENT-040 合并到 root 的旧 `#auto_summary` / `#auto_extracted` /
     * `#auto_summary_id:NNN` 散节点从 ObjectBox 删除（root 节点 tag 字面是 `_root` 后缀 + `#auto_root`
     * 标识 tag，与散节点不同所以安全）。
     *
     * **前置门禁**：必须先确认 R-AGENT-040 done flag (`R_AGENT_040_auto_node_consolidation_done`)
     * 为 `true`，否则跳过本次执行（不写 041-a done flag），下次冷启重试，避免在合并完成前就把数据删掉。
     *
     * 自己的防重入键 `R_AGENT_041_legacy_node_deletion_done`（位于 `hermes_data_migrations` 文件，
     * 与 R-AGENT-029 / R-AGENT-040 共用）。失败不写完成标记，下次启动重试。
     *
     * 删除算法（每个 profile）：
     *  - SUMMARY: `repo.deleteByTag("#auto_summary")` —— root 是 `#auto_summary_root`，字面不同安全
     *  - EXTRACTED: `repo.deleteByTag("#auto_extracted")`
     *  - SUMMARY_ID: 走 `findTagsByNamePrefix("#auto_summary_id:")` 收集 owner ids，**排除带 `#auto_root`
     *    的节点**（防御性，防止任何未来命名漂移误删 root），再 `deleteMemories(ids)` +
     *    `cleanupOrphanTagsByPrefix("#auto_summary_id:")` 清扫变长后缀孤儿 tag。
     */
    private fun launchLegacyAutoNodeDeletionIfNeeded() {
        applicationScope.launch {
            val migrationStartTime = System.currentTimeMillis()
            val prefs = getSharedPreferences("hermes_data_migrations", Context.MODE_PRIVATE)
            val gateKey = "R_AGENT_040_auto_node_consolidation_done"
            val key = "R_AGENT_041_legacy_node_deletion_done"

            // 自己的短路：041-a 已跑过则跳过
            if (prefs.getBoolean(key, false)) {
                return@launch
            }
            // 前置门禁：R-AGENT-040 还没把数据合并到 root 之前，绝对不能删散节点
            if (!prefs.getBoolean(gateKey, false)) {
                AppLogger.d(TAG, "R-AGENT-041-a: gating on R-AGENT-040 not yet done, skip this run")
                return@launch
            }

            try {
                val profiles = preferencesManager.profileListFlow.first()
                if (profiles.isEmpty()) {
                    AppLogger.d(TAG, "R-AGENT-041-a: no profiles yet, skip deletion this run")
                    return@launch
                }
                var totalDeleted = 0
                var totalOrphanTagsCleaned = 0
                for (profileId in profiles) {
                    val repo = com.ai.assistance.operit.data.repository.MemoryRepository(
                        applicationContext,
                        profileId
                    )

                    // SUMMARY: deleteByTag("#auto_summary") —— root 是 `#auto_summary_root`，字面不同所以安全
                    val deletedSummary = try {
                        repo.deleteByTag("#auto_summary")
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "R-AGENT-041-a: profile=$profileId SUMMARY deleteByTag failed: ${e.message}")
                        0
                    }

                    // EXTRACTED: deleteByTag("#auto_extracted")
                    val deletedExtracted = try {
                        repo.deleteByTag("#auto_extracted")
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "R-AGENT-041-a: profile=$profileId EXTRACTED deleteByTag failed: ${e.message}")
                        0
                    }

                    // SUMMARY_ID: prefix 扫 + 排除 #auto_root + 批删 + 清孤儿 tag
                    var deletedSummaryId = 0
                    var orphanTagsCleaned = 0
                    try {
                        val summaryIdTags = repo.findTagsByNamePrefix("#auto_summary_id:")
                        val candidateNodes = mutableListOf<com.ai.assistance.operit.data.model.Memory>()
                        for (tag in summaryIdTags) {
                            candidateNodes.addAll(tag.memories.toList())
                        }
                        // 防御性：排除任何带 `#auto_root` 二级 tag 的节点（root 节点不删）
                        val idsToDelete = candidateNodes
                            .filter { node -> node.tags.none { it.name == "#auto_root" } }
                            .map { it.id }
                            .distinct()
                        if (idsToDelete.isNotEmpty()) {
                            deletedSummaryId = repo.deleteMemories(idsToDelete)
                        }
                        // 节点删完后清扫变长后缀的孤儿 tag（与 R-AGENT-029 同款）
                        orphanTagsCleaned = repo.cleanupOrphanTagsByPrefix("#auto_summary_id:")
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "R-AGENT-041-a: profile=$profileId SUMMARY_ID delete failed: ${e.message}")
                    }

                    totalDeleted += deletedSummary + deletedExtracted + deletedSummaryId
                    totalOrphanTagsCleaned += orphanTagsCleaned
                    AppLogger.d(
                        TAG,
                        "R-AGENT-041-a: profile=$profileId deleted summary=$deletedSummary extracted=$deletedExtracted summaryId=$deletedSummaryId orphanTags=$orphanTagsCleaned"
                    )
                }
                prefs.edit().putBoolean(key, true).apply()
                AppLogger.d(
                    TAG,
                    "R-AGENT-041-a: deletion done, totalDeleted=$totalDeleted orphanTagsCleaned=$totalOrphanTagsCleaned across ${profiles.size} profile(s), 耗时${System.currentTimeMillis() - migrationStartTime}ms"
                )
            } catch (e: Exception) {
                AppLogger.w(TAG, "R-AGENT-041-a: legacy node deletion failed, will retry next launch: ${e.message}")
            }
        }
    }

    private fun cleanDirectory(tempDir: File, preserveRootNoMedia: Boolean): Int {
        if (!tempDir.exists() || !tempDir.isDirectory) {
            return 0
        }
        if (preserveRootNoMedia) {
            val noMediaFile = File(tempDir, ".nomedia")
            if (!noMediaFile.exists()) {
                noMediaFile.createNewFile()
            }
        }
        AppLogger.d(TAG, "开始清理临时文件目录: ${tempDir.absolutePath}")
        val totalDeleted =
            deleteRecursively(
                rootDir = tempDir,
                file = tempDir,
                preserveRootNoMedia = preserveRootNoMedia,
                isRoot = true
            )
        AppLogger.d(TAG, "已删除${totalDeleted}个临时文件: ${tempDir.absolutePath}")
        return totalDeleted
    }

    private fun deleteRecursively(
        rootDir: File,
        file: File,
        preserveRootNoMedia: Boolean,
        isRoot: Boolean = false
    ): Int {
        var deletedCount = 0
        if (file.isDirectory) {
            val children = file.listFiles()
            children?.forEach { child ->
                deletedCount += deleteRecursively(
                    rootDir = rootDir,
                    file = child,
                    preserveRootNoMedia = preserveRootNoMedia,
                    isRoot = false
                )
            }
            if (!isRoot && file.exists()) {
                file.delete()
            }
        } else if (file.isFile) {
            val isRootNoMedia =
                preserveRootNoMedia &&
                    file.parentFile?.absolutePath == rootDir.absolutePath &&
                    file.name == ".nomedia"
            if (!isRootNoMedia && file.delete()) {
                deletedCount++
            }
        }
        return deletedCount
    }

    private fun startGlobalAIForegroundServiceIfNeeded() {
        try {
            val alwaysListeningEnabled = runBlocking {
                WakeWordPreferences(applicationContext).alwaysListeningEnabledFlow.first()
            }
            val externalHttpEnabled = runBlocking {
                ExternalHttpApiPreferences.getInstance(applicationContext).enabledFlow.first()
            }
            if ((!alwaysListeningEnabled && !externalHttpEnabled) || AIForegroundService.isRunning.get()) {
                return
            }
            val intent = Intent(this, AIForegroundService::class.java).apply {
                putExtra(AIForegroundService.EXTRA_STATE, AIForegroundService.STATE_IDLE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "按持久后台职责状态启动 AIForegroundService 失败: ${e.message}", e)
        }
    }

    /** 初始化应用语言设置 */
    private fun initializeAppLanguage() {
        try {
            // 同步获取已保存的语言设置
            val languageCode = runBlocking {
                try {
                    // 使用更安全的方式检查preferencesManager
                    val manager = runCatching { preferencesManager }.getOrNull()
                    if (manager != null) {
                        manager.appLanguage.first()
                    } else {
                        UserPreferencesManager.DEFAULT_LANGUAGE
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "获取语言设置失败", e)
                    UserPreferencesManager.DEFAULT_LANGUAGE
                }
            }

            AppLogger.d(TAG, "获取语言设置: $languageCode")

            // 立即应用语言设置
            val locale = LocaleUtils.getLocaleForLanguageCode(languageCode, this)
            // 设置默认语言
            Locale.setDefault(locale)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ 使用AppCompatDelegate API
                val localeList = LocaleListCompat.create(locale)
                AppCompatDelegate.setApplicationLocales(localeList)
                AppLogger.d(TAG, "使用AppCompatDelegate设置语言: $languageCode")
            } else {
                // 较旧版本Android - 此处使用的部分更新将在attachBaseContext中完成更完整更新
                val config = Configuration()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val localeList = LocaleList(locale)
                    LocaleList.setDefault(localeList)
                    config.setLocales(localeList)
                } else {
                    config.locale = locale
                }

                resources.updateConfiguration(config, resources.displayMetrics)
                AppLogger.d(TAG, "使用Configuration设置语言: $languageCode")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "初始化语言设置失败", e)
        }
    }

    override fun attachBaseContext(base: Context) {
        configureOpenMpEnvironment()
        // 在基础上下文附加前应用语言设置
        try {
            val code = LocaleUtils.getCurrentLanguage(base)
            val locale = LocaleUtils.getLocaleForLanguageCode(code, base)
            val config = Configuration(base.resources.configuration)

            // 设置语言配置
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val localeList = LocaleList(locale)
                LocaleList.setDefault(localeList)
                config.setLocales(localeList)
            } else {
                config.locale = locale
                Locale.setDefault(locale)
            }

            // 使用createConfigurationContext创建新的上下文
            val context = base.createConfigurationContext(config)
            super.attachBaseContext(context)
            AppLogger.d(TAG, "成功应用基础上下文语言: $code")
        } catch (e: Exception) {
            AppLogger.e(TAG, "应用基础上下文语言失败", e)
            super.attachBaseContext(base)
        }
    }

    override fun onTerminate() {
        super.onTerminate()

        AppLifecycleHookPluginRegistry.dispatchAsync(
            event = AppLifecycleEvent.APPLICATION_TERMINATE,
            params = AppLifecycleHookParams(applicationContext)
        )
        
        try {
            if (AIForegroundService.isRunning.get()) {
                val intent = Intent(applicationContext, AIForegroundService::class.java)
                stopService(intent)
                AppLogger.d(TAG, "应用终止，已停止 AIForegroundService")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "终止时停止 AIForegroundService 失败: ${e.message}", e)
        }

        // 清理终端管理器和SSH连接
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Terminal.getInstance(applicationContext).destroy()
                AppLogger.d(TAG, "应用终止，已清理所有终端会话和SSH连接")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "清理终端管理器失败: ${e.message}", e)
        }
        
        // 在应用终止时关闭LocalWebServer服务器
        try {
            val webServer = LocalWebServer.getInstance(applicationContext, LocalWebServer.ServerType.WORKSPACE)
            if (webServer.isRunning()) {
                webServer.stop()
                AppLogger.d(TAG, "应用终止，已关闭本地Web服务器")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "关闭本地Web服务器失败: ${e.message}", e)
        }

        // 在应用终止时，关闭虚拟屏幕 Overlay 并断开 Shower WebSocket 连接
        try {
            VirtualDisplayOverlay.hideAll()
        } catch (e: Exception) {
            AppLogger.e(TAG, "终止时隐藏 VirtualDisplayOverlay 失败: ${e.message}", e)
        }
        try {
            ShowerController.shutdown()
        } catch (e: Exception) {
            AppLogger.e(TAG, "终止时关闭 ShowerController 失败: ${e.message}", e)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        AppLifecycleHookPluginRegistry.dispatchAsync(
            event = AppLifecycleEvent.APPLICATION_LOW_MEMORY,
            params = AppLifecycleHookParams(applicationContext)
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        AppLifecycleHookPluginRegistry.dispatchAsync(
            event = AppLifecycleEvent.APPLICATION_TRIM_MEMORY,
            params =
                AppLifecycleHookParams(
                    context = applicationContext,
                    extras =
                        mapOf(
                            "level" to level
                        )
                )
        )
    }
}
