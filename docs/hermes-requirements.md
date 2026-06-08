# Hermes Requirements (R-Doc)

> **状态**: 2026-04-26（§0.1 三阶段文档的第 ① 阶段）
> **下游**: 本文件描述"**需求是什么**"，**不写**具体断言、数值、枚举映射、字段值——这些属于 `docs/hermes-test-cases.md` 的测试用例层。
> **链路**: R-ID（本文件，需求行为）→ TC-ID（test-cases.md，可测断言）→ JUnit（hermes-android/app 的 `src/test/`）
> **来源标注**: Python 上游时引用 `reference/hermes-agent/.../xxx.py:line`；纯 Android 侧标注"无 Python 上游"。
>
> **ID 规则**: `R-<DOMAIN>-<NNN>`，`NNN` 全局递增不回收；删掉的需求标 `[DELETED]` 保留占位。
>
> **DOMAIN**: `CORE` / `AGENT` / `TOOL` / `PARSER` / `ACP` / `MCP` / `GATEWAY` / `STATE` / `SKILL` / `CRON` / `SAFETY` / `CONFIG` / `UI`

---

## 域 CORE — 项目核心要求

本域不针对单个 Python 文件，而是描述整个 HermesApp 的立项目标与冲突仲裁规则。CORE 是其他所有域的顶层约束——具体域的需求与 CORE 冲突时，以 CORE 为准。

### R-CORE-001: HermesApp 是 Hermes agent 的 Android 版本，必须与上游最大程度对齐

**来源**: 项目立项目标 + CLAUDE.md §1 / §2
**行为**: HermesApp 是 Python `reference/hermes-agent/` 在 Android 上的 Kotlin 实现。对齐 **尽最大可能** 在以下维度 1:1：

- **类名**：Python `class FooBar` → Kotlin `class FooBar`（保留原名，不加 `Adapter`/`Client`/`Impl` 等后缀）
- **方法名**：Python `def _save_trajectory` → Kotlin `_saveTrajectory`（前导 `_` 保留，snake_case → camelCase）
- **变量名**：同方法命名规则
- **文件名**：Python `snake_case.py` → Kotlin `PascalCase.kt` 严格 1:1
- **常量名**：全大写原样保留

**范围**: `hermes-android/` 模块强制对齐；`app/` 宿主壳层（Compose UI、DataStore、Foreground Service 等 Android 独有层）不强制。

**平台差异容许**: 函数体允许替换实现（Python SDK → OkHttp / Kotlin idiom / 显式 "not supported on Android" stub），**但签名与结构必须保持**。

**回归守卫**: CLAUDE.md §2 三件套（verify_align / scan_stubs / deep_align）持续零。

### R-CORE-002: 与 Hermes 冲突时以 Hermes 为准

**来源**: 项目立项目标 + CLAUDE.md §0.0 #3
**行为**: HermesApp 的 `app/` 模块包含较早从其他开源项目（Androidclaw / Operit）继承的存量代码。这些存量代码目前很多能力没有和 Hermes 对接。冲突仲裁规则：

- **Hermes 有 / app 没** → 在 app 侧把 Hermes 能力对接进来（走 `hermes-android` 的接口）
- **app 有 / Hermes 没 / 能力适合 Hermes** → 把这个能力对接到 Hermes 合约上（不是独立保留在 app 侧）
- **app 有 / Hermes 没 / 能力不属于 Hermes 范畴** → 保留在 app 侧（例如 Android 独有的 UI 壳）
- **两边都有且行为冲突** → **以 Hermes 为标准**，app 侧改到匹配 Hermes 行为。不允许出现"app 走自己一套，hermes-android 走另一套"的分裂实现

**范围**: 覆盖所有能力域——agent loop / tools / state / skills / gateway / MCP / cron / safety。

**不允许**: 在 app 侧保留与 Hermes 同名但语义不同的实现；保留 app 专属 fallback 路径绕过 Hermes。

---

## 索引

| 域 | 编号范围 | 覆盖状态 |
|---|---|---|
| CORE | R-CORE-001 .. 002 | 🟢 两条顶层约束 |
| PARSER | R-PARSER-001 .. 090 | 🟢 10 parser 家族（Longcat / Qwen / Qwen3-Coder / Llama / GLM-4.5 / GLM-4.7 / DeepSeek-V3 / DeepSeek-V3.1 / Kimi-K2 / Mistral + Hermes 通用基类） |
| AGENT | R-AGENT-001 .. 008 | 🟡 4 条（turn-loop / 错误处理合并 / 辅助合并 / 凭证池轮转） |
| ACP | R-ACP-001 .. 004 | 🟡 4 条（server 协议 / tool-kind 映射 / 事件生命周期 / client 连 Copilot） |
| TOOL | R-TOOL-001 .. 003, 016 | 🟡 4 条（内置工具集 / 审批 / 预算 / launch_app BAL 兜底） |
| GATEWAY | R-GW-001 .. 006 | 🟡 6 个子系统级能力（base+runner+config / Feishu / Weixin / QQ / 其他平台 / 前台服务） |
| STATE | R-STATE-001 .. 003 | 🟡 3 个子系统级能力 |
| SKILL | R-SKILL-001 .. 003 | 🟡 3 条（发现+hub+loader / 启用+guard / 同步） |
| MCP | R-MCP-001 .. 003 | 🟡 3 条（client / server / OAuth） |
| CRON | R-CRON-001 | 🟡 1 个子系统级能力 |
| SAFETY | R-SAFETY-001 .. 002 | 🟡 2 个子系统级能力（审批 / 清洗+脱敏） |
| CONFIG | — | ⚫ 整域删除（2026-04-26 二次剪裁），能力归 R-UI-001 / R-GW-001 |
| UI | R-UI-001 .. 003 | 🟡 3 条（Hermes Settings hub / 持久化指令 toggle / Gateway 运行时悬浮球） |

> **注**: R 条目只写"能力是什么"。断言、枚举映射、字段值、边界数字等测试用例层内容在 `docs/hermes-test-cases.md`。R→TC 的反向索引由 test-cases.md 的"验 R"列保证。
>
> **2026-04-26 aggressive prune（一轮）**: 从 ~250 条剪到 57 条家族级。
>
> **2026-04-26 aggressive prune（二轮）**: 57 → 34 条。二轮剪除 CONFIG 全域（R-CONFIG-001..003 代码结构），TOOL 15 → 3（内置工具集 / 审批 / 预算），AGENT 7 → 3（turn-loop / 错误处理合并 / 辅助合并），UI 5 → 1（Settings hub 一站式）。按 §0.1 ID 不回收规则，旧 ID（R-TOOL-004..015、R-AGENT-004..007、R-UI-002..005、R-CONFIG-001..003）视为 `[DELETED]` 占位。
>
> **2026-04-26 完善补漏（三轮）**: 34 → 42 条。对照 Python 上游 (`reference/hermes-agent/`) 与 Kotlin 侧 (`hermes-android/`) 的实际实现补缺：补 4 条 parser（R-PARSER-060 DeepSeek-V3 / 070 GLM-4.5 / 080 Kimi-K2 / 090 Mistral），补 R-AGENT-008 凭证池与轮转、R-ACP-004 Copilot ACP client、R-SKILL-003 增量同步、R-MCP-003 OAuth 流程。同时修正 Python 源路径（`hermes/agent/*` → `agent/*`，`hermes/utils/*` → `agent/*`，`hermes/state/*` → 顶层 `hermes_state.py` / `trajectory_compressor.py`，`hermes/skills/*` → `agent/skill_*` + `tools/skills_*`，`hermes/mcp/*` → `tools/mcp_*` + `mcp_serve.py`，`hermes/cron/*` → `cron/` + `tools/cronjob_tools.py`，parser 路径由 `hermes/*_parser.py` → `environments/tool_call_parsers/*.py`）并按实际 Kotlin 实现补充每条 R 的子系统列表。

---

## 域 PARSER — Tool-call parsers

HermesApp 必须为主流开源模型家族提供 tool-call 文本格式解析。Python 解析器位于 `reference/hermes-agent/environments/tool_call_parsers/`；Kotlin 解析器位于 `hermes-android/src/main/java/com/xiaomo/hermes/hermes/*Parser.kt`。

### R-PARSER-001: HermesApp 支持 Longcat 模型的 tool-call 解析
**来源**: `reference/hermes-agent/environments/tool_call_parsers/longcat_parser.py`
**行为**: 识别 Longcat 模型响应里的 `<longcat_tool_call>…</longcat_tool_call>` 内联 JSON 标签，将 `name` + `arguments` 提取为统一 ToolCall；无 tag 的纯文本响应不视为错误。

### R-PARSER-010: HermesApp 支持 Qwen3-Coder 模型的 tool-call 解析
**来源**: `reference/hermes-agent/environments/tool_call_parsers/qwen3_coder_parser.py`
**行为**: 识别 `<tool_call><function=name><parameter=k>v</parameter>…</function></tool_call>` 这一 XML-like 嵌套语法，把每个 function 块转成 ToolCall，其 parameter 子标签的值按字面量自动转为对应类型（布尔/数字）。

### R-PARSER-020: HermesApp 支持 Llama 3 / 4 JSON 格式的 tool-call 解析
**来源**: `reference/hermes-agent/environments/tool_call_parsers/llama_parser.py`
**行为**: 识别 `<|python_tag|>{…JSON…}` 或纯 JSON 响应，提取 `name` + `arguments`（接受 `parameters` 作为 arguments 同义词）；畸形 JSON / 无 tag 时按 "无 tool-call" 处理，不抛异常。

### R-PARSER-030: HermesApp 支持 GLM-4.7 的 tool-call 解析
**来源**: `reference/hermes-agent/environments/tool_call_parsers/glm47_parser.py`
**行为**: 识别 `<tool_call>name\n<arg_key>k</arg_key>\n<arg_value>v</arg_value></tool_call>` 的 GLM 专用 k/v 语法，转为 ToolCall；无 tag 时按"无 tool-call"处理。

### R-PARSER-040: HermesApp 支持 Qwen / Hermes 通用格式的 tool-call 解析
**来源**: `reference/hermes-agent/environments/tool_call_parsers/qwen_parser.py` + `hermes_parser.py`
**行为**: 识别 `<tool_call>{"name":..,"arguments":{..}}</tool_call>` 这一 Qwen 与 Hermes 通用格式；`hermes_parser.py` 作为 `ToolCallParser` 抽象基类提供共享解析流程，`supportedModels` 包含 `qwen`；Kotlin 对应 `QwenParser.kt` + `HermesParser.kt`（基类）。

### R-PARSER-050: HermesApp 支持 DeepSeek-V3.1 的 tool-call 解析
**来源**: `reference/hermes-agent/environments/tool_call_parsers/deepseek_v3_1_parser.py`
**行为**: 识别 DeepSeek-V3.1 用心形 emoji `❤️` 作为分隔符的 tool-call 语法（`❤️<name>❤️` 形式，可多次出现），按出现顺序产出 ToolCall 列表；无分隔符时返回空列表而非 null。

