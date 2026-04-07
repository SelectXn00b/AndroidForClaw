# OpenClaw <-> AndroidForClaw 映射表

> 最后更新: 2026-04-07
> OpenClaw 版本: 2026.3.11 (29dc654)
> Kotlin 总量: ~102K LOC / 513 个 .kt 文件 (agent/ 181 文件)

---

## 对齐总览

| 类别 | 模块数 | 状态 |
|------|--------|------|
| 已完整对齐 (Wave 1-5) | 36 | 逻辑一比一移植 |
| 已实现 (核心功能) | 15 | agent/gateway/config/core 等 |
| 不可移植 | 7 | CLI/TUI/daemon/terminal/node-host/types/i18n |
| 仅测试用 | 4 | scripts/docs/test-helpers/test-utils |

---

## 顶层目录

| OpenClaw | AndroidForClaw | 说明 |
|----------|----------------|------|
| `src/` | `app/src/main/java/com/xiaomo/androidforclaw/` | 主代码 |
| `skills/` | `app/src/main/assets/skills/` | 内置 Skills |
| `extensions/` | `extensions/` | 渠道扩展 |
| `test/` | `app/src/test/` | 单元测试 |
| `docs/` | `docs/` | 文档 |

---

## src/ 模块映射 (58 模块)

### 已完整对齐 (Wave 1-5, 逻辑一比一移植)

| OpenClaw 模块 | Kotlin 包 | 文件数 | Wave |
|---|---|---|---|
| `acp/` | `acp/` | 7 | 5 |
| `auto-reply/` | `autoreply/` | 10 | 4 |
| `channels/` (共享基础设施) | `chat/` | 11 | 5 |
| `commands/` | `commands/` | 4 | 2 |
| `context-engine/` | `contextengine/` | 4 | 2 |
| `flows/` | `flows/` | 2 | 2 |
| `hooks/` | `hooks/` | 8 | 4 |
| `image-generation/` | `imagegeneration/` | 4 | 3 |
| `infra/` | `infra/` | 11 | 1 |
| `interactive/` | `interactive/` | 1 | 2 |
| `link-understanding/` | `linkunderstanding/` | 3 | 3 |
| `markdown/` | `markdown/` | 4 | 3 |
| `media/` | `media/` | 7 | 4 |
| `media-generation/` | `mediageneration/` | 1 | 3 |
| `media-understanding/` | `mediaunderstanding/` | 4 | 3 |
| `memory-host-sdk/` | `memoryhostsdk/` | 6 | 5 |
| `music-generation/` | `musicgeneration/` | 4 | 3 |
| `pairing/` | `pairing/` | 6 | 4 |
| `plugin-sdk/` | `pluginsdk/` | 18 | 5 |
| `plugins/` | `plugins/` | 11 | 5 |
| `process/` | `process/` | 3 | 1 |
| `realtime-transcription/` | `realtimetranscription/` | 2 | 3 |
| `realtime-voice/` | `realtimevoice/` | 2 | 3 |
| `routing/` | `routing/` | 4 | 2 |
| `secrets/` | `secrets/` | 14 | 5 |
| `sessions/` | `sessions/` + `session/` + `agent/session/` | 11 | 2 |
| `shared/` | `shared/` | 24 | 1 |
| `tasks/` | `tasks/` | 11 | 4 |
| `tts/` | `tts/` | 4 | 3 |
| `video-generation/` | `videogeneration/` | 4 | 3 |
| `web-fetch/` | `webfetch/` | 2 | 3 |
| `web-search/` | `websearch/` | 2 | 3 |
| `wizard/` | `wizard/` | 3 | 4 |

### 已实现 (核心功能, 非 Wave 对齐)

