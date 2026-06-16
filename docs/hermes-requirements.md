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

### R-AGENT-016: APP 内自动摘要时一并把【关键事实】拆成独立 memory 节点（事实自我学习）
**来源**: 无 Python 直接上游（Python Hermes 通过 `MemoryProvider.sync_turn` 把每轮 user/assistant 委托给云端插件 Honcho/Mem0/Hindsight 做 fact extraction，本身不内嵌抽取逻辑——见 `reference/hermes-agent/agent/memory_provider.py:114` + `agent/memory_manager.py:210`。Android 侧目前没有任何 MemoryProvider 插件落地——hermes-android 的 `MemoryProvider` 接口翻译都没做，更没有 Honcho/Mem0 平替。R-AGENT-016 是 Android 平台 fallback：复用 R-AGENT-013 既已存在的"自动摘要 LLM"输出，把 LLM 早就写好的 `【关键事实】 / [Key Facts]` 段每行拆成一条独立 memory 节点，打 `#auto_extracted` tag，让用户的偏好/事实独立可查、独立可改、独立可删——比 `#auto_summary` 整段摘要的颗粒度细一档）

**背景**: R-AGENT-013 让 APP 内每次自动摘要强行写入 ObjectBox 一条带 `#auto_summary` tag 的整段记忆。R-AGENT-014 让 agent 知道该 tag 存在并能按 tag 查。R-AGENT-015 让 agent 调 LLM 前自动 prefetch 召回 fence 拼到 user message。三者构成"读"侧闭环。但**写**侧仍有断层：
1. **整段摘要颗粒度过粗** —— 用户说"我喜欢用 Tailwind CSS"，会被 R-AGENT-013 揉进一整段 `==========对话摘要==========` 摘要节点。下次跨 chat 问"我喜欢什么 CSS 框架"时，prefetch 召回的是整段摘要（包含核心任务状态 / 多条事实），fence 里夹了大量噪声 token。
2. **用户编辑成本高** —— 用户在 `MemoryScreen` 编辑摘要节点要面对 500-2000 字的整块文本，细颗粒度的事实改不动 / 删不动，只能整段编辑。
3. **跨 chat 复用度低** —— `#chat:<chatId>` tag 让摘要绑定具体 chat，跨 chat 召回时摘要里的"核心任务状态"成为干扰项，模型容易被无关任务引导。
4. **Python 上游对应**：Mem0 的 fact extraction 路径（POST 一段对话 → Mem0 返回 N 条 atomic fact 字符串 → 各自落库）；本需求是 Android 内嵌 LLM-free 平替——不再调一次 LLM，**直接复用** R-AGENT-013 已经调过的 summary LLM 的输出，零额外 token 成本。

修复策略：在 `MessageCoordinationDelegate.forcePersistSummaryToMemory` 落 `#auto_summary` 整段摘要**之后**（line 1815-1880），追加一个 `extractAndPersistFacts(summaryText, chatId, parentMemoryId, useEnglish)` 步骤——
- 用 `FunctionalPrompts.SUMMARY_SECTION_KEY_INFO_CN/EN` 段头定位 `【关键事实】 / [Key Facts]` 节
- 用 `SUMMARY_MARKER_*` 结尾分隔线（或下个 `=` 起头行）定位段尾
- 对节内每行 `trim()` 之后保留以 `- ` / `* ` / `• ` 开头的 bullet line（容错三种 marker）
- 剥掉 bullet 前缀后取首句（或 `take(800)` 防超长），一行一条 fact
- 每条 fact 独立调 `memoryRepository.saveMemory(Memory(...))` 落库 + 打 `#auto_extracted` + `#chat:$chatId`（R-AGENT-027 删除原本的 `#auto_summary_id:$parentMemoryId` 第三 tag —— 全代码库无读取方且 R-AGENT-026 keepDecision=false 路径产生孤儿污染）

**架构合规（§6 红线）**：
- **不**改 `SUMMARY_PROMPT` / `SUMMARY_PROMPT_EN` ——既有 prompt 已经稳定输出 `【关键事实】 / [Key Facts]` bullet 段，零 prompt 风险，纯下游解析。
- **不**新调一次 LLM——复用 R-AGENT-013 已经调过的 summary 结果，token 成本 0。
- **不**改 `forcePersistSummaryToMemory` 既有主体（line 1815-1877 全部保留：sanitize / saveMemory `#auto_summary` 整段 / addTagToMemory）；只在它末尾追加一个 `extractAndPersistFacts(...)` 调用。
- **不**改 `MemoryRepository` API 表面（用既有 `saveMemory` + `addTagToMemory`）。
- **不**改 R-AGENT-014 `query_memory` tag filter 流程——`#auto_extracted` 自动 piggyback 上车（`tags=#auto_extracted` 已经能用）。
- **不**改 R-AGENT-015 prefetch 流程——`searchMemories` 拿到的 hit 里同时包含 `#auto_summary` 整段 + `#auto_extracted` 单条事实，由相关性打分自然排序，模型读到的 fence 反而更精炼（命中具体事实而不是整段摘要）。
- **不**碰 hermes-android 模块（解析 + 落库都在 app 模块）。

**行为**:
- **入口**: `MessageCoordinationDelegate.forcePersistSummaryToMemory(summaryText, chatId)` 函数体末尾、`addTagToMemory(memory, "#chat:$chatId")`（line 1876）**之后**追加调用 `extractAndPersistFacts(summaryText, chatId, parentMemoryId = id, useEnglish = ...)`。
- **`extractAndPersistFacts` 新方法签名**: `private suspend fun extractAndPersistFacts(summaryText: String, chatId: String, parentMemoryId: Long, useEnglish: Boolean)`，置于 `forcePersistSummaryToMemory` 紧邻位置（同 class）。
- **`useEnglish` 来源**: `MessageCoordinationDelegate` 内调 `forcePersistSummaryToMemory` 的两条路径（`launchAsyncSummaryForSend` line 1507, `summarizeHistory` line 1629）都已经持有 `useEnglish` boolean（决定调英文还是中文 SUMMARY_PROMPT），把它一并透传到 `forcePersistSummaryToMemory(summaryText, chatId, useEnglish)`，再透传到 `extractAndPersistFacts`。
- **解析逻辑（核心）**:
  - `val sectionHeader = if (useEnglish) FunctionalPrompts.SUMMARY_SECTION_KEY_INFO_EN else FunctionalPrompts.SUMMARY_SECTION_KEY_INFO_CN`
  - 调 `sanitizeContext(summaryText)`（防御）→ `lines()` → 找到 `trim() == sectionHeader` 那一行的 index `headerIdx`；找不到 → 直接 return（容错，无事实可抽）
  - 从 `headerIdx + 1` 开始遍历，**遇到下面任一即停**：(a) 行 `trim()` 以 `=` 起头（即下一段分隔线 `============================` / `=======================================`）（b) 行 `trim()` 以 `【` / `[` 起头（下一个 section header）（c) 列表结束（连续 ≥ 2 个空行）
  - 每行 `trim()` 满足以下任一即视为 bullet：以 `- ` / `* ` / `• ` / `· ` 起头
  - 剥前缀后 `take(800)` + `trim()`；空字符串跳过；长度 < 5（极短/噪声）跳过
  - 整段最多抽 **20 条**（防 LLM 输出失控塞 200 行 fact 把 ObjectBox 撑爆）
- **每条 fact 落库**:
  - `val factMemory = Memory(title = <fact 首句最多 60 字符 + "…">, content = <fact 全文>, contentType = "text/plain", source = "auto_extracted", credibility = 0.85f, importance = 0.5f, folderPath = <继承父 memory 的 folderPath，或 "自动摘要">)`
  - `val factId = repository.saveMemory(factMemory)`
  - `repository.addTagToMemory(factMemory, "#auto_extracted")`
  - `repository.addTagToMemory(factMemory, "#chat:$chatId")`
  - ~~`repository.addTagToMemory(factMemory, "#auto_summary_id:$parentMemoryId")`~~ —— **R-AGENT-027 (2026-06-13) 删除**：全代码库无任何读取方；R-AGENT-026 keepDecision=false 路径会产生 `#auto_summary_id:-1` 孤儿污染；`#chat:<chatId>` 已足够提供会话级溯源
- **去重（最小防御）**:
  - 落库前调 `repository.searchMemories(query = factContent, limit = 3, tags = listOf("#auto_extracted"))`，命中已有 `#auto_extracted` 节点且其 `content` `equals(factContent, ignoreCase = true)` → 跳过本次落库（避免同一条事实在多次自动摘要里重复积累——R-AGENT-013 每次自动摘要都会重跑全段抽取，没有去重就会爆 N 倍冗余）。
  - 严格相等比对（不做语义去重）—— LLM 输出同一事实通常字面一致；语义级去重（"我喜欢 Tailwind" vs "我用 Tailwind 写样式"）由用户在 MemoryScreen 手动合并兜底。
- **失败容忍**:
  - 解析阶段（找不到段头 / bullet 全为空 / parse 异常）：catch 所有 Throwable，仅打日志 `AppLogger.w(TAG, "R-AGENT-016 fact extraction failed: ${t.message}")`，不影响父 `#auto_summary` 落库（已在前面完成）。
  - 单条 fact saveMemory 失败：try-catch 单条，记录日志后继续下一条（部分成功优于全失败）。
- **enableMemoryQuery 开关**: 复用 R-AGENT-013 既有 gate —— `forcePersistSummaryToMemory` 已经在 `enableMemoryQuery == true` 路径里调用，`extractAndPersistFacts` 自动继承同一开关，无需新增。
- **i18n**: 解析逻辑同时支持中英文 SUMMARY_PROMPT 输出（`useEnglish` 透传到 `extractAndPersistFacts`，按 `useEnglish` 选 `SUMMARY_SECTION_KEY_INFO_EN` 或 `..._CN` 段头）。bullet marker 三种（`- ` / `* ` / `• ` / `· `）兼容中英文 LLM 常见输出风格。无 `res/values` 字符串改动（用户不可见，纯解析）。
- **测试策略**: `MessageCoordinationDelegate` 重度依赖 ObjectBox / Hermes / Android Context，JVM mock ROI 极低，与 R-AGENT-013/014/015 同策略走源码字符串扫描守 wiring。运行时正确性由手测兜底：发"我喜欢 Tailwind CSS / 我用 IntelliJ IDEA / 我的服务器在东京"等多条偏好，触发自动摘要（约 30 条消息后），等 `MemoryScreen` 出现 `#auto_extracted` tag 节点 N 条（每条独立）、内容确为单条事实。

**验收**:
- `MessageCoordinationDelegate.kt` 的 `forcePersistSummaryToMemory` 函数体末尾必须含对 `extractAndPersistFacts` 或等价名（`extract*Facts*` / `*ExtractedFacts*`）的调用（源码扫描确认）。
- `MessageCoordinationDelegate.kt` 必须新增一个 private suspend 函数体含：(a) `SUMMARY_SECTION_KEY_INFO_CN` 或 `SUMMARY_SECTION_KEY_INFO_EN` 字面引用、(b) bullet 切分（`startsWith("- ")` 或 `Regex` 含 `-` `*` `•` 之一）、(c) `"#auto_extracted"` tag 字面字符串、(d) `repository.saveMemory(...)` 调用、(e) `repository.addTagToMemory(...)` 调用。
- 单条 fact content 必须按 800 字符上限截断（源码内含 `take(800)` 或同名常量）。
- 单次抽取 fact 数量上限 20 条（源码内含 `take(20)` / `coerceAtMost(20)` / 同名常量）。
- 去重逻辑：源码内含对 `searchMemories` 调用且 `tags` 参数含 `"#auto_extracted"` 字面值，或等价的 dedup 路径（如先查 `#auto_extracted` tag 子集再 `equals` 比对）。
- `useEnglish` 形参必须从 `launchAsyncSummaryForSend` / `summarizeHistory` 透传到 `forcePersistSummaryToMemory` 再透传到 `extractAndPersistFacts`（源码扫描确认 3 处签名）。
- 整个 fact extraction 流程必须 try-catch 包裹（catch Throwable / Exception），不得让父 `#auto_summary` 落库被回滚。
- 既有测试类不破坏：`MessageCoordinationDelegateAutoSummaryMemoryWiringTest` / `MessageCoordinationDelegateSummaryStripWiringTest` / `EnhancedAIServiceMemoryContextInjectionWiringTest` / `PersistentInstructionAgentHintTest` / `QueryMemoryToolPromptsTagsWiringTest` 全部维持绿。
- **手测验收**: APP 安装后，在一个 chat 里依次说"我喜欢用 Tailwind CSS"、"我用 IntelliJ IDEA 写代码"、"我的服务器在东京"，触发自动摘要（连发 N 条让 `shouldGenerateSummary` 返回 true）；等摘要 LLM 完成；打开 `MemoryScreen` 应看到 ≥3 个新节点带 `#auto_extracted` tag，每个节点内容确为单条事实（不是整段摘要）；新建一个 chat 问"你还记得我喜欢什么 CSS 框架吗？"，agent prefetch 命中 Tailwind fact 后正确回答。
- §2 四件套：`verify_align / scan_stubs / deep_align` 维持零；`scan_functional_stubs` ≤ 390（不增；本需求纯新增胶水代码，不消除已有 stub，但也不引入新 stub —— 解析 + 落库都是真实实现，非 stub）。

---

### R-AGENT-017: 让 agent 知道自己有 memory 维护职责（自我学习闭环：能存能用能改）
**来源**: 无 Python 直接上游对应；Python Hermes 通过 `MemoryProvider.sync_turn` 把 fact 维护委托给云端插件（Mem0/Hindsight 自带 dedup/conflict 解决），所以上游 agent 角色定位是"消费者"。Android 侧没有云端插件托底，本地写入路径已建（R-AGENT-013/016 自动落库 + `create_memory` 工具），但 `SystemPromptConfig.kt` 当前 `MEMORY USAGE GUIDANCE` 段把 agent 角色定位成纯"查询者 + persistent_instruction setter"——L60 英文 `"automatically updated by a background system after each conversation turn — you do not need to save memories manually"` / L86 中文「记忆库会在每轮对话结束后由后台系统自动提取和更新，无需你手动保存」**显式劝退** agent 去主动维护 fact。结果：即使 `update_memory` / `delete_memory` / `link_memories` 工具早已注册（`SystemToolPromptsInternal.kt:485/500/507`），agent 几乎不会主动用——读写齿轮在工具层咬合，但在 prompt 引导层断裂。

**背景**: 用户 2026-06-08 反馈："还是需要让 agent 知道一下，它们自己有了自我学习能力，这样它才会调整已经有的记忆库节点"。R-AGENT-015 + 016 解决了"能存能用"（读取齿轮 + 自动写入齿轮），但**没解决"能改"**——当新对话产生的事实与旧 fact 矛盾时（e.g. 用户前后说"我用 Tailwind" → "我换 Bootstrap 了"），agent 当前会**叠加**两条 fact 进库（甚至两条都 prefetch 进 `<memory-context>` 互相打架），不会去 update / 标 contradicts / 删旧的。这一步补上"维护齿轮"，让自我学习闭环真正成环。

**架构合规**:
- 不改 R-AGENT-016 抽取流程、不改 MemoryRepository API、不改任何工具实现——只动**两段 prompt 文本**（`SystemPromptConfig.GATEWAY_AWARENESS_EN` / `GATEWAY_AWARENESS_CN` 的 `MEMORY USAGE GUIDANCE` 区块）。
- **关键约束（呼应用户决策）**: 告诉 agent "能力 + 责任"，不告诉 agent "机制"。
  - ✅ 该说："你的记忆库里旧 fact 可能过时或与新信息矛盾，你应当主动用 `update_memory` 修正 / 用 `link_memories` 建 `contradicts` 关系 / 用 `delete_memory` 删过时的"
  - ❌ 不该说："系统会自动从你的 bullet 抽 fact"（R-AGENT-016 机制泄漏 → prompt 污染：agent 会故意多输出 bullet 想"教育"系统 / 回避具体表述怕被错抽 / 把自己的幻觉当 fact 递归引用）
  - ❌ 不该说："auto-extracted 的 fact 可能不准"（会让 agent 预先质疑所有自动抽出来的内容，对正常 fact 也举棋不定）
- 老句子"automatically updated by a background system ... you do not need to save memories manually" / 中文「由后台系统自动提取和更新，无需你手动保存」**必须删/替换**——这是堵路的元凶。改成中性的"日常 fact 抽取由后台兜底，但**矛盾发现 + 一致性维护是你的职责**"。

**行为**:
- 在 `SystemPromptConfig.GATEWAY_AWARENESS_EN`（约 L58-66 `MEMORY USAGE GUIDANCE:` 区块）和 `GATEWAY_AWARENESS_CN`（约 L83-91「记忆库使用指导：」区块）各追加 2 条 bullet（删/替换老的"无需手动保存"那一条）：
  1. **矛盾发现职责**（EN / CN 各一句）：
     - EN: `- When you notice the memory library contains an outdated fact, or a fact that contradicts what the user just told you, take action: use update_memory to correct the old entry, or link_memories with link_type="contradicts" to flag the disagreement, or delete_memory if it is obsolete. Do NOT silently add a new memory while leaving a wrong old one in place — that creates conflicting facts in future prefetches.`
     - CN: `- 当你发现记忆库里的旧 fact 已过时，或与用户刚说的话矛盾时，请采取行动：用 update_memory 修正旧记录、或用 link_memories 以 link_type="contradicts" 标记冲突、或用 delete_memory 删掉过时的。不要默默写一条新记忆而留着旧的错的——那会让将来 prefetch 时同时拿到两条互相矛盾的事实。`
  2. **资产 / 责任语义**（EN / CN 各一句）：
     - EN: `- The memory library is your long-term asset — keeping it accurate is part of your job, not a separate system's. Routine fact extraction runs in the background, but conflict resolution and consistency maintenance belong to you.`
     - CN: `- 记忆库是你的长期资产——保持它准确是你的职责的一部分，不是另一个系统的事。日常 fact 抽取由后台兜底，但矛盾解决和一致性维护归你管。`
