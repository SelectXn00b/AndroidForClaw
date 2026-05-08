package com.ai.assistance.operit.core.skillrecorder

import android.content.Context
import com.ai.assistance.operit.core.tools.system.action.ActionListener
import com.ai.assistance.operit.data.model.skillrecorder.EventDetails
import com.ai.assistance.operit.data.model.skillrecorder.RecordingFrame
import com.ai.assistance.operit.data.repository.UIHierarchyManager
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * 帧捕获器：监听无障碍事件，抓取 UI 层级，构建 RecordingFrame。
 */
class FrameCapture(private val context: Context) {

    companion object {
        private const val TAG = "FrameCapture"
        /** 同类型事件去抖间隔 (ms) */
        private const val DEBOUNCE_MS = 300L
        /** SCROLL 事件最大频率 (ms) */
        private const val SCROLL_THROTTLE_MS = 500L
        /** 最大帧数 */
        private const val MAX_FRAMES = 500

        private val SIGNIFICANT_EVENTS = setOf(
            ActionListener.ActionType.CLICK,
            ActionListener.ActionType.LONG_CLICK,
            ActionListener.ActionType.TEXT_INPUT,
            ActionListener.ActionType.SCROLL,
            ActionListener.ActionType.SCREEN_CHANGE
        )

        /** 常见 Launcher / 系统 UI 包名前缀，录制启动阶段自动跳过 */
        private val LAUNCHER_PACKAGES = setOf(
            "com.android.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.miui.home",
            "com.huawei.android.launcher",
            "com.oppo.launcher",
            "com.vivo.launcher",
            "com.sec.android.app.launcher",
            "com.oneplus.launcher",
            "com.realme.launcher",
            "com.nothing.launcher",
            "com.teslacoilsw.launcher",
            "com.microsoft.launcher",
            "net.oneplus.launcher",
            "com.android.systemui"
        )
    }

    private val frameIndex = AtomicInteger(0)
    private val mutex = Mutex()
    private var lastEventTime = 0L
    private var lastEventType = ""
    private var lastScrollTime = 0L
    /** Track last SCREEN_CHANGE activity to prevent duplicate captures from polling + callback */
    private var lastScreenChangeActivity: String? = null
    /** Resolved at construction time so we filter against the real applicationId */
    private val selfPackage: String = context.packageName
    /** Kotlin package prefix — Activities use this, not applicationId */
    private val selfKotlinPackage: String = "com.ai.assistance.operit"
    /** Store previous UI hierarchy for diff-based click inference */
    private var previousUiHierarchy: String = ""

    /**
     * Whether we are still in the "launch phase" — the period after recording starts
     * where the user navigates from this app / launcher to the target app.
     * During this phase, events from launchers and this app are silently skipped.
     * Once we see an event from a non-launcher, non-self package, the launch phase ends.
     */
    private var inLaunchPhase = true