### R-PARSER-060: HermesApp 支持 DeepSeek-V3 的 tool-call 解析
**来源**: `reference/hermes-agent/environments/tool_call_parsers/deepseek_v3_parser.py`
**行为**: 识别 DeepSeek-V3（非 V3.1）早期 tool-call 语法；Kotlin `DeepseekV3Parser.kt` 与 V3.1 并存，保留独立解析路径以支持 V3 系列模型部署。

### R-PARSER-070: HermesApp 支持 GLM-4.5 的 tool-call 解析
**来源**: `reference/hermes-agent/environments/tool_call_parsers/glm45_parser.py`
**行为**: 识别 GLM-4.5 tool-call 语法（与 GLM-4.7 k/v 标签语法存在差异），产出 ToolCall 列表；Kotlin `Glm45Parser.kt` 与 `Glm47Parser.kt` 并存。

### R-PARSER-080: HermesApp 支持 Kimi-K2 的 tool-call 解析
**来源**: `reference/hermes-agent/environments/tool_call_parsers/kimi_k2_parser.py`
**行为**: 识别 Moonshot Kimi-K2 系列模型的 tool-call 语法，产出 ToolCall 列表；Kotlin `KimiK2Parser.kt` 1:1 对齐。

### R-PARSER-090: HermesApp 支持 Mistral 的 tool-call 解析
**来源**: `reference/hermes-agent/environments/tool_call_parsers/mistral_parser.py`
**行为**: 识别 Mistral 系列模型的 tool-call 语法，产出 ToolCall 列表；Kotlin `MistralParser.kt` 1:1 对齐。

---

## 域 AGENT — Agent turn-loop 与辅助

HermesApp 必须提供与 Python Hermes 等价的 agent turn-loop 内核。Python 源位于 `reference/hermes-agent/agent/`（子系统模块）+ `reference/hermes-agent/run_agent.py`（AIAgent 顶层编排器）；Kotlin 对应 `hermes-android/src/main/java/com/xiaomo/hermes/hermes/agent/` + `hermes/AgentLoop.kt`。

### R-AGENT-001: HermesApp 提供 Hermes agent turn-loop 内核
**来源**: `reference/hermes-agent/run_agent.py` + `agent/prompt_builder.py` + `agent/display.py` + `agent/transports/`
**行为**: 驱动多轮 tool-calling；每轮由 `PromptBuilder` 组装 system/user/tool 消息 → 通过 provider 特定 transport（Anthropic / Bedrock / Codex Responses / Gemini native / Gemini CloudCode / Copilot ACP 等）发送 → 解析 tool_calls → dispatch tool → 收 tool_result → 进下一轮；达到 max_turns / 收到 stop 信号 / 最终回复无 tool_call 时终止；所有一轮内产生的事件（ContentDelta / ToolCallStart / ToolCallEnd / TurnComplete 等）以 `AgentEvent` 流给调用方。

### R-AGENT-002: 错误处理 + 模型 catalog + OpenCode Zen 公开 key 兜底
**来源**:
- 错误分类/重试/路由: `reference/hermes-agent/agent/error_classifier.py` + `retry_utils.py` + `rate_limit_tracker.py` + `nous_rate_guard.py` + `model_metadata.py`
- 模型 catalog（启动顺序 / TTL / cache / snapshot）: `reference/hermes-agent/agent/models_dev.py:1-626`
- OpenCode Zen public-key 兜底: **无 Python 上游**（Python Hermes 不内置共享 key；行为借鉴 sst/opencode TS 上游 `packages/opencode/src/provider/provider.ts:160-182` 的 `apiKey="public"` + `cost.input==0` 过滤；落地仅 Android 侧）

**行为**:
1. **错误分类**：API 错误按 HTTP 状态 + provider error_code + 文本归入统一类别（retry / rotate / fallback / compress / non-retriable），驱动指数退避 / Retry-After / 立即失败。
2. **限流**：`rate_limit_tracker` 每 provider 限流窗口；`nous_rate_guard` 防超配；与 Python 上游 1:1。
3. **fallback 路由**：primary 返回 fallback 类错误时按 `model_metadata` + `models.dev` 模型能力/成本表自动切换 provider。
4. **models.dev catalog 启动顺序**（对齐 `agent/models_dev.py:207-248`）：
   - 内存 cache（TTL=3600s，对齐 `:35` `_MODELS_DEV_CACHE_TTL`）
   - 内嵌 snapshot：Android 从 `assets/models_dev_snapshot.json` 加载（Python 用 pkg-resources，Android 等价物为 `AssetManager`，**Android-only**）
   - 磁盘 cache：Python `~/.hermes/models_dev_cache.json`，Android `applicationContext.cacheDir/models_dev_cache.json`
   - 网络拉取 `https://models.dev/api.json`（15s timeout）
   - 后台 60min refresh（Android 由 `ApplicationScope` coroutine 触发）
5. **OpenCode Zen 兜底（无 Python 上游）**：
   - models.dev provider id `opencode`（Hermes 已在 `agent/models_dev.py:157` PROVIDER_TO_MODELS_DEV 收录）
   - 用户未配置任何 provider key 时，注入字面量 `apiKey="public"` 走 `https://opencode.ai/zen/v1/chat/completions`
   - 该路径**只**放行 `cost.input == 0` 模型（与 opencode TS `provider.ts:170` 同语义）
   - **三层选择**（`OpenCodeZenCatalog.selectDefaultFreeModelLive`）：
     1. **live**: GET `https://opencode.ai/zen/v1/models` with `Authorization: Bearer public`，过滤 id 以 `-free` 结尾的取第一个（OpenCode Zen 自有命名约定，且 endpoint 实际服务的 model 列表是 models.dev `opencode` 的严格子集）
     2. **catalog**: 回退到 models.dev `opencode` provider，`tool_call==true && cost.input==0` 且不命中 `NOISE_PATTERN` → 按 `release_date` 倒序 → 取第一个
     3. **baseline**: 全空时退回 `BASELINE_FREE_MODEL = "nemotron-3-super-free"`（已验证 live endpoint 接受；早期 plan 候选 `qwen/qwen3-coder` 与 `grok-code` 在 models.dev `opencode` 里有但 live endpoint 401 ModelError，已排除）
6. **首次启动默认 provider**：`OPENCODE_ZEN`（取代旧 `OPENROUTER + BuiltInKeyProvider`）；不再嵌入加密 key。
7. **彻底废弃旧路径**：`BuiltInKeyProvider.kt` 整文件移除；`ApiPreferences.DEFAULT_API_*` 改 OpenCode Zen 公开值；无 fallback、无 feature flag——按 §0 "彻底切换"。

**验收**（agent-level）：
- 新装/清 DataStore 后冷启动，**无任何用户配置**即可发起 chat 并收到 `aiResponsePreview` 含 TOKEN（TC-AGENT-200-c 重写、TC-AGENT-200-h 全自动启动路径）
- catalog 三层 fallback：网络断 → disk cache；disk 缺 → snapshot；snapshot 缺 → BASELINE_FREE_MODEL
- §2 四件套：`verify_align / scan_stubs / deep_align` 维持零；`scan_functional_stubs` ≤ 390（不增）

### R-AGENT-003: Agent 辅助工具——标题、FileSafety、上下文压缩、memory 合一
**来源**: `reference/hermes-agent/agent/title_generator.py` + `file_safety.py` + `context_compressor.py` + `memory_manager.py` + `memory_provider.py` + `manual_compression_feedback.py` + `context_references.py` + `redact.py` + `prompt_caching.py`
**行为**: 对话结束时自动生成标题（用户首问 + LLM 压缩，失败回退截断）；文件写类工具调用前统一经 FileSafety 层检查路径合法性；长对话接近 context window 时自动压缩早期轮次（保留系统提示 + 最近 N 轮 + 摘要），`/compact` 用户手动触发时走同一压缩链并记录反馈；memory manager 负责 pin / unpin / 摘要化生命周期；上下文 @-引用（`@file` / `@url`）由 `context_references` 解析并展开；secret/PII 按 `redact.py` 规则从日志 / trajectory 中抹除；Anthropic / Gemini 支持 prompt cache breakpoint 插入——以上辅助能力行为与 Python 上游 1:1。

### R-AGENT-008: 凭证池与轮转
**来源**: `reference/hermes-agent/agent/credential_pool.py` + `credential_sources.py` + `account_usage.py` + `usage_pricing.py`
**行为**: 同一 provider 支持多 key 凭证池；按健康度 / 配额 / 成本轮转；key 出现鉴权 / 限流错误时按 R-AGENT-002 分类从池中标记并切换到下一条；凭证来源（env / 文件 / keychain / EncryptedSharedPreferences）由 `credential_sources` 统一解析；`account_usage` 聚合每凭证 token / 金额消耗，`usage_pricing` 提供模型 → 成本表；轮转策略与 Python 上游 1:1。

### R-AGENT-009: 持久指令注入（Eager System Prompt Injection）
**来源**: 无 Python 上游（Android 侧用户体验需求；Python 上游的 memory_manager 是 lazy-RAG 模式，不主动注入）
**行为**: 用户在对话中明确表达的持久偏好（输出格式、语气、默认动作等）必须在 **后续每一轮 LLM 请求** 中自动呈现在 system prompt 末尾，不依赖模型主动调 `query_memory`、不依赖 chat history 不被截断、不被自动总结流程覆盖。
- **存储复用**：复用现有 Memory 体系（ObjectBox `Memory` 实体），不引入第二套存储；以特殊 tag `#persistent_instruction` 标识"持久指令"节点。
- **写入路径**：
  1. agent 在对话中识别用户明确意图（"以后…/记住…/下次回复…/回复时…"等长期意图触发词）时，主动调 `create_memory` 写入并附加 tag `#persistent_instruction`；
  2. 用户也可直接在 APP 记忆库 UI 手编节点并打 tag（路径 P0 不专门做 UI，沿用现有 MemoryScreen）。
- **读取注入**：`ConversationService.prepareConversationHistory` 拼装 `finalSystemPrompt` 时，从 `MemoryRepository` 拉所有带 `#persistent_instruction` tag 的节点 → 按 `updatedAt desc` 排序 → 拼接为 `[Persistent user instructions]\n- <content1>\n- <content2>\n...` 块，追加到 system prompt 末尾（位于 `User preference description` 之后）；无任何持久指令节点时不注入任何文本。
- **抗侵蚀保护**：`MemoryLibrary.saveMemory` 的自动合并 / 重写流程跳过带 `#persistent_instruction` tag 的节点（不参与 `mergedEntities` / `updatedEntities` / 自动 folder 重分类），保证用户写入的原文一字不改。
- **作用域**：P0 全局（所有飞书群 + APP UI 共享当前激活 Profile 的持久指令池），与现有 Memory 的 per-Profile 全局模型一致；P1 如有需求再加 per-chatId 维度。
- **路径覆盖**：UI 聊天 / Floating chat / Gateway（飞书等）三个路径共用同一份注入逻辑，因为三者都走 `ConversationService.prepareConversationHistory`。
- **验收**：
  - 无 `#persistent_instruction` 节点 → system prompt 不含 `[Persistent user instructions]` 段（行为完全等价于改动前）
  - 有 1 条 → system prompt 末尾出现该段，含原文 content
  - 有 N 条 → 按 `updatedAt desc` 顺序拼接为 N 个 bullet
  - 用户用 `update_memory` 改 content → 下一轮 LLM 请求注入更新后的 content
  - 用户删除 Memory 节点或去掉 tag → 下一轮 LLM 请求不再注入该条
  - 自动总结跑完一轮 → 带 tag 的节点 content 一字不动（不被合并/重写）
  - §2 四件套：`verify_align / scan_stubs / deep_align` 维持零；`scan_functional_stubs` ≤ 390（不增）

