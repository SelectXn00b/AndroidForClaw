# Hermes for Android 架构文档

## 项目数据目录

```
/sdcard/.hermes/              ← 项目数据根目录
├── config/
│   └── openclaw.json                 ← 主配置文件
├── workspace/
│   ├── skills/                       ← 用户自定义 Skills (优先级最高)
│   ├── sessions/                     ← 会话历史 (JSONL)
│   └── memory/                       ← 持久化记忆
├── skills/                           ← 托管 Skills (ClawHub 安装)
└── logs/
```

---

## 总体架构

```
┌──────────────────────────────────────────┐
│  Channels                                 │
│  飞书 · Discord · 设备内对话               │
├──────────────────────────────────────────┤
│  Gateway (HermesGatewayController)        │
│  GatewayRunner → agentRunner → Core       │
│  ChatRuntimeSlot.GATEWAY 隔离             │
├──────────────────────────────────────────┤
│  Agent Runtime                            │
│  ChatServiceCore → EnhancedAIService      │
│  → LLM Providers → Tool Call API          │
├──────────────────────────────────────────┤
│  hermes-android (Kotlin 1:1 port)         │
│  AgentLoop · PromptBuilder · Plugins      │
├──────────────────────────────────────────┤
│  Android Platform                         │
│  Accessibility · Termux SSH ·             │
│  MediaProjection · BrowserForClaw         │
└──────────────────────────────────────────┘
```

---

## 核心组件

### 1. ChatServiceCore 与 Runtime Slot

**核心设计**：每个使用场景通过独立 `ChatRuntimeSlot` 隔离。

```kotlin
enum class ChatRuntimeSlot {
    MAIN,       // 主 UI 聊天
    FLOATING,   // 悬浮窗聊天
    GATEWAY     // Hermes Gateway 消息（LOCAL_ONLY 模式）
}
```

`ChatRuntimeHolder`（单例）为每个 slot 持有独立的 `ChatServiceCore` 实例。

**ChatServiceCore 六大 Delegate**：
- `MessageCoordinationDelegate` — `sendUserMessage` 入口，编排协调
- `MessageProcessingDelegate` — 底层消息发送，管理 streaming 状态
- `ChatHistoryDelegate` — Room DB 操作，会话切换
- `TokenStatisticsDelegate` — Token 计数与上下文窗口追踪
- `ApiConfigDelegate` — 模型/工具/thinking 模式配置
- `AttachmentDelegate` — 文件附件管理

**跨 Slot 同步**：
- MAIN → FLOATING：chat selection 同步
- GATEWAY → MAIN：turn 完成后 MAIN 自动刷新（通过 `registerTurnSync`）

---

### 2. HermesGatewayController

**职责**：桥接 `GatewayRunner`（hermes-android 模块）与 `ChatServiceCore`。

**消息流**：
```
Platform (Feishu/Discord)
  → GatewayRunner._handleMessage
    → agentRunner(text, sessionKey, platform, chatId, userId)
      → HermesGatewayController.runHermesAgent
        → ChatRuntimeHolder.getCore(GATEWAY)
        → core.switchChatLocal("gw:$sessionKey:$chatId")
        → core.sendUserMessage(chatIdOverride, isSubTask=true)
        → poll activeStreamingChatIds for completion
        → read final AI message from Room DB
        → extractFinalReply (strip XML markup)
      → return plain text reply
    → DeliveryRouter → Platform reply
```

**GatewayChatEventBus**：SharedFlow 事件总线，解耦 gateway 生命周期与 UI 刷新。

---

### 3. hermes-android 模块

Python `hermes-agent/` 的 1:1 Kotlin 翻译（210 个 .kt 文件，194/194 对齐）。

**关键组件**：
- `AgentLoop.kt` — 核心执行循环（multi-turn tool calling）
- `PromptBuilder.kt` — 系统提示词构建
- `gateway/Run.kt` — GatewayRunner（会话管理、平台适配器生命周期）
- `gateway/platforms/Feishu.kt` — 飞书适配器（WebSocket、事件去重）

**agentRunner 合约**：
```kotlin
var agentRunner: (suspend (text: String, sessionKey: String, platform: String, chatId: String, userId: String) -> String)? = null
```

简单的 text-in text-out 挂起函数，由 app 侧 Controller 设置。

---

### 4. LLM Provider 层

**EnhancedAIService** — 统一入口，管理 ChatRuntime 生命周期。

**Provider 实现**：
- `OpenAIProvider` — OpenAI / OpenRouter / 自定义兼容 API
- `ClaudeProvider` — Anthropic Claude API
- `GeminiProvider` — Google Gemini API
- `AIServiceFactory` — 根据 model ID 前缀路由到对应 Provider