| OpenClaw 模块 | Kotlin 包 | 文件数 | 说明 |
|---|---|---|---|
| `agents/` | `agent/` | 181 | 核心 Agent 循环/工具/上下文/技能 (1:1 文件对齐) |
| `bindings/` | `bindings/` | 2 | 绑定记录 |
| `canvas-host/` | `canvas/` | 2 | Canvas |
| `channels/` (渠道插件) | `channel/` + `extensions/` | 6+N | 飞书/Discord 完整, 其余框架 |
| `compat/` | `compat/` | 1 | 兼容层 |
| `config/` | `config/` | 9 | 配置体系 |
| `cron/` | `cron/` | 9 | 定时任务 |
| `gateway/` | `gateway/` | 20 | 网关服务 |
| `logging/` | `logging/` | 3 | 日志 |
| `mcp/` | `mcp/` | 3 | MCP 协议 |
| `security/` | `security/` | 5 | 安全 (TokenAuth 等) |
| `utils/` | `util/` | 12 | 工具函数 |
| `web/` | `web/` | 1 | Web 服务 |

### 不可移植 / 仅测试

| OpenClaw 模块 | 原因 |
|---|---|
| `cli/` | Android 用 UI, 无 CLI |
| `daemon/` | Android Service 替代 |
| `terminal/` | 桌面专用 |
| `tui/` | 桌面 TUI |
| `node-host/` | Node.js 专用 |
| `types/` | TypeScript .d.ts |
| `i18n/` | Android 用 res/values/ |
| `scripts/` | 仅测试 |
| `docs/` | 仅测试 |
| `test-helpers/` | 仅测试 |
| `test-utils/` | 仅测试 |

---

## 核心文件级映射

### Agent Runtime (1:1 文件对齐完成)

#### agent/loop/ — 核心循环

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `agents/pi-embedded-runner/run.ts` | `agent/loop/Run.kt` | ✅ |
| `agents/agent-command.ts` | `agent/loop/AgentCommand.kt` | ✅ |
| `agents/pi-embedded-subscribe.ts` | `agent/loop/Subscribe.kt` | ✅ |
| `agents/pi-embedded-runner/run/attempt.ts` | `agent/loop/Attempt.kt` | ✅ |
| `agents/pi-embedded-runner/run/failover-policy.ts` | `agent/loop/FailoverPolicy.kt` | ✅ |
| `agents/pi-embedded-runner/run/incomplete-turn.ts` | `agent/loop/IncompleteTurnDetector.kt` | ✅ |
| `agents/tool-loop-detection.ts` | `agent/loop/ToolLoopDetection.kt` | ✅ |
| `agents/tool-call-argument-repair.ts` | `agent/loop/ToolCallArgumentRepair.kt` | ✅ |
| `agents/tool-call-normalization.ts` | `agent/loop/ToolCallNormalization.kt` | ✅ |
| `agents/stop-reason-recovery.ts` | `agent/loop/StopReasonRecovery.kt` | ✅ |
| `agents/auth-profile-rotation.ts` | `agent/loop/AuthProfileRotation.kt` | ✅ |
| `agents/anthropic-transport-stream.ts` | `agent/loop/AnthropicTransportStream.kt` | ✅ |
| `agents/anthropic-payload-policy.ts` | `agent/loop/AnthropicPayloadPolicy.kt` | ✅ |
| `agents/google-transport-stream.ts` | `agent/loop/GoogleTransportStream.kt` | ✅ |
| `agents/openai-transport-stream.ts` | `agent/loop/OpenAITransportStream.kt` | ✅ |
| `agents/provider-transport-stream.ts` | `agent/loop/ProviderTransportStream.kt` | ✅ |
| `agents/provider-transport-fetch.ts` | `agent/loop/ProviderTransportFetch.kt` | ✅ |
| `agents/model-auth.ts` | `agent/loop/ModelAuth.kt` | ✅ |
| `agents/model-selection.ts` | `agent/loop/ModelSelection.kt` | ✅ |