### R-AGENT-010: Gateway 路径每轮强制保存对话摘要到长期记忆
**来源**: 无 Python 上游（Android 侧用户体验需求；用户报"app 里聊天有自动总结，飞书 gateway 路径没看到自动生成的记忆"）
**背景**: APP 内聊天路径 `EnhancedAIService.handleTaskCompletion` 会调 `MemoryLibrary.saveMemoryAsync` 自动总结当轮对话写入长期记忆，但**只有 agent 输出 `<complete>` 标记**（`ConversationMarkupManager.containsTaskCompletion(aggregatedContent) == true`）时才触发。飞书 / 微信等 Gateway 场景下，agent 是被动应答短消息，几乎不会主动写 `<complete>`，导致这些路径的对话从不进入长期记忆，违背"agent 应当持续积累用户上下文"的预期。
**行为**: `HermesGatewayController.runHermesAgent`（飞书 / 微信 / 其他平台都经此入口）在 agent 回复成功生成、即将 return 给 GatewayRunner 之前，**强制调用一次** `MemoryLibrary.saveMemoryAsync(appContext, toolHandler, conversationHistory, aiText, memoryService, ...)`，不依赖 `<complete>` 标记。
- **触发时机**：`runHermesAgent` 的"agent 回复非空且即将返回"分支；中断 (`interruptCheck() == true`) / 异常 / 空回复路径**不存**。
- **conversationHistory 来源**：从 `ChatHistoryManager.loadChatMessages(historyChatId)` 取当前 gateway 会话的消息列表（`historyChatId = "gw:$sessionKey:$chatId"`，§1 已用），转换为 `List<Pair<String,String>>`（pair.first = role "user"/"assistant"/"system"，pair.second = content）。
- **memoryService 来源**：`multiServiceManager.getServiceForFunction(FunctionType.MEMORY)`，与 APP 内聊天路径走同一 MEMORY function 模型。
- **enableMemoryQuery 开关**：复用 `ApiPreferences.enableMemoryQueryFlow`，与 APP 内路径同一开关；false 时跳过保存（一致行为）。
- **失败容忍**：`saveMemoryAsync` 内部异常 / 网络失败不得影响 gateway 回复返回——`saveMemoryAsync` 已是 fire-and-forget 协程，自带异常隔离，gateway 这边 best-effort 启动后立即继续。
- **去重 / 合并**：复用现有 `MemoryLibrary.saveMemory` 内部的合并 / 重写 / tag 跳过逻辑（R-AGENT-009 的 `#persistent_instruction` 节点天然受保护），gateway 这层不重复造轮子。
- **不与 R-AGENT-009 冲突**：本需求负责"主动总结"，R-AGENT-009 负责"注入持久指令"，二者写入与读取链路独立。
- **验收**：
  - gateway 走完一轮非空回复 → `MemoryRepository` 新增 1 条由 MEMORY 模型总结的记忆节点
  - gateway 中断 / 异常 / agent 返回空文本 → 不新增记忆
  - `enableMemoryQuery = false` → 即使 gateway 正常回复也不新增记忆
  - APP 内聊天路径行为不变（仍由 `handleTaskCompletion` 在 `<complete>` 时触发，避免重复保存）
  - §2 四件套：`verify_align / scan_stubs / deep_align` 维持零；`scan_functional_stubs` ≤ 390（不增）

### R-AGENT-011: Gateway 保存的记忆节点强制打 `#gateway:<platform>` tag
**来源**: 无 Python 上游（Android 侧用户体验需求；用户报"R-AGENT-010 上线后 gateway 的记忆和 APP UI 自己创建的混在一起，无法在 MemoryScreen 区分/过滤"，2026-06-06 明确要求加 tag）
**背景**: R-AGENT-010 让飞书 / 微信等 gateway 路径每轮自动保存记忆，但 MEMORY 总结模型产出的节点 tag 完全由 LLM 决定。实战中这些 tag 与 APP UI 内 agent 主动 `create_memory` 产出的 tag 同质化（比如都是话题词），用户既无法在 `MemoryScreen` 里通过 tag 过滤掉"机器人路径的记忆"，也无法快速定位某个 IM 平台积累了哪些上下文。需要在 gateway 写入路径**强制**附加一个固定前缀的 tag，UI 侧靠这个前缀即可分类。
**行为**: `MemoryLibrary.saveMemoryAsync` 在签名上扩展一个可选参数 `extraTags: List<String> = emptyList()`（默认空，保 APP UI 路径行为不变）；`saveMemory` 内部在创建主问题节点 / 实体节点时除遍历 LLM 给出的 `mainProblem.tags` / `entity.tags` 外，**额外**遍历 `extraTags` 调 `memoryRepository.addTagToMemory(memory, tagName)`。`HermesGatewayController.runHermesAgent` 在调用 `saveMemoryAsync` 时传入 `extraTags = listOf("#gateway:$platform")`，`platform` 取自 `sessionKey.substringBefore(':')`（与 §1 sessionKey 约定 `<platform>:<chat>` 一致，如 `feishu:oc_xxx` → `feishu`）。
- **触发时机**：与 R-AGENT-010 同一调用点；不新增任何独立写入分支。
- **tag 命名约定**：固定前缀 `#gateway:`，后接平台名小写；保留 `#` 前缀与 `#persistent_instruction` 同风格，便于 UI 用 `startsWith("#gateway:")` 一次性筛掉/筛出。
- **APP UI 路径行为不变**：`EnhancedAIService.handleTaskCompletion` 不传 `extraTags`（走默认 `emptyList()`），不会被打 `#gateway:` tag。
- **不影响合并 / 去重**：`MemoryLibrary` 内部合并已存在节点时仍按既有逻辑（content / title 匹配），`extraTags` 只在**新建**节点时附加，不在合并分支重复写。
- **不与 R-AGENT-009 冲突**：`#persistent_instruction` 是 R-AGENT-009 的标记，gateway 保存的节点不会带它（gateway 这层只加 `#gateway:<platform>`），所以也不会误把 gateway 的总结当成持久指令注回 system prompt。
- **验收**：
  - `MemoryLibrary.saveMemoryAsync` 签名含 `extraTags: List<String> = emptyList()`
  - `HermesGatewayController.runHermesAgent` 调用 `saveMemoryAsync` 时显式传 `extraTags = listOf("#gateway:$platform")`，且 `platform` 从 `sessionKey` 派生（不是硬编码 `"unknown"` 或 `""`）
  - APP UI 路径（`EnhancedAIService.handleTaskCompletion`）调用 `saveMemoryAsync` 时**不传** `extraTags`（保持默认 emptyList），不被打 gateway tag
  - `MemoryLibrary.saveMemory` 主问题创建分支 + 实体创建分支均 `forEach extraTags` 调 `addTagToMemory`
  - §2 四件套：`verify_align / scan_stubs / deep_align` 维持零；`scan_functional_stubs` ≤ 390（不增）

### R-AGENT-012: MemoryScreen 让 `#gateway:<platform>` tag 可视化 + 可过滤
**来源**: 无 Python 上游（Android 侧 UI 需求；用户报"R-AGENT-011 加了 tag 但 MemoryScreen 看不出区别 —— 不是和之前一样吗？"，2026-06-06 明确要"做 UI 让 tag 真正能用"）
**背景**: R-AGENT-011 已让 gateway 路径写入的记忆节点强制带 `#gateway:<platform>` tag（如 `#gateway:feishu`），但 MemoryScreen 的整条读取链（`MemoryRepository.pickNodeColorByAttributes` / `MemoryViewModel.MemoryUiState` / `MemorySearchBar` / `GraphVisualizer` / `MemoryDialogs`）对 `#gateway:` 前缀完全无感知 —— 节点颜色与 APP UI 创建的混在一起、过滤维度只有 folder 树和全文搜索。用户在图谱里既看不出哪些节点是 gateway 来源，也无法只看/只屏蔽某个 IM 平台。
**行为**: 在 MemoryScreen 引入"gateway 来源"维度的可视化 + 过滤能力，不改变 Memory 数据模型与 ObjectBox schema。
- **节点颜色区分**：`MemoryRepository.pickNodeColorByAttributes` 的 tag 识别分支扩展，识别任意以 `#gateway:` 开头的 tag → 返回 gateway 专属色（蓝绿 `0xFF26A69A`）。优先级位于现有 `#persistent_instruction`（金色）与 `isDocumentNode`（紫色）之后、`Person/Concept/默认` 分支之前 —— 用户手工写入语义（persistent_instruction）和"这是文档"这种结构属性（isDocumentNode）都比"来自哪个 IM 平台"语义更强。
- **ViewModel state 扩展**：`MemoryUiState` 追加两个字段：
  - `availableGatewayPlatforms: List<String> = emptyList()` —— 从当前 profile 全库扫描所有以 `#gateway:` 开头的 tag，去前缀后 distinct + 排序得到（如 `["feishu", "wechat"]`）；`refreshGraph()` / `selectFolder()` / `searchMemories()` 任一刷新点末尾同步更新。
  - `gatewayFilter: GatewayFilter = GatewayFilter.All` —— 三态枚举：`All`（不过滤）/ `OnlyGateway(platforms: Set<String>)`（只看选中的 platform；空集合 = 看全部 gateway）/ `ExcludeGateway`（屏蔽所有 gateway 节点，只看 APP UI 创建的）。
- **过滤策略（client-side）**：`refreshGraph()` / 搜索结果路径在拿到 `List<Memory>` 后、调 `repository.getGraphForMemories` 之前先按 `gatewayFilter` 过滤：遍历 `memory.tags.map { it.name }` 判断是否 `startsWith("#gateway:")` 与是否匹配选中 platform。性能上节点数千级别可接受；超过即考虑 P1 下推到 ObjectBox query（本期不做）。
- **UI 注入**：`MemoryScreen.kt` 在 `MemorySearchBar` 与 `GraphVisualizer` 之间插一行横向 chip 容器（`LazyRow` 或 `FlowRow`），渲染：
  - 左：`FilterChip("全部")` —— 选中 = `GatewayFilter.All`
  - 中：`FilterChip("无网关")` —— 选中 = `GatewayFilter.ExcludeGateway`
  - 右：`uiState.availableGatewayPlatforms` 每项一个 `FilterChip(platform)` —— 多选切换为 `OnlyGateway(selected)`
  - chip 行高度紧凑（< 48dp），不挤压图谱区
  - 平台列表为空（用户从未跑过 gateway）时整行隐藏，不留视觉残留