- 老句子的处理：
  - EN L60 `"The memory library is automatically updated by a background system after each conversation turn — you do not need to save memories manually. But proactively query when it would help you answer better."` → 改成 `"Routine fact extraction runs in the background after each turn, but proactively query when it helps answer better, and proactively maintain (update/delete/link) when you notice stale or conflicting entries."`
  - CN L86「记忆库会在每轮对话结束后由后台系统自动提取和更新，无需你手动保存。但如果记忆查询能帮助你更好地回答，就主动查询。」→ 改成「日常 fact 抽取由后台在每轮结束后兜底；但如果查询能帮你更好回答就主动查，发现过时或矛盾的条目就主动维护（update / delete / link）。」
- **不动**的：`MEMORY USAGE GUIDANCE` 段头本身、persistent_instruction 那条 bullet（R-AGENT-009/245）、AUTO-SUMMARY MEMORIES 那条 bullet、query_memory 关键词搜索那条 bullet、WORKSPACE MEMORY FILES 段。

**验收**:
- `SystemPromptConfig.kt` 英文常量 `GATEWAY_AWARENESS_EN` 同时包含以下关键字面字符串（源码字符串扫描守 wiring）：
  - `update_memory` + `link_memories` + `delete_memory` 三个工具名同时出现在 `MEMORY USAGE GUIDANCE` 段附近
  - `contradicts` 字面值（鼓励 link_type="contradicts" 的 hint）
  - `conflict resolution` 或 `consistency maintenance` 任一字面值（"维护责任"语义）
  - **不再包含** `"you do not need to save memories manually"` 这句（堵路的老句子已删）
- `SystemPromptConfig.kt` 中文常量 `GATEWAY_AWARENESS_CN` 同时包含：
  - `update_memory` + `link_memories` + `delete_memory` 三个工具名
  - `contradicts` 字面值（中英 link_type 字面值保留英文）
  - `矛盾` + （`维护` 或 `职责`）任一字面值
  - **不再包含**「无需你手动保存」这句
- 既有测试类不破坏：`MessageCoordinationDelegateAutoSummaryMemoryWiringTest` / `MessageCoordinationDelegateSummaryStripWiringTest` / `MessageCoordinationDelegateFactExtractionWiringTest` / `EnhancedAIServiceMemoryContextInjectionWiringTest` / `PersistentInstructionAgentHintTest` / `QueryMemoryToolPromptsTagsWiringTest` 全部维持绿——本需求只动 prompt 文本，不改任何工具/数据/coordination 逻辑。
- **手测验收**: APP 安装后，在 chat A 说"我喜欢 Tailwind CSS"，触发 auto-summary 让 R-AGENT-016 抽 fact 落库；新建 chat B 说"我从 Tailwind 换到 Bootstrap 了"，观察 agent 是否主动调 `query_memory` 找出旧 Tailwind fact → 用 `update_memory` 改成 Bootstrap（或 `link_memories` 标 contradicts）。如果 agent 默默写一条新 Bootstrap fact 而不动旧的，prompt 调整失败（手测兜底，运行时正确性靠模型）。
- §2 四件套：`verify_align / scan_stubs / deep_align` 维持零；`scan_functional_stubs` ≤ 390（本需求 0 行代码改动，仅改 prompt 字符串）。

---

### R-AGENT-029: 启动时一次性清理旧库 `#auto_summary_id:*` 孤儿 tag
**来源**: 无 Python 上游对应（Python Hermes 通过 `MemoryProvider.sync_turn` 把 fact 维护委托给云端插件 Mem0 / Hindsight，本地不存 fact↔父摘要的串联 tag）。本需求由用户 2026-06-13 明确提出："`#auto_summary_id:xxx` 我觉得需要关停这些节点的生成"。R-AGENT-027（同日 working tree）已堵住生产源（`extractAndPersistFacts` 不再写该 tag），但历史 APK（含 `app-release-r026-aikeep-ba814a70.apk` MD5 `ba814a70db7e5c58605f62dfb5f48f16`）装机的 ObjectBox 仍有残留 + R-AGENT-026 keepDecision=false 路径产生的 `#auto_summary_id:-1` 孤儿，需一次性迁移清理。

**背景**: `#auto_summary_id:<parentId>` 是 R-AGENT-016 设计冗余——原意"反查 fact 来自哪条父摘要"，但**全代码库无任何读取方**（`grep findMemoriesByTag\("#auto_summary_id:"` 0 命中），属"写而不读"的死 tag。R-AGENT-026 keepDecision=false 路径还会产生 parentId=-1 的孤儿 tag 进一步污染。R-AGENT-027 已删除写入，本条补上历史数据清理。

**架构合规**:
- 不动 R-AGENT-016/027 的抽取流程；不动 Python 上游对齐基线（本来就没这个概念）。
- 新增 `MemoryRepository` 两个仓储 API（按 prefix 查 tag + 按 prefix 清孤儿 tag），不破坏既有 tag 读写路径。
- 启动钩子放在 `OperitApplication.onCreate`，仿 `launchCleanOnExitCleanup()` 范式（异步 IO + 失败容忍 + 不阻塞主线程）。
- SharedPreferences 防重入键，幂等无副作用——失败不写完成标记，下次启动重试。

**行为**:
- 新增仓储 API（`MemoryRepository.kt`，per-profile 实例方法）：
  - `suspend fun findTagsByNamePrefix(prefix: String): List<MemoryTag>` —— 用 ObjectBox `tagBox.query().startsWith(MemoryTag_.name, prefix, CASE_SENSITIVE).build().find()`
  - `suspend fun cleanupOrphanTagsByPrefix(prefix: String): Int` —— 在 `store.runInTx { }` 单事务内：找出匹配前缀的 tag → 对每个 tag 遍历其 `tag.memories`（@Backlink）→ 从 `memory.tags` ToMany 移除并 `memoryBox.put(memory)` → 最后 `tagBox.remove(tag)`。返回清掉的 tag 行数。
- 新增 Application 钩子（`OperitApplication.kt::onCreate`）：
  - 在 `launchCleanOnExitCleanup()` 后追加 `launchOrphanTagMigrationsIfNeeded()`，`applicationScope.launch` + try/catch 包围。
  - SharedPreferences 名 `"hermes_data_migrations"`，key `R_AGENT_029_auto_summary_id_orphan_cleanup_done`。已完成 → return。
  - 通过 `preferencesManager.profileListFlow.first()` 拿所有 profileId（默认 + 用户自建）；每个 profile 实例化 `MemoryRepository(applicationContext, profileId)` 调 `cleanupOrphanTagsByPrefix("#auto_summary_id:")`。
  - 全部 profile 成功才写完成标记；任一失败 `AppLogger.w` 记日志、不写标记，下次启动重试。
- **多 profile 必须遍历**: `MemoryRepository` 是 per-profile（每个 profile 独立 ObjectBox dbName `objectbox_<id>`），不能只清 active profile。
- **删 tag 实体顺序**: `MemoryTag.memories` 是 `@Backlink(to = "tags")` 反向虚拟关系——删 MemoryTag 行不会自动反向解 Memory 的 ToMany；必须先解 ToMany + put memory，再 remove tag 实体，避免 Memory 的 tags ToMany 留下指向死 id 的"幽灵关系"。