#### agent/context/ — 上下文构建

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `agents/system-prompt.ts` | `agent/context/SystemPrompt.kt` | ✅ |
| `agents/pi-embedded-runner/system-prompt.ts` | `agent/context/EmbeddedSystemPrompt.kt` | ✅ |
| `agents/bootstrap-files.ts` + `bootstrap-budget.ts` | `agent/context/BootstrapFiles.kt` | ✅ |
| `agents/pi-embedded-helpers.ts` | `agent/context/EmbeddedHelpers.kt` | ✅ |
| `agents/context.ts` + `context-window-guard.ts` | `agent/context/ContextWindowGuard.kt` | ✅ |
| `agents/compaction.ts` | `agent/context/MessageCompactor.kt` | ✅ |
| `agents/compaction-real-conversation.ts` | `agent/context/CompactionRealConversation.kt` | ✅ |
| `agents/session-tool-result-guard.ts` | `agent/context/ToolResultContextGuard.kt` | ✅ |
| `agents/tool-policy-pipeline.ts` | `agent/context/ToolPolicyPipeline.kt` | ✅ |
| `agents/tool-policy.ts` | `agent/context/ToolPolicyRules.kt` | ✅ |
| `agents/tool-policy-shared.ts` | `agent/context/ToolPolicyShared.kt` | ✅ |
| `agents/bootstrap-cache.ts` | `agent/context/BootstrapCache.kt` | ✅ |
| `agents/context-cache.ts` | `agent/context/ContextCache.kt` | ✅ |

#### agent/session/ — 会话管理

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `agents/session-dirs.ts` | `agent/session/SessionDirs.kt` | ✅ |
| `agents/command/session-store.ts` | `agent/session/SessionStore.kt` | ✅ |
| `agents/session-transcript-repair.ts` | `agent/session/SessionTranscriptRepair.kt` | ✅ |
| `agents/pi-embedded-utils.ts` | `agent/session/EmbeddedUtils.kt` | ✅ |

#### agent/subagent/ — 子代理

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `agents/subagent-spawn.ts` | `agent/subagent/SubagentSpawn.kt` | ✅ |
| `agents/subagent-announce.ts` | `agent/subagent/SubagentAnnounce.kt` | ✅ |
| `agents/subagent-control.ts` | `agent/subagent/SubagentControl.kt` | ✅ |
| `agents/subagent-registry.ts` | `agent/subagent/SubagentRegistry.kt` | ✅ |
| `agents/subagent-registry-queries.ts` | `agent/subagent/SubagentRegistryQueries.kt` | ✅ |
| `agents/subagent-registry.store.ts` | `agent/subagent/SubagentRegistryStore.kt` | ✅ |
| `agents/subagent-registry.types.ts` | `agent/subagent/SubagentRegistryTypes.kt` | ✅ |
| `agents/subagent-capabilities.ts` | `agent/subagent/SubagentCapabilities.kt` | ✅ |
| `agents/subagent-lifecycle-events.ts` | `agent/subagent/SubagentLifecycleEvents.kt` | ✅ |

#### agent/memory/ — 记忆系统

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `agents/memory/sqlite.ts` | `agent/memory/MemorySqlite.kt` | ✅ |
| `agents/memory/sqlite-vec.ts` | `agent/memory/MemoryVec.kt` | ✅ |
| `agents/memory/search-manager.ts` | `agent/memory/SearchManager.kt` | ✅ |
| `agents/memory/hybrid.ts` | `agent/memory/HybridSearch.kt` | ✅ |
| `agents/memory-search.ts` | `agent/tools/memory/MemorySearchSkill.kt` | ✅ |

#### agent/skills/ — 技能

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `agents/skills.ts` | `agent/skills/SkillsLoader.kt` | ✅ |
| `agents/skills-status.ts` | `agent/skills/SkillStatus.kt` | ✅ |

#### agent/tools/ — 工具

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `agents/pi-tools.read.ts` | `agent/tools/ReadFileTool.kt` | ✅ |
| `agents/apply-patch.ts` | `agent/tools/EditFileTool.kt` + `WriteFileTool.kt` | ✅ |
| `agents/bash-tools.exec.ts` | `agent/tools/ExecTool.kt` | ✅ |
| `agents/pi-tools.ts` | `agent/tools/ToolCallDispatcher.kt` | ✅ |
| `agents/pi-tools.policy.ts` | `agent/tools/ToolPolicy.kt` | ✅ |
| `agents/pi-tools.before-tool-call.ts` | `agent/tools/BeforeToolCall.kt` | ✅ |
| `agents/pi-tools.schema.ts` | `agent/tools/ToolSchema.kt` | ✅ |
| `agents/tool-display.ts` | `agent/tools/ToolDisplay.kt` | ✅ |
| `agents/tool-catalog.ts` | `agent/tools/ToolRegistry.kt` | ✅ |
| `agents/tools/web-fetch.ts` | `agent/tools/WebFetchTool.kt` | ✅ |
| `agents/tools/web-search.ts` | `agent/tools/WebSearchTool.kt` | ✅ |
| `agents/tools/memory-tool.ts` | `agent/tools/memory/MemorySearchSkill.kt` | ✅ |
| `agents/tools/tts-tool.ts` | `agent/tools/TtsTool.kt` | ✅ |