- **MemoryInfoDialog 不动**：现有"Tags: a, b, c"一行展示 tag 已包含 `#gateway:` 前缀，节点详情天然可见；不做 chip 化以最小改动
- **APP UI 路径完全不受影响**：APP UI 创建的节点不带 `#gateway:` tag → `pickNodeColorByAttributes` 走原有分支 → 颜色不变；`gatewayFilter == All` 默认值 = 全部显示，老用户开 app 行为完全不变
- **i18n**：chip 文案走 `res/values*/strings.xml`，新增键 `memory_filter_all` / `memory_filter_no_gateway` / `memory_filter_gateway_platform_format`（zh: "全部" / "无网关" / "%s"；en: "All" / "No Gateway" / "%s"）
- **验收**：
  - 用户从未跑过 gateway → MemoryScreen 行为与 R-AGENT-012 之前完全一致（chip 行隐藏、节点颜色不变）
  - 跑完一轮飞书 gateway → `availableGatewayPlatforms` 含 "feishu"，chip 行出现含 "feishu" 项，图谱中 gateway 节点显示为蓝绿色
  - 选中 "无网关" chip → 图谱只显示无 `#gateway:` tag 的节点
  - 选中 "feishu" chip → 图谱只显示带 `#gateway:feishu` tag 的节点
  - 同时多选 "feishu" + "wechat" → 图谱显示两者并集
  - `#persistent_instruction` 节点同时带 `#gateway:feishu` tag（极端 case）→ 颜色显示金色（persistent_instruction 优先），过滤按 gateway 维度仍可命中
  - §2 四件套：`verify_align / scan_stubs / deep_align` 维持零；`scan_functional_stubs` ≤ 390（不增）

### R-AGENT-013: APP 内聊天的自动摘要强制写入长期记忆（绕过 LLM 价值判官）
**来源**: 无 Python 上游（Android 侧用户体验需求；用户 2026-06-07 明确："自动总结一定要加上对话上下文保存到长期记忆，不是 agent 决定要不要保存，而是强行摘要保存为长期记忆"。Python 上游有 `memory_provider.on_pre_compress` 钩子的概念（`reference/hermes-agent/agent/memory_provider.py:163`），Android 侧对齐这一"压缩前必落档"的语义）
**背景**: APP 内聊天有两条摘要触发路径：
1. **发送时阈值触发** —— `MessageCoordinationDelegate.launchAsyncSummaryForSend`（`AIMessageManager.shouldGenerateSummary` 返回 true 时启动），产出 `ChatMessage(sender="summary", ...)` 插回对话历史用于压缩上下文
2. **token-limit 异常触发** —— `MessageCoordinationDelegate.summarizeHistory`（`handleTokenLimitExceeded` 路径调用），同样产出 `summary` 消息插回历史

两条路径产出的摘要**只**进 ChatMessage JSON 历史，不进 `MemoryRepository` 长期记忆库。现有 `MemoryLibrary.saveMemoryAsync` 路径走 `EnhancedAIService.generateAnalysis`（独立 LLM 价值判官），可能因模型判断"内容无价值"返回 `{}` 直接丢弃。结果：用户在 APP 内长时间聊天产出大量摘要，但 `MemoryScreen` / `query_memory` 工具看不到这些总结性上下文 —— 跨会话的累积知识完全断层。

修复策略：在 APP 内聊天的两条摘要插入路径（`launchAsyncSummaryForSend` 与 `summarizeHistory`）成功调用 `chatHistoryDelegate.addSummaryMessage` **之后**，**强制**调用 `MemoryRepository.saveMemory(Memory(content=summaryText, tags=[...]))` 直接落 ObjectBox + embedding，**完全绕过** `MemoryLibrary.saveMemoryAsync` / `generateAnalysis` 的 LLM 价值判官 —— 摘要永不被丢弃。

**行为**:
- **写入入口**：`MessageCoordinationDelegate` 在两条路径成功 `addSummaryMessage` 之后调用 `memoryRepository.saveMemory(memory)`，其中 `memory = Memory(title=<chat 标题或 "自动摘要 yyyy-MM-dd HH:mm">, content=<summaryMessage.content>, contentType="text/plain", source="auto_summary", credibility=0.9f, importance=0.6f, folderPath=<默认或 "自动摘要">)`
- **绕过 LLM 判官**：禁止走 `MemoryLibrary.saveMemoryAsync` —— 它会调 `EnhancedAIService.generateAnalysis` 让 MEMORY 模型决定是否保留，与本需求"强行保存"的语义冲突。直接调 `MemoryRepository.saveMemory(memory)` 是唯一允许路径。
- **强制 tag**：写入后立刻调 `memoryRepository.addTagToMemory(memory, "#auto_summary")`，外加来源 tag `addTagToMemory(memory, "#chat:$chatId")`（`chatId` 即 `originalChatId` / `currentChatId`）。这两个 tag 让用户在 `MemoryScreen` 区分"自动摘要"vs"agent create_memory 产出"vs"R-AGENT-011 gateway 路径产出"。
- **不与 R-AGENT-010/011 冲突**：R-010/011 只覆盖 gateway 路径（`HermesGatewayController.runHermesAgent`），且走 `MemoryLibrary.saveMemoryAsync`（LLM 判官路径）；R-013 只覆盖 APP UI 路径（`MessageCoordinationDelegate.launchAsyncSummaryForSend` / `summarizeHistory`），走 `MemoryRepository.saveMemory`（直写路径）。两组互不相交，不会双写。
- **失败容忍**：`saveMemory` 内部异常（ObjectBox / embedding 网络）必须 try-catch 隔离，不得影响 `addSummaryMessage` 本身的持久化或后续 `refreshStableContextWindow` 调用。失败仅打日志。
- **触发时机精确**：必须在 `chatHistoryDelegate.addSummaryMessage(...)` 调用**之后**（确保摘要先入 chat 历史），且在同一 `try` 块内（catch CancellationException + Exception 已存在）。中断 / 异常 / `summaryMessage == null` 路径**不存**。
- **enableMemoryQuery 开关**：复用 `ApiPreferences.enableMemoryQueryFlow`，与 R-AGENT-010 / 现有 APP 内 `handleTaskCompletion` 同一开关；false 时跳过保存（一致行为）。
- **去重**：`MemoryRepository.saveMemory` 内部按 `memory.id` 处理重写 vs 新建；本路径每次都传新 `Memory()`（id=0），无需在 delegate 这层做去重 —— 用户即使短时间内连续触发摘要，每次摘要内容本身已经不同（包含新轮次的对话）。
- **embedding 处理**：`saveMemory` 内部已经在 IO 线程同步生成 embedding，调用方协程已是 `coroutineScope.launch`（async），用户感知无阻塞。
- **架构合规**（§6 红线）：本需求只在 Android UX 层做"摘要必落档"的兜底，**不**改 Hermes agent loop 架构、**不**新增 RAG 注入 system prompt（Python 上游也只在 USER 消息里注入 memory，不动 system prompt）、**不**改 `MemoryLibrary` API 表面 —— 保留 LLM 判官路径供 agent `create_memory` / gateway 路径继续使用。
- **验收**：
  - APP 内聊天触发"阈值摘要"（`shouldGenerateSummary` 返回 true）→ `MemoryRepository` 新增 1 条 `source="auto_summary"` + tags 含 `#auto_summary` + `#chat:<chatId>` 的记忆
  - APP 内聊天触发"token-limit 摘要"（`summarizeHistory` 路径）→ 同样新增 1 条
  - `enableMemoryQuery = false` → 即使摘要成功也不写记忆
  - `MemoryLibrary.saveMemoryAsync` / `generateAnalysis` 被调用次数 = 0（确认绕过判官）
  - `saveMemory` 异常 → 日志记录但 `addSummaryMessage` / `refreshStableContextWindow` 继续执行
  - gateway 路径（R-AGENT-010）行为不变：不产出 `#auto_summary` tag、仍走 `saveMemoryAsync` / `generateAnalysis`
  - 用户在 `MemoryScreen` 编辑 `#auto_summary` 节点（改 content / 改 tag / 删）后，下次 `query_memory` 返回的是用户编辑后的版本（不被 LLM 判官回滚 —— 因为编辑路径走 `updateMemory` 同样不触发判官）
  - §2 四件套：`verify_align / scan_stubs / deep_align` 维持零；`scan_functional_stubs` ≤ 390（不增）

### R-AGENT-014: Agent 感知 `#auto_summary` 节点 + `query_memory` 支持 tag 过滤
**来源**: 无 Python 上游（Android 侧扩展；Python 上游 `tools/memory_tool.py` 用的是 "MEMORY.md + USER.md + 单一 `memory` 工具带 action 参数" 的完全不同设计，没有 `query_memory` 概念、没有 ObjectBox `MemoryTag` 体系。R-AGENT-014 作为 R-AGENT-013 的闭环兜底——用户 2026-06-07 明确："agent 知道自己有长期记忆了吗？" 经查 R-AGENT-013 让摘要强行落库但 agent 完全不知道 `#auto_summary` tag 的存在，`query_memory` 也没有 tag 过滤入口，导致摘要"存了没人查"）
**背景**: R-AGENT-013 让 APP 内每轮对话都强行把摘要落到 `MemoryRepository`，并打 `#auto_summary` + `#chat:<chatId>` 两个 tag。但目前：
1. **agent 不知道 `#auto_summary` 的存在** —— `SystemPromptConfig.GATEWAY_AWARENESS_EN/CN` 的 `MEMORY USAGE GUIDANCE` 段（行 49-71 / 73-95）只提到 `query_memory` / `create_memory` / `#persistent_instruction`，完全没提自动摘要 tag。agent 不会想起来"我可以查最近的对话摘要"。
2. **`query_memory` 没有 tag 过滤参数** —— `SystemToolPrompts.memoryTools(Cn)` 的 query_memory description（行 494-508 / 525-539）参数列表只有 `query / folder_path / start_time / end_time / snapshot_id / threshold / limit` 七个。即使 agent 知道 `#auto_summary` tag，也没法说"我只要带这个 tag 的记忆"，只能靠语义模糊检索撞运气命中。
3. **`MemoryRepository.searchMemories`（行 1200-1224）** 的 `tagWeight` 是**打分权重**而非**硬过滤** —— 不能用来做"必须含某 tag"的前置过滤。