**StructuredToolCallBridge** — Tool Call API 与 XML-in-text 模式的桥接。

---

### 5. Tool 系统

**ToolRegistration** — 动态工具注册，支持 gateway-safe 子集过滤。

| 类别 | 工具 |
|------|------|
| 设备操作 | `device` (Playwright 模式：snapshot/tap/type/scroll/press/open) |
| 文件 | `read_file` / `write_file` / `edit_file` / `list_dir` |
| 执行 | `exec` (自动路由 Termux SSH 或内置 Shell) |
| 网络 | `web_search` / `web_fetch` |
| JavaScript | `javascript` (QuickJS) |
| 技能 | `skills_search` / `skills_install` |
| 记忆 | `memory_search` / `memory_get` |
| 配置 | `config_get` / `config_set` |
| Android | `list_installed_apps` / `install_app` / `start_activity` / `stop` / `eye` / `log` |

---

### 6. Skill Recorder

录制用户操作步骤，自动生成可复用的 Skill。

- `FrameCapture` — 捕获 Accessibility 树 + 元素 bounds
- `FrameSimplifier` — 帧降噪、元素去重
- `ClickTargetInferrer` — 从 AccessibilityNodeInfo 推断点击目标
- `SkillSummarizer` — 从录制帧生成 step-by-step 指令
- `SkillRecorderService` — 录制生命周期管理 + 悬浮窗
- `SkillRecorderOverlayManager / OverlayUI` — Compose 悬浮窗控制界面

---

### 7. Skills 系统

Skills = 教 Agent 如何使用工具的 Markdown 文档。三层优先级：

1. **工作区 Skills** (最高) — `/sdcard/.hermes/workspace/skills/`
2. **托管 Skills** (中等) — `/sdcard/.hermes/skills/` (ClawHub 安装)
3. **内置 Skills** (最低) — `app/src/main/assets/skills/`

---

## Android 平台集成

### Accessibility Service
- **用途**: UI 操作（点击、滑动、输入）和 UI 树遍历
- **实现**: `AccessibilityActionListener` + `ActionListenerFactory`

### MediaProjection
- **用途**: 屏幕截图
- **实现**: MediaProjection API

### Termux SSH
- **用途**: 完整 Linux 命令执行环境
- **实现**: SSH 连接池 + IGNORE 探活 + 活动超时 + 自动重连
- **路由**: 自动判断 Termux 可用性，fallback 到内置 Shell

### MCP Server
- **用途**: 暴露无障碍/截屏能力给外部 Agent (端口 8399, Streamable HTTP)

---

## 包结构

```
app/src/main/java/com/ai/assistance/operit/
├── api/chat/                     # ChatRuntime 管理
│   ├── ChatRuntimeHolder.kt      # Slot 单例持有者
│   ├── ChatRuntimeSlot.kt        # MAIN / FLOATING / GATEWAY
│   ├── EnhancedAIService.kt      # 统一 AI 服务入口
│   └── llmprovider/              # OpenAI / Claude / Gemini Provider
├── core/
│   ├── chat/AIMessageManager.kt  # 消息解析与格式化
│   ├── config/                   # SystemPromptConfig, SystemToolPrompts
│   ├── tools/                    # ToolRegistration + 所有 Tool 实现
│   ├── skillrecorder/            # Skill Recorder 核心逻辑
│   └── ...
├── hermes/
│   ├── HermesAdapter.kt          # Hermes ↔ App 桥接
│   ├── OperitChatCompletionServer.kt  # OpenAI-spec server 实现
│   ├── OperitToolDispatcher.kt   # Tool 派发
│   └── gateway/
│       ├── HermesGatewayController.kt  # Gateway 主控制器
│       └── GatewayChatEventBus.kt      # 事件总线
├── services/
│   ├── ChatServiceCore.kt        # 核心服务（6 delegate 组合）
│   └── core/                     # Delegate 实现
├── data/                         # Room DB, DataStore, Model
└── ui/                           # Compose UI

hermes-android/src/main/java/com/xiaomo/hermes/
├── hermes/
│   ├── AgentLoop.kt              # 核心 Agent 循环
│   ├── agent/PromptBuilder.kt    # 提示词构建
│   ├── gateway/
│   │   ├── Run.kt                # GatewayRunner
│   │   └── platforms/Feishu.kt   # 飞书适配器
│   ├── plugins/                  # Memory, MCP 等插件
│   └── ...
```

---

## 配置

**主配置文件**: `/sdcard/.hermes/config/openclaw.json`

包含：模型 Provider 配置、Agent 默认参数、渠道配置、模型覆盖。

---

**架构版本**: v5.0
**最后更新**: 2026-05-08