#### agent/ — 顶层

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `agents/agent-paths.ts` | `agent/AgentPaths.kt` | ✅ |
| `agents/workspace.ts` | `agent/Workspace.kt` | ✅ |
| `agents/workspace-run.ts` | `agent/WorkspaceRun.kt` | ✅ |
| `agents/identity-file.ts` | `agent/IdentityFile.kt` | ✅ |
| `agents/runtime-plugins.ts` | `agent/RuntimePlugins.kt` | ✅ |
| `agents/models-config.ts` | `config/ModelConfig.kt` + `ProviderRegistry.kt` | ✅ |
| `agents/model-catalog.ts` | `config/ProviderRegistry.kt` | ✅ |
| `agents/model-id-normalization.ts` | `providers/ModelIdNormalization.kt` | ✅ |
| `agents/model-compat.ts` | `providers/ModelCompat.kt` | ✅ |
| `agents/model-fallback.ts` | `providers/ModelFallback.kt` | ✅ |
| `agents/api-key-rotation.ts` | `providers/ApiKeyRotation.kt` | ✅ |

#### 不适用 / 未实现

| OpenClaw | 原因 |
|----------|------|
| `agents/agent-scope.ts` | ❌ 未实现 |
| `agents/acp-spawn.ts` | ❌ 未实现 |
| `agents/sandbox.ts` | ❌ 不适用 (Android 无沙箱) |

### Gateway

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `gateway/server.ts` | `gateway/GatewayServer.kt` | ✅ |
| `gateway/boot.ts` | `gateway/GatewayController.kt` | ✅ |
| `gateway/server-methods.ts` | `gateway/GatewayService.kt` | ✅ |
| `gateway/server-chat.ts` | `gateway/MainEntryAgentHandler.kt` | ✅ |
| `gateway/auth.ts` | `gateway/security/TokenAuth.kt` | ✅ |
| `gateway/server-ws-runtime.ts` | `gateway/websocket/GatewayWebSocketServer.kt` | ✅ |
| `gateway/server-cron.ts` | `gateway/methods/CronMethods.kt` | ✅ |
| `gateway/server-channels.ts` | - | ❌ |
| `gateway/server-plugins.ts` | - | ❌ |
| `gateway/device-auth.ts` | - | ❌ |

### Config

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `config/config.ts` | `config/OpenClawConfig.kt` | ✅ |
| `config/io.ts` | `config/ConfigLoader.kt` | ✅ |
| `config/types.*.ts` (8 个) | `config/OpenClawConfig.kt` + `ModelConfig.kt` | ✅ |

### Sessions

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `sessions/session-id.ts` | `sessions/SessionId.kt` | ✅ |
| `sessions/session-label.ts` | `sessions/SessionLabel.kt` | ✅ |
| `sessions/session-lifecycle-events.ts` | `sessions/SessionLifecycleEvents.kt` | ✅ |
| `sessions/transcript-events.ts` | `sessions/TranscriptEvents.kt` | ✅ |
| `sessions/input-provenance.ts` | `sessions/InputProvenance.kt` | ✅ |
| `sessions/model-overrides.ts` | `sessions/ModelOverrides.kt` | ✅ |
| `sessions/send-policy.ts` | `sessions/SendPolicy.kt` | ✅ |
| `sessions/session-key-utils.ts` | `sessions/SessionKeyUtils.kt` | ✅ |
| `sessions/session-chat-type.ts` | `sessions/SessionChatType.kt` | ✅ |
| `sessions/session-id-resolution.ts` | `sessions/SessionIdResolution.kt` | ✅ |
| `sessions/level-overrides.ts` | `sessions/LevelOverrides.kt` | ✅ |