修复策略：给 agent 加一条 system-prompt 提示让它知道 `#auto_summary` tag 存在 + 给 `query_memory` 工具加可选 `tags` 参数（多 tag 用 `|` 分隔）+ `MemoryRepository.searchMemories` 加 `tags: List<String>?` 前置过滤参数。三处变更最小工作量打通"agent 知道有日记 → 能精准查日记 → 仓库支持按 tag 取日记"全链路。架构红线：**不**改 agent loop、**不**新增 system prompt 自动 RAG 注入（Python `memory_provider.prefetch()` 在 Android `InMemoryMemoryProvider` 留空是历史决策，要补需独立评估，R-014 不动）、**不**改既有 `query_memory` 调用签名（`tags` 参数默认为空 = 老调用 100% 兼容）。

**行为**:
- **MEMORY USAGE GUIDANCE 加 `#auto_summary` 段（EN+CN）**：在 `SystemPromptConfig.GATEWAY_AWARENESS_EN` 行 49-64 的 `MEMORY USAGE GUIDANCE` 段尾、紧接 "search with short keywords" 行之后插入一行，明确告知：
  - "Conversation summaries are automatically saved to the memory library with the `#auto_summary` tag after each compression. To recall previous chats in this app, call query_memory with `tags=#auto_summary` or `tags=#chat:<chatId>`."
  - 中文版（行 73-88 `GATEWAY_AWARENESS_CN` 的 "记忆库使用指导" 段尾）对应插入："对话摘要会在每次压缩后自动保存到记忆库并打上 `#auto_summary` tag。想回顾本 app 之前聊过的内容，调用 query_memory 并传 `tags=#auto_summary` 或 `tags=#chat:<chatId>` 即可精准检索。"
- **`query_memory` 工具 description 加 `tags` 参数（EN+CN）**：在 `SystemToolPrompts.memoryTools` 和 `memoryToolsCn` 的 `query_memory` ToolPrompt 的 `parametersStructured` 列表里**新增**一个 `ToolParameterSchema(name = "tags", type = "string", required = false)`，description 明确：
  - EN: "optional, string. Filter results to memories carrying **all** of these tags. Multiple tags are separated by `|`, e.g. `#auto_summary` or `#auto_summary|#chat:abc123`. Common tags: `#auto_summary` (auto conversation summaries), `#persistent_instruction` (long-term user rules), `#chat:<chatId>` (specific in-app chat), `#gateway:<platform>` (external gateway conversations)."
  - CN 对应文案。
- **`MemoryQueryToolExecutor.executeQueryMemory` 解析 `tags` 参数**：在 `executeQueryMemory`（`MemoryQueryToolExecutor.kt` 行 176+）里读 `tool.parameters.find { it.name == "tags" }?.value`，按 `|` 切分、trim、过滤空串得到 `List<String>?`（空 list 视为 null），下传到 `memoryRepository.searchMemories(..., tags = ...)`。空字符串 / null → 不过滤（与既有行为一致）。
- **`MemoryRepository.searchMemories` 加 `tags: List<String>? = null` 参数**：默认值 null 保证既有 18 处调用方零改动。`runSearchMemoriesWithDebug` 内部在 `timeFilteredMemoriesInScope` 那一步之后追加一次 filter：`if (tags.isNullOrEmpty()) timeFiltered else timeFiltered.filter { mem -> tags.all { req -> mem.tags.any { it.name == req } } }`（**all** 语义：要求 memory 同时含**所有**指定 tag，便于 `tags=#auto_summary|#chat:abc` 精确定位某个 chat 的某条摘要）。
- **不引入 ToolParameterSchema 类型 breaking change**：直接复用既有 `type = "string"` + `description` 里写明 `|` 分隔约定（与 query 参数的 `|` 关键词分隔风格一致），不新增 `array` 类型支持。
- **架构合规（§6 红线）**：本需求仅在 prompt + tool description + 工具/Repository 入口加一个可选 tag filter；**不**改 agent loop turn-cycle、**不**改 `ConversationService.buildPersistentInstructionsText`、**不**改 `<memory_context>` attachment 路径、**不**触碰 Python `memory_provider.prefetch()` 缺失的 RAG 设计决策（独立需求评估）。
- **i18n 完整性**：所有面向 agent 的字符串都在 `SystemPromptConfig` / `SystemToolPrompts` 的中英文常量里两份并行（EN+CN），与 R-AGENT-009 / R-AGENT-013 一致。无 `res/values` 字符串改动（用户不可见，纯 agent prompt）。
- **测试策略**：与 R-AGENT-009 (`PersistentInstructionAgentHintTest`) / R-AGENT-013 同策略走源码字符串扫描守 wiring（`SystemPromptConfig` / `SystemToolPrompts` / `MemoryQueryToolExecutor` 三处都用 wiring test）。`MemoryRepository.searchMemories` 的 tag 过滤运行时单测因重度依赖 ObjectBox in-memory 启动 + Robolectric ROI 低，沿用 §0.3 策略：wiring 扫描 + 手测兜底。
- **验收**：
  - `SystemPromptConfig.kt` 的 `GATEWAY_AWARENESS_EN` 和 `GATEWAY_AWARENESS_CN` 常量内 `MEMORY USAGE GUIDANCE` / `记忆库使用指导` 段都含 `"#auto_summary"` 字面字符串 + `"query_memory"` + tag 用法引用（如 `"tags="` 或 `"tags 参数"`）
  - `SystemToolPrompts.kt` 的 `memoryTools` 和 `memoryToolsCn` 的 `query_memory` ToolPrompt 的 `parametersStructured` 列表都含 `name = "tags"` 的 ToolParameterSchema；EN description 含 `"#auto_summary"` 示例；CN description 含 `"#auto_summary"` 示例
  - `MemoryQueryToolExecutor.kt` 的 `executeQueryMemory` 函数体含解析 `"tags"` 参数 + 把解析结果传给 `searchMemories` 的代码片段（源码扫描）
  - `MemoryRepository.kt` 的 `searchMemories` 公开签名含 `tags: List<String>?` 参数；`runSearchMemoriesWithDebug` 函数体含按 `tags` 过滤 `tags.all { ... mem.tags.any ...}` 风格代码
  - agent 收到 system prompt 后能在 tool description 里看到 `tags` 参数说明（手测：发送 chat，模型回 `<query_memory>` 工具调含 `tags="#auto_summary"`，工具结果只返回带该 tag 的 memory）
  - 既有 `query_memory` 调用方（agent 不传 tags）行为完全不变（向后兼容）
  - R-AGENT-009 `PersistentInstructionAgentHintTest` / R-AGENT-013 `MessageCoordinationDelegateAutoSummaryMemoryWiringTest` / R-UI-004 `EditMemoryDialogAutoSummaryHintWiringTest` 三个既有测试类全绿（不破坏既有 wiring）
  - §2 四件套：`verify_align / scan_stubs / deep_align` 维持零；`scan_functional_stubs` ≤ 390（不增）

---

### R-AGENT-015: 调 LLM 前自动注入 `<memory-context>` 召回围栏到当前轮 user message
**来源**: `reference/hermes-agent/run_agent.py:8948-8969, 9087-9107, 11857-11860` + `reference/hermes-agent/agent/memory_manager.py:65-80, 178-195`（Python 上游每轮 `prefetch_all` → `build_memory_context_block` → 拼到当轮 user message → 仅 API 调用时拼，不污染持久化历史；用户 2026-06-08 反馈"AI 完全不记得之前聊过的事，每次都要我重新解释"）

**背景**: R-AGENT-013 让 APP 内 chat 自动摘要强行打 `#auto_summary` tag 落库。R-AGENT-014 让 agent 知道这个 tag 存在并能用 `query_memory` 主动查（lazy 路径）。但 lazy 路径依赖 agent 自己意识到"我应该查一下"，不可靠——很多用户场景下模型直接回"我不记得我们之前聊过这个"，把已经在 ObjectBox 里的相关摘要白白晾着。Python Hermes 的 `run_agent.py:9087-9107` 的做法是 eager prefetch：每轮调 LLM 之前后台用 `original_user_message` 当 query 调 `prefetch_all`，结果拼到当轮 user message 末尾用 `<memory-context>` fence 包起来，直接喂给模型——模型读了 fence 内容就能"自然"用上记忆，不需要主动调 query_memory。

Hermes Android 翻译已半成品：`hermes-android/.../MemoryManager.kt:339-362` 的 `_INTERNAL_CONTEXT_RE` / `sanitizeContext` / `buildMemoryContextBlock` 全部 1:1 翻译完毕，但 agent loop 里**无人调用** `buildMemoryContextBlock`——完全没有把 ObjectBox `MemoryRepository` 接进来当 prefetch source。R-AGENT-015 就是把这个空挡补上：让 `EnhancedAIService.runAgentLoopViaHermes` 在 `openAiMessages = requestHistory.toOpenAiMessages()`（行 1064）之后、首次发请求之前，对末尾 user 那条 OpenAI message 原地拼接 `buildMemoryContextBlock(prefetchedContext)`，prefetch source 就走 `MemoryRepository.searchMemories(query=originalUserMessage, ...)`。

架构红线（§6）：本需求**只补 Python 上游已有但 Kotlin 漏译的功能**，**不**新增任何 Python 上游没有的设计；**不**新增专属开关、**不**改 ChatMessage 持久化层、**不**改 ChatHistoryDelegate、**不**触碰 hermes-android 模块（注入点放 app 模块的 `EnhancedAIService` 内最小侵入；hermes-android 内桥接 `MemoryProvider` 接口为 P1 留给以后单独评估）。

**行为**:
- **注入点**: `EnhancedAIService.runAgentLoopViaHermes`（`app/src/main/java/com/ai/assistance/operit/api/chat/EnhancedAIService.kt`）内 `openAiMessages = requestHistory.toOpenAiMessages().toMutableList()` 之后、首次发请求之前。仅修改 `openAiMessages`，**绝不**修改 `requestHistory`（PromptTurn 列表）。
- **触发开关**: 复用 `runAgentLoopViaHermes` 已有的 `enableMemoryQuery: Boolean` 形参（与 R-AGENT-010 / R-AGENT-013 / `MemoryQueryToolExecutor` 同一开关）。`enableMemoryQuery == false` → 跳过整个注入流程；不新增任何专属开关。
- **prefetch query**: 用 `requestHistory` 末尾 user PromptTurn 的 content，与 Python `original_user_message` 对齐。如果末尾不是 user role，跳过注入（容错，不报错）。
- **prefetch 调用**: 调 `MemoryRepository.searchMemories(query=originalUserMessage, limit=N, tags=null, ...)`，其中：
  - `limit` 从 `memorySearchSettingsPreferences.load()` 读，但**强制上限 5**（防 token 爆炸）。如果用户偏好超过 5 取 5。
  - `tags = null`（不限制 tag —— 让 #auto_summary / 用户 create_memory 节点 / R-AGENT-009 #persistent_instruction 等都能命中）。
  - 其他 weight / threshold 沿用 `memorySearchSettingsPreferences.load()` 的用户配置（与 `MemoryQueryToolExecutor` 一致）。