    /**
     * 处理一个 ActionEvent，决定是否捕获帧并加入 frameBuffer。
     * 在协程中调用。
     */
    suspend fun processEvent(
        event: ActionListener.ActionEvent,
        frameBuffer: MutableList<RecordingFrame>
    ): RecordingFrame? = withContext(Dispatchers.IO) {
        // 帧数限制
        if (frameBuffer.size >= MAX_FRAMES) return@withContext null

        // 过滤自身事件（applicationId 或 Kotlin package 前缀都匹配）
        val pkg = event.elementInfo?.packageName
        if (pkg != null && isSelfPackage(pkg)) return@withContext null

        // 启动阶段过滤：跳过 Launcher / 系统 UI / 自身 app 事件，直到用户到达目标 app
        if (inLaunchPhase) {
            if (pkg == null || isLauncherPackage(pkg) || isSelfPackage(pkg)) {
                return@withContext null
            }
            // 到达目标 app，结束启动阶段
            inLaunchPhase = false
            AppLogger.i(TAG, "启动阶段结束，目标应用: $pkg")
        }

        // 录制中途过滤：如果用户切回 Launcher 或自身 app，跳过这些帧
        // （典型场景：录制结束时用户切回我们的 app 停止录制）
        if (pkg != null && (isLauncherPackage(pkg) || isSelfPackage(pkg))) {
            return@withContext null
        }

        val eventType = event.actionType.name

        // 只关注有意义的事件
        if (event.actionType !in SIGNIFICANT_EVENTS) return@withContext null

        // 去抖
        mutex.withLock {
            val now = System.currentTimeMillis()

            // SCROLL 降频
            if (event.actionType == ActionListener.ActionType.SCROLL) {
                if (now - lastScrollTime < SCROLL_THROTTLE_MS) return@withContext null
                lastScrollTime = now
            }

            // SCREEN_CHANGE 去重：同一 Activity 只记录一次（防止 polling + callback 双路径重复）
            if (event.actionType == ActionListener.ActionType.SCREEN_CHANGE) {
                val activityName = event.elementInfo?.className
                if (activityName != null && activityName == lastScreenChangeActivity) {
                    return@withContext null
                }
                lastScreenChangeActivity = activityName
            }

            // 同类型事件去抖
            if (eventType == lastEventType && now - lastEventTime < DEBOUNCE_MS) {
                return@withContext null
            }

            lastEventTime = now
            lastEventType = eventType
        }

        try {
            // 抓取 UI 层级
            val uiHierarchy = try {
                UIHierarchyManager.getUIHierarchy(context) ?: ""
            } catch (e: Exception) {
                AppLogger.w(TAG, "获取UI层级失败: ${e.message}")
                ""
            }

            // 获取当前 Activity
            val activityName = try {
                UIHierarchyManager.getCurrentActivityName(context)
            } catch (e: Exception) {
                null
            }

            // Build EventDetails — for CLICK events with no element info,
            // try to infer the click target by diffing before/after UI trees
            val details = if (event.actionType == ActionListener.ActionType.CLICK &&
                event.elementInfo?.text.isNullOrBlank() &&
                event.elementInfo?.contentDescription.isNullOrBlank() &&
                event.elementInfo?.resourceId.isNullOrBlank() &&
                previousUiHierarchy.isNotBlank() && uiHierarchy.isNotBlank()
            ) {
                val inferred = ClickTargetInferrer.inferClickTarget(previousUiHierarchy, uiHierarchy)
                if (inferred != null) {
                    EventDetails(
                        className = inferred.className,
                        text = inferred.text,
                        contentDescription = inferred.contentDescription,
                        resourceId = inferred.resourceId,
                        inputText = null,
                        additionalData = mapOf("inferConfidence" to inferred.confidence.name)
                    )
                } else {
                    EventDetails(
                        className = event.elementInfo?.className,
                        text = event.elementInfo?.text,
                        contentDescription = event.elementInfo?.contentDescription,
                        resourceId = event.elementInfo?.resourceId,
                        inputText = event.inputText,
                        additionalData = emptyMap()
                    )
                }
            } else {
                EventDetails(
                    className = event.elementInfo?.className,
                    text = event.elementInfo?.text,
                    contentDescription = event.elementInfo?.contentDescription,
                    resourceId = event.elementInfo?.resourceId,
                    inputText = event.inputText,
                    additionalData = emptyMap()
                )
            }

            val frame = RecordingFrame(
                index = frameIndex.getAndIncrement(),
                timestamp = event.timestamp,
                eventType = eventType,
                eventDetails = details,
                activityName = activityName,
                packageName = pkg,
                uiHierarchySummary = uiHierarchy,
                previousUiHierarchy = previousUiHierarchy
            )

            // Update previousUiHierarchy for next frame
            previousUiHierarchy = uiHierarchy

            frameBuffer.add(frame)
            frame
        } catch (e: Exception) {
            AppLogger.e(TAG, "构建帧失败", e)
            null
        }
    }

    fun reset() {
        frameIndex.set(0)
        lastEventTime = 0L
        lastEventType = ""
        lastScrollTime = 0L
        lastScreenChangeActivity = null
        inLaunchPhase = true
        previousUiHierarchy = ""
    }

    /**
     * Check if a package name belongs to a known launcher or system UI.
     */
    private fun isLauncherPackage(pkg: String): Boolean {
        return LAUNCHER_PACKAGES.any { pkg == it || pkg.startsWith("$it.") }
    }

    /**
     * Check if a package name belongs to this app (either applicationId or Kotlin package).
     */
    private fun isSelfPackage(pkg: String): Boolean {
        return pkg == selfPackage || pkg.startsWith("$selfPackage.") ||
               pkg == selfKotlinPackage || pkg.startsWith("$selfKotlinPackage.")
    }
}