**验收**:
- `MemoryRepository.kt` 必须新增 `findTagsByNamePrefix` + `cleanupOrphanTagsByPrefix` 两个 suspend 函数；签名形态如上。
- `OperitApplication.kt::onCreate` 必须含 `launchOrphanTagMigrationsIfNeeded()` 调用；该方法必须用 SharedPreferences `"hermes_data_migrations"` 防重入；必须遍历 `preferencesManager.profileListFlow.first()`。
- 单元测试覆盖（source-scan + 行为）：tag 实体被清；Memory.tags ToMany 不再含被清 tag；Memory 本身保留；其它 tag（`#auto_extracted` / `#chat:*`）不受影响；prefix 不匹配的 tag（`#auto_summary` 父节点本身）不被误清；幂等（连跑两次第二次返回 0）；空库跑返回 0。
- §2 四件套：`verify_align / scan_stubs / deep_align` 维持零；`scan_functional_stubs` ≤ 当前基线 390（本需求纯新增能力，不消除已有 stub，但也不引入新 stub）。本改动**只动 app/ 模块，不动 hermes-android/**——四件套指标理论上应该不变。
- **手测验收**: 在装了旧 APK 残留数据的设备上更新到含 R-AGENT-029 的 APK，启动一次后用 `adb logcat` 检查 `R-AGENT-029: migration done` 行；MemoryScreen 打开任一 fact 节点 EditMemoryDialog 的 tag 列表里不再展示 `#auto_summary_id:*` tag。

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

### R-GW-008: Telegram 入站语音 / 音频真下载 + STT 自动转写
**来源**:
- 用户 2026-06-14 直接指令链：① "能增加飞书处理音频和图片能力？" → ② 三方案对比（飞书 vs Telegram）后用户选 "Telegram 方案 a 第一步"（更划算的试点平台 + STT 可复用资产）→ ③ AskUserQuestion 提交 4 个决策点，用户选 "OpenAI Whisper 起步 / Telegram 入站自动转写 / mediaUrls 本地路径 / 源码扫描+Robolectric+E2E" → ④ 第二轮 AskUserQuestion 收敛"本轮只动 audio/voice STT，图片下次 R"
- Python 上游 `reference/hermes-agent/gateway/platforms/telegram.py:2641-2660`（`_handle_media_message` 中 voice/audio 分支：`get_file()` → `download_as_bytearray()` → `cache_audio_from_bytes`）+ `gateway/run.py:8181-8263`（`_enrich_message_with_transcription`：调 `transcribe_audio` → 拼 `[The user sent a voice message~ Here's what they said: "<transcript>"]` 前置到 user message）
- R-AGENT-032（同时立）提供 STT 工具基础设施
- 当前 Kotlin `Telegram.kt:283-321` voice/audio 分支只塞 file_id 到 `mediaUrls`，**不下载、不转写**——agent 拿到的只是占位符，等于没听到

**背景**:
- Telegram Bot API 鉴权简单（bot token 在 URL 里），下载分两步：(1) `GET https://api.telegram.org/bot<TOKEN>/getFile?file_id=<id>` 拿到 `result.file_path`；(2) `GET https://api.telegram.org/file/bot<TOKEN>/<file_path>` 拿 bytes。`TelegramAdapter._httpClient`（`Telegram.kt:89-93`）已配 timeout，可直接复用；`TelegramNetworkClient.getFileUrl` 当前有 bug（写成调 `getUpdates`，`TelegramNetwork.kt:158-164`），不复用。
- Python 上游对 voice 强制 `.ogg`、对 audio 强制 `.mp3`（`telegram.py:2641-2660`）。Kotlin 1:1 翻译保留这套 ext 选择。
- 缓存路径：本项目把 `hermesHome`（长期数据，`filesDir/.hermes`）和 `cacheDir/media/<type>`（短期媒体）分开。Telegram 私有缓存复用既有 `TelegramAdapter._fileCacheDir = File(context.cacheDir, "telegram_files")` 模式，新增 audio 子目录：`File(context.cacheDir, "media/telegram/audio/")`（用户决策："Telegram 专属缓存路径分层"）。
- voice/audio 入站后**自动调 STT**（用户决策："Telegram 入站自动转写"，对齐 Python `gateway/run.py:_enrich_message_with_transcription` 行为），不暴露独立 transcribeAudio 工具给 agent；图片处理不在本 R 范围（用户决策："本轮只动 audio/voice STT，图片下次 R"）。
- **MVP scope（按用户决策）**：本 R 只动 voice / audio 两个分支的下载 + STT 接线；photo / document / video / sticker 维持现状（继续塞 file_id），后续在新 R 中处理。

**架构合规**:
- 复用 `TelegramAdapter._httpClient`，不新建 OkHttpClient 实例（避免新增依赖配置）
- 写盘走 `BasePlatformAdapter.cacheAudioFromBytes(context, bytes, ext)`（`Adapter.kt:666-675`），不裸 `File.writeBytes`，保持与既有平台一致；该 helper 当前路径是 `<context.cacheDir>/media/audio/`——按用户决策的"专属缓存路径分层"，新增 Telegram 子目录约定：实际落盘走 `<context.cacheDir>/media/audio/`（既有 helper），但下载临时层先写到 `File(context.cacheDir, "telegram_files")` 已有目录，转交 `cacheAudioFromBytes` 后该目录文件可清理（避免双层冗余）
- STT 调用走 R-AGENT-032 的 `TranscriptionTools.transcribeAudio(filePath, model=null)`（平台无关，对齐 Python `tools/transcription_tools.py:transcribe_audio`）
- 转写文本前置到 `event.text`，**不**经过 mediaUrls 注入到 LLM image/audio_url 部分（保持 Python 上游"STT 转写 → 文本注入"的一致性）

**行为**:
- **Telegram._handleMessage 内 voice/audio 分支重写**（`hermes-android/.../gateway/platforms/Telegram.kt`）：
  - voice 分支（`message.has("voice")`）：取 `fileId` → 调 `_downloadTelegramFile(fileId, "ogg")` 拿 `localPath` → 调 `TranscriptionTools.transcribeAudio(localPath)` → 若成功，把 `text` 改成 `"[The user sent a voice message~ Here's what they said: \"$transcript\"]\n\n${原 caption（voice 没 caption 取空）}"` → `mediaUrls = listOf(localPath)`、`mediaTypes = listOf("audio/ogg")`
  - audio 分支（`message.has("audio")`）：同上但 ext 为 `mp3`、mediaTypes 为 `audio/mpeg`、保留原 caption
  - 两分支转写失败时：`text` 改成 `"[The user sent a voice message but I had trouble transcribing it~ ($error)]\n\n${原 caption}"` → 仍下载并保留 `mediaUrls`（让用户/agent 至少能在 chat 历史里看到文件路径）
  - photo / document / video / sticker 分支**不动**（保持现有 file_id 占位行为）
- **新增 `_downloadTelegramFile(fileId: String, ext: String): String?` 私有方法**（同文件）：
  - `GET ${API_BASE}/bot${_token}/getFile?file_id=${fileId}` → 解析 `result.file_path`
  - `GET ${API_BASE}/file/bot${_token}/${file_path}` → bytes
  - 调 `BasePlatformAdapter.Companion.cacheAudioFromBytes(context, bytes, ".$ext")` → 返回本地绝对路径
  - 任一步失败 → log + 返回 null
  - 用 `_httpClient` 共用实例，不 new OkHttpClient

**验收**:
- **A. Telegram.kt voice/audio 分支已接通**：
  - voice 分支函数体含 `_downloadTelegramFile(` 调用 + `TranscriptionTools.transcribeAudio(` 引用（或等价的 `TranscriptionTools` import + 后续调用）
  - voice 分支函数体**不**含原占位字面值（fileId 直塞 mediaUrls 那种，确认改成 localPath）
  - voice 分支函数体含 `[The user sent a voice message~ Here's what they said:` 字面值前缀
  - audio 分支同上，但 ext 为 `mp3`
- **B. _downloadTelegramFile 已落盘**：
  - `Telegram.kt` 文件含 `private suspend fun _downloadTelegramFile` 或 `private fun _downloadTelegramFile` 函数声明
  - 函数体含 `getFile?file_id=` 字面值（拼接 URL）+ `/file/bot` 字面值（下载 URL）
  - 函数体调 `cacheAudioFromBytes(`（写盘）
- **C. 图片/视频/文档分支不动**（守"本轮只做语音"红线）：
  - photo 分支 `mediaUrls = listOf(...)` 仍是 `fileId`（不能误改）
  - document / video / sticker 三分支同上
- **D. §2 四件套**：`verify_align / scan_stubs / deep_align` 维持零；`scan_functional_stubs` 不增（理想状态减若干，因为 Telegram 之前对 voice/audio 是占位 stub）
- **E. 单元测试**：源码扫描（wiring 关键字面值）；行为完整性 deferred to §3 E2E + 手测（hermes-android testImpl 无 MockWebServer 依赖）。详见 TC-GW-008-a..g
- **F. 手测验收**：
  - 用户在 Telegram 给 bot 发语音 → bot 收到后 chat 内显示 `[The user sent a voice message~ Here's what they said: "..."]` 前置 + 原文转写
  - 转写失败（key 错 / 网络错）→ 显示降级文案，不崩溃
  - 用户发图片 → 维持现状（不下载、占位符）—— 守"图片下次 R"红线

### R-GW-009: Telegram 平台凭证在 app UI 与启动配置链路里接通
**来源**:
- 用户 2026-06-14 直接指令：在尝试按 R-GW-008 的"手测建议"配 Telegram bot 时报告"在 app 上找不到配置 Telegram Bot"——R-GW-008 只接通了 hermes-android 内核侧的 voice/audio 自动转写链路，但 **app/HermesGatewayCredentialsScreen + HermesGatewayPreferences + HermesGatewayConfigBuilder 三处都没有 Telegram**，用户填不进 token，启动路径也不会构造 `Platform.TELEGRAM` 的 `PlatformConfig`，结果是内核代码齐全但永远不会被实例化。
- 与 R-GW-002（Feishu）/ R-GW-003（Weixin）一一对应——这两个平台已经在 app UI 接通；R-GW-009 把同一套接线补到 Telegram。
- Python 上游对应字段（`reference/hermes-agent/gateway/run.py` 的 `platforms.telegram` config + `gateway/platforms/telegram.py` 的 `TelegramAdapter.__init__` 读取的 `bot_token` / `allowed_chat_ids`）。

**背景**:
- `HermesGatewayPreferences.kt:170-192` 当前仅声明 `PLATFORM_FEISHU` / `PLATFORM_WEIXIN` 两个平台 key，没有 `PLATFORM_TELEGRAM`。
- `HermesGatewayConfigBuilder.kt:21-33` 的 `build(appContext)` 只调 `buildFeishu` / `buildWeixin`，不会构造 telegram `PlatformConfig`。
- `HermesGatewayCredentialsScreen.kt:67-90` 只渲染 Feishu / Weixin 两张 `PlatformCredentialsCard`，没有 Telegram 卡片。
- 因此即便 `Telegram.kt` + `Run.kt#_createAdapter` 的 `Platform.TELEGRAM` 分支齐全，**用户无法在 app 内输入 token**，R-GW-008 实际上跑不起来。
- Telegram bot 默认任何人加好友 / 拉群即可触发，没有 chat 白名单 = 任何人都能消耗 OpenAI key 跑转写——`allowed_chat_ids` 是安全门，不是 nice-to-have。

**架构合规**:
- 复用 R-GW-002/003 的接线模式（Preferences key 命名 + Card 复用 + Builder 分支添加），不引入新抽象。
- 字段集只暴露 `token`（必填）+ `allowed_chat_ids`（可选，逗号分隔的 chat id 列表）；`base_url` 这次不做（用户没明确提，Python 上游 Telegram 也无 base_url 字段；后续真有自建反代需求再开新 R）。
- 不加 feature flag——直接跟 Feishu / Weixin 同级走 `HermesGatewayConfigBuilder.build`，对齐 CLAUDE.md §6"不做 feature flag"反模式。

**行为**:
- **HermesGatewayPreferences.kt**：
  - 加 `const val PLATFORM_TELEGRAM = "telegram"`
  - 加 `val TELEGRAM_FIELDS = listOf("token", "allowed_chat_ids")` 之类的字段元数据（与 Feishu/Weixin 风格一致）
  - 现有的 `savePlatformEnabled(platformKey, enabled)` / `setCredentialField` 等通用 API 自动适用，不改签名
- **HermesGatewayConfigBuilder.kt**：
  - 加 `private fun buildTelegram(appContext: Context): PlatformConfig?` 方法：
    - 检查 `isPlatformEnabled(PLATFORM_TELEGRAM)`，未启用 → 返回 null
    - 读 `getCredentialField(PLATFORM_TELEGRAM, "token")`；空 → 返回 null（缺 token 不构造 config，避免 Telegram.kt 启动失败）
    - 读 `getCredentialField(PLATFORM_TELEGRAM, "allowed_chat_ids")`，逗号分隔解析为 `List<String>`（空字符串 → 空列表）
    - 构造 `PlatformConfig(platform = Platform.TELEGRAM, token = token, extra = mapOf("allowed_chat_ids" to chatIds))` 之类（具体字段名按 `PlatformConfig` 现有 schema）
  - `build(appContext)` 主入口加调用：`buildTelegram(appContext)?.let { configs += it }`，与 `buildFeishu` / `buildWeixin` 同级
- **HermesGatewayCredentialsScreen.kt**：
  - 加第三张 `PlatformCredentialsCard`，platformKey = `PLATFORM_TELEGRAM`，字段集 = `TELEGRAM_FIELDS`
  - card 标题"Telegram"，副标题简短引导（如"配置 Bot Token，填了 allowed_chat_ids 才能限制谁能触发"）
  - `allowed_chat_ids` 留空时给非阻断 warning（不是 error）：当前实现允许空白名单，但 UI 上文字提醒"留空表示任何与 bot 对话的人都会触发"

**验收**:
- **A. Preferences 已加常量**：
  - `HermesGatewayPreferences.kt` 含 `PLATFORM_TELEGRAM` 常量声明，值为 `"telegram"`
  - 含 `TELEGRAM_FIELDS` 常量声明，至少包含 `"token"` 和 `"allowed_chat_ids"` 两个字段名
- **B. ConfigBuilder 已加分支**：
  - `HermesGatewayConfigBuilder.kt` 含 `buildTelegram(` 函数声明
  - `build(appContext)` 函数体含 `buildTelegram(` 调用
  - `buildTelegram` 函数体读 `PLATFORM_TELEGRAM` + `"token"` + `"allowed_chat_ids"` 三个字面值
- **C. UI 已加 Telegram 卡片**：
  - `HermesGatewayCredentialsScreen.kt` 含 `PLATFORM_TELEGRAM` 引用
  - 含 `TELEGRAM_FIELDS` 引用（或等价的 fields 字段集表达）
  - 含 `"Telegram"` 字面值（标题）
- **D. Feishu / Weixin 接线不变**（红线守卫）：
  - `HermesGatewayConfigBuilder.kt` 仍含 `buildFeishu(` 与 `buildWeixin(` 调用
  - `HermesGatewayCredentialsScreen.kt` 仍含 `PLATFORM_FEISHU` / `PLATFORM_WEIXIN` 引用
- **E. §2 四件套**：`verify_align / scan_stubs / deep_align / scan_functional_stubs` 维持基线（本 R 只动 app 层接线，不动 hermes-android 内核 / 不新增 Python 上游对齐文件）
- **F. 单元测试**：源码扫描守 wiring（TC-GW-009-a..d）；Compose UI 行为测 deferred 到手测（app 模块的 Compose 测试基础设施未配 ComposeTestRule）
- **G. 手测验收**：
  - 打开 app → 设置 → Hermes Gateway → 凭证页：可见 Telegram 卡片，能填 token + allowed_chat_ids
  - 启用 Telegram 后启动 gateway → logcat `HermesGatewayController` / `Run` 日志含 Telegram adapter 启动行
  - token 留空 → buildTelegram 返回 null，gateway 不启动 telegram，无崩溃
  - 配好 token + 空 chat_ids → 给 bot 发语音，agent 拿到 R-GW-008 定义的 bilingual 前缀

### R-GW-010: [DELETED 2026-06-14]
原 R-GW-010「Telegram bot HTTP/SOCKS proxy support」已被 commit `e202d87e` revert（用户 2026-06-14 撤回该需求）；按 §0.1 ID 不可回收规则保留占位。

### R-GW-011: Gateway 处理消息时向用户发送 typing 提示（接通 _keepTyping 进 message lifecycle）
**来源**:
- 用户 2026-06-14 直接报告："但我发现bot，在处理信息的时候没有任何提示，正在处理等等让用户知道是正常运作工作中"——bot 收到消息后到 agent 回复前的 N 秒（甚至几十秒，含 LLM 思考 + 工具调用）chat 内完全静默，用户无从判断 bot 是死了还是在工作。
- Python 上游 `reference/hermes-agent/gateway/platforms/base.py:1121-1136`（`send_typing` / `stop_typing` abstract，默认 no-op）+ `:1431-1475`（`_keep_typing` 后台循环每 2s 刷一次，含 `_typing_paused` 跳过 + `stop_event` 中断 + `finally: stop_typing`）+ `:1812-1826`（lifecycle：`typing_task = asyncio.create_task(_keep_typing(...))` 包住 `on_processing_start` + handler，`finally: typing_task.cancel()`）。
- Telegram 平台 override：`reference/hermes-agent/gateway/platforms/telegram.py:1969-1997`（`send_typing` 调 `bot.send_chat_action(chat_id, action="typing", message_thread_id=...)`，5s 服务端过期）。

**背景**:
- `Base.kt:313` 已声明 `open suspend fun sendTyping(chatId, metadata)`（默认 no-op）。
- `Telegram.kt:662-687` **已正确 override** `sendTyping`：POST `https://api.telegram.org/bot<token>/sendChatAction` body=`{"chat_id":..., "action":"typing"}`，与上游 `bot.send_chat_action` 等价。
- `Base.kt:942-955` 已实现 `BasePlatformAdapter._keepTyping(chatId, intervalMs=2000)` 扩展函数：`while (Job.isActive) { sendTyping(); delay(intervalMs) }`，`finally { stopTyping(chatId) }` 自清理。
- `Run.kt:349-352` 已调 `adapter?.onProcessingStart(event)`；`Run.kt:458-463` 已调 `adapter?.onProcessingComplete(...)`。
- **缺口**：`Run.kt:357-375`（主入口 `runner(...)`）+ `Run.kt:492-503`（pending-event 循环 `runner(...)`）两处 agent loop 调用**都没用 `coroutineScope { launch { _keepTyping } }` 包住** —— 等同 Python 上游的 lifecycle 缺了 `typing_task = asyncio.create_task(_keep_typing(...))` + `finally: typing_task.cancel()`。结果：infrastructure（sendTyping override + _keepTyping 循环 + 钩子点）**已就位 80%**，差最后一条接线。

**架构合规**:
- 复用既有抽象：`sendTyping` 是 `BasePlatformAdapter` open member（已有），`_keepTyping` 是 `BasePlatformAdapter` 扩展函数（已有），`onProcessingStart` / `onProcessingComplete` 钩子（已有）。**不引入新接口、新类、新文件**。
- 只在 `Run.kt._handleMessage` 两处 `runner(...)` 调用周围加 `coroutineScope { val typingJob = launch { adapter._keepTyping(chatId) }; try { runner(...) } finally { typingJob.cancel(); typingJob.join() } }`。
- Feishu / Weixin 不重写 `sendTyping`（默认 no-op）= 与 Python 上游一致（`feishu.py` 显式注释 "API doesn't support typing indicator"；`weixin.py` 用复杂的 typing ticket 单独路径，本轮**不**做对齐）。Kotlin 侧 `Feishu.kt:1216-1218` 已有显式 no-op `override suspend fun sendTyping(...) { /* Feishu doesn't have a typing indicator API */ }`——这是 Python 注释的 Kotlin 落地，是正确的；本轮不动。
- 不做 feature flag、不做兜底——直接接通；`adapter == null`（不在 platform 路径上）时，跳过 `_keepTyping` launch（与 `onProcessingStart` 同条件）。
- **不动** `onProcessingStart` / `onProcessingComplete` 钩子的 base 实现（这是 R-GW-012 范围，当前轮次不做）。

**行为**:
- **Run.kt._handleMessage 主入口**（line 354-375 改造）：
  - 现状：`adapter?.onProcessingStart(event)` → `runner(...)` → 处理 INTERRUPTED_SENTINEL → 走 delivery → `adapter?.onProcessingComplete(...)`。
  - 改造：把 `runner(...)` 调用包到 `coroutineScope { val typingJob = launch { adapter._keepTyping(event.source.chatId) }; try { responseText = runner(...) } finally { typingJob.cancel(); typingJob.join() } }`。
  - `adapter` 为 null 时不 launch `_keepTyping`（与 `onProcessingStart` 同条件守护）。
  - try/catch 包 runner 抛错的逻辑保留（agentOk = false 路径不变）。
- **Run.kt._handleMessage pending-event 循环**（line 492-503 改造）：
  - 同样的 `coroutineScope { launch _keepTyping(pendingEvent.source.chatId) ... finally cancel }` 包住 pending 的 `runner(...)`。
  - chatId 用 `pendingEvent.source.chatId`（pending event 的 chat id，不是初始 event 的）。
- **Base.kt / Telegram.kt 不动**（已就位）。
- **Feishu.kt / Weixin.kt / 其他平台不动**：无 override 时调到 base 默认 no-op `sendTyping`，`_keepTyping` 也仅消耗 ~0 IO（每 2s no-op call），不影响别的平台。

**验收**:
- **A. Run.kt 主入口 runner 调用被 _keepTyping 包住**：
  - `Run.kt` 中 `_handleMessage` 函数内、`onProcessingStart` 之后、`runner(event.text,` 之前必须出现 `_keepTyping(` 调用（任意形式，带或不带前缀）。
  - 同一函数体内出现 `coroutineScope` 字面值 + `typingJob` 字面值（变量名）+ `cancel()` 字面值，证明 launch+cancel 配对。
- **B. Run.kt pending-event 循环 runner 调用同样被包住**：
  - line ~485 后的 pending-event 处理段含第二次 `_keepTyping(` 调用（针对 `pendingEvent.source.chatId`）。
- **C. Telegram.kt sendTyping override 不被误改（红线）**：
  - `Telegram.kt` 仍含 `override suspend fun sendTyping(chatId: String, metadata: JSONObject?)` 函数声明，函数体仍含字面值 `sendChatAction` + `"typing"`。
- **D. Base.kt _keepTyping 扩展不被误改（红线）**：
  - `Base.kt` 仍含 `suspend fun BasePlatformAdapter._keepTyping(` 声明；函数体仍含 `sendTyping(` 调用 + `delay(intervalMs)` + `finally` + `stopTyping(`。
- **E. §2 四件套**：`verify_align / scan_stubs / deep_align / scan_functional_stubs` 维持基线（本 R 不动 hermes-android Python 对齐文件，仅在 `Run.kt` 的两个 `runner(...)` 调用点加 5–10 行 wrap 代码）。
- **F. 单元测试**（`*Test.kt`）：源码扫描守 wiring（TC-GW-011-a..e）。运行时行为（"消息发后 2s 内 chat 出现 typing 提示"）由 §3 E2E + 手测兜底（hermes-android testImpl 无 MockWebServer，`sendChatAction` HTTP 行为完整性由真 Telegram bot E2E 验证）。
- **G. 手测验收**：
  - 配好 Telegram bot + 启用 gateway → 给 bot 发条会让 agent 思考 ≥3 秒的消息（如"想个长点的笑话"）。
  - chat 内应在 2 秒内看到 "正在输入…" / "is typing…" 提示（Telegram client 标准展现），并持续刷新直到 agent 回复。
  - agent 回复落地后，typing 提示在 5 秒内自动消失（Telegram 服务端 5s 过期 + `_keepTyping` finally `stopTyping` 双重保险）。
  - 多轮对话不残留、不串号；同一 chat 中断后再发新消息也能重新出现 typing。
  - Feishu / Weixin / 其他平台**不**出现任何"奇怪的输入提示"（默认 no-op，无副作用）。

### R-GW-013: Telegram 出站对齐 Python 的 retry_after / Markdown fallback / 长消息分段
**来源**:
- 用户 2026-06-15 直接报告："gateway 有时候飞书和 telegram 无法收到结果"——用户视角偶发"agent 处理完了但 chat 没收到回复"。
- Python 上游 `reference/hermes-agent/gateway/platforms/telegram.py:1023-1106` 的 `send` 单 chunk 内部循环 3 次重试 + 显式抓 `RetryAfter` / `"retry after"` 字符串、读 `retry_after` 字段按服务端要求 sleep + `BadRequest` 内分流（thread_not_found / reply_target_deleted 自愈）+ Markdown 解析失败 → 自动 strip 重发 plain text + `TimedOut` 视为可能已送达不重试（防重复）+ 长消息走 `truncate_message` 多 chunk + chunk 后缀提示。
- Kotlin 现状 `Telegram.kt:552-591` 仅 1 次 HTTP POST，HTTP 不 200 直接 `SendResult(success=false, error="HTTP ${resp.code}")`：429（flood control）不读 `Retry-After`、Markdown parse 失败无 plain-text fallback、>4096 字符走 `take(MAX_MESSAGE_LENGTH)` 静默截断、reply_to_message_not_found 不退到 direct send。
- 真正丢消息的 Top-2 路径：(1) 群活跃→Telegram 429 + `retry_after: 10` → Run.kt 外层只 sleep 2s → 第二次仍 429 → 最终失败；(2) agent 输出含未配对 `_`/`*`/`[`（特别是 LLM 思考输出穿插代码块时）→ HTTP 400 "can't parse entities" → Run.kt 重试同样的 Markdown → 又 400。

**背景**:
- Python 上游 `_RETRYABLE_ERROR_PATTERNS`（`base.py:832-842`）+ `_send_with_retry`（`base.py:1565-1665`）+ telegram.py 内部三段式策略是 Python 侧统一的网络错误处理框架。Kotlin 把这层降级成 Run.kt 外层裸 1 次 retry，每个平台 adapter 内部又各自实现部分策略——结果是 retry 时机点（外层 2s 固定 vs 内层指数 + jitter）不对齐、错误归类（retryable / timeout / format）缺失。
- 本 R **只在 Telegram 适配器内**对齐 Python `telegram.py:1023-1106` 的 send 内部 retry 策略；Run.kt 外层那 1 次 retry 暂保留（属于 R-GW-001 的"简化版" `_send_with_retry`），未来另立 R-GW 把 base.py 的 `_send_with_retry` 整套搬过来时再统一。
- Markdown fallback 用 `parse_mode=null` + plain-text 内容（剥离 `_*[]()` 等需要转义的字符）；不引入 MarkdownV2 严格转义，对齐当前 Python `_strip_mdv2`。
- 长消息切分（>4096）按 Telegram 服务端硬限切成多 chunk，每 chunk 末尾加 ` (1/N)` 提示，对齐 Python `truncate_message` 行为。本 R **只**做切分循环，不做"先切到 \n / 段落边界"的智能切分（Python 上游也只做基础切）。
- `TimedOut` 在 Kotlin 层用 OkHttp 的 `SocketTimeoutException` 等价物识别，不重试（防重复发）。
- HTTP 429 的 `retry_after` 来源：Telegram Bot API 把它放在 response body JSON `{"ok":false, "error_code":429, "description":"...", "parameters":{"retry_after":10}}`，**不在** HTTP `Retry-After` header 里。本 R 解析 response body 的 `parameters.retry_after`。

**架构合规**:
- 复用既有 `TelegramAdapter._httpClient`，不新建 OkHttpClient 实例。
- 不动 `Base.kt` / 其他平台 / `Run.kt`：本 R 只改 `Telegram.kt::send`，把单次 POST 升级为 3 次内部重试 + Markdown fallback + 长消息切分。
- 不引入 feature flag / 配置开关——直接行为升级；现有调用点（`Run.kt::_handleMessage` / `_sendBusyAck` / 任意 controller 入口）无须改动，行为透明。
- 不改 `SendResult` 接口——内部 retry 失败时仍返回 `SendResult(success=false, error=...)`；Run.kt 的外层 1 次 retry 保留作最终保险。

**行为**:
- **Telegram.kt::send 单 chunk 重试循环**（line 552-591 改造）：
  - for `attempt in 0 until 3`：
    1. 构造 payload（含 `parse_mode=Markdown` if `_parseMarkdown`），POST `/sendMessage`。
    2. **HTTP 200 + `ok:true`** → 成功，返回 `SendResult(success=true, messageId=...)`。
    3. **HTTP 200 + `ok:false`**：检查 `description`：
       - 含 `"can't parse"` / `"can not parse"` / `"parse_entities"` / `"parse error"` → Markdown fallback：去掉 `parse_mode`、`text` 用 `_stripMarkdownToPlain(content)`、retry 同 attempt（不 break），continue 一次特殊 retry。
       - 含 `"message thread not found"` 且 metadata 有 thread_id → 清掉 thread，continue retry。
       - 含 `"replied message not found"` 且 replyTo 非 null → 清掉 replyTo，continue retry。
       - 其他 → 返回 `SendResult(success=false, error=description)`，**不**重试（永久错误）。
    4. **HTTP 429**：解析 response body `parameters.retry_after`（默认 1.0 秒），`if attempt < 2: delay(retry_after_ms); continue`，否则返回失败。
    5. **HTTP 5xx 或 IO/SocketTimeoutException**：
       - `SocketTimeoutException` → 直接返回失败（不重试，防重复发，对齐 Python `TimedOut`）。
       - 其他网络错误：`if attempt < 2: delay(2 ** attempt seconds); continue`，否则返回失败。
    6. **HTTP 4xx 其他**：返回失败，不重试。
- **Telegram.kt::send 长消息切分**：
  - `_splitForTelegram(content)` helper：按 `MAX_MESSAGE_LENGTH = 4096`（Telegram bot API 硬限）切成多段；段数 N≥2 时每段末尾追加 ` (k/N)`（k 从 1 起）；N=1 时不加。
  - send 顶层把 `content` 切成 chunks，for chunk in chunks 各跑上面的 3 次内部重试。某 chunk 失败 → 直接返回失败（不再继续后续 chunks，等同 Python 上游 raise）。
- **`_stripMarkdownToPlain` helper**（Telegram 私有版本，对齐 Python `_strip_mdv2`）：
  - 移除 markdown emphasis / link / code 字符：`_` `*` `~` `\`` `[` `]` `(` `)` `>` `#`（保留普通文字与换行）。
  - 用作 `parse_mode=null` 的 fallback text。

**验收**:
- **A. Telegram.kt::send 函数体含 3 次重试循环**：
  - `Telegram.kt::send` 函数体含 `for` 循环字面值 + `attempt` 变量 + 至少出现 3 次的 attempt-bounded 字面值（`< 3` 或 `until 3`）。
  - 含 `retry_after` 字面值（解析 Telegram 限流字段）+ `parameters` 字面值（response body 路径）。
  - 含 `429` 字面值 + `parse` / `markdown` / `parse_entities` 字面值（Markdown fallback 检测）。
  - 含 `message thread not found` 或 `thread_not_found` 字面值（thread 自愈）+ `replied message not found` 字面值（reply 自愈）。
- **B. Telegram.kt 含 _stripMarkdownToPlain helper 函数声明**：
  - `private fun _stripMarkdownToPlain(` 函数声明出现一次。
- **C. Telegram.kt::send 不再用 `take(MAX_MESSAGE_LENGTH)` 静默截断**：
  - 函数体中**不**含 `content.take(MAX_MESSAGE_LENGTH)` 字面值（红线，防回归）。
  - 长消息切分使用 `_splitForTelegram` helper（含函数声明）+ 在多 chunk 时追加 ` (1/N)` 标记字面值。
  - `MAX_MESSAGE_LENGTH` 常量值仍为 `4096`（Telegram 服务端硬限，不动）。
- **D. SocketTimeoutException 不重试（红线）**：
  - send 函数体含 `SocketTimeoutException` 字面值，且**不**走 retry 循环（直接 return 失败）；防止 timeout 后重复发送同消息导致 chat 出现两条相同回复。
- **E. §2 四件套**：`verify_align / scan_stubs / deep_align / scan_functional_stubs` 维持基线（本 R 增的 `_stripMarkdownToPlain` / `_splitForTelegram` 是 Python `_strip_mdv2` / `truncate_message` 的等价 Kotlin helper，属于 platform difference 允许的本地等价物，不引入新 stub）。
- **F. 单元测试**（`*Test.kt`）：源码扫描守 wiring（TC-GW-013-a..c）。运行时行为（"群活跃时不丢消息"）由 §3 E2E + 手测兜底（hermes-android testImpl 无 MockWebServer 依赖）。
- **G. 手测验收**：
  - 配好 Telegram bot + 启用 gateway，在群里给 bot 连发 ≥10 条 trigger 消息（触发 Telegram flood control）。
  - 每条消息的回复都应送达 chat（即使有些消息间隔 10s+ 因 retry_after），无任何静默丢消息。
  - 让 agent 输出含 ` ``` ` / `_` / `*` 的 markdown 内容（如代码块），chat 应能收到（Markdown 渲染或 plain text fallback 都算成功），不能因 `can't parse entities` 丢消息。
  - 让 agent 输出 ≥5000 字符长回复，chat 内应看到 ` (1/N)` ... ` (N/N)` 多段消息，不能因 `take()` 静默截断。
  - 真机 Telegram chat 不出现两条完全相同的回复（timeout 防重复有效）。

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

### R-AGENT-030: Agent system prompt 注入「应用自我感知」段（工具箱 / Settings / Memory hub 等 UI 入口）
**来源**: 无 Python 上游对应（Python Hermes 是无 GUI 的 server-side agent，不存在"宿主 app 内置 UI 入口"概念）。本需求由用户 2026-06-13 提出："agent 他们知道 app 有一个叫工具箱的地方吗？" —— 当前 `SystemPromptConfig.kt` 注入的 `GATEWAY_AWARENESS_SECTION` 只覆盖飞书 / 微信 gateway 多人会话场景，全文 0 处提及"工具箱 / Toolbox / Settings / MemoryScreen / SkillRecorder / Hermes Settings hub"等用户实际能进入的 UI 子屏，agent 在被用户问"打开工具箱"或"我去哪里管理记忆"时只能瞎猜。

**背景**: HermesApp 的 NavItem 主目录（`ui/common/NavItem.kt`）登记了 21 个一级入口（`ai_chat / shizuku_commands / assistant_config / settings / tool_permissions / user_preferences_guide / user_preferences_settings / chat_history_settings / packages / memory_base / terminal / toolbox / about / mcp / agreement / help / token_config / workflow / model_config / feedback / hermes_settings`），其中 `toolbox` 子屏（`ui/features/toolbox/screens/ToolboxScreen.kt`）下挂 18 个具名子工具（`tool_test_center / tool_file_manager / tool_tts / tool_speech_recognition / tool_permission_manager / tool_user_agreement / tool_default_assistant_guide / tool_terminal / tool_ui_debugger / tool_shell_executor / tool_log_viewer / tool_sql_viewer / token_config / tool_process_limit_remover / tool_html_packager / tool_autoglm_one_click / tool_autoglm_tool / tool_skill_recorder`）。这些是**用户视角**的功能入口，与 agent 工具集（`AIToolHandler` 注册的 `read_file / execute_shell / use_package / sleep ...`）不在同一层；agent 应该知道用户可以"自己去工具箱里跑 UI 调试器 / 终端 / SQL viewer / 技能录制器"，从而能在"用户问怎么操作"时给出导航式回答而非自己代劳。

**架构合规**:
- 仿 `GATEWAY_AWARENESS_EN/CN` 既有范式新增 `APP_SELF_AWARENESS_EN/CN` 常量 + `APP_SELF_AWARENESS_SECTION` 占位符，**不破坏**现有 9 段 system prompt 模板顺序——插在 `GATEWAY_AWARENESS_SECTION` 之后、`TOOL_USAGE_GUIDELINES_SECTION` 之前。
- 仅注入到 `SYSTEM_PROMPT_TEMPLATE` / `SYSTEM_PROMPT_TEMPLATE_CN`（主 agent）；**不**注入到 `SUBTASK_AGENT_PROMPT_TEMPLATE`（子任务 agent 不需要 UI 自我感知，与 GATEWAY_AWARENESS 同处理）。
- `getSystemPrompt()` 的 replace 链按既有"先 GATEWAY、再 SCHEDULED_TASK、再 PERSISTENT_INSTRUCTIONS"顺序追加 `APP_SELF_AWARENESS_SECTION` 替换。
- 段内仅描述功能定位与导航语义（"如果用户想看记忆库，引导他打开 Memory hub"），**不**列举具体路由 key / Compose 函数名（避免 UI 重构时反复改 prompt）。
- 中英两版**完全等价**翻译，避免 i18n drift（与 `GATEWAY_AWARENESS_EN/CN` 同标准）。

**行为**:
- 新增 `APP_SELF_AWARENESS_EN`（英文段）和 `APP_SELF_AWARENESS_CN`（中文段）两个 const val，紧邻 `GATEWAY_AWARENESS_EN/CN` 声明。
- 段内最少必含的核心导航点（用户视角，非工具白名单）：
  - **Toolbox**: 用户进入 app 主菜单 → "工具箱 / Toolbox" 入口可访问 文件管理 / 终端 / UI 调试器 / Shell 执行器 / 日志查看 / SQL viewer / 技能录制器 / 进程限制清除 / HTML 打包 / TTS / 语音识别 / 权限管理 等子工具，agent 被问"我能不能 X"时若 X 在工具箱里应建议用户自行打开。
  - **Memory hub（记忆库）**: 用户可在 "记忆库 / Memory" 主入口查看 / 编辑 / 删除自己的长期记忆与持久化指令；自动摘要节点 `#auto_summary` 与 gateway 节点 `#chat:*` 也都呈现在该图谱里。
  - **Settings / Hermes Settings hub**: 全部用户可见配置（API key / 模型 / agent 参数 / gateway 凭证 / 服务开关）由 Settings 与 Hermes Settings 子页承载。
  - **Skill Recorder**: 用户可在工具箱内录制自己的 UI 操作序列作为可复用 skill。
  - **Terminal**: app 内置终端可执行 shell 命令（与 agent 的 `execute_shell` 工具不同：终端是**用户**手动执行）。
- `SYSTEM_PROMPT_TEMPLATE`（英文）在 `GATEWAY_AWARENESS_SECTION\n\n` 之后、`TOOL_USAGE_GUIDELINES_SECTION` 之前插入新行 `APP_SELF_AWARENESS_SECTION\n\n`。
- `SYSTEM_PROMPT_TEMPLATE_CN`（中文）同位置同样插入 `APP_SELF_AWARENESS_SECTION\n\n`。
- `getSystemPrompt(useEnglish, ...)` 的 replace 链新增一行：`prompt = prompt.replace("APP_SELF_AWARENESS_SECTION", if (useEnglish) APP_SELF_AWARENESS_EN else APP_SELF_AWARENESS_CN)`，紧邻现有 `GATEWAY_AWARENESS_SECTION` 替换之后。
- 段头建议（用户视角语气，与 GATEWAY_AWARENESS 风格一致）：英文 `## App Self-Awareness (Hermes Android Host)`；中文 `## 应用自我感知（Hermes Android 宿主）`。

**验收**:
- `core/config/SystemPromptConfig.kt` 必须含字面 const `APP_SELF_AWARENESS_EN` 和 `APP_SELF_AWARENESS_CN`，两者均为 `const val ... = """..."""` 形态。
- `SYSTEM_PROMPT_TEMPLATE`（英文）和 `SYSTEM_PROMPT_TEMPLATE_CN`（中文）必须各含一处字面 `APP_SELF_AWARENESS_SECTION` 占位符，且位于 `GATEWAY_AWARENESS_SECTION` 与 `TOOL_USAGE_GUIDELINES_SECTION` 之间。
- `getSystemPrompt(...)` 函数体必须含 `replace("APP_SELF_AWARENESS_SECTION", ...)` 调用，三元根据 `useEnglish` 选 EN/CN 常量。
- 两个常量字符串体内必须同时含核心导航关键字（中文版含 `工具箱` / `记忆` / `设置` / `技能录制` / `终端`；英文版含 `Toolbox` / `Memory` / `Settings` / `Skill` / `Terminal`），用于守 prompt 内容不被空段或单语段意外提交。
- `SUBTASK_AGENT_PROMPT_TEMPLATE` 必须**不**含 `APP_SELF_AWARENESS_SECTION` 字面值（守"只主 agent 注入"红线，与 `GATEWAY_AWARENESS_SECTION` 同处理）。
- 单元测试：源码字符串扫描（与 `SystemPromptMemoryMaintenanceWiringTest.kt` 同范式），不依赖 Android Context / LLM。
- §2 四件套：`verify_align / scan_stubs / deep_align` 维持零；`scan_functional_stubs` ≤ 当前基线 390。本改动**只动 app/ 模块**（prompt 常量 + 单测），不动 hermes-android/。
- **手测验收**: 安装新 APK 后开 logcat（或手动观察 chat 行为），用户问 "我能在哪里管理记忆 / 我能录制技能吗 / 工具箱里有什么" 时 agent 回答应包含对应 UI 入口的导航说明，而非"我没办法 / 我可以帮你做 X"。

### R-AGENT-031: 接通 hermes-android cron 子系统（cronjob CRUD + WorkManager tick + Repository 注回）
**来源**:
- 用户 2026-06-13 直接指令链：① "app 有心跳、计时器，等等这些功能有的吗？" → ② "选择1，你自己全部处理"（接通 hermes-android cron）→ ③ AskUserQuestion 提交 3 个风险点后用户回 "1+3+4"（hermes-android 只动数据、prompt-injection 软法防递归、Worker 走 Repository 而不是 Delegate）
- Python 上游 `reference/hermes-agent/cron/scheduler.py` + `cron/jobs.py` + `tools/cronjob_tools.py` + `gateway/run.py::_start_cron_ticker` 是 source of truth；Kotlin 1:1 翻译 `hermes-android/.../cron/Jobs.kt`（已 real）+ `Scheduler.kt`（tick 框架 real，runJob/deliverResult 是 stub）+ `tools/CronjobTools.kt`（CRUD 入口 stub）+ `gateway/Run.kt::_startCronTicker = null`（stub）
- R-CRON-001（L642 一行骨架占位）由本需求承接为完整实现规格

**背景**:
- **App 当前缺失 cron 能力**：`AIToolHandler` 里 `cronjob` schema 暴露但 `CronjobTools.cronjob()` 直接返回 `toolError("cronjob tool is not available on Android")`；`checkCronjobRequirements()` 永远 `false`；Scheduler 没有任何 ticker 拉它跑；`Scheduler.runJob` 写死 `finalResponse = ""`、`deliverResult` 只 `logger.info("would deliver ...")` —— 全链路 0 输出。
- **Python vs Android 平台差异**：Python `_start_cron_ticker` 用 `threading.Thread(daemon=True)` 60s loop；`run_job` 直接调 `AIAgent(...).chat(...)`；`_deliver_result` 走平台 adapter（IM webhook、stdout）。Android 三处不通：(1) 长驻线程在 Doze 下不可靠，必须 WorkManager `PeriodicWorkRequest` 15min；(2) `HermesAgentLoop` 实例化要 provider/key/SessionDB 等 UI 上下文，无法 in-Worker 直接构造（已有 `ExternalChatRequestExecutor` 是这条路的 headless 包装）；(3) "deliver" 目标是回到原 chat 而非外部 IM。
- **Python 上游的 `disabled_toolsets=['cronjob','messaging','clarify']` 在 Android 没法 1:1 翻译**：`HermesAgentLoop` 没有 `disabled_toolsets` 形参（修签名 = 破坏性变更，本次不做）。改用 prompt-injection 软法：cron 触发的 agent 在 system prompt 末尾追加一段"运行在 cron 上下文，不要再调 cronjob 工具创建任务"，靠 LLM 遵循软法。承认弱于上游硬约束，但避免改动 1:1 翻译过的核心 loop 接口。
- **Worker 不走 ChatHistoryDelegate**：Delegate 是 UI 层（持有 `_chatHistory` MutableStateFlow，只在 UI 流程里被调）。直接持久化层是 `ChatHistoryManager.getInstance(appContext).addMessage(chatId, message)`（Room DAO + Mutex，suspend，已有多个 headless 调用方：`HermesGatewayController` / `WebChatHttpBridge` / `StandardChatManagerTool`）。
- **MVP scope（按用户 1+3+4 决策）**：cronjob 工具 CRUD（5 actions: create/list/update/remove/run）+ WorkManager 15min tick + ExternalChatRequestExecutor 复用 + cron context prompt 软防御 + ChatHistoryManager.addMessage 写回。**不**覆盖：cron 表达式（保 R-CRON-013 拒 cron-expr 红线不动）/ subprocess 脚本（`_runJobScript` 保持 stub）/ 多平台 deliver（webhook / 文件 IO 留 follow-up）/ HermesAgentLoop 增加 `disabled_toolsets` 形参（保 1:1 接口）/ pause/resume action（落上游字面 schema 但暂返 not-yet-implemented）。

**架构合规（按用户选 1+3+4）**:
- **路径 1（hermes-android 只承担数据层）**：`hermes-android/.../cron/Jobs.kt` 是数据 CRUD（real）；`Scheduler.tick()` 框架（real）只做 due-jobs 迭代 + advanceNextRun + saveJobOutput + markJobRun。**所有"调 agent + 写回 chat"的胶水代码落在 app 模块**（`OperitApplication`/`CronTickWorker`/`CronAgentRunner`），hermes-android 模块**不依赖** app（保依赖方向：app → hermes-android，禁反向）。Scheduler 不加任何静态 var lambda 注入位（避免 deep_align 抓"Python 没有的字段"），改为：
  - `CronTickWorker`（在 app 模块）用 `Jobs.getDueJobs()` 直接拿到期 job 列表，**自己**走 advanceNextRun → invoke runner → saveJobOutput → markJobRun → deliverer 流水线
  - `Scheduler.runJob` / `Scheduler.deliverResult` 两个 stub 不动（保留它们作为"如果将来需要 hermes-android 内自带 ticker 时的接口形状"），仅在文件头加注释说明 Android 由 app 模块的 CronTickWorker 接管这两步
- **路径 3（递归 cronjob 走 prompt-level 软防御）**：cron 触发的 agent 调用前，在 prompt 末尾追加一段 cron context 警告。技术实现：
  - `ExternalChatRequest` 已有 `message: String` 字段（用户 prompt）。在 `CronAgentRunner.run` 拼 ExternalChatRequest 时，把 `message` 设为 `prompt + "\n\n[CRON CONTEXT] You are running under cron tick. Do not call cronjob tool to create new schedules; instead just answer the user's prompt."`（中文 prompt 用中文 marker；按 prompt 是否含中文字符自动选）
  - 不修改 `ExternalChatRequestExecutor` 内部（避免影响其他 entry point），只在 cron 调用方拼 message 时附加
- **路径 4（Worker 用 ChatHistoryManager 而非 Delegate）**：
  - 调 `ChatHistoryManager.getInstance(appContext).addMessage(chatId, ChatMessage(sender="ai", content=...))` 直接写 Room
  - sidebar `chatHistoriesFlow` 自动刷新（已是 Room Flow）
  - 活跃 chat 的 `_chatHistory` MutableStateFlow（在 ChatHistoryDelegate 内）走既有 `GatewayChatEventBus.events.emit(Event.ProcessingCompleted(chatId, ...))`：`ChatHistoryDelegate.kt:223-227` 已订阅这个 event 并自动调 `reloadChatMessagesSmart`。复用既有 event bus，零新增 bus。
- **不引入 cron-utils / Quartz**：保 R-CRON-013 在 Android 拒 cron-expr 不变。
- **不修改 HermesAgentLoop 形参**：保 1:1 翻译接口。
- **15min 下限是 OS 硬约束**：WorkManager `PeriodicWorkRequest` < 15min 会 silently round up；在 cronjob() API 边界硬性拒绝并显式告知 agent。

**行为**:
- **CronjobTools.cronjob 入口接通（`hermes-android/.../tools/CronjobTools.kt`）**：
  - 替换 `return toolError("cronjob tool is not available on Android", ...)` 为按 `action` 分派：
    - `create`: 调 `_scanCronPrompt(prompt)` 做威胁扫描 → 若返回非空提示则 `toolError(scanWarning)`；通过则**预校验** `every` 字段（若 < 15min → toolError）→ 调 `Jobs.createJob(name, prompt, schedule, repeat=..., skills=..., origin=..., deliver=...)` → 用 `_formatJob(job)` 序列化返回 `toolResult(...)`
    - `list`: 调 `Jobs.listJobs()` → `map { _formatJob(it) }` 包入 `toolResult`
    - `update`: 同 create 的 every 校验 + scan → 调 `Jobs.updateJob(id, ...)` → `_formatJob(...)` → `toolResult`
    - `remove`: 调 `Jobs.removeJob(id)` → `toolResult({"removed": true, "id": id})`
    - `run`: 调 `Jobs.triggerJob(id)`（手动触发不等 tick） → `toolResult({"triggered": true, "id": id})`
    - `pause`/`resume`: 暂返回 `toolError("Not yet implemented on Android (use 'remove' + 'create' as workaround)")`
    - 任何 `IllegalArgumentException`（来自 `Jobs.parseSchedule` 拒 cron-expr / 拒 every < 15min）→ 转 `toolError(e.message)` 返回 agent 友好错误
  - **min-interval 守卫**：在调 `Jobs.createJob` / `Jobs.updateJob` 之前预先解析 `every` 字段，若解析后 interval < 15 min → 返回 `toolError("Schedule interval below 15 minutes is not supported on Android due to WorkManager constraints. Use 'every 15m' or longer.")`
  - `checkCronjobRequirements(): Boolean` 从 `false` 改为 **`true`**（Android 现在支持 cron）
- **Scheduler 文件头注释**（`hermes-android/.../cron/Scheduler.kt`）：
  - 在 `runJob` / `deliverResult` 两个 stub 上方加 KDoc 说明：`* On Android the cron pipeline is driven by the app module's CronTickWorker, which uses Jobs.getDueJobs() directly. This stub is preserved for hermes-android-internal callers and remains unimplemented on Android.`
  - **不**修改函数体（保留 stub 不动，避免新增"Python 上游没有的字段"被 deep_align 抓住）
  - **不**新增 `agentRunner` / `resultDeliverer` 静态 var
- **CronTickWorker 新建（`app/.../cron/CronTickWorker.kt`，注意是 app 模块不是 hermes-android）**：
  - 继承 `androidx.work.CoroutineWorker`，`override suspend fun doWork(): Result`
  - body 流程（按 Python `Scheduler.tick` 顺序但全部在 app 模块走）：
    1. `Jobs.acquireLock()`（hermes-android 已有 `FileChannel.tryLock` 实现）；获取失败 → `Result.success()`（让下一个 tick 试）
    2. `val due = Jobs.getDueJobs()`
    3. for each job: `Jobs.advanceNextRun(jobId)`（at-most-once 必须先 advance）→ `runOneJob(job)`
    4. `Jobs.releaseLock()`（finally 块）
    5. 任何顶层 throwable → log + `Result.success()`（避免 Worker 失败导致 WorkManager 退避停排）
  - `private suspend fun runOneJob(job)`：
    1. 调 `CronAgentRunner(applicationContext).run(job)` → `JobRunResult`
    2. `Jobs.saveJobOutput(jobId, fullOutputDoc)`
    3. 若 `finalResponse` 非空 + 非 SILENT_MARKER → 调 `CronAgentRunner.deliver(job, finalResponse)`
    4. `Jobs.markJobRun(jobId, success, errorMessage)`
  - 伴生对象 `CronTickWorker.Companion.enqueue(context: Context)`：用 `PeriodicWorkRequestBuilder<CronTickWorker>(15, TimeUnit.MINUTES)` + `setBackoffCriteria(LINEAR, 30, SECONDS)` + `WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("hermes_cron_tick", KEEP, request)`（KEEP 防 app 重启清空时间表）
- **CronAgentRunner 新建（`app/.../cron/CronAgentRunner.kt`）**：
  - `class CronAgentRunner(private val appContext: Context)`
  - `suspend fun run(job: Map<String,Any?>): JobRunResult`：
    1. 取 `prompt = job["prompt"] as String`、`origin = job["origin"] as Map<*,*>?`、`originChatId = origin?.get("chat_id") as String?`
    2. 拼 `cronContextSuffix`：若 prompt 含 CJK 字符 → 中文版 `\n\n[CRON 上下文] 你正在 cron tick 中运行。请直接回答上面的 prompt，不要再调用 cronjob 工具创建新任务。`；否则英文版 `\n\n[CRON CONTEXT] You are running under cron tick. Do not call cronjob tool to create new schedules; just answer the prompt above.`
    3. 拼 `ExternalChatRequest(message=prompt+suffix, chatId=originChatId, createIfNone=false, returnToolStatus=false, showFloating=false, stopAfter=false, requestId="cron-${job["id"]}-${System.currentTimeMillis()}")`
    4. 调 `ExternalChatRequestExecutor(appContext).execute(request)` → 把 `aiResponse` 包成 `JobRunResult(success=result.success, fullOutputDoc=aiResponse ?: "", finalResponse=aiResponse ?: "", errorMessage=result.error)`
  - `suspend fun deliver(job: Map<String,Any?>, content: String): String?`：
    1. 取 `originChatId = (job["origin"] as Map<*,*>?)?.get("chat_id") as String?`；空 → 返回 null
    2. `chatHistoryManager = ChatHistoryManager.getInstance(appContext)`
    3. `chatHistoryManager.ensureChatWithId(originChatId, title="Cron")`（idempotent，防原 chat 被删后 cron 写入失败）
    4. 拼 `ChatMessage(sender="ai", content=content, timestamp=System.currentTimeMillis())`
    5. `chatHistoryManager.addMessage(originChatId, message)`
    6. emit `GatewayChatEventBus.events.emit(Event.ProcessingCompleted(chatId=originChatId, ...))` 或等价（让活跃 chat 的 `_chatHistory` 通过既有 `ChatHistoryDelegate.kt:223-227` 订阅触发 reloadChatMessagesSmart）
    7. 返回 `"chat:$originChatId"` 用于 logger
- **OperitApplication.onCreate 注入**（`app/.../OperitApplication.kt`）：
  - 在既有 `launchOrphanTagMigrationsIfNeeded()`（R-AGENT-029）之后调 `CronTickWorker.enqueue(this)`
  - **不**调 `Scheduler.agentRunner = ...`（路径 1 决策：Scheduler 不接管 Android cron）
- **APP_SELF_AWARENESS prompt 增补**（`app/.../core/config/SystemPromptConfig.kt`）：
  - `APP_SELF_AWARENESS_EN` / `APP_SELF_AWARENESS_CN` 各加一句关于 `cronjob`：
    - EN 例: "You can also schedule prompts to run later via the `cronjob` tool (15-minute minimum interval; results post back as your reply in the original chat)."
    - CN 例: "你也可以用 `cronjob` 工具登记定时任务（最小间隔 15 分钟；到点结果会以你的回复形式回到原对话）。"
  - 该增补**不**改既有"工具箱 / Memory hub / Settings / Skill Recorder / Terminal" 5 个核心导航点，仅追加一句

**验收**:
- **A. CronjobTools 入口已接通**：
  - `CronjobTools.kt` 整文件**不**含 `"cronjob tool is not available on Android"` 字面值
  - `checkCronjobRequirements()` 函数体含 `return true`（不再 `return false`）
  - `cronjob(...)` 函数体含 `when (action) {` 或等价 dispatch（按 action 分派至少 5 个分支：create/list/update/remove/run）
  - `cronjob(...)` 函数体含 `_scanCronPrompt(` 调用（create / update 路径）+ `Jobs.createJob(` / `Jobs.listJobs(` / `Jobs.updateJob(` / `Jobs.removeJob(` / `Jobs.triggerJob(` 五处函数引用
  - `cronjob(...)` 函数体含 min-interval guard 字面值（`15` + (`minutes` 或 `min`) 共现 + `toolError(`）
- **B. Scheduler stub 状态**：
  - `Scheduler.kt::runJob` / `Scheduler.kt::deliverResult` 两个函数**不**新增 lambda 注入字段（守路径 1 红线，不让 deep_align 抓到 Python 没有的字段）
  - 两个函数 KDoc 含 `CronTickWorker` 字面值（说明 Android 由 app 模块接管的注释）
- **C. CronTickWorker 已落盘（在 app 模块）**：
  - `app/src/main/java/com/ai/assistance/operit/cron/CronTickWorker.kt` 文件存在
  - 内含 `class CronTickWorker` 继承 `CoroutineWorker`、`override suspend fun doWork()`
  - 函数体调 `Jobs.getDueJobs(` + `Jobs.advanceNextRun(` + `Jobs.saveJobOutput(` + `Jobs.markJobRun(` 四处
  - 伴生对象含 `enqueueUniquePeriodicWork("hermes_cron_tick"` + `PeriodicWorkRequestBuilder<CronTickWorker>(15, TimeUnit.MINUTES)` + `KEEP` 字面值
- **D. CronAgentRunner 已落盘**：
  - `app/src/main/java/com/ai/assistance/operit/cron/CronAgentRunner.kt` 文件存在
  - 内含 `ExternalChatRequestExecutor(` 引用 + `[CRON CONTEXT]` 或 `[CRON 上下文]` 字面值（cron context prompt 软防御）
  - 内含 `ChatHistoryManager.getInstance(` + `addMessage(` + `sender = "ai"` 字面值
  - 内含 `GatewayChatEventBus` 引用 + `ProcessingCompleted` 字面值（触发 UI reloadChatMessagesSmart）
- **E. OperitApplication 启动注入**：
  - `OperitApplication.kt::onCreate` 函数体含 `CronTickWorker.enqueue(` 调用
  - 同函数体**不**含 `Scheduler.agentRunner = ` 或 `Scheduler.resultDeliverer = ` 字面值（守路径 1）
- **F. APP_SELF_AWARENESS prompt 增补**：
  - `SystemPromptConfig.kt::APP_SELF_AWARENESS_EN` 常量体含 `cronjob` 字面值 + `15` 字面值
  - `SystemPromptConfig.kt::APP_SELF_AWARENESS_CN` 常量体含 `cronjob` 字面值 + `15` 字面值
- **G. 单元测试**：源码字符串扫描 + behavioral test —— 详见 TC-AGENT-031-a..n
- **H. §2 四件套**：`verify_align / scan_stubs / deep_align` 维持零；`scan_functional_stubs` **减少**（CronjobTools.cronjob + checkCronjobRequirements 至少 -2，目标 ≤ 388）
- **I. 手测验收**：
  - 启动 APK → 等 ≤ 16 分钟（首次 PeriodicWork 触发）→ logcat 应见 `CronTickWorker doWork` + `Jobs.getDueJobs` 日志，无 ANR / crash
  - 在 chat 内告诉 agent "every 15m send me '心跳'" → agent 调 `cronjob(action="create", ...)` 成功；过一个 tick 后该 chat 内出现 sender=ai 的"心跳"消息且活跃 chat UI 自动滚动到底
  - 跟 agent 说 "every 5m ..." → agent 应得到 `toolError` 提示 15min 下限
  - 跟 agent 说 "在每天早上 9 点 ..." (cron-expr) → agent 应得到 `toolError("Cron expressions are not supported on Android")` 并 fallback 到 `every` 语法（守 R-CRON-013 不回归）
  - cron 触发的 agent 在收到 `[CRON CONTEXT] / [CRON 上下文]` 后应直接回复用户 prompt，不再调 `cronjob(create)` 嵌套生成新 job（软防御，弱于 Python `disabled_toolsets` 但够用）

### R-AGENT-032: STT 工具基础设施（OpenAI Whisper `/v1/audio/transcriptions` 接通）
**来源**:
- 用户 2026-06-14 决策链：① Telegram 方案 a 第一步（图片 + 语音都通）→ ② AskUserQuestion 选 "OpenAI Whisper 起步"（GLM-asr / Groq / Mistral / local 后续追加）→ ③ "Telegram 入站自动转写"（不暴露独立 transcribeAudio 工具，对齐 Python `gateway/run.py` 上游行为）
- Python 上游 `reference/hermes-agent/tools/transcription_tools.py:581 transcribe_audio(file_path, model=None)`（公共入口）+ `:485 _transcribe_openai`（OpenAI provider 实现）+ `:75 STT_OPENAI_BASE_URL` 环境变量 + `:67 DEFAULT_STT_MODEL = "whisper-1"`
- 跟 R-GW-008（Telegram audio 下载）一起立，作为可复用的 STT 基础设施供其他平台后续接入

**背景**:
- Python 上游 STT 是 5-provider 体系：`local (faster-whisper) / local_command / groq / openai / mistral`，按 auto-detect 顺序选。Kotlin 第一版**只**实现 OpenAI provider（用户决策："仅 OpenAI Whisper 起步"，简化对齐）；其余 provider 在 R 文档里登记为"已知偏离上游"，由后续 R 追加（不在本 R 范围）。
- HTTP 调用形状（OpenAI Whisper）：`POST {base_url}/audio/transcriptions`，`Authorization: Bearer <key>`，`multipart/form-data`，字段 `model=whisper-1` + `file=<binary>` + `response_format=text`。
- 配置读取顺序对齐上游：`VOICE_TOOLS_OPENAI_KEY` → `OPENAI_API_KEY` fallback；`STT_OPENAI_BASE_URL` → 默认 `https://api.openai.com/v1`；`STT_OPENAI_MODEL` → 默认 `whisper-1`。
- 文件大小 / 格式校验（对齐上游 `transcription_tools.py:77,79`）：25 MB 上限；接受扩展名 `mp3/mp4/mpeg/mpga/m4a/wav/webm/ogg/aac/flac`。
- 失败返回 `{"success": false, "transcript": "", "error": <message>}` —— 调用方（R-GW-008 Telegram）按 error 是否含 `"No STT provider"` / `"Missing"` 决定降级文案。
- **不暴露 agent 工具入口**：本轮按用户决策只用作 R-GW-008 的内部依赖；工具暴露层（让 agent 在对话里直接调 transcribeAudio）留给后续 R。

**架构合规**:
- 平台无关入口 `TranscriptionTools.transcribeAudio(filePath: String, model: String? = null): TranscribeResult`（对齐 Python `transcribe_audio` 函数签名）
- 同模块同包：`hermes-android/src/main/java/com/xiaomo/hermes/hermes/tools/TranscriptionTools.kt`，1:1 对齐 Python `tools/transcription_tools.py` 文件名
- 复用既有 OkHttp 实例（不在 hermes-android 里新建全工程单例，但可以在本文件内 lazy 一个 OkHttpClient + 30s timeout，对齐 Python OpenAI SDK `timeout=30, max_retries=0`）
- 配置读取走 `Config.kt` / `HermesConstants.kt` 既有路径（如已存在的 `OPENAI_API_KEY` 读取点），新增 `VOICE_TOOLS_OPENAI_KEY` / `STT_OPENAI_BASE_URL` / `STT_OPENAI_MODEL` 三个独立读取键
- `TranscribeResult` 为 sealed/data class，包含 `success: Boolean / transcript: String / error: String? / provider: String?`

**行为**:
- **新增文件 `TranscriptionTools.kt`**（`hermes-android/src/main/java/com/xiaomo/hermes/hermes/tools/`）：
  - 顶层常量：
    - `const val DEFAULT_STT_MODEL = "whisper-1"`
    - `const val STT_OPENAI_BASE_URL_DEFAULT = "https://api.openai.com/v1"`
    - `const val MAX_FILE_SIZE = 25L * 1024L * 1024L` (25 MB)
    - `val SUPPORTED_FORMATS = setOf(".mp3", ".mp4", ".mpeg", ".mpga", ".m4a", ".wav", ".webm", ".ogg", ".aac", ".flac")`
    - `val OPENAI_MODELS = setOf("whisper-1", "gpt-4o-mini-transcribe", "gpt-4o-transcribe")`
  - data class `TranscribeResult(val success: Boolean, val transcript: String, val error: String? = null, val provider: String? = null)`
  - `fun transcribeAudio(filePath: String, model: String? = null): TranscribeResult`（**suspend 或同步均可**，对齐 Python 同步函数；调用方 `Telegram.kt` 用 `withContext(Dispatchers.IO)` 包装）：
    1. `_validateAudioFile(filePath)` —— 检文件存在 / 扩展名在 `SUPPORTED_FORMATS` / 大小 ≤ 25 MB；任一失败 → 返回 `TranscribeResult(false, "", error="...")`
    2. 解析 provider：当前硬编码 `"openai"`（Python 上游的 `_get_provider` 简化为单 provider）；后续 R 加 dispatch
    3. 调 `_transcribeOpenai(filePath, model ?: DEFAULT_STT_MODEL)`
  - `private fun _transcribeOpenai(filePath: String, model: String): TranscribeResult`：
    1. 读 key：`getEnvOrConfig("VOICE_TOOLS_OPENAI_KEY") ?: getEnvOrConfig("OPENAI_API_KEY")`；空 → `TranscribeResult(false, "", error="No STT API key configured (set VOICE_TOOLS_OPENAI_KEY or OPENAI_API_KEY)", provider="openai")`
    2. 读 base url：`getEnvOrConfig("STT_OPENAI_BASE_URL") ?: STT_OPENAI_BASE_URL_DEFAULT`
    3. 读 model override：`getEnvOrConfig("STT_OPENAI_MODEL") ?: model`；如果传入的 model 是 Groq-only 名（自动校正，对齐上游 `:500`，第一版可省略，后续追加）
    4. 构造 multipart：
       - `model: $modelName`
       - `response_format: text`（对齐上游 `:512` 对 `whisper-1` 的处理）
       - `file: <binary, filename=<File(filePath).name>, mediaType=audio/*>`
    5. 构造 request：`POST $baseUrl/audio/transcriptions`，`Authorization: Bearer $key`
    6. 调 `_httpClient.newCall(request).execute()`：
       - 2xx → 读 body 为 `String`（response_format=text 时 body 就是裸 transcript）→ `TranscribeResult(true, body.trim(), provider="openai")`
       - 4xx/5xx → `TranscribeResult(false, "", error="OpenAI STT $code: ${body or message}", provider="openai")`
       - IOException → `TranscribeResult(false, "", error="Network error: ${e.message}", provider="openai")`
  - `private fun getEnvOrConfig(key: String): String?` 帮手：先 `System.getenv(key)`，再读 `HermesConstants.getConfigString(key)`（既有的 config layer），都空返回 null
  - 私有 `_httpClient: OkHttpClient` lazy 单例（30s timeout × 3，0 retries）

**验收**:
- **A. TranscriptionTools.kt 已落盘**：
  - 文件路径 `hermes-android/src/main/java/com/xiaomo/hermes/hermes/tools/TranscriptionTools.kt` 存在
  - 含 `fun transcribeAudio(filePath: String` 函数声明（顶层 fun，与 Python module-level 函数一致）
  - 含 `data class TranscribeResult` 声明
- **B. OpenAI provider 实现**：
  - 含 `whisper-1` 字面值
  - 含 `https://api.openai.com/v1` 字面值（`STT_OPENAI_BASE_URL_DEFAULT` 常量）
  - 含 `/audio/transcriptions` URL path 字面值
  - 含 `Authorization` + `Bearer ` 字面值
  - 含 `multipart/form-data` 或等价的 `MultipartBody.Builder()` 调用
  - 含 `response_format` + `text` 字面值
- **C. Key 读取顺序**：
  - 函数体含 `VOICE_TOOLS_OPENAI_KEY` + `OPENAI_API_KEY` 两个环境变量名字面值（fallback 顺序）
  - missing key 时返回的 error 字面值含 `No STT API key`
- **D. 文件校验**：
  - 含 `25` + `1024` 字面值（MAX_FILE_SIZE 计算）或 `MAX_FILE_SIZE` 常量名
  - 含 `SUPPORTED_FORMATS` 常量声明，set 中含 `.mp3` + `.ogg` + `.wav` 字面值
- **E. §2 四件套**：
  - `verify_align`：新增文件 `tools/TranscriptionTools.kt` 对齐 Python `tools/transcription_tools.py`，达成 195/195
  - `scan_stubs`：维持零
  - `deep_align`：维持零（OpenAI 路径完整；其他 provider 通过文件 KDoc 标注"R-AGENT-032 only OpenAI; other providers deferred"豁免）
  - `scan_functional_stubs`：不增（理想 -1，因为 hermes-android 之前没有 transcription tool 文件，stub 数从基线增加 0）
- **F. 单元测试**：源码扫描 + 纯 JVM 行为测（missing-key + missing-file 短路）；完整 multipart shape 验证 deferred to §3 E2E（hermes-android testImpl 无 MockWebServer 依赖）。详见 TC-AGENT-032-a..f
- **G. 手测验收**：
  - 配 `VOICE_TOOLS_OPENAI_KEY` 后给 Telegram bot 发语音，logcat 应见 `TranscriptionTools` 调用 + multipart POST 到 `/audio/transcriptions`
  - 不配 key → 返回 `error="No STT API key configured..."`，Telegram chat 中显示降级文案
  - 用 26 MB 音频 → 返回 `error="File exceeds 25MB"` 或等价

### R-AGENT-033: cron→IM 投递回路（origin 注入 + 平台 adapter dispatch）
**来源**:
- 用户 2026-06-15 直接指令链：① "做定时任务呢？比如每天10点给发新闻等等工作呢？" → ② agent 反馈"cronjob 工具在当前版本不可用"（Explore agent 确认 cronjob 真没接入 LLM 工具表）→ ③ AskUserQuestion 选 B（立项接入）→ ④ Stage 0 调研发现 cron 投递路径是死信，agent 跑完只写 Room DB，IM 用户永远收不到 → ⑤ 用户 "b 吧，后面的事情都由你来决定"（拆 R-AGENT-033 补投递回路 + R-AGENT-034 暴露工具入口，先做 033）
- Python 上游 `reference/hermes-agent/cron/scheduler.py:269-452 _deliver_result` + `tools/cronjob_tools.py:71-88 _origin_from_env` + `gateway/run.py:3964 _set_session_env / :4772 _clear_session_env` + `gateway/session_context.py:61-63,73-75` 三块 contextvar 是 source of truth
- R-AGENT-031 验收 D 设计层就只要求"写 Room DB + 通知 UI"，**没有要求**回投到 IM；本 R 补这个缺口

**背景**:
- **三个独立 bug 复合导致 IM 触发的 cron 完全收不到回复**：
  - **Bug A**：IM 入站时 `Run.kt::_handleMessage` 没调 `SessionContext.setSessionVars(...)` —— 全 hermes-android `setSessionVars` 0 调用方，平台路由信息根本没写入 ThreadLocal。Python 上游 `gateway/run.py:3964` 在 `_handle_message` 里调 `_set_session_env(context)` 写 contextvar，`:4772` 在 `finally` 里 `_clear_session_env(tokens)` 配对清理。Kotlin 缺这两步。
  - **Bug B**：`CronjobTools.kt:447 _originFromEnv` 用 `System.getenv("HERMES_SESSION_PLATFORM")` 读 OS 环境变量 —— Android 上永远返 null（OS 层没有暴露写入 env 的接口）。Python 同名函数 `cronjob_tools.py:73-86` 调 `get_session_env(...)`（contextvar-aware），Kotlin 应改读 `getSessionEnv(...)`（`SessionContext.kt:96` 已有该函数，已被 `SendMessageTool.kt:147-150,348` 使用）。
  - **Bug C**：`CronAgentRunner.deliver` (app/.../core/cron/CronAgentRunner.kt:112-133) 不读 `job["origin"]` 也不读 `job["deliver"]`，HermesGatewayController 也没暴露按 platform+chatId 直投的接口；`Scheduler.kt:478` 的 `runJob` / `deliverResult` 路径有 `TODO: Route through Android platform adapters` 注释，但代码层面是 no-op log。
- **R-AGENT-031 设计就没考虑 IM 投递**：验收 D（hermes-requirements.md:1014-1017）只要求 `addMessage` + `ProcessingCompleted` event 触发 UI reload；验收 I 手测项也只验"app 内 chat UI 出现 ai 消息"。结果是 app 前台用户能看到，IM 用户（飞书 bot 跟 agent 说"每天 10 点发新闻"）完全看不到。
- **现有 outbound 基础设施可复用**：`GatewayRunner.deliveryRouter: DeliveryRouter` 已经是 public（`Run.kt:52`），含 `getAdapter(platformName): BasePlatformAdapter?`（`Delivery.kt:62`）+ `deliverText(platform, chatId, text, replyTo)` (`Delivery.kt:73`)。各 platform `send(chatId, content, replyTo, metadata)` 签名已统一在 `Base.kt:270`。`HermesGatewayController.runner` 是 private，需要新增一个公共 `dispatchOutgoing(...)` 方法对外暴露。
- **MVP scope**：修这 3 个 bug 让 IM 触发的 cron 能真正发回去。**不**覆盖：HTTP standalone fallback（Python `_deliver_result:415-448` 的兜底，Android 不上线该路径）/ media strip 分支（Python `scheduler.py:336` 调 `extract_media` 处理图片标签，Kotlin 第一版只处理纯文本，含 media 标签时直接当文本发）/ deliver 字段 explicit `platform:chat_id[:thread_id]` 解析（已有 `DeliveryTarget.parse`，但本轮只走 `deliver=origin` 默认路径）。

**架构合规**:
- **不破坏 hermes-android → app 单向依赖**：`Scheduler.kt` 在 hermes-android，不能直接 import `app/` 的 HermesGatewayController。改用注入式回调：在 `Scheduler.kt` 顶层（或 companion object）放一个 `var cronOutboundDispatcher: (suspend (platform: String, chatId: String, text: String, threadId: String?) -> Boolean)? = null`，由 app 模块的 `OperitApplication.onCreate` 注入实际指向 `HermesGatewayController.dispatchOutgoing` 的 lambda。这与 R-AGENT-031 路径 1 决策（"hermes-android 不引入静态 var lambda"）有偏离 —— **但偏离是必要的**，因为不放注入点就只能让 cron 端 import HermesGatewayController（破坏依赖方向）或反射（更糟）；且 R-AGENT-031 当时是因为不需要 cron→IM 投递才避开注入点，现在需求变了，注入点是最小代价方案。
- **ThreadLocal 配对原则**：所有 `setSessionVars` 必须有对应 `clearSessionVars()` in `finally` 块；`setCronAutoDeliverVars` 同理。避免协程切线程 + 上一轮残留导致跨会话污染。
- **复用 R-AGENT-027 已写完的 origin schema**：`{"platform", "chat_id", "chat_name", "thread_id"}` Map<String, String?>；不引入 dataclass。
- **threadId metadata 平台分支**：Telegram 用 `metadata.put("message_thread_id", threadId.toIntOrNull() ?: 0)`（`Telegram.kt:567` 既有读取点）；Feishu / Slack 暂用通用 `"thread_id"` key（`Feishu.kt::send` 当前不读，但写进 metadata 不影响行为，未来扩展时再加 read 点）。

**行为**:
- **Run.kt::_handleMessage 调用 setSessionVars + finally clearSessionVars**（`hermes-android/.../gateway/Run.kt:251-...`）：
  1. 在 `_interruptFlags[event.sessionKey] = interruptFlag` 行后、`try {` 行之前，调：
     ```
     setSessionVars(
         platform = event.source.platform,
         chatId = event.source.chatId,
         chatName = event.source.chatName,
         threadId = event.source.threadId ?: "",
         userId = event.source.userId,
         userName = event.source.userName,
         sessionKey = event.sessionKey,
     )
     ```
  2. 把现有 try 体最末尾的 `_sessionSemaphore.release()` 包成 `try { ... } finally { clearSessionVars(); _sessionSemaphore.release(); }`（保留语义，新增 clear）
- **SessionContext.kt 加 cron auto-deliver ThreadLocal + helper**（`hermes-android/.../gateway/SessionContext.kt`）：
  1. `_VAR_MAP` 增加 3 项：
     - `"HERMES_CRON_AUTO_DELIVER_PLATFORM"`
     - `"HERMES_CRON_AUTO_DELIVER_CHAT_ID"`
     - `"HERMES_CRON_AUTO_DELIVER_THREAD_ID"`
     与现有 `HERMES_SESSION_*` 同形 ThreadLocal<Any>，`getSessionEnv(name, default)` 自动 dispatch
  2. 新增 `fun setCronAutoDeliverVars(platform: String, chatId: String, threadId: String = "")` 函数，写入 3 个 ThreadLocal
  3. 新增 `fun clearCronAutoDeliverVars()` 配对 clear
  - 对齐 Python `gateway/session_context.py:61-63`（变量定义） + `:73-75`（exposed names）
- **CronjobTools._originFromEnv 改读 ThreadLocal**（`hermes-android/.../tools/CronjobTools.kt:447-460`）：
  1. import `getSessionEnv`（与 `SendMessageTool.kt:18` 同 import path）
  2. 替换 4 处 `System.getenv("HERMES_SESSION_*")` 为 `getSessionEnv("HERMES_SESSION_*", "")`（4 个变量：PLATFORM / CHAT_ID / CHAT_NAME / THREAD_ID）
  3. 保留 `takeIf { it.isNotEmpty() }` null 化语义不变
- **Scheduler.deliverResult 接 cronOutboundDispatcher 回调**（`hermes-android/.../cron/Scheduler.kt`）：
  1. 顶层（同 file `package` 下）新增：
     ```
     var cronOutboundDispatcher: (suspend (platform: String, chatId: String, text: String, threadId: String?) -> Boolean)? = null
     ```
  2. `deliverResult(job, content)` 函数体（line 446-...）：
     - 调 `resolveDeliveryTargets(job)` 拿 `targets: List<DeliveryTarget>`
     - 对每个 target：
       - 调 `cronOutboundDispatcher?.invoke(target.platform, target.chatId, content, target.threadId)`
       - 回调返回 `true` → log "cron deliver ok platform=$platform chatId=$chatId len=${content.length}"
       - 回调返回 `false` 或 `null`（dispatcher 未注入）→ log "cron deliver fallback (no dispatcher or send failed) platform=$platform chatId=$chatId"
     - **不**做 HTTP standalone fallback（Python `:415-448` 路径不上线第一版）
  3. 移除原 `TODO: Route through Android platform adapters` 注释（line 478）
- **HermesGatewayController.dispatchOutgoing + 注入回调**（`app/.../hermes/HermesGatewayController.kt`）：
  1. 新增公共方法：
     ```
     suspend fun dispatchOutgoing(
         platform: String,
         chatId: String,
         text: String,
         threadId: String? = null,
     ): Boolean {
         val r = runner ?: return false
         val adapter = r.deliveryRouter.getAdapter(platform) ?: return false
         val metadata = threadId?.takeIf { it.isNotEmpty() }?.let {
             when (platform.lowercase()) {
                 "telegram" -> JSONObject().apply { put("message_thread_id", it.toIntOrNull() ?: 0) }
                 else -> JSONObject().apply { put("thread_id", it) }
             }
         }
         val result = adapter.send(chatId = chatId, content = text, replyTo = null, metadata = metadata)
         return result.success
     }
     ```
  2. 在已有 `start(...)` / 初始化路径（HermesGatewayController.kt:69-93 附近）注入：
     ```
     com.xiaomo.hermes.hermes.cron.cronOutboundDispatcher = { platform, chatId, text, threadId ->
         dispatchOutgoing(platform, chatId, text, threadId)
     }
     ```
  3. 在 `stop(...)` / cleanup 路径置空：`com.xiaomo.hermes.hermes.cron.cronOutboundDispatcher = null`

**验收**:
- **A. Run.kt setSessionVars + finally clearSessionVars 接通**：
  - `Run.kt::_handleMessage` 函数体含 `setSessionVars(` 调用，参数串含 `event.source.platform` + `event.source.chatId` + `event.source.threadId` 三处引用
  - 同函数体含 `clearSessionVars()` 调用，且**位于 finally 块**（用 `finally\s*\{[\s\S]{0,500}clearSessionVars` 跨行 regex 验证）
- **B. SessionContext.kt cron auto-deliver vars 落盘**：
  - `SessionContext.kt` 文件含 `HERMES_CRON_AUTO_DELIVER_PLATFORM` + `HERMES_CRON_AUTO_DELIVER_CHAT_ID` + `HERMES_CRON_AUTO_DELIVER_THREAD_ID` 三个字面值
  - 含 `fun setCronAutoDeliverVars(` 函数声明
  - 含 `fun clearCronAutoDeliverVars(` 函数声明
  - 该 3 个变量名出现在 `_VAR_MAP` 注册区（即变量 ThreadLocal 已注册到 dispatcher）
- **C. CronjobTools._originFromEnv 切到 getSessionEnv**：
  - `CronjobTools.kt::_originFromEnv` 函数体含 `getSessionEnv(` 至少 3 次调用（platform / chat_id / thread_id 三个变量读取）
  - 同函数体**不**含 `System.getenv("HERMES_SESSION_` 字面值（红线：旧 OS env 路径必须移除）
- **D. Scheduler.deliverResult 接 cronOutboundDispatcher**：
  - `Scheduler.kt` 文件顶层（package-level / companion / object）含 `cronOutboundDispatcher` 字面值 + 类型签名含 `suspend` + `Boolean` 字面值
  - `Scheduler.kt::deliverResult` 函数体含 `cronOutboundDispatcher` 引用 + `target.platform` / `target.chatId` 引用
  - **不**含原 `TODO: Route through Android platform adapters` 字面值（红线：TODO 必须移除）
- **E. HermesGatewayController.dispatchOutgoing + 回调注入**：
  - `HermesGatewayController.kt` 含 `suspend fun dispatchOutgoing(` 函数声明，参数列表含 `platform` + `chatId` + `text` + `threadId` 四处
  - 函数体含 `deliveryRouter.getAdapter(` 调用 + `adapter.send(` 调用
  - 同文件含 `cronOutboundDispatcher` 字面值（注入回调或置空 2 处）
  - Telegram 分支含 `message_thread_id` 字面值（thread metadata）
- **F. §2 四件套**：
  - `verify_align`：维持零（不新增/删除文件，仅函数体修改）
  - `scan_stubs`：维持零
  - `deep_align`：维持零（cronOutboundDispatcher 是注入点而非 Python 上游字段；通过 Scheduler.kt 文件头 KDoc 说明"Android-only injection point for app→hermes-android dependency direction"豁免）
  - `scan_functional_stubs`：减少（`Scheduler.deliverResult` 从 stub-log-only 变为真投递，至少 -1）
- **G. 单元测试**：源码扫描 wiring tests，详见 TC-AGENT-033-a..h
- **H. 手测验收**（依赖 R-AGENT-034 暴露工具入口后才能完整验）：
  - 飞书 bot 跟 agent 说"每 15 分钟提醒我喝水"→ agent 调 cronjob 创建 → 16 分钟后**飞书原会话**收到 ai 消息（不是只在 app 内）
  - 同上 Telegram 路径 / Telegram thread (super-group topic) 路径 thread_id 正确路由
  - 切换会话不污染：A 会话发消息 → A 会话 cron job origin.chat_id=A；同期 B 会话发消息 → B 会话 cron job origin.chat_id=B（验 ThreadLocal finally-clear 隔离）

### R-AGENT-034: 暴露 cronjob 工具入口给 LLM（SystemToolPrompts + ToolRegistration 桥接）
**来源**:
- 用户 2026-06-15 决策 "b" 第二阶段（R-AGENT-033 闭环 IM 投递落地后）：让 LLM 真能在对话里调 `cronjob(action="create", ...)` 创建定时任务
- 上游对齐：Python `reference/hermes-agent/tools/cronjob_tools.py` 的 schema 暴露给 LLM 是默认行为；Android 因为 `SystemToolPrompts.getAIAllCategoriesEn/Cn` 硬编码 4 个 category（basic/file/http/memory）漏掉了 cronjob
- R-AGENT-031 实现完成时 cronjob 工具底层 CRUD 已通（Jobs.kt + CronjobTools.cronjob 入口已 real），缺的只是 LLM tool registry 注册

**背景**:
- **两条 tool dispatch path 现状**：
  - Gateway path（IM 入站，飞书/Telegram bot 触发）：`HermesAdapter.kt:77-86` 调 `SystemToolPrompts.getAIAllCategoriesEn/Cn` 拿 categories → 平铺 schema → 喂给 LLM
  - APP UI path（用户直接在 app chat panel 里跟 agent 说话）：`EnhancedAIService.kt:1259` 调 `getAvailableToolsForFunction(...)` → 拿 schema 列表
  - 两条 path 共享同一个 dispatcher（`OperitToolDispatcher.kt:38` → `AIToolHandler.executeTool`），所以 ToolRegistration 这层是单点
- **漏注册的具体表现**：grep `cronjob` 在 `SystemToolPrompts.kt` 文件内 0 命中；agent 在 IM 里能在 system prompt 自我感知段（R-AGENT-031 验收 F 已加）看到"我有 cronjob 工具"，但 LLM 实际拿到的 OpenAI tools array 里没有 `cronjob` schema，调用时被 dispatcher 拒。这就是用户报 "agent 反馈 cronjob 工具在当前版本不可用" 的根因。
- **schema 已现成**：`CronjobTools.kt` 内 `CRONJOB_SCHEMA`（line 357-...）已经是完整的 OpenAI function calling schema，直接复用
- **executor 桥接**：`AIToolHandler` 注册的 executor lambda 需要把 `AITool` 的 parameters 反序列化为 `Map<String, Any?>` 喂给 `CronjobTools.cronjob(...)`；`cronjob(...)` 返回 `ToolResult`（已是 AIToolHandler 期望的形状）

**架构合规**:
- 只动 2 个 app 模块文件：`SystemToolPrompts.kt`（加 cronjob category）+ `ToolRegistration.kt`（注册 executor 桥接）
- 不修改 hermes-android 模块（CronjobTools 已是上游对齐 1:1 翻译）
- 不影响其他 4 个 category（basic/file/http/memory）的现有 schema
- 工具 dispatch 失败必须返回**结构化** ToolResult（`success=false, error=...`），不抛异常炸 dispatcher

**行为**:
- **SystemToolPrompts.kt 加 cronjob category**（`app/.../core/config/SystemToolPrompts.kt`）：
  1. 在 `getAIAllCategoriesEn()` 返回的 list 末尾追加 `cronjobToolsEn`（新建 ToolCategory 实例）
  2. 同样在 `getAIAllCategoriesCn()` 返回的 list 末尾追加 `cronjobToolsCn`
  3. 新建 `private val cronjobToolsEn = ToolCategory(name="cronjob", tools=listOf(<cronjob schema 引用>))` —— 这里 schema 直接 reference `com.xiaomo.hermes.hermes.tools.CRONJOB_SCHEMA`（已存在），通过 `OpenAiFunctionToToolDef` 之类的既有 helper 转成 ToolDef（必要时新增 helper 函数 `Map<String,Any?> -> ToolDef`，不动既有 4 category 的 schema 表）
  4. CN 版同形（中文描述如有需要走 i18n / 既有翻译机制；若 cronjob schema 已是双语描述则复用）
- **ToolRegistration.registerAllTools 加 cronjob executor 桥接**（`app/.../core/tools/ToolRegistration.kt:124`）：
  1. 在 `registerAllTools` 函数体末尾追加：
     ```
     handler.registerTool(
         name = "cronjob",
         executor = { tool ->
             try {
                 val params = tool.parameters // Map<String, Any?>（既有形状）
                 com.xiaomo.hermes.hermes.tools.cronjob(params)
             } catch (e: Exception) {
                 ToolResult(toolName = "cronjob", success = false, error = "cronjob dispatch error: ${e.message}", result = null)
             }
         }
     )
     ```
  2. 不在 dispatcher 层做参数预校验（让 `CronjobTools.cronjob` 内部的 `when (action)` 自己处理 unknown action / missing param 的 toolError 返回；保持 single source of truth）

**验收**:
- **A. SystemToolPrompts cronjob category 落盘**：
  - `SystemToolPrompts.kt::getAIAllCategoriesEn` 函数体或同 file 顶层含 `cronjob` 字面值（category name 或 ToolCategory 名字）
  - 同样 `getAIAllCategoriesCn` 函数体含 `cronjob` 字面值
  - 文件含 `CRONJOB_SCHEMA` 引用（来自 hermes-android 的 tools 包），或等价的 schema-list 引用
- **B. ToolRegistration executor 桥接**：
  - `ToolRegistration.kt::registerAllTools` 函数体含 `"cronjob"` 字符串字面值（registerTool name 参数）
  - 同函数体含 `com.xiaomo.hermes.hermes.tools.cronjob` 引用 或 `CronjobTools.cronjob` 引用
  - 含 `try {` + `catch` 包围（dispatch 异常不炸 handler）
  - 含 `ToolResult(` 构造调用（异常路径返回结构化错误）
- **C. §2 四件套**：维持零；`scan_functional_stubs` 不增（理想再 -0，因为 cronjob 入口在 R-AGENT-031 已经从 stub 切到 real，本 R 仅做"前端注册"）
- **D. 单元测试**：源码扫描 wiring tests，详见 TC-AGENT-034-a..d
- **E. 手测验收**（端到端，依赖 R-AGENT-033 已落地）：
  - 飞书 bot 跟 agent 说 "每 15 分钟提醒我喝水" → agent 真调 `cronjob(action="create", ...)` 成功（不再回"工具不可用"）
  - `cronjob(action="list")` agent 能列出已建任务
  - `cronjob(action="remove", id=...)` agent 能删任务
  - 16 分钟后飞书原会话收到 ai 消息（验证 R-AGENT-033 + R-AGENT-034 闭环）

---

### R-AGENT-035: cron tick 真实路径（CronAgentRunner）接入 origin → IM 投递分支
**来源**:
- 用户 2026-06-15 端到端测试 R-AGENT-033 + R-AGENT-034 时报失败：飞书 bot 让 agent 创建多个定时任务，cronjob 工具调用成功，但定时点到达后**飞书端始终收不到任何 ai 消息**。
- 用户提供的 logcat / dumpsys 诊断（agent dump 给我）：`HermesGatewayController` / `dispatchOutgoing` / `Scheduler` / `cronOutboundDispatcher` 等关键 tag 在 logcat 中**零命中**；但 dumpsys jobscheduler 显示 WorkManager 的 15min 周期 tick job（`u0a517/1024`，`Minimum latency: +14m59s`）在 RUNNABLE 状态——证明 tick 调度本身在跑，但 R-AGENT-033 的 dispatcher 注入路径**根本没被触达**。
- 后续源码审查发现：R-AGENT-033 把 dispatcher 注入到 `Scheduler.kt::deliverResult`，但 `Scheduler.kt` 头注释 line 6-8 明确写着"Android-side cron tick path enters via `CronAlarmReceiver.kt`/`CronTickWorker.kt` directly and dispatches to the agent runtime there, **bypassing this file's `runJob` / `deliverResult`** (kept here for 1:1 Python parity only)"——意味着 R-AGENT-033 的 dispatcher 注入点是**死代码路径**，运行时永远不会被调用。

**背景**:
- **cron 真实运行路径**：
  ```
  WorkManager 15min 周期
    → CronTickWorker.doWork()           [app/core/cron/CronTickWorker.kt:33]
      → getDueJobs() / advanceNextRun
      → CronAgentRunner.run(job)         [app/core/cron/CronAgentRunner.kt:45]
        → ExternalChatRequestExecutor.execute()   ← agent 跑完
        → CronAgentRunner.deliver()      [app/core/cron/CronAgentRunner.kt:112]
          → ChatHistoryManager.addMessage()       ← ★ 只写本地 Room DB
          → GatewayChatEventBus.emit(ProcessingCompleted)  ← 只 ping 本地 UI
  ```
  即：`Scheduler.kt::deliverResult` **从未被 Android 运行时调用**——它只是 Python `hermes/cron/scheduler.py::deliver_result` 的 1:1 平移翻译，保留为 deep_align/对齐参考用。R-AGENT-033 把 `cronOutboundDispatcher` 注入到 `Scheduler.deliverResult`，结构上正确但接到了死路径。
- **R-AGENT-033 链路里其他部分是对的**：
  - 4.1 `SessionContext.kt` 加的 cron-auto-deliver ThreadLocals 正确
  - 4.3 `CronjobTools._originFromEnv()` 改走 `getSessionEnv` 正确
  - 4.4 `Run.kt::_handleMessage` 入站时 `setSessionVars` + `setCronAutoDeliverVars` 正确
  - 4.5 `HermesGatewayController.dispatchOutgoing()` 实现正确
  - **唯一断点**：dispatcher 必须从 `Scheduler.deliverResult`（死代码）迁移/复制到 `CronAgentRunner.deliver()`（真路径）
- **origin 字段已在 jobs.json 持久化**（验证完毕）：
  - `CronjobTools.kt:215` `_createCronJob` 调 `_originFromEnv()` 传给 `Jobs.kt::createJob`
  - `Jobs.kt:471` `createJob` 把 `"origin" to origin` 落到 jobs.json
  - `Jobs.kt:431` `createJob` 算 `effectiveDeliver = deliver ?: if (origin != null) "origin" else "local"`——origin 存在时 deliver 自动是 `"origin"`
  - 即：cron job 数据结构上已经具备"知道自己要回投到哪个 IM 平台"的所有信息（`job["origin"]={platform, chat_id, chat_name, thread_id}` + `job["deliver"]="origin"`），缺的只是 `CronAgentRunner.deliver()` 的消费逻辑。
- **WorkManager 15min 最小周期带来的二级问题**（不在本 R 范围）：
  - 飞书用户 23:42 创建 23:45 单次任务，需要等下一个 tick 边界（最早 24:00），无法做到 3 分钟级精确触发。这是 R-AGENT-031 的设计约束（`ANDROID_CRON_MIN_INTERVAL_MINUTES = 15`），用户应被引导用 ≥15min 间隔。本 R **只解决"投递路径"问题**，"准时性"问题如有需要另起 R-AGENT-036+。
- **dumpsys 看不到 23:45 entry 的原因**：23:45 单次任务**不**会成为独立 JobScheduler 条目，它只是 jobs.json 里的一行；CronTickWorker tick 时由 `getDueJobs()` 扫描 jobs.json 判定 due。

**架构合规**:
- 只动 1 个 app 模块文件：`CronAgentRunner.kt`
- 不修改 hermes-android 模块（origin 持久化链路已就位）
- 不修改 R-AGENT-033 已落地的 4.1/4.3/4.4/4.5 改动（这些都是必须的）
- 保留 `Scheduler.cronOutboundDispatcher` 注入点 + `HermesGatewayController.dispatchOutgoing` 桥（4.2/4.5）：作为 Python 1:1 parity 留存，并在 `Scheduler.kt` 头注释里**追加说明** Android 实际通过 `CronAgentRunner.deliver()` 触达 dispatcher，这一注入点为防止上游 `Scheduler` 真的被调用时仍能正确投递（保险栓）。
- 不破坏 hermes-android → app 单向依赖（`CronAgentRunner` 在 app 模块，可以直接 import `HermesGatewayController.dispatchOutgoing`，无需注入式回调）
- Python 上游对齐：Python `gateway/run.py` cron deliver loop 里就是 `adapters[platform].send(...)`——`CronAgentRunner.deliver()` 调 `HermesGatewayController.dispatchOutgoing()` → `adapter.send()` 是同等语义

**行为**:
- **`CronAgentRunner.deliver()` 加 origin 路径分支**（`app/.../core/cron/CronAgentRunner.kt`）：
  1. 函数签名增加 `job: Map<String, Any?>` 参数（或在 `run()` 里把 job 传进 `deliver()`），让 deliver 有访问 `job["origin"]` 和 `job["deliver"]` 的能力。
  2. 进 deliver 后第一步：读 `job["deliver"] as? String`（默认 `"local"`）+ `job["origin"] as? Map<String, Any?>`。
  3. **deliver = "local" 或 origin 缺失** → 走当前路径（写 ChatHistoryManager + 发 ProcessingCompleted UI 事件），与现状完全一致。
  4. **deliver = "origin" 且 origin 完整**（`platform` + `chat_id` 都非空）→ 走新增 IM 路径：
     - 调 `HermesGatewayController.getInstance(context).dispatchOutgoing(platform=origin["platform"], chatId=origin["chat_id"], text=body, threadId=origin["thread_id"])`
     - 投递成功 → 仍可选写本地 ChatHistoryManager（让 app UI 也能看到 cron 输出）+ 发 `ProcessingCompleted`，与 IM 投递并行不冲突
     - 投递失败（dispatchOutgoing 返 false）→ 把 error 写进 `markJobRun(deliveryError=...)`，**不**抛异常炸 worker；并 fallback 写本地 ChatHistoryManager（让用户至少能在 app 里看到）
  5. **deliver = "platform:chat_id:thread_id"** 显式形式（Python 上游支持）→ 解析后走 dispatchOutgoing 同样路径。
  6. 所有分支都通过 `AppLogger` + `GatewayFileLogger` 打日志（INFO 投递成功 / WARN 投递失败 / DEBUG 选择的分支），让下次 logcat 排查能看到链路。
- **`Scheduler.kt` 头注释追加**（`hermes-android/.../cron/Scheduler.kt`）：
  - 在现有"Android-side cron tick path enters via `CronAlarmReceiver.kt`/`CronTickWorker.kt`..."注释后追加一段说明：`cronOutboundDispatcher` var 在 Android 实际**也**由 `CronAgentRunner.deliver()` 直接消费 origin 字段并调 `HermesGatewayController.dispatchOutgoing`；保留本注入点是 Python parity + 保险栓。
- **不修改的部分**（保留 R-AGENT-033 已落地）：
  - `SessionContext.kt` cron-auto-deliver ThreadLocals → 留
  - `Run.kt::_handleMessage` setSessionVars + setCronAutoDeliverVars + finally clear → 留
  - `CronjobTools._originFromEnv` 改走 getSessionEnv → 留
  - `HermesGatewayController.dispatchOutgoing` → 留（被 CronAgentRunner 调用）
  - `Scheduler.kt::cronOutboundDispatcher` 顶层 var → 留（保险栓 + Python parity）
  - `HermesGatewayController.start()/stop()` 注入/清空 dispatcher → 留（保险栓）

**验收**:
- **A. 编译自检**：`./gradlew :hermes-android:compileDebugKotlin :app:compileDebugKotlin :app:compileDebugUnitTestKotlin` 全绿
- **B. 单测**：新增 `CronAgentRunnerOriginDeliveryWiringTest`（app 模块），断言：
  - `CronAgentRunner.deliver` 函数体含 `job["origin"]` / `job["deliver"]` 字面读取
  - 含 `HermesGatewayController` reference + `dispatchOutgoing(` 调用
  - 含 `"local"` 和 `"origin"` deliver 字面值（防止后续 drift）
  - origin 缺失分支 fallback 到 ChatHistoryManager.addMessage（防 origin 链路把本地 chat 写入误删）
- **C. §2 四件套**：维持零；`scan_functional_stubs` 不增（理想再 -0，因为 R-AGENT-035 接入的是 R-AGENT-031 已计入的能力）
- **D. TC-AGENT-033-c/d/e 处理**：原 TC 断言 `Scheduler.deliverResult` 含 dispatcher 调用，事实上不会 runtime 触达。处理方式：
  - **不删 TC**（ID 不回收）
  - 用例文档里标注 `[SUPERSEDED-BY R-AGENT-035]`，说明断言虽然源码层为 true 但运行时不可达；真正 runtime 验收转移到 TC-AGENT-035-a/b/c/d
  - Wiring test 类（`SchedulerCronOutboundDispatcherWiringTest` 等）不删（保险栓还在），但加 KDoc 标注 R-AGENT-035 关系
- **E. 手测验收**（端到端）：
  - 飞书 bot 跟 agent 说 "每 15 分钟提醒我喝水"
  - 在 jobs.json 里能看到 `origin = {platform: "feishu", chat_id: ..., thread_id: ...}` + `deliver = "origin"`
  - 16 分钟后飞书原会话**真的**收到 ai 消息（这是 R-AGENT-033 + R-AGENT-034 + R-AGENT-035 三 R 闭环的最终验收）
  - GatewayFileLogger 里能看到 `dispatchOutgoing: delivered platform=feishu chatId=... len=...` INFO 行

### R-AGENT-036: HermesAgentLoop 增加 `steer()` 接口（mid-turn 用户引导内核）
**来源**:
- 用户 2026-06-16 提需求："插话功能 — agent 正常处理事情的时候，可以插入新的对话"
- Python 上游 `reference/hermes-agent/run_agent.py:945-953`（字段声明）+ `:3608-3642`（`steer()` 方法）+ `:3644-3658`（`_drain_pending_steer()`）+ `:3660-3721`（`_apply_pending_steer_to_tool_results()`）+ `:3599-3606`（`clear_interrupt()` 清空 pending steer）
- 用户决定：P4 四档全对齐（interrupt + queue + steer + bypass-cmd）；执行顺序：插话先，微信后

**背景**:
- 当前 `HermesAgentLoop` 只有 `beforeNextTurn` hook（`AgentLoop.kt:178/310-320`），无法在「tool batch 进行中」注入用户引导
- Python 上游用 `_pending_steer` + `_pending_steer_lock` 实现并发安全的"待注入文本槽"，agent 跑完一批 tool 后从最后一条 `role:"tool"` 消息尾部追加 `"\n\nUser guidance: {text}"`，保持 message-role alternation 不被破坏（不插入新的 user turn）
- **本 R 只加接口和字段，不加消费点** —— 6 个消费点放在 R-AGENT-037。这样让本 R 单测可独立断言 steer / drain / lock 行为，commit 粒度小

**架构合规**:
- 只动 1 个 hermes-android 模块文件：`AgentLoop.kt`
- 不动 app 模块（消费方在 R-AGENT-037 才接入；UI 暴露在 R-UI-062）
- ThreadLocal 不适用（steer 是「跨线程注入」的反向语义：caller 在 gateway 线程，consumer 在 agent loop 线程），用 `synchronized + @Volatile` 而非 `ThreadLocal`

**行为**:
- **`HermesAgentLoop` 新增字段**（class body）:
  - `@Volatile private var _pendingSteer: String? = null` — 待注入文本槽
  - `private val _pendingSteerLock = Any()` — 并发锁
- **`HermesAgentLoop` 新增方法**:
  1. `fun steer(text: String): Boolean` —— public，对齐 Python `run_agent.py:3608-3642`
     - 空串/空白返 false，不改 `_pendingSteer`
     - 非空 → `text.trim()`，在 lock 内 append（已有内容用 `"\n"` 拼接，否则覆盖）
     - 返 true
  2. `internal fun _drainPendingSteer(): String?` —— 对齐 Python `:3644-3658`
     - lock 内原子读取 + 清空，返回旧值
  3. `internal fun _applyPendingSteerToToolResults(messages: MutableList<Map<String, Any?>>, numToolMsgs: Int)` —— 对齐 Python `:3660-3721`
     - `numToolMsgs <= 0` 或 `messages.isEmpty()` 直接返
     - drain 出 steer text（null 则返）
     - 从 `messages` 末尾向前扫 `numToolMsgs+1` 个位置找 `role == "tool"`，找到则在其 `content` 末尾追加 marker
       - String content → `existing + "\n\nUser guidance: {text}"`
       - List<Map> 多模态 → append `{type:"text", text:"User guidance: {text}"}` block（Anthropic 兼容路径）
     - 找不到 tool 消息 → 文本回填 `_pendingSteer`（让外层 fallback 当做下一轮 user message 投递）
     - 落地后 `Log.i` 一行（含字符数 + 前 120 字预览）
  4. `fun clearPendingSteer()` —— public，给 `EnhancedAIService.cancelConversation` 调，对齐 Python `:3599-3606`
     - lock 内置 null

**验收**:
- **A. 编译自检**：`./gradlew :hermes-android:compileDebugKotlin :hermes-android:compileDebugUnitTestKotlin` 全绿
- **B. 单测**：新增 `HermesAgentLoopSteerTest`（hermes-android 模块），覆盖 TC-AGENT-036-a..i 全套
- **C. §2 四件套**：维持零；`scan_functional_stubs` 不增（本 R 是新功能，不改既有 stub）
- **D. 不接入消费点**：本 R 故意不调 `_applyPendingSteerToToolResults` —— 验收通过 unit-call 直接触发，不走 turn-loop。turn-loop 接入在 R-AGENT-037

### R-AGENT-037: HermesAgentLoop 接入 4 个 steer 消费点 + leftover handoff

**来源**:
- Python `run_agent.py:8029-8032`（per-tool drain — parallel）
- Python `run_agent.py:8040-8045`（post-batch drain — parallel）
- Python `run_agent.py:8397-8401`（per-tool drain — sequential，Kotlin 无 sequential fallback，对齐合并到 §1）
- Python `run_agent.py:8432-8436`（post-batch drain — sequential，同上合并）
- Python `run_agent.py:9032-9080`（pre-API-call drain）
- Python `run_agent.py:11828-11833`（leftover handoff via `result["pending_steer"]`）

**背景**:
R-AGENT-036 给 `HermesAgentLoop` 加了 `steer()` 内核（field + apply + drain + clear），但故意没接到 turn-loop —— `_applyPendingSteerToToolResults` 必须由 loop 主体在每个 tool batch 边界 / 每次 API call 之前调用，否则 steer 进来就石沉大海。

Python 上游有 6 个消费点（parallel 路径 2 个 + sequential 路径 2 个 + pre-API 1 个 + leftover handoff 1 个）。当前 Kotlin `AgentLoop.kt` 只实现 parallel-or-single 一条路径（line 611-667，valid_preps.size <= 1 走单 tool 同步、>= 2 走 coroutineScope async + awaitAll），**没有独立的 sequential fallback**，所以 6 个 Python 点在 Kotlin 里映射成 4 个：per-tool drain、post-batch drain、pre-API drain、leftover handoff。

**架构合规**:
- Python `run_agent.py` 是 source of truth，4 个消费点的语义/位置对齐其 6 个调用点
- 不引入新 abstraction、不改 turn-loop 边界、不动 ChatCompletionServer / ToolDispatcher 接口
- `AgentResult` 加 `val pendingSteer: String? = null` 字段对齐 Python `result["pending_steer"]`（仅在 leftover-handoff 路径非空，否则 null）

**行为**:
1. **B.1 Per-tool drain**：`for (prep in preps)` 循环内每个 `messages.add(role:"tool", ...)`（line 714-717）后立即调 `_applyPendingSteerToToolResults(messages, 1)`。语义：steer 期间正好夹在一个 tool 跑完和下一个 tool 派发之间，本 tool 结果就吃到 marker，不必等整批。对齐 Python `:8029-8032` + `:8397-8401`（合并）。
2. **B.2 Post-batch drain**：`for (prep in preps)` 循环结束后（line 718 之后，turnElapsed 日志之前）调 `_applyPendingSteerToToolResults(messages, preps.size)`。语义：兜底 —— 如果 steer 进来时 batch 已经全部 append 完，post-batch 这次 drain 把它落到最后一个 tool 上。对齐 Python `:8040-8045` + `:8432-8436`（合并）。
3. **B.5 Pre-API-call drain**：`server.chatCompletion(...)` 调用之前（line 475 之前）从 `_drainPendingSteer()` 取一次：
   - 反向扫 `messages`，找最后一个 `role:"tool"`，找到则 append marker `"\n\nUser guidance: $text"`（同 `_applyPendingSteerToToolResults` 一致的多模态分支）
   - 找不到（如 turn 1 还没出过 tool）→ 文本回填 `_pendingSteer`（同 `_applyPendingSteerToToolResults` 的回填分支，保证下一轮 tool batch 后能 drain 到）
   对齐 Python `:9044-9080`。语义：API 长 call 期间用户 steer 进来，下次 API call 之前先 drain 一次让模型这一轮就看到，不必等下个 tool batch（如果模型直接 final response 就再没 tool batch 可 drain 了）。
4. **B.6 Leftover handoff**：每个 `return AgentResult(...)` 退出之前（包括 4 个错误退出 + final 自然退出 + maxTurns 退出共 6 处）调 `_drainPendingSteer()`，结果赋给 `AgentResult.pendingSteer` 字段。对齐 Python `:11828-11833`。语义：所有 turn 跑完后还有未投递的 steer（罕见，如 final response 之后才进来）由 caller 决定怎么处理（可作为下一轮 user message 注入）。
5. **`AgentResult` 加字段**：`val pendingSteer: String? = null` —— 仅 R-AGENT-037 写、caller 读，本 R 不要求 caller 立刻消费（caller 改造在后续 R-GATEWAY-036 做）。

**验收**:
- **A. 编译自检**：`./gradlew :hermes-android:compileDebugKotlin :hermes-android:compileDebugUnitTestKotlin` 全绿
- **B. 单测**：新增 `HermesAgentLoopSteerLoopTest`（hermes-android 模块），用 fake `ChatCompletionServer` + fake `ToolDispatcher` 跑完 1-2 个 turn，覆盖 TC-AGENT-037-a..e
- **C. §2 四件套**：维持零；`scan_functional_stubs` 不增
- **D. 不破坏 R-AGENT-036**：`HermesAgentLoopSteerTest` 全套保持绿
- **E. 不破坏既有 turn-loop**：`_pendingSteer == null` 时所有消费点 short-circuit 零开销，既有行为完全不变

---

### R-GATEWAY-035: GatewayRunner 加载 `_busyInputMode` 字段

**来源**:
- Python `gateway/run.py:217-218`（`_display_cfg["busy_input_mode"]` → 写到 `HERMES_GATEWAY_BUSY_INPUT_MODE` env）
- Python `gateway/run.py:608`（`_busy_input_mode: str = "interrupt"` 类默认）
- Python `gateway/run.py:631`（构造时 `self._busy_input_mode = self._load_busy_input_mode()`）
- Python `gateway/run.py:1389-1402`（`_load_busy_input_mode` loader：env 优先 → config.yaml → "interrupt"，唯一接受 "queue" 切档）
- Python `gateway/run.py:1230-1231`（消费点：`_queue_during_drain_enabled` 仅在 `_restart_requested && mode == "queue"` 返 true）

**为什么要做这个**:
当前 Kotlin `GatewayRunner.queueDuringDrainEnabled()` 读 `config.extra["queue_during_drain"]`，**与 Python 不一致**——Python 用单一 `_busy_input_mode` 字段统一控制 drain 期与（未来）busy 期行为。本 R 让 Kotlin 加载这个字段、对齐 Python loader 优先级（env → config.extra → 默认 "interrupt"），并把现有 `queueDuringDrainEnabled()` 切到读这个字段，使 R-GATEWAY-037（drain reject/queue 完整路径）能直接消费它。

**注意**：
- Python 上游里 `_busy_input_mode` **只**在 `_queue_during_drain_enabled` 里被消费（normal-busy 路径永远 interrupt）。本 R 不动 normal-busy 路径，只把字段引入 + 对齐 `queueDuringDrainEnabled()` 实现。
- 计划文档 `virtual-foraging-ripple.md` 中 §二 R-GATEWAY-035 设想了"normal-busy 也分流到 queue"，但与 Python 实现不符。**对齐 Python 优先**——只动 drain 路径的语义来源。

**改动清单**:
1. **`GatewayRunner` 字段**（Run.kt class 体内，`_pendingEvents` 附近）：
   ```kotlin
   /** R-GATEWAY-035: gateway drain-time busy-input behavior. Either "interrupt" (default) or "queue".
    *  Mirrors Python `gateway/run.py:608, 631`. */
   @Volatile private var _busyInputMode: String = "interrupt"
   ```
2. **构造时初始化**（`init {}` 块或 `start()` 顶部，对齐 Python `:631`）：
   ```kotlin
   _busyInputMode = _loadBusyInputMode()
   ```
3. **新增 loader**（class 体内或 companion 内 static helper）：
   ```kotlin
   /** R-GATEWAY-035: Load drain-time busy-input behavior. Mirrors Python `:1389-1402`.
    *  Priority: env `HERMES_GATEWAY_BUSY_INPUT_MODE` → `config.extra["busy_input_mode"]` → "interrupt".
    *  Only literal "queue" (lowercase, trimmed) flips to queue; everything else → "interrupt". */
   private fun _loadBusyInputMode(): String {
       val envMode = System.getenv("HERMES_GATEWAY_BUSY_INPUT_MODE")?.trim()?.lowercase() ?: ""
       val mode = if (envMode.isNotEmpty()) envMode
                  else (config.extra["busy_input_mode"]?.toString()?.trim()?.lowercase() ?: "")
       return if (mode == "queue") "queue" else "interrupt"
   }
   ```
4. **`queueDuringDrainEnabled()` 重写**（line ~679，从读 `extra["queue_during_drain"]` 切到读 `_busyInputMode`，对齐 Python `:1230-1231`）：
   ```kotlin
   fun queueDuringDrainEnabled(): Boolean = _busyInputMode == "queue"
   ```
   注意：Python `:1231` 还有 `_restart_requested` 守卫——这个由 R-GATEWAY-037 加（本 R 只动语义来源）。
5. **getter 暴露**（供 R-GATEWAY-037 / 测试使用）：
   ```kotlin
   /** R-GATEWAY-035: Read current busy-input mode. */
   fun busyInputMode(): String = _busyInputMode
   ```

**验收**:
- **A. 编译自检**：`./gradlew :hermes-android:compileDebugKotlin :hermes-android:compileDebugUnitTestKotlin` 全绿
- **B. 单测**：新增 `GatewayBusyInputModeTest`（hermes-android 模块），覆盖 TC-GATEWAY-035-a..d
- **C. §2 四件套**：维持零；`scan_functional_stubs` 不增
- **D. 不破坏既有 drain 行为**：`queueDuringDrainEnabled()` 默认依然返 false（默认 mode = interrupt）；既有调用方无需改

### R-GATEWAY-036: gateway 命令路由 + Commands.kt + steerActiveAgent 回调

**Python 上游**：`hermes_cli/commands.py:267-284`（`ACTIVE_SESSION_BYPASS_COMMANDS`）+ `gateway/run.py:3225-3395`（`_handle_message` 大量命令分发）。

**目标 (本 R 范围)**：让 gateway 在 active session busy 期间识别 slash 命令并按命令名分流。**最小可用范围**——只接入与"插话功能"直接相关的 3 个命令，其它已识别命令礼貌拒绝；非命令文本沿用现状（fall-through 到 R-GATEWAY-035 的 busy 路径）：

| 命令 | 动作 | Python 对齐 |
|---|---|---|
| `/steer <text>` | 调 `steerActiveAgent(sessionKey, text)` 回调 → R-AGENT-036 的 `loop.steer()` | `gateway/run.py:3290-3334` |
| `/queue <text>` | 仅入 `_pendingEvents`，不打断 | `gateway/run.py:3261-3282` |
| `/stop` | 调 `cancelActiveAgent(sessionKey)` 回调 | `gateway/run.py:3225-3245` |
| 其它已识别命令（`/agents` `/approve` `/deny` `/help` `/new` `/profile` `/restart` `/status` `/update` `/background` `/commands`） | 礼貌拒绝："Agent is running — `/${cmd}` can't run mid-turn"；不打断、不入队 | `gateway/run.py:3340-3395` |

**未来扩展**：`/yolo` `/verbose` 内联开关、`/approve` `/deny` 工具批准、其它命令的实际语义留给后续 R（不在本 R 范围）。

**改动**:

1. **新建 `hermes-android/.../gateway/Commands.kt`**：
   ```kotlin
   /** R-GATEWAY-036: command names that bypass busy gate. Mirrors Python
    * `hermes_cli/commands.py:267-284`. */
   internal val ACTIVE_SESSION_BYPASS_COMMANDS = setOf(
       "agents", "approve", "background", "commands", "deny", "help", "new",
       "profile", "queue", "restart", "status", "steer", "stop", "update",
   )

   /** R-GATEWAY-036: parse a leading slash command from `text`. Returns
    * (cmdName, argText) or null if `text` does not start with a recognized
    * `/<cmd>` token. Mirrors Python `hermes_cli/commands.py:resolve_command`. */
   internal fun resolveCommand(text: String): Pair<String, String>? { ... }
   ```
   语义：
   - 必须以 `/` 开头（位置 0；前导空白允许）
   - 第一个 token（`\s` 切分）去掉前导 `/`，lowercase，trim
   - 必须落在 `ACTIVE_SESSION_BYPASS_COMMANDS` 集合
   - 余下文本作为 argText（trim）
   - 不在集合 → 返 null（让 fall-through 走非命令路径）

2. **`Run.kt::GatewayRunner` 加 2 个回调字段（构造参数）**：
   ```kotlin
   /** R-GATEWAY-036: forward `/steer <text>` to the active agent loop.
    * Returns true if accepted, false if no active agent or steer rejected. */
   private val steerActiveAgent: (suspend (sessionKey: String, text: String) -> Boolean)? = null,
   /** R-GATEWAY-036: forward `/stop` to the active agent loop.
    * Returns true if cancellation was issued. */
   private val cancelActiveAgent: (suspend (sessionKey: String) -> Boolean)? = null,
   ```

3. **`Run.kt::_handleMessage` busy 分支顶部插入命令路由**（在现有 busy guard 之前）：
   ```kotlin
   val cmd = resolveCommand(event.text)
   if (cmd != null) {
       when (cmd.first) {
           "steer" -> {
               val argText = cmd.second
               if (argText.isBlank()) { _sendAck(sessionKey, "/steer needs a message"); return }
               val ok = steerActiveAgent?.invoke(sessionKey, argText) ?: false
               _sendAck(sessionKey, if (ok) "Steered: $argText" else "No active agent to steer")
               return
           }
           "queue" -> {
               mergePendingMessageEvent(_pendingEvents, sessionKey, event.copy(text = cmd.second))
               _sendAck(sessionKey, "⏳ Queued for the next turn")
               return
           }
           "stop" -> {
               val ok = cancelActiveAgent?.invoke(sessionKey) ?: false
               _sendAck(sessionKey, if (ok) "🛑 Stopping current task" else "No active agent to stop")
               return
           }
           else -> {
               // Other recognized commands: reject mid-turn (don't interrupt, don't queue).
               _sendAck(sessionKey, "Agent is running — `/${cmd.first}` can't run mid-turn")
               return
           }
       }
   }
   // fall through to existing busy interrupt/queue path (R-GATEWAY-035)
   ```

4. **`_sendAck(sessionKey, text)`** 复用现有 `_sendBusyAck` 的发送通道（gateway IM ack）。如已有则复用；否则抽出来。

**约束**:
- **本 R 不接入 caller 端 wiring** —— `steerActiveAgent` / `cancelActiveAgent` 默认 null。caller 端（`HermesGatewayController` 等）的弱引用注册留到 R-UI-062。
- **本 R 不动 Python `_handle_message` 的命令大块翻译** —— 太大。本 R 是"骨架 + 3 命令"，剩余命令的实际语义留给后续 R。
- **不破坏现状**：当 callback 为 null（如本 R 单测构造），`/steer` `/stop` 返"No active agent" ack；不抛错；不影响其它消息路径。

**验收**:
- **A. 编译自检** 全绿
- **B. 单测**：`GatewayCommandRoutingTest`（覆盖 TC-GATEWAY-036-a..f）全绿
- **C. §2 四件套** 维持零；`scan_functional_stubs` 不增
- **D. 既有 busy 行为不变**：非 `/<cmd>` 文本 fall through，与 R-GATEWAY-035 之前完全一致