- **黑名单排除**: prefetch 结果里 `mem.tags.any { it.name == "#persistent_instruction" }` 的节点必须过滤掉——R-AGENT-009/245 已经把持久指令注入到 system prompt 末尾（`ConversationService.buildPersistentInstructionsText`），再注一次浪费 token 且语义重复。
- **每条 content 截断**: 单条 memory content 超过 800 字符必须 `take(800) + "…"`，防超长摘要把 user message 撑爆。
- **拼接**: 调 `MemoryManager.buildMemoryContextBlock(rawContext)` 包 `<memory-context>` fence。`rawContext` 格式：每条 memory 一行，"[<title> #<tag1> #<tag2>] <content_truncated>"（title 缺省时用 "memory"）。空结果 → buildMemoryContextBlock 返回空串 → 不拼。
- **写回 user message**: 末尾 user OpenAI message 的 content 改成 `originalContent + "\n\n" + fence`。仅一次，不在多 turn 里重复注入。
- **死循环防御（关键）**:
  1. **`forcePersistSummaryToMemory` 落库前 sanitize**: `MessageCoordinationDelegate.forcePersistSummaryToMemory(summaryText, chatId)` 在调 `memoryRepository.saveMemory(...)` 之前，对 `summaryText` 调一次 `MemoryManager.sanitizeContext(...)`（hermes-android 已有），剥掉任何残留的 `<memory-context>` fence + System note + fence 标签。理论上 summarizeMemory 拿的是 `List<ChatMessage>`（持久化层不带 fence）所以剥不到东西，但作为防御性代码——一旦未来路径变化 fence 漏进 ChatMessage 也不会扩散到 ObjectBox。
  2. **prefetch 限 limit ≤ 5**: 见上文。
  3. **每条 content 截断 800 字符**: 见上文。
- **不污染聊天历史 / 持久化层**:
  - 仅修改 `openAiMessages`（in-memory MutableList，单次 sendMessage 生命周期），不改 `requestHistory`、不改 `execContext.conversationHistory` 的入口（line 928）、不改 `ChatHistoryDelegate.saveCurrentChat` 的 ChatMessage、不改 ObjectBox。
  - 用户在 ChatScreen 回看消息时**看不到** `<memory-context>` fence（因为 ChatHistoryDelegate 写的是 ChatMessage，根本不是 PromptTurn / OpenAI message 链路）。
  - line 1140-1141 / 1217-1218 的 `openAiMessages.toPromptTurnsForHistory()` 会把带 fence 的 user message 写回 `execContext.conversationHistory`——可接受，因为 ExecContext 是单次 sendMessage 临时副本（每次 sendMessage 新建 `MessageExecutionContext`），fence 不会跨 sendMessage 持久化。这与 Python 上游 `run_agent.py:9087-9107` 的 `api_messages` 行为一致（fence 在多 turn agent loop 内一直存在，跨 sendMessage 不存在）。
- **架构合规（§6 红线）**:
  - **不**改 hermes-android 模块（`buildMemoryContextBlock` 已有 1:1 翻译；本需求只在 app 模块调用它）。P1 严格按 Python 在 hermes-android 内引入 `MemoryProvider` 接口让 app 注入委托给 `MemoryRepository` 的实现——独立评估，不在本需求范围。
  - **不**改 R-AGENT-014a 的 `#auto_summary` system prompt 引导段（lazy + eager 共存：lazy 是兜底，eager 是默认）。
  - **不**改 R-AGENT-009/245 持久指令的 system prompt 注入路径（黑名单排除避免重复）。
  - **不**改 R-AGENT-013 的 `forcePersistSummaryToMemory` 主体逻辑（仅在落库前加一行 `sanitizeContext` 防御）。
  - **不**新增专属开关（一个 `enableMemoryQuery` 管所有记忆功能）。
- **i18n 完整性**: 本需求面向 agent / API payload，不涉及用户可见 UI 文案。`buildMemoryContextBlock` fence 字面值（`<memory-context>` / `[System note: ...]`）与 Python 上游一致英文，不需要 i18n 分支。
- **测试策略**: 与 R-AGENT-013 / R-AGENT-014 同策略——`EnhancedAIService` 重度依赖 Android Context / OkHttp / Hermes agent loop / ObjectBox，JVM mock ROI 极低，走源码字符串扫描守 wiring。运行时正确性由手测 + §3 E2E 兜底（agent-level TOKEN 回显验收）。

**验收**:
- `EnhancedAIService.kt` 的 `runAgentLoopViaHermes` 函数体内必须含：(a) `enableMemoryQuery` gate（`if (enableMemoryQuery)` 或等价）、(b) 对 `MemoryRepository.searchMemories` 的调用、(c) 对 `MemoryManager.buildMemoryContextBlock(...)`（或同名 hermes-android helper）的调用、(d) 修改 `openAiMessages` 末尾 user message 的代码（源码扫描确认）。
- `MessageCoordinationDelegate.forcePersistSummaryToMemory` 函数体内必须在 `memoryRepository.saveMemory(...)` 调用之前对 `summaryText` 调一次 `sanitizeContext` 或等价 fence 剥离（源码扫描确认）。
- prefetch 结果包含 `#persistent_instruction` tag 的节点必须被过滤掉（源码内必须含对该 tag 字面字符串的引用 + filter 调用）。
- 单条 memory content 必须按 800 字符上限截断（源码内必须含 `take(800)` 或 `800` 字面值或等价常量定义）。
- limit 必须强制 ≤ 5（源码内必须含 `coerceAtMost(5)` 或 `5` 字面值或等价常量定义）。
- 既有测试类不破坏：`MessageCoordinationDelegateAutoSummaryMemoryWiringTest` / `MessageCoordinationDelegateSummaryStripWiringTest` / `PersistentInstructionAgentHintTest` / `QueryMemoryToolPromptsTagsWiringTest` / `MemoryQueryToolExecutorTagsWiringTest` / `MemoryRepositorySearchTagsFilterWiringTest` 全部维持绿。
- **手测验收**：APP 安装后，先发"我喜欢用 Tailwind CSS"等带明显偏好/事实的 chat、等其触发自动摘要落 `#auto_summary` 节点；新建/换一个 chat 问"你还记得我喜欢什么 CSS 框架吗？"——agent 回答必须能命中 Tailwind（即 prefetch 注入了相关摘要）。
- §2 四件套：`verify_align / scan_stubs / deep_align` 维持零；`scan_functional_stubs` ≤ 390（不增；本需求不消除任何已有 functional stub，仅新增胶水代码）。

---

## 域 ACP — Agent Client Protocol

HermesApp 支持 ACP 双向：作为 **server** 暴露自身 agent 给外部 client（Zed / CLI）；作为 **client** 连到外部 ACP server（如 GitHub Copilot）。Python 源：`reference/hermes-agent/acp_adapter/` + `acp_registry/` + `agent/copilot_acp_client.py`。

### R-ACP-001: HermesApp 实现 ACP server 协议
**来源**: `reference/hermes-agent/acp_adapter/server.py` + `session.py` + `entry.py` + `auth.py` + `permissions.py`
**行为**: JSON-RPC over stdio / HTTP / WebSocket，实现 ACP 标准方法（session 生命周期、消息发送、工具事件流、中止、auth 握手、permission 提示）；消息格式与 ACP 上游 schema 对齐；`acp_registry/agent.json` 描述 agent 能力与 icon 供 client 发现。

### R-ACP-002: Tool-kind 映射 HermesApp 工具到 ACP 工具类别
**来源**: `reference/hermes-agent/acp_adapter/tools.py`
**行为**: HermesApp 的每个内部工具映射到 ACP 规范的 tool-kind（read / edit / execute / search 等），供 client 侧做 UI 分类与权限决策。

### R-ACP-003: ACP 工具事件生命周期与 agent 事件流对齐
**来源**: `reference/hermes-agent/acp_adapter/events.py`
**行为**: agent 内部的 ToolCallStart / ToolCallEnd / ContentDelta 事件映射为 ACP session notification；外部 client 看到的事件序列与内部一致。

### R-ACP-004: HermesApp 作为 ACP client 连接外部 server（GitHub Copilot）
**来源**: `reference/hermes-agent/agent/copilot_acp_client.py`
**行为**: agent 作为 ACP 客户端连接 GitHub Copilot 等外部 ACP server，把远端工具能力注册进本地工具列表；消息与事件格式按 ACP 协议；与 R-ACP-001 的 server 路径并存。

---

## 域 TOOL — Built-in tools

HermesApp 提供 Hermes agent 可调用的内建工具集。Python 源位于 `reference/hermes-agent/tools/`；Kotlin 对应 `hermes-android/src/main/java/com/xiaomo/hermes/hermes/tools/`（skills / mcp 家族与工具同目录，不单立子包）。

### R-TOOL-001: 内置工具集与 Python 上游对齐
**来源**: `reference/hermes-agent/tools/` 全体（registry / file_operations / file_tools / terminal_tool / code_execution_tool / process_registry / memory_tool / todo_tool / checkpoint_manager / clarify_tool / send_message_tool / web_tools / browser_tool / browser_cdp_tool / vision_tools / image_generation_tool / voice_mode / tts_tool / transcription_tools / delegate_tool / mixture_of_agents_tool / session_search_tool / skill_manager_tool / skills_tool / feishu_doc_tool / feishu_drive_tool / discord_tool / homeassistant_tool / cronjob_tools / tool_result_storage 等）
**行为**: Hermes agent 可调用的工具族按 Python 上游的签名、参数、返回结构、错误码 1:1 暴露；涵盖文件读写编辑 / 终端执行（前台+后台 `process_registry`）/ 代码执行 / 浏览器（Playwright CDP + Camoufox）/ web fetch+search / 多媒体（vision / image-gen / TTS / STT / voice mode）/ 子 agent 委派（delegate + mixture-of-agents）/ memory / todo / checkpoint / clarify / send_message / 会话历史搜索 / skill 调用 / MCP 调用 / 平台集成（Feishu Doc/Drive、Discord、HomeAssistant）/ cron；未注册 name 与异常路径均返回结构化错误而非抛异常；超大 tool_result 经 `tool_result_storage` 分页存储；注册表同时供 ACP / skill / MCP 层枚举能力。Android 平台独有的浮窗 / 通知 / 系统设置等工具以平台特供方式注册进同一 registry。

### R-TOOL-002: 敏感操作统一审批
**来源**: `reference/hermes-agent/tools/approval.py` + `path_security.py` + `url_safety.py` + `website_policy.py`
**行为**: 文件写 / 命令执行 / 网络请求 / 路径敏感参数在执行前汇总到统一审批层；审批模式（always / once / never / auto-accept）由配置决定；被拒调用返回明确的 declined 错误；路径规则（敏感目录、路径遍历、符号链接、项目外绝对路径等）+ URL / 网站白名单策略与 Python 上游一致。FileSafety 的 agent 侧拦截面由 R-AGENT-003 承载，此处只负责 tool 层审批门控。