### Tools

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `agents/pi-tools.read.ts` | `agent/tools/ReadFileTool.kt` | ✅ |
| `agents/apply-patch.ts` | `agent/tools/EditFileTool.kt` + `WriteFileTool.kt` | ✅ |
| `agents/bash-tools.exec.ts` | `agent/tools/ExecTool.kt` | ✅ |
| `agents/tools/web-fetch.ts` | `agent/tools/WebFetchTool.kt` | ✅ |
| `agents/tools/web-search.ts` | `agent/tools/WebSearchTool.kt` | ✅ |
| `agents/tools/memory-tool.ts` | `agent/tools/memory/MemorySearchSkill.kt` | ✅ |
| `agents/tools/tts-tool.ts` | `agent/tools/TtsTool.kt` | ✅ |
| `agents/tools/sessions-*-tool.ts` | - | ❌ |
| `agents/tools/subagents-tool.ts` | - | ❌ |
| `agents/tools/pdf-tool.ts` | - | ❌ |
| `agents/tools/canvas-tool.ts` | - | ❌ |
| `agents/tools/image-generate-tool.ts` | - | ❌ |

---

## Android 独有组件

### 设备工具

| 文件 | 说明 |
|------|------|
| `agent/tools/device/DeviceTool.kt` | 统一设备操作工具 |
| `agent/tools/ScreenshotSkill.kt` | 截图 |
| `agent/tools/GetViewTreeSkill.kt` | UI 树 |
| `agent/tools/TypeSkill.kt` | 输入 |
| `agent/tools/OpenAppSkill.kt` | 启动应用 |
| `agent/tools/ListInstalledAppsSkill.kt` | 应用列表 |
| `agent/tools/ClawImeInputSkill.kt` | 输入法输入 |

### 系统服务

| 文件 | 说明 |
|------|------|
| `core/ForegroundService.kt` | 前台服务 |
| `service/ClawIME.java` | 自定义输入法 |
| `accessibility/AccessibilityProxy.kt` | 无障碍代理 |

### UI

| 路径 | 说明 |
|------|------|
| `ui/activity/MainActivityCompose.kt` | 主页 (Compose) |
| `ui/compose/ChatScreen.kt` | 聊天界面 |
| `ui/compose/ForClawConnectTab.kt` | Connect Tab |
| `ui/compose/ForClawSettingsTab.kt` | Settings Tab |
| `ui/viewmodel/ChatViewModel.kt` | 聊天 ViewModel |

### 扩展

| 路径 | 说明 |
|------|------|
| `extensions/feishu/` | 飞书 (完整: 消息解析/回复/流式卡片) |
| `extensions/discord/` | Discord (完整) |
| `extensions/telegram/` | Telegram (框架) |
| `extensions/slack/` | Slack (框架) |
| `extensions/signal/` | Signal (框架) |
| `extensions/whatsapp/` | WhatsApp (框架) |
| `extensions/weixin/` | 微信 (框架) |
| `extensions/observer/` | Observer APK |

---

## 存储路径

| OpenClaw | AndroidForClaw |
|----------|----------------|
| `~/.openclaw/` | `context.getExternalFilesDir(null)/` |
| `~/.openclaw/openclaw.json` | `<appDir>/openclaw.json` |
| `~/.openclaw/config/models.json` | `<appDir>/config/models.json` |
| `~/.openclaw/agents/main/sessions/` | `<appDir>/agents/main/sessions/` |
| `~/.openclaw/workspace/` | `<appDir>/workspace/` |
| `~/.openclaw/workspace/skills/` | `<appDir>/workspace/skills/` |

---

## 未实现的关键缺口

| 功能 | OpenClaw 来源 | 说明 |
|------|--------------|------|
| Subagent | `agents/subagent-*.ts` | 子代理生成/管理 |
| Streaming | `agents/pi-embedded-subscribe.ts` | 流式响应 (当前非流式) |
| Session 工具 | `agents/tools/sessions-*-tool.ts` | 会话间通信 |
| Gateway 渠道/插件 | `gateway/server-channels.ts` / `server-plugins.ts` | 网关级渠道/插件管理 |
| 设备认证 | `gateway/device-auth.ts` | 设备级认证 |

---

## 符号说明

| 符号 | 含义 |
|------|------|
| ✅ | 已对齐 |
| ⚠️ | 部分对齐 |
| ❌ | 未实现 |
| `-` | 无对应 / 不适用 |