### R-TOOL-003: 工具预算约束
**来源**: `reference/hermes-agent/tools/budget_config.py` + `managed_tool_gateway.py`
**行为**: 按工具类别跟踪单轮 / 会话累计调用次数、字节、token；超预算拒绝继续调用并返回预算用尽的结构化错误；`managed_tool_gateway` 负责远程管理型工具的配额 / 限流；配额阈值与 Python 上游一致。

### R-TOOL-016: launch_app 工具——经 monkey 突破 Android BAL 启动其他 APP
**来源**: 无 Python 上游（Android 平台特供工具）。原因：Android 10+ 引入的 background activity launch (BAL) 限制使 `start_app` 的 `am start` / `Context.startActivity` 路径在 agent 后台运行时被静默拒绝（命令成功但 activity 不切前台），需要走 shell `monkey` 路径绕开。
**行为**:
- 暴露 `launch_app(package_name: String)` 工具，注册进 R-TOOL-001 registry。
- 实现走 `AndroidShellExecutor.executeShellCommand("monkey -p <pkg> -c android.intent.category.LAUNCHER 1")`；由 `ShellExecutorFactory` 按用户当前权限层（STANDARD / DEBUGGER(Shizuku) / ROOT / ADMIN / ACCESSIBILITY）路由——DEBUGGER 及以上层运行在 `shell` uid 上，BAL 放行；STANDARD 层在自身 uid 上跑 monkey，BAL 仍可能挡，但作为 best-effort 保留。
- 与 `start_app` **并存**（不替换）。两者职责对照：
  - `start_app`：走 `am start -n pkg/Activity` 或 PackageManager intent，干净无副作用，但 Android 14+ 后台调用易被 BAL 拒。
  - `launch_app`：走 `monkey -c LAUNCHER`，能突破 BAL，但**已知副作用**——monkey 会触发系统的"屏幕方向锁"等设置变更（见历史决策注释 `DebuggerSystemOperationTools.kt:310` 说明为何 `start_app` 当年从 monkey 改回 am start）。Agent 应优先用 `start_app`，仅在 `start_app` 命令成功但前台未切换时回退到 `launch_app`。
- 返回 `AppOperationData(operationType="launch", packageName=<pkg>, success=<bool>, details=<stdout/stderr 摘要>)`。
- 参数缺失 / 包不存在 → `ToolResult(success=false, error="...")`，不抛异常。
- 工具描述（system prompt 里 AI 可见的）必须明确告诉模型「`launch_app` 是 `start_app` 的 BAL 兜底，并存使用」。

---

## 域 GATEWAY — 外部平台网关

HermesApp 作为网关把 agent 能力接到外部 IM / 协作平台（飞书、微信、Telegram、QQ、企业微信、Slack、Discord、Matrix、WhatsApp、Signal、钉钉、SMS、邮件、ApiServer、Webhook、HomeAssistant、Mattermost、BlueBubbles）。

### R-GW-001: Gateway 基础类与通用能力
**来源**: `reference/hermes-agent/gateway/base.py` + `helpers.py` + `run.py` + `config.py` + `session.py` + `session_context.py` + `stream_consumer.py` + `delivery.py` + `pairing.py` + `hooks.py` + `status.py` + `restart.py` + `channel_directory.py` + `display_config.py` + `mirror.py` + `sticker_cache.py`
**行为**: 统一的 platform adapter 基类；规范化消息 / DM 策略 / 群策略 / 去重 / 批量合并 / 重连等通用能力集中在 base + helpers；`GatewayRunner`（run.py，~513k 一等编排器）负责 adapter 生命周期；`config.py` 负责加载 / 校验配置；`session.py` / `session_context.py` 维护每 channel 的会话上下文；`stream_consumer.py` 把 agent 事件流消费为平台可渲染 chunk，`delivery.py` 管出站派发；`pairing.py` 处理平台账号配对；`hooks.py` 分发 gateway 级钩子（如 builtin boot.md）；`status.py` 暴露健康状态供 UI 订阅；`restart.py` 做 supervised 重启；`channel_directory.py` 管 channel 白名单；`display_config.py` 按平台解析表情 / 明文 / markdown；`mirror.py` 做跨平台会话镜像；`sticker_cache.py` 缓存表情素材。

### R-GW-002: Feishu 平台功能完整对齐
**来源**: `reference/hermes-agent/gateway/platforms/feishu.py` + `feishu_comment.py` + `feishu_comment_rules.py`
**行为**: 完整实现 Feishu 长连接 WSS / 消息收发 / 卡片交互 / reaction / batching / drive comment 机器人 / 管理员与群 ACL / QR onboarding；webhook 模式在 Android 上显式标注为 "not supported without reverse proxy"（R-CORE-001 允许的平台差异）。

### R-GW-003: Weixin 平台功能完整对齐
**来源**: `reference/hermes-agent/gateway/platforms/weixin.py`
**行为**: QR 登录 / AES-128-ECB CDN 加解密 / 账号状态持久化 / 分段发送 / DM 群策略 / typing ticket / context token cache / 批量发送——均与 Python 上游对齐。

### R-GW-004: QQ 机器人平台功能对齐
**来源**: `reference/hermes-agent/gateway/platforms/qqbot/`
**行为**: QQ 官方 bot API 长连接 / 消息收发 / 群与私聊路径 / 权限与别名处理。

### R-GW-005: 其余平台维持类方法级对齐
**来源**: `reference/hermes-agent/gateway/platforms/{telegram,slack,discord,wecom,matrix,whatsapp,signal,dingtalk,email,sms,api_server,webhook,homeassistant,mattermost,bluebubbles}.py`
**行为**: 以上平台保持与 Python 上游的类 / 方法签名对齐（verify_align 零违规）；Android 上不可用的路径在方法体里返回显式"not supported on Android"；用户后续明确要完整跑通时按 R-GW-002 / 003 的方式补充实现。

### R-GW-006: Gateway 运行时前台服务
**来源**: 无 Python 直接上游（Android 特有）；对应 Python runner 的生命周期
**行为**: Gateway 在 Android 上通过前台服务运行（`GatewayForegroundService`），随应用/开机自启策略、电量白名单引导、存活通知均由前台服务负责；服务状态可被 UI 实时订阅。

---

## 域 STATE — 会话状态 / trajectory

### R-STATE-001: Hermes 会话状态结构与 Python 上游一致
**来源**: `reference/hermes-agent/hermes_state.py` + `agent/trajectory.py`
**行为**: 会话包含 messages / tool events / token usage / metadata 等字段；支持 resume / branching 操作；序列化 / 反序列化对齐 Python `HermesState`；版本升级有向前兼容路径；trajectory 记录模型由 `agent/trajectory.py` 提供。

### R-STATE-002: Trajectory 压缩与 Python 上游一致
**来源**: `reference/hermes-agent/trajectory_compressor.py`
**行为**: 长 trajectory 按既定策略压缩（保留边界事件 / 汇总中间步骤）；压缩前后事件序列仍可被 agent 正确重放。

### R-STATE-003: 持久化到 Android 本地
**来源**: Android 平台实现，对齐 Python 文件系统持久化语义
**行为**: 会话 state 持久化到应用私有目录；读写符合 DataStore / 文件 API 规范；断电 / 进程重启后可恢复。

---

## 域 SKILL — Skill 加载 / 启用 / 同步

### R-SKILL-001: Skill 发现、加载、hub 索引
**来源**: `reference/hermes-agent/agent/skill_utils.py` + `tools/skills_hub.py` + `tools/skills_tool.py` + `tools/skill_manager_tool.py` + `skills/` + `optional-skills/`
**行为**: 扫描 skill 目录（内建 `skills/` + `optional-skills/` + 用户导入）；解析 frontmatter + 正文；由 `skills_hub` 构造中心索引加入全局注册表；非法 skill 给出明确错误；skill 调用通过 `skills_tool` 工具执行，`skill_manager_tool` 负责 install / uninstall / list。

### R-SKILL-002: Skill 启用 / 禁用 + 执行护栏
**来源**: `reference/hermes-agent/agent/skill_commands.py` + `tools/skills_guard.py`
**行为**: 支持按 name / glob 启用或禁用 skill；禁用 skill 不出现在 R-TOOL-001 枚举结果里，调用也被拒绝；`skills_guard` 在执行时做权限 / 签名 / 策略校验；slash-command 调度由 `skill_commands` 承载。

### R-SKILL-003: Skill 增量同步
**来源**: `reference/hermes-agent/tools/skills_sync.py`
**行为**: 支持从上游仓库增量同步新 / 更新的 skill 定义到本地；冲突 / 签名失败拒绝覆盖；同步后刷新 R-SKILL-001 的 hub 索引。

---

## 域 MCP — Model Context Protocol

### R-MCP-001: HermesApp 作为 MCP client 连接外部 MCP server
**来源**: `reference/hermes-agent/tools/mcp_tool.py`
**行为**: 支持 stdio / SSE / WebSocket 传输；能列举 / 调用 server 暴露的 tools / resources / prompts；错误 / 超时结构化返回；被调工具注册进 R-TOOL-001 registry。

### R-MCP-002: HermesApp 可选作为 MCP server 暴露自身工具
**来源**: `reference/hermes-agent/mcp_serve.py`
**行为**: 将 R-TOOL-001 注册表按 MCP tools 协议暴露；可选启用；与 ACP 服务可并存。

### R-MCP-003: MCP OAuth 流程
**来源**: `reference/hermes-agent/tools/mcp_oauth.py` + `mcp_oauth_manager.py`
**行为**: 对需要 OAuth 的 MCP server 走标准授权码流程；token 由 `mcp_oauth_manager` 管理（获取 / 刷新 / 失效）；授权请求由用户审批（UI 侧），令牌存储经加密。

---

## 域 CRON — 定时任务

### R-CRON-001: Cron 定时任务子系统
**来源**: `reference/hermes-agent/cron/scheduler.py` + `cron/jobs.py` + `tools/cronjob_tools.py`
**行为**: 支持创建 / 列举 / 删除定时任务；支持一次性与周期性；到点触发 agent prompt；持久化到本地；与 Python 上游一致的 cron 表达式解析与下一次触发时间计算；agent 可通过 `cronjob_tools` 工具族自管 cron。

---

## 域 SAFETY — 安全护栏

### R-SAFETY-001: 敏感操作统一需要审批
**来源**: 跨越多个工具实现的统一安全策略（对应 R-AGENT-003 FileSafety / R-TOOL-002 approval + path_security）
**行为**: 文件写、命令执行、网络请求等敏感操作在执行前汇总到统一的审批层；审批策略可配置；被拒操作返回结构化 declined 错误。

### R-SAFETY-002: 外部输入清洗与 secret 脱敏
**来源**: `reference/hermes-agent/agent/redact.py` + `tools/url_safety.py` + `tools/website_policy.py` + `tools/tirith_security.py` + `tools/osv_check.py` + 各工具参数校验
**行为**: 工具参数 / 外部消息在进入 agent 前做 sanitize（控制字符、超长字符串、过深 JSON）；日志 / trajectory 写入前经 `redact.py` 规则抹除 API key / token / PII；URL 与 website 按 `url_safety` + `website_policy` 策略准入；依赖漏洞 / 恶意代码检查通过 `osv_check` 与 `tirith_security`；不合规输入被拒绝或截断并标注。

---

## 域 CONFIG — [DELETED 2026-04-26]

CONFIG 原先的三条 R-CONFIG-001..003（Preferences / ConfigBuilder / Controller）是代码结构、不是用户可见需求，整域剪除。用户可见的配置能力由 R-UI-001 的 Hermes Settings hub 承载；gateway 配置落盘归属 R-GW-001 base。

---

## 域 UI — Hermes Settings hub

### R-UI-001: Hermes Settings hub 承载 gateway 与 agent 的全部用户可见配置
**来源**: 无 Python 上游；Android UI
**行为**: 应用 Settings hub 新增 "Hermes / 墨思" 入口，子屏集中覆盖——gateway 平台凭证录入 / 清除、DM / 群 / 批量 / 去重 / 重连等策略、agent 参数（max_turns / persona / memory 策略 / 工具 allow-deny）、服务开关 + 电量白名单 + 开机自启 + 实时状态、QR 绑定（Feishu probe_bot / qr_register、Weixin qr_login）。敏感字段经加密存储；保存后运行时组件读取该配置而非硬编码。

### R-UI-002: 记忆详情页手动 toggle 持久化指令
**来源**: 无 Python 上游；Android UI 体验需求（用户兜底 R-AGENT-009 写入路径）
**背景**: R-AGENT-009 规定带 `#persistent_instruction` tag 的 memory 节点每轮注入到 system prompt 末尾。原写入路径完全依赖 agent 在对话里识别意图后主动调 `create_memory` 附加该 tag，实战中 agent 会把日期 / 元描述 / 系统行为描述等"非规则"内容塞进 content，又或者干净规则被打错 tag，导致用户既无法新增也无法清理。用户需要一条不依赖 agent 的兜底通路。
**行为**:
- `MemoryInfoDialog`（记忆详情对话框）的 Tags 区域下方增加一个 `Switch`，标签例如 "设为持久化指令"
- Switch 初始状态 = `memory.tags.any { it.name == "#persistent_instruction" }`
- 开 → 调 `MemoryRepository.addTagToMemory(memory, "#persistent_instruction")`；关 → 调 `MemoryRepository.removeTagFromMemory(memory, "#persistent_instruction")`
- 操作通过 `MemoryViewModel.togglePersistentInstruction(memoryId, enabled)` 走，与现有 `updateMemory` / `deleteMemory` 同一异步模式（`viewModelScope.launch` + `_uiState.update`）；toggle 后刷新 graph 让节点颜色（R-AGENT-009 金色）即刻更新
- 仅操作 tag 关系，不动 memory 本身的 title / content / importance / credibility / 其它 tag
- 文档节点（`isDocumentNode = true`）同样允许 toggle（文档可以是规则参考）
- 该 toggle 不应触发 `MemoryLibrary` 的自动合并 / 重写流程
**验收**:
- toggle on 后：`memory.tags.map { it.name }` 包含 `#persistent_instruction`；下一轮 `ConversationService.buildPersistentInstructionsText()` 返回的 bullet 列表包含该 memory 的 content
- toggle off 后：tag 被移除；下一轮注入不再包含该条；MemoryTag 实体本身保留（其它 memory 可能仍引用）
- 重复 toggle on / off N 次后仅一份 tag 关系（无重复挂载）
- toggle 不改 memory.title / content / updatedAt 之外的元数据；按 §1.2 `addTagToMemory` / `removeTagFromMemory` 已 `memoryBox.put(memory)` 写回，关闭对话框再打开看到的状态与最新值一致（无 ObjectBox `ToMany` 缓存陷阱）
- §2 四件套：`verify_align / scan_stubs / deep_align` 维持零；`scan_functional_stubs` ≤ 390（不增）

### R-UI-003: Gateway agent 运行时悬浮球
**来源**: 无 Python 上游；Android UI 体验需求（用户报 "飞书对话时，会出现悬浮球，感知用户 agent 在工作中"）
**背景**: 仅 gateway 链路（飞书 / 微信外部聊天经 `HermesGatewayController` 触发 agent）需要一个全局悬浮球作为"agent 正在工作"的视觉信号——UI 内 chat 已经有 `FloatingChatService` 的 status indicator 覆盖该需求。gateway 场景下用户不在 app 内，必须有 system-overlay 才能感知。
**行为**:
- 新增 `AgentStatusOverlayService`（前台 Service，channel `hermes_agent_status_overlay`，重要性 MIN，notification id 71_643），生命周期由 `GatewayForegroundService` 联动：gateway `onCreate` 启动 / `onDestroy` 停止
- 订阅 `GatewayChatEventBus`：`ProcessingStarted` → 加入 activeChats 并 show；`ProcessingCompleted` → remove；若 activeChats 空 → hide；`ProcessingFailed` → 红色闪烁 `ERROR_FLASH_MS = 2500ms` 后 hide
- 订阅 `AgentEventBus`（新增全局 sharedFlow，`EnhancedAIService.runAgentLoopViaHermes` 与 `HermesAdapter.sendMessage` 在创建 `HermesAgentLoop` 时把每个 `AgentEvent` 转发到此 bus）：取 `turn` 数 / `lastToolName` 作为细粒度状态
- 订阅 `AgentTokenBus`（同上，从 `OperitChatCompletionServer` 的 `onTokensUpdated` / `onTurnComplete` 回调累加）：展示累计 input / output token
- 订阅 `ChatRuntimeHolder.GATEWAY.inputProcessingStateByChatId` 作为 fallback 状态文字源
- 圆球 56dp + 紫色径向渐变 + 闪电图标 + 1.5s 旋转动画；点击展开 240-320dp 卡片面板展示 platform / chatId 短哈希 / 状态 / 已运行秒 / turn / token / activeChatCount
- 拖拽位置持久化到 `SharedPreferences("agent_status_overlay")`
- 无 SYSTEM_ALERT_WINDOW 权限：onCreate 先 `startForeground` 再 `stopSelf`（避免 5s 超时崩溃），仅打日志
- UI 工具临时隐藏：`ToolRegistration.executeUiToolWithVisibility` 在 UI 工具执行前 `setOverlayVisible(false)`、执行完 `setOverlayVisible(true)`，避免悬浮球挡住 agent 要点击的 UI
**验收**:
- `AndroidManifest.xml` 注册 `.services.AgentStatusOverlayService`（exported=false, foregroundServiceType=dataSync）
- `GatewayForegroundService` 在 `onCreate` 调 `AgentStatusOverlayService.start(this)`、`onDestroy` 调 `AgentStatusOverlayService.stop(this)`
- `EnhancedAIService.runAgentLoopViaHermes`：sink 内 `AgentEventBus.emit(taskIdValue, event)`；token 回调内 `AgentTokenBus.emit(taskIdValue, input, output, turnComplete=true/false)`
- `HermesAdapter.sendMessage`：sink 内 `AgentEventBus.emit(chatId, event)`
- 用户主动点 X：Service `stopSelf`；下次新 chat 触发 `ProcessingStarted` 时由 `GatewayForegroundService` 重新拉起
- §2 四件套：`verify_align / scan_stubs / deep_align` 维持零；`scan_functional_stubs` ≤ 390（不增）

### R-UI-004: `EditMemoryDialog` content 高度上限抬高 + `#auto_summary` 节点 hint
**来源**: 无 Python 上游；Android UI 体验需求（用户 2026-06-07 决策："长期记忆需要可以被用户编辑"，作为 R-AGENT-013 的兜底通路 —— 自动摘要可能很长，现有 200dp 高度上限对自动摘要节点的编辑严重不友好）
**背景**: R-AGENT-013 让 APP 内每次自动摘要强行写入长期记忆 + `#auto_summary` tag。这些节点 content 通常较长（往往 500~2000 字），且用户需要保留编辑权来手工修剪 / 增删事实。当前 `EditMemoryDialog.kt:111` 的 content `OutlinedTextField` 使用 `Modifier.heightIn(min = 100.dp, max = 200.dp)` —— 200dp 在主流屏幕上只能容纳 7~9 行文本，长摘要必须靠内部 scroll 编辑，体验极差。R-UI-002 已经验证 `EditMemoryDialog` 的保存路径走 `MemoryViewModel.updateMemory → MemoryRepository.updateMemory`，**不**触发 LLM 价值判官（用户编辑结果不会被回滚）—— 所以只剩 UI 输入区域大小的问题。
**行为**:
- `EditMemoryDialog.kt:111` content `OutlinedTextField` 的 `.heightIn(min = 100.dp, max = 200.dp)` 改为 `.heightIn(min = 160.dp, max = 480.dp)`（min 抬到 160dp 让短摘要也舒展；max 抬到 480dp ≈ 18~20 行，结合 dialog 外层 `.fillMaxHeight(0.9f)` + `.verticalScroll(scrollState)` 不会破坏对话框整体布局）
- 当被编辑的 `memory.tags` 含有 `"#auto_summary"` 时，在 content `OutlinedTextField` 上方插入一条小型 `AssistChip` / `SuggestionChip`（label 例如 "自动摘要节点"，icon 用 `Icons.Default.AutoAwesome` 或等价），让用户在编辑界面就能识别此节点的来源（与 R-AGENT-012 在 `MemoryScreen` 图谱上做颜色区分形成"图谱看色 / 编辑看 chip"两层视觉提示）
- chip 文案走 `res/values*/strings.xml`，新增键 `memory_auto_summary_chip`（zh: "自动摘要节点（可编辑）" / en: "Auto-summary node (editable)"）
- 不改 `EditMemoryDialog` 函数签名 / 调用方 / 保存逻辑；只动 UI 渲染层
- 文档节点（`isDocumentNode = true`）的 content `OutlinedTextField` 仍保持 `enabled = false`，本需求不解锁它（与既有约束一致）
- 当 memory 不含 `#auto_summary` tag 时（普通节点 / R-AGENT-011 gateway 节点 / R-AGENT-009 persistent_instruction 节点），chip 不渲染 —— 老用户编辑体验完全不变（除了高度上限抬高，这是普适改进）
**验收**:
- 编辑 `#auto_summary` 节点：content 文本框可舒展到 480dp 高度；上方出现"自动摘要节点（可编辑）" chip
- 编辑普通节点 / gateway 节点 / persistent_instruction 节点：content 文本框高度上限同样为 480dp（普适改进），chip 不出现
- 编辑文档节点（`isDocumentNode = true`）：content 文本框仍为 disabled；chip 不出现
- 保存后 `MemoryRepository.updateMemory` 路径不变；`#auto_summary` tag 不被自动删除（仍在 `tags` 列表中可见，由用户在 `TagsEditor` 区域手动删除）
- §2 四件套：`verify_align / scan_stubs / deep_align` 维持零；`scan_functional_stubs` ≤ 390（不增）
