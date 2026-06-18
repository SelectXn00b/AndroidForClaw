# Hermes Test Cases (TC-Doc)

> **状态**: 2026-04-26（§0.1 三阶段文档的第 ② 阶段）
> **上游**: `docs/hermes-requirements.md`（每条 TC 引用一个 R-ID）
> **下游**: `hermes-android/src/test/**` 的 JUnit 方法（每条 TC 至少一个 `@Test`，注释里必须写 `TC-XXX-NNN-x`）
>
> **ID 规则**: `TC-<DOMAIN>-<NNN>-<letter>`，NNN 对齐需求编号，letter 枚举分支 / 变体。TC ID 本身不回收；R-ID 自 2026-04-26 aggressive prune 起用家族 / 子系统级新编号（见 requirements.md 顶部 note）。
>
> **状态**: ✅ 已落地（注明 test 类#方法名）/ 🟡 待写 / 🔴 阻塞
>
> **2026-04-26 验-R cascade（一轮）**: requirements.md 从 ~250 条实现细节级 R 剪裁为 57 条家族 R；本文件 ~494 条 TC 的"验 R"列重指到新家族 R-ID。
>
> **2026-04-26 验-R cascade（二轮）**: requirements.md 57 → 34 条（删 CONFIG 域 / TOOL 15→3 / AGENT 7→3 / UI 5→1）；本文件再批量重指死 ID（R-TOOL-004..015 / R-AGENT-004..007 / R-UI-002..005 / R-CONFIG-001..003）到新合并 R-ID。TC 本身（输入 / 期望 / 测试方法）未动。
>
> **2026-04-26 验-R cascade（三轮）**: requirements.md 34 → 42 条（补 R-PARSER-060/070/080/090 / R-AGENT-008 / R-ACP-004 / R-MCP-003 / R-SKILL-003；修 Python 源路径）。本文件对应补：PARSER 新增 14 TC（4 家 parser，对应已落地的 `DeepseekV3ParserTest` / `Glm45ParserTest` / `KimiK2ParserTest` / `MistralParserTest`）；新增 CORE / AGENT-TurnLoop / AGENT-CredentialPool / ACP-CopilotClient 段；MCP 把 OAuth 相关 TC 从 R-MCP-001 改指 R-MCP-003，补 R-MCP-002；SKILL 补 R-SKILL-003 覆盖（复用 `SkillsSync*` 已有 TC）。

---

## 索引

| 域 | 已落地 TC | 待写 TC | 测试类 |
|---|---|---|---|
| CORE | 4 (alignment) | 0 | §2 三件套对齐脚本 |
| PARSER | 36 | 0 | `MissingParsersTest.kt` + `DeepseekV3ParserTest.kt` + `Glm45ParserTest.kt` + `KimiK2ParserTest.kt` + `MistralParserTest.kt` |
| AGENT (ErrorClassifier) | 48 | 0 | `ErrorClassifierTest.kt` |
| AGENT (Helpers) | 29 | 0 | `AgentHelpersTest.kt` + `ChatUtilsTest.kt` |
| AGENT (FileSafety) | 10 | 0 | `FileSafetyTest.kt` |
| AGENT (TurnLoop) | 3 (E2E) + 6 (unit) | 0 | `scripts/e2e/*.sh` + `HermesAgentLoopBeforeNextTurnTest.kt` |
| AGENT (CredentialPool) | 0 | 7 | `CredentialPoolTest` (待建) |
| AGENT (PersistentInstruction) | 0 | 8 | `PersistentInstructionInjectionTest`, `MemoryLibraryPersistentInstructionGuardTest` (待建) |
| ACP | 49 | 4 (Copilot client) | `AcpToolsTest.kt`, `AcpAuthTest.kt` + `CopilotAcpClientTest`（待建）|
| MCP | 13 | 1 | `McpToolTest.kt`, `McpOAuthTest.kt`, `ManagedToolGatewayTest.kt` |
| 其他域 | 详见各域表 | 详见各域表 | — |

跑当前所有已落地：
```bash
./gradlew :hermes-android:testDebugUnitTest \
  --tests "com.xiaomo.hermes.hermes.MissingParsersTest" \
  --tests "com.xiaomo.hermes.hermes.DeepseekV3ParserTest" \
  --tests "com.xiaomo.hermes.hermes.Glm45ParserTest" \
  --tests "com.xiaomo.hermes.hermes.KimiK2ParserTest" \
  --tests "com.xiaomo.hermes.hermes.MistralParserTest" \
  --tests "com.xiaomo.hermes.hermes.HermesAgentLoopBeforeNextTurnTest" \
  --tests "com.xiaomo.hermes.hermes.agent.ErrorClassifierTest" \
  --tests "com.xiaomo.hermes.hermes.agent.AgentHelpersTest" \
  --tests "com.xiaomo.hermes.hermes.agent.FileSafetyTest" \
  --tests "com.xiaomo.hermes.hermes.acp.AcpToolsTest" \
  --tests "com.xiaomo.hermes.hermes.acp.AcpAuthTest" \
  --tests "com.xiaomo.hermes.hermes.tools.McpToolTest" \
  --tests "com.xiaomo.hermes.hermes.tools.McpOAuthTest" \
  --tests "com.xiaomo.hermes.hermes.tools.ManagedToolGatewayTest"
```

---

## 域 CORE

CORE 域的两条顶层约束（R-CORE-001 1:1 对齐 / R-CORE-002 冲突以 Hermes 为准）是**元需求**，不走 JUnit 单测——由 CLAUDE.md §2 的三件套对齐脚本守护。此处登记三个脚本作为 CORE 的验收手段。

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-CORE-001-a | R-CORE-001 | `python3.11 scripts/verify_align.py --hermes $H --android $A` | 缺失文件数 0 | alignment | `scripts/hermes-align/scripts/verify_align.py` ✅ |
| TC-CORE-001-b | R-CORE-001 | `python3.11 scripts/scan_stubs.py --android $A` | stub 数 0 | alignment | `scripts/hermes-align/scripts/scan_stubs.py` ✅ |
| TC-CORE-001-c | R-CORE-001 | `python3.11 references/deep_align.py --pybase $H --kbase $A --json` | findings 0 | alignment | `scripts/hermes-align/references/deep_align.py` ✅ |
| TC-CORE-002-a | R-CORE-002 | **[DELETED 2026-04-26]** 反向冗余守卫（多余类/方法/常量）移除；`check_reverse.py` 已删除，CORE-002 由 TC-CORE-002-b（语义对齐）单独覆盖。 | — | — | — |
| TC-CORE-002-b | R-CORE-002 | HermesApp 与 Hermes 存在同名类 | 语义与 Hermes 一致（非 Kotlin 自家分叉） | alignment | 由 `verify_align.py` 的结构对齐 + `deep_align.py` body 字面对齐双重保障 ✅ |

---

## 域 PARSER

测试类: `hermes-android/src/test/java/com/xiaomo/hermes/hermes/MissingParsersTest.kt`（Longcat / Qwen3Coder / Llama / Glm47 / Qwen / DeepseekV31）+ `DeepseekV3ParserTest.kt` / `Glm45ParserTest.kt` / `KimiK2ParserTest.kt` / `MistralParserTest.kt`（各一个独立测试类）

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-PARSER-001-a | R-PARSER-001 | `Longcat parses single tool call` | ✅ |
| TC-PARSER-002-a | R-PARSER-001 | `Longcat returns null when no tag` | ✅ |
| TC-PARSER-003-a | R-PARSER-001 | `Longcat skips entries with empty name` | ✅ |
| TC-PARSER-004-a | R-PARSER-001 | `Longcat supportedModels includes longcat` | ✅ |
| TC-PARSER-010-a | R-PARSER-010 | `Qwen3Coder parses function with parameters` | ✅ |
| TC-PARSER-011-a | R-PARSER-010 | `Qwen3Coder converts boolean parameter` | ✅ |
| TC-PARSER-012-a | R-PARSER-010 | `Qwen3Coder no tool_call returns null` | ✅ |
| TC-PARSER-013-a | R-PARSER-010 | `Qwen3Coder _parseFunctionCall returns null when no gt` | ✅ |
| TC-PARSER-014-a | R-PARSER-010 | `Qwen3Coder supportedModels` | ✅ |
| TC-PARSER-020-a | R-PARSER-020 | `Llama parses arguments object` | ✅ |
| TC-PARSER-021-a | R-PARSER-020 | `Llama accepts parameters key as synonym` | ✅ |
| TC-PARSER-022-a | R-PARSER-020 | `Llama no json and no token returns null` | ✅ |
| TC-PARSER-023-a | R-PARSER-020 | `Llama invalid json returns null` | ✅ |
| TC-PARSER-024-a | R-PARSER-020 | `Llama supportedModels` | ✅ |
| TC-PARSER-030-a | R-PARSER-030 | `Glm47 supportedModels` | ✅ |
| TC-PARSER-031-a | R-PARSER-030 | `Glm47 uses arg_key arg_value syntax` | ✅ |
| TC-PARSER-032-a | R-PARSER-030 | `Glm47 no tool call returns null` | ✅ |
| TC-PARSER-040-a | R-PARSER-040 | `Qwen supportedModels contains qwen` + `Qwen parses Hermes-format tool call` | ✅ |
| TC-PARSER-050-a | R-PARSER-050 | `DeepseekV31 supportedModels` | ✅ |
| TC-PARSER-051-a | R-PARSER-050 | `DeepseekV31 parses heart-emoji delimited call` | ✅ |
| TC-PARSER-052-a | R-PARSER-050 | `DeepseekV31 no delimiter returns empty list` | ✅ |
| TC-PARSER-053-a | R-PARSER-050 | `DeepseekV31 parses multiple calls` | ✅ |
| TC-PARSER-060-a | R-PARSER-060 | `parse no tool call returns original content and null` (DeepseekV3ParserTest) | ✅ |
| TC-PARSER-061-a | R-PARSER-060 | `parse single tool call` (DeepseekV3ParserTest) | ✅ |
| TC-PARSER-062-a | R-PARSER-060 | `parse multiple tool calls` (DeepseekV3ParserTest) | ✅ |
| TC-PARSER-063-a | R-PARSER-060 | `parse tool call with preceding text` (DeepseekV3ParserTest) | ✅ |
| TC-PARSER-070-a | R-PARSER-070 | `parse single tool call with arg tags` (Glm45ParserTest) | ✅ |
| TC-PARSER-071-a | R-PARSER-070 | `parse no tool call` (Glm45ParserTest) | ✅ |
| TC-PARSER-072-a | R-PARSER-070 | `supportedModels includes glm45` (Glm45ParserTest) | ✅ |
| TC-PARSER-080-a | R-PARSER-080 | `parse single tool call` (KimiK2ParserTest) | ✅ |
| TC-PARSER-081-a | R-PARSER-080 | `parse multiple tool calls` (KimiK2ParserTest) | ✅ |
| TC-PARSER-082-a | R-PARSER-080 | `parse no tool calls` (KimiK2ParserTest) | ✅ |
| TC-PARSER-090-a | R-PARSER-090 | `parse v11 format - single tool call` (MistralParserTest) | ✅ |
| TC-PARSER-090-b | R-PARSER-090 | `parse v11 format - multiple tool calls` + `parse pre-v11 format - JSON array` + `parse pre-v11 format - single JSON object` (MistralParserTest) | ✅ |
| TC-PARSER-091-a | R-PARSER-090 | `parse no tool calls` (MistralParserTest) | ✅ |
| TC-PARSER-092-a | R-PARSER-090 | `tool call IDs are 9 chars` (MistralParserTest) | ✅ |

---

## 域 AGENT — ErrorClassifier

测试类: `hermes-android/src/test/java/com/xiaomo/hermes/hermes/agent/ErrorClassifierTest.kt`

### 状态码分支

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-AGENT-001-a | R-AGENT-002 | `401 routes to auth with rotate and fallback` | ✅ |
| TC-AGENT-002-a | R-AGENT-002 | `403 plain routes to auth without rotate` | ✅ |
| TC-AGENT-003-a | R-AGENT-002 | `403 with key limit text routes to billing` | ✅ |
| TC-AGENT-004-a | R-AGENT-002 | `402 generic routes to billing` | ✅ |
| TC-AGENT-005-a | R-AGENT-002 | `402 with transient usage limit text routes to rate_limit` | ✅ |
| TC-AGENT-006-a | R-AGENT-002 | `404 is model_not_found` | ✅ |
| TC-AGENT-007-a | R-AGENT-002 | `413 is payload_too_large with compress` | ✅ |
| TC-AGENT-008-a | R-AGENT-002 | `429 is rate_limit with rotate and fallback` | ✅ |
| TC-AGENT-009-a | R-AGENT-002 | `long context tier trips before 429 rate_limit` | ✅ |
| TC-AGENT-010-a | R-AGENT-002 | `thinking signature trips before generic 400 classification` | ✅ |
| TC-AGENT-011-a | R-AGENT-002 | `400 with context_length msg routes to context_overflow` | ✅ |
| TC-AGENT-012-a | R-AGENT-002 | `400 with invalid model msg routes to model_not_found` | ✅ |
| TC-AGENT-013-a | R-AGENT-002 | `400 rate-limit text beats billing pattern order` | ✅ |
| TC-AGENT-014-a | R-AGENT-002 | `400 billing pattern routes to billing` | ✅ |
| TC-AGENT-015-a | R-AGENT-002 | `400 with generic short body + large session infers context_overflow` | ✅ |
| TC-AGENT-016-a | R-AGENT-002 | `400 with unknown text falls to format_error` | ✅ |
| TC-AGENT-017-a | R-AGENT-002 | `500 is server_error retryable` | ✅ |
| TC-AGENT-018-a | R-AGENT-002 | `503 is overloaded retryable` + `529 is overloaded (Anthropic-style)` | ✅ |
| TC-AGENT-019-a | R-AGENT-002 | `other 4xx falls into format_error` | ✅ |
| TC-AGENT-020-a | R-AGENT-002 | `other 5xx falls into server_error retryable` | ✅ |

### Error code 分支

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-AGENT-021-a | R-AGENT-002 | `error code resource_exhausted maps to rate_limit` | ✅ |
| TC-AGENT-022-a | R-AGENT-002 | `error code insufficient_quota maps to billing` | ✅ |
| TC-AGENT-023-a | R-AGENT-002 | `error code context_length_exceeded maps to context_overflow` | ✅ |
| TC-AGENT-024-a | R-AGENT-002 | `error code model_not_found maps to model_not_found` | ✅ |

### 消息模式分支

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-AGENT-030-a | R-AGENT-002 | `msg-only payload too large routes to payload_too_large` | ✅ |
| TC-AGENT-031-a | R-AGENT-002 | `msg-only billing pattern routes to billing` | ✅ |
| TC-AGENT-032-a | R-AGENT-002 | `msg-only rate_limit pattern routes to rate_limit` | ✅ |
| TC-AGENT-033-a | R-AGENT-002 | `msg-only context overflow pattern routes to context_overflow` | ✅ |
| TC-AGENT-034-a | R-AGENT-002 | `msg-only auth pattern routes to auth` | ✅ |
| TC-AGENT-035-a | R-AGENT-002 | `msg-only model not found pattern routes to model_not_found` | ✅ |

### Transport / Disconnect / Unknown

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-AGENT-040-a | R-AGENT-002 | `IOException is classified as timeout` + `SocketTimeoutException is classified as timeout` | ✅ |
| TC-AGENT-041-a | R-AGENT-002 | `disconnect plus large session infers context_overflow` | ✅ |
| TC-AGENT-042-a | R-AGENT-002 | `disconnect alone routes to timeout` | ✅ |
| TC-AGENT-043-a | R-AGENT-002 | `totally unknown error falls through to unknown` | ✅ |

### 辅助提取器

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-AGENT-050-a | R-AGENT-002 | `_extractErrorCode reads error dot code` | ✅ |
| TC-AGENT-051-a | R-AGENT-002 | `_extractErrorCode falls back to top-level code` | ✅ |
| TC-AGENT-052-a | R-AGENT-002 | `_extractErrorCode reads integer code as string` | ✅ |
| TC-AGENT-053-a | R-AGENT-002 | `_extractErrorCode returns empty for empty body` | ✅ |
| TC-AGENT-054-a | R-AGENT-002 | `_extractMessage prefers body error message over throwable` + `_extractMessage trims and caps at 500 chars` | ✅ |
| TC-AGENT-055-a | R-AGENT-002 | `_extractMessage falls back to throwable when body empty` | ✅ |
| TC-AGENT-056-a | R-AGENT-002 | `_extractStatusCode returns null when absent` + `_extractStatusCode reads reflective getStatusCode` | ✅ |
| TC-AGENT-057-a | R-AGENT-002 | `_extractErrorBody parses body on the exception` | ✅ |
| TC-AGENT-060-a | R-AGENT-002 | `isAuth true for auth and auth_permanent only` | ✅ |
| TC-AGENT-061-a | R-AGENT-002 | `FailoverReason enum values match python names` | ✅ |

---

## 域 AGENT — Helpers (SmartModelRouting / RetryUtils / TitleGenerator)

测试类: `hermes-android/src/test/java/com/xiaomo/hermes/hermes/agent/AgentHelpersTest.kt`

### SmartModelRouting

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-AGENT-100-a | R-AGENT-002 | `smart routing short simple message goes cheap` | ✅ |
| TC-AGENT-101-a | R-AGENT-002 | `smart routing code fence bumps complexity` | ✅ |
| TC-AGENT-102-a | R-AGENT-002 | `smart routing long detailed analysis goes expensive` | ✅ |
| TC-AGENT-103-a | R-AGENT-002 | `smart routing mid-length hands off to current model` | ✅ |

### RetryUtils

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-AGENT-110-a | R-AGENT-003 | `calculateRetryDelayMs caps at maxMs` | ✅ |
| TC-AGENT-111-a | R-AGENT-003 | `calculateRetryDelayMs exponential no jitter` | ✅ |
| TC-AGENT-112-a | R-AGENT-003 | `calculateRetryDelayMs jitter stays within 1x-1_5x` | ✅ |
| TC-AGENT-113-a | R-AGENT-003 | `shouldRetry caps at maxRetries` | ✅ |
| TC-AGENT-114-a | R-AGENT-003 | `shouldRetry accepts IO exceptions` | ✅ |
| TC-AGENT-115-a | R-AGENT-003 | `shouldRetry rejects unrelated exception` | ✅ |
| TC-AGENT-116-a | R-AGENT-003 | `jitteredBackoff stays within cap` | ✅ |
| TC-AGENT-117-a | R-AGENT-003 | `CountIterator always hasNext and increments` | ✅ |
| TC-AGENT-118-a | R-AGENT-003 | `withRetry returns on first success` | ✅ |
| TC-AGENT-119-a | R-AGENT-003 | `withRetry rethrows non-retriable exception immediately` | ✅ |

### TitleGenerator

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-AGENT-130-a | R-AGENT-003 | `generate returns New Chat for empty input` | ✅ |
| TC-AGENT-131-a | R-AGENT-003 | `generate normalizes whitespace and returns short title as-is` | ✅ |
| TC-AGENT-132-a | R-AGENT-003 | `generate strips stop words when truncating long input` | ✅ |
| TC-AGENT-133-a | R-AGENT-003 | `generate falls back to truncation when all words are stop words` | ✅ |
| TC-AGENT-134-a | R-AGENT-003 | `generateFromMessages picks first user message` | ✅ |
| TC-AGENT-135-a | R-AGENT-003 | `generateFromMessages New Chat when no user` | ✅ |
| TC-AGENT-136-a | R-AGENT-003 | `TITLE_PROMPT contains instructions` | ✅ |
| TC-AGENT-137-a | R-AGENT-003 | `generateTitle returns null stub` | ✅ |

### ChatUtils — Thinking 标签提取（飞书自动总结无响应 bug 修复）

测试类: `app/src/test/java/com/ai/assistance/operit/util/ChatUtilsTest.kt`

背景: commit 57725517 在 `OperitChatCompletionServer.chatCompletion()` 引入 `ChatUtils.extractThinkingContent`，原正则 `<think(?:ing)?>([\s\S]*?)</think(?:ing)?>` 不兼容**未闭合** `<think>`（流式被截断 / 模型输出不完整时）。脏开放 think 标签落库 → 下一轮请求历史被污染 → 部分模型（DeepSeek / OpenRouter / MiMo think）首轮空回复 → AgentLoop 空回复兜底在 `turn==0 && reasoning==null` 不触发 → 飞书网关读到空消息，用户必须 `/new` 才能恢复。

修复: `extractThinkingContent` 与同文件 `removeThinkingContent` 对齐，未闭合分支用 `\z` 吃到末尾；`OpenAIProvider.comparableContentForTurn` 的清洗扩到 `TOOL_CALL` kind；`EnhancedAIService.toOpenAiMessages`（飞书 / bot relay 路径）发请求前对 assistant/tool_result 历史 content 过一次 `removeThinkingContent`。

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-AGENT-003-thinkfix-a | R-AGENT-003 | `extractThinkingContent_handlesUnclosedThink` | ✅ |
| TC-AGENT-003-thinkfix-b | R-AGENT-003 | `extractThinkingContent_handlesUnclosedThinking` | ✅ |
| TC-AGENT-003-thinkfix-c | R-AGENT-003 | `extractThinkingContent_closedTagStillExtractsCorrectly` | ✅ |
| TC-AGENT-003-thinkfix-d | R-AGENT-003 | `extractThinkingContent_noThinkTagPreservesContent` | ✅ |
| TC-AGENT-003-thinkfix-e | R-AGENT-003 | `extractThinkingContent_closedFollowedByUnclosed` | ✅ |
| TC-AGENT-003-thinkfix-f | R-AGENT-003 | `extractThinkingContent_alignsWithRemoveThinkingContent_closed` | ✅ |
| TC-AGENT-003-thinkfix-g | R-AGENT-003 | `extractThinkingContent_alignsWithRemoveThinkingContent_unclosed` | ✅ |

### StructuredToolCallBridge — `compileHistoryForProvider` 保留 `reasoningContent`（飞书自动总结后空回复 bug 真因修复）

测试类: `app/src/test/java/com/ai/assistance/operit/api/chat/llmprovider/StructuredToolCallBridgeTest.kt`

背景: thinkfix 段（上）只解决了"脏 `<think>` 污染历史 content"，但飞书自动总结后空回复仍偶发。真因：`StructuredToolCallBridge.compileHistoryForProvider` 在合并 ASSISTANT / TOOL_CALL turn 进同一 ProviderHistoryBlock 时**完全丢弃了 `reasoningContent` 字段**。MiMo thinking-mode 协议要求带 `tool_calls` 的 assistant 历史必须回传 `reasoning_content`，否则返回 400 code:3 → 飞书显示 (empty response)。同时在 `EnhancedAIService.toOpenAiMessages` 里给 assistant 角色补一条 fallback：当 `turn.reasoningContent` 为空但 content 内嵌 `<think>...</think>` 时，用 `ChatUtils.extractThinkingContent` 抽出来当 reasoning_content。

修复点:
- `StructuredToolCallBridge.kt`: `flushCurrentBlock` 把 `currentReasoningContent` 写进 `PromptTurn.reasoningContent`；`appendToBlock` 在 ASSISTANT block 上取第一个非空 `reasoningContent` 保留（与 `OpenAIProvider.queueToolCalls` 行为一致），不被后续 turn 覆盖；USER / TOOL_RESULT block 不带 reasoning。
- `EnhancedAIService.toOpenAiMessages`: assistant 角色用 `extractThinkingContent` 拆分 content / 内嵌 think；`turn.reasoningContent` 优先，否则用抽出来的 think 当 fallback 写进 OpenAI 消息的 `reasoning_content` 字段。

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-AGENT-003-rcfix-a | R-AGENT-003 | `compileHistoryForProvider_preservesReasoningContentOnSingleAssistantTurn` | 🟡 |
| TC-AGENT-003-rcfix-b | R-AGENT-003 | `compileHistoryForProvider_preservesFirstNonEmptyReasoningOnMergedAssistantTurns` | 🟡 |
| TC-AGENT-003-rcfix-c | R-AGENT-003 | `compileHistoryForProvider_keepsReasoningWhenAssistantThenToolCallMerged` | 🟡 |
| TC-AGENT-003-rcfix-d | R-AGENT-003 | `compileHistoryForProvider_userBlockHasNoReasoning` | 🟡 |
| TC-AGENT-003-rcfix-e | R-AGENT-003 | `compileHistoryForProvider_toolResultBlockHasNoReasoning` | 🟡 |
| TC-AGENT-003-rcfix-f | R-AGENT-003 | `compileHistoryForProvider_summaryMergedIntoUserBlockDoesNotLeakReasoning` | 🟡 |
| TC-AGENT-003-rcfix-g | R-AGENT-003 | `compileHistoryForProvider_emptyHistoryReturnsEmpty` | 🟡 |

---

## 域 AGENT — FileSafety

测试类: `hermes-android/src/test/java/com/xiaomo/hermes/hermes/agent/FileSafetyTest.kt`

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-AGENT-160-a | R-AGENT-003 | `buildWriteDeniedPaths includes ssh private keys` | ✅ |
| TC-AGENT-161-a | R-AGENT-003 | `buildWriteDeniedPaths includes shell rc files` | ✅ |
| TC-AGENT-162-a | R-AGENT-003 | `buildWriteDeniedPrefixes ends with separator` | ✅ |
| TC-AGENT-163-a | R-AGENT-003 | `isWriteDenied for absolute blocked path` | ✅ |
| TC-AGENT-164-a | R-AGENT-003 | `isWriteDenied for prefix matches sub path` | ✅ |
| TC-AGENT-165-a | R-AGENT-003 | `isWriteDenied returns false for ordinary user file` | ✅ |
| TC-AGENT-166-a | R-AGENT-003 | `getSafeWriteRoot returns null when env unset` | ✅ |
| TC-AGENT-167-a | R-AGENT-003 | `getReadBlockError non-hermes path returns null` | ✅ |
| TC-AGENT-168-a | R-AGENT-003 | `getReadBlockError hermes cache path returns message` | ✅ |
| TC-AGENT-169-a | R-AGENT-003 | `getReadBlockError expands tilde` | ✅ |

---

## 域 AGENT — TurnLoop (R-AGENT-001)

R-AGENT-001 描述 agent turn-loop 内核，验收以 **E2E 为主**（§3 三脚本要求 `aiResponsePreview` 含脚本种下的 TOKEN，是 agent-level 正确性的充分信号），辅以 `HermesAgentLoopBeforeNextTurnTest` 对 hook 行为的单元覆盖。

**TC-AGENT-200-c 改写**: 旧 c 验 BuiltInKeyProvider 解密 + OpenRouter；新 c 改 OpenCode Zen public-key（无解密）。ID 保留维持追溯链；R 引用从 R-AGENT-001 迁到 R-AGENT-002。

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-AGENT-200-a | R-AGENT-001 | 纯聊天（无工具），广播 EXTERNAL_CHAT + TOKEN 要求 | `aiResponsePreview` 含 TOKEN；无 4xx/NonRetriable | e2e | `scripts/e2e/test_api_config_e2e.sh` ✅ |
| TC-AGENT-200-b | R-AGENT-001 | 强制 sleep 工具的 chat | `HermesBridge/Tool dispatch IN+OUT` + `aiResponsePreview` 含 TOKEN（证明 agent 读了 tool_result） | e2e | `scripts/e2e/test_tool_call_e2e.sh` ✅ |
| TC-AGENT-200-c | R-AGENT-002 | 新用户路径（清 api_settings + model_configs DataStore）+ OpenCode Zen public-key 兜底 | `aiResponsePreview` 含 TOKEN；catalog 选出的 free model 写入 default 配置；apiKey 字面量 == "public" | e2e | `scripts/e2e/test_builtin_key_e2e.sh` 🟡 (代码路径已验证 ✅；当前 HEAD 受 OpenCode Zen public-key 每日共享配额 `FreeUsageLimitError 429` 阻塞，等冷却后单跑可绿) |
| TC-AGENT-200-d | R-AGENT-002 | hermes-android `fetchModelsDevWithSnapshot` 在 mem 空 + 注入 snapshot 字符串时 | 返回非空 Map，含 `opencode` provider | unit | `ModelsDevSnapshotTest#fetchSnapshot_loadsBundledAsset_whenNetworkAndDiskMissing` 🟢 |
| TC-AGENT-200-e | R-AGENT-002 | `OpenCodeZenCatalog.listFreeModels(catalog)` 输入混合 cost / tool_call / NOISE_PATTERN 模型 | 仅返回 cost.input==0 且 tool_call==true 且非 noise；按 release_date desc 排序 | unit | `OpenCodeZenCatalogTest#listFreeModels_filtersByCostZero_andSortsByReleaseDateDesc` 🟢 |
| TC-AGENT-200-f | R-AGENT-002 | `OpenCodeZenCatalog.selectDefaultFreeModel(catalog)` catalog 含多个 free model | 返回 release_date 最新者；string 非空 | unit | `OpenCodeZenCatalogTest#selectDefaultFreeModel_picksLatestToolCapableFreeModel` 🟢 |
| TC-AGENT-200-g | R-AGENT-002 | `selectDefaultFreeModel` 在 catalog 完全为空时 | 返回 BASELINE_FREE_MODEL（"nemotron-3-ultra-free"，2026-06-18 由 super-free 切换：上游 OpenCode Zen catalog 下架 super-free，curl `/v1/models` Bearer public 验证 ultra-free 在列且 chat/completions 可用），不抛异常 | unit | `OpenCodeZenCatalogTest#selectDefaultFreeModel_fallsBackToBaselineWhenCatalogEmpty` 🟢 |
| TC-AGENT-200-h | R-AGENT-002 | 全自动新用户首启：清 DataStore，**不**发 SET_API_KEY，直接 EXTERNAL_CHAT + TOKEN | `ModelConfigManager.initializeIfNeeded` 自动 seed `apiProviderType=OPENCODE_ZEN, apiKey="public"`；agent 完成回合且 aiResponsePreview 含 TOKEN | e2e | `scripts/e2e/test_opencode_zen_autoboot_e2e.sh` 🟡 (autoboot 路径已验证：无 ANR + 自动 seed + 请求到达 OpenCode Zen；但与 builtin-key 测试连跑后命中 OpenCode Zen public-key 共享配额 `FreeUsageLimitError 429`，等冷却后单跑可绿) |
| TC-AGENT-200-i | R-AGENT-002 | `OpenCodeZenCatalog.selectDefaultFreeModelLive(catalog, liveFetcher)` 五种解析：(1) live 含 BASELINE 时优先返回 BASELINE (2) live 含其它 `-free` 但无 BASELINE 时取首个 `-free` (3) live=null 时回退 catalog (4) live + catalog 都空时回退 BASELINE (5) live 全付费 id 时回退 catalog | 行为 1: 返回 `BASELINE_FREE_MODEL`（empirical: nemotron 为唯一稳定的 `-free`）；行为 2: 取 live 列表中首个 `-free` id；行为 3/5: 走 catalog 选择；行为 4: 返回 `BASELINE_FREE_MODEL` | unit | `OpenCodeZenCatalogTest#selectDefaultFreeModelLive_{prefersBaselineWhenServedByLive,prefersLiveFreeIdOverCatalog,fallsBackToCatalogWhenLiveNull,fallsBackToBaselineWhenAllEmpty,ignoresLiveListWithoutFreeIds}` 🟢 |
| TC-AGENT-201-a | R-AGENT-001 | `beforeNextTurn` 返回 false on turn 0 | 首次调用前中止，无 chatCompletion 发起 | unit | `HermesAgentLoopBeforeNextTurnTest#beforeNextTurn_returnsFalseOnTurn0_abortsBeforeFirstCall` ✅ |
| TC-AGENT-201-b | R-AGENT-001 | `beforeNextTurn` 返回 false after turn 0 | 第 N 次调用前中止 | unit | `HermesAgentLoopBeforeNextTurnTest#beforeNextTurn_returnsFalseAfterTurn0_abortsBeforeNthCall` ✅ |
| TC-AGENT-201-c | R-AGENT-001 | `beforeNextTurn` 抛异常 | 被吞，视为 continue | unit | `HermesAgentLoopBeforeNextTurnTest#beforeNextTurn_throwing_isCaughtAndTreatedAsContinue` ✅ |
| TC-AGENT-201-d | R-AGENT-001 | `beforeNextTurn` 返 true | 正常继续 | unit | `HermesAgentLoopBeforeNextTurnTest#beforeNextTurn_returningTrue_proceedsNormally` ✅ |
| TC-AGENT-201-e | R-AGENT-001 | 无 hook 时 | loop 照常推进 | unit | `HermesAgentLoopBeforeNextTurnTest#noBeforeNextTurn_loopProceedsWithoutHook` ✅ |
| TC-AGENT-202-a | R-AGENT-001 | 自然 stop（无 tool_call 的 final reply） | 终止回合、发送 TurnComplete | unit | 由 `AgentLoopDataTest` + E2E 回合完成日志双重覆盖 ✅ |
| TC-AGENT-245-a | R-AGENT-001 | `PromptTurn(kind=SUMMARY, content="...")` 经 `EnhancedAIService.toOpenAiMessages` 序列化 | 输出消息 `role == "user"`，不是 `"system"`（避免与 chat system prompt 形成两连 system 被 MIMO 400 拒） | unit | `SummaryRoleRoundTripTest#TC-245-a *` 🟢 |
| TC-AGENT-245-b | R-AGENT-001 | `PromptTurn(kind=SUMMARY, ...)` 经 `HermesAdapter` gateway 分支（`chatHistory` 循环，line ~150）序列化 | 输出消息 `role == "user"` | unit | `SummaryRoleRoundTripTest#TC-245-b HermesAdapter does not hard-code SUMMARY to system` 🟢 |
| TC-AGENT-245-c | R-AGENT-001 | `PromptTurn(kind=SUMMARY, ...)` 经 `HermesAdapter.buildOpenAiMessages`（非 gateway 分支，line ~276）序列化 | 输出消息 `role == "user"` | unit | `SummaryRoleRoundTripTest#TC-245-c EnhancedAIService does not hard-code SUMMARY to system` 🟢 |
| TC-AGENT-245-d | R-AGENT-001 | 完整 round-trip：构造 `[SYSTEM, USER, ASSISTANT, SUMMARY, USER]` → 走 `toOpenAiMessages` → 再走 `toPromptTurnsForHistory`（反序列化） | 不出现两连 `role=system`；SUMMARY turn 序列化后落到 USER role（identity 在 wire 边界故意收敛到 USER，对齐 OpenAIProvider 既有行为） | unit | `SummaryRoleRoundTripTest#TC-245-d *` 🟢 |

---

## 域 AGENT — CredentialPool (R-AGENT-008)

测试类: `CredentialPoolTest` (21 tests) / `CredentialSourcesTest` (12) / `AccountUsageTest` (10) / `UsagePricingTest` (27) — all green 2026-04-26.

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-AGENT-220-a | R-AGENT-008 | 同 provider 配多 key | 轮转选下一个 | unit | `CredentialPoolTest#round robin across keys` 🟢 |
| TC-AGENT-220-b | R-AGENT-008 | 当前 key 401 | 标记 unhealthy + 切下一个 | unit | `CredentialPoolTest#401 marks unhealthy and rotates` 🟢 |
| TC-AGENT-220-c | R-AGENT-008 | 当前 key 429 | 标记限流 + 切下一个 | unit | `CredentialPoolTest#429 rate limited rotates` 🟢 |
| TC-AGENT-221-a | R-AGENT-008 | 池全不健康 | 触发 fallback provider（由 R-AGENT-002 接管） | unit | `CredentialPoolTest#all unhealthy falls through` 🟢 |
| TC-AGENT-222-a | R-AGENT-008 | env / 文件 / keychain / EncryptedPrefs 混合 | 按 (provider, source) 路由到对应 `RemovalStep` | unit | `CredentialSourcesTest#env source routes to env removal step` 🟢 |
| TC-AGENT-223-a | R-AGENT-008 | 统计每 key token / 金额 | `renderAccountUsageLines` 输出 title/provider/windows/details | unit | `AccountUsageTest#renderAccountUsageLines includes title provider and windows` 🟢 |
| TC-AGENT-224-a | R-AGENT-008 | 模型 → 成本查找 | 命中 `UsagePricing` 表 | unit | `UsagePricingTest#getPricingEntry resolves anthropic claude-opus-4` 🟢 |

---

## 域 AGENT — Persistent Instruction Injection (R-AGENT-009)

测试类: `app/src/test/java/com/ai/assistance/operit/api/chat/enhance/PersistentInstructionInjectionTest.kt` + `app/src/test/java/com/ai/assistance/operit/api/chat/library/MemoryLibraryPersistentInstructionGuardTest.kt`

**2026-06-04 bugfix 落地**：合并 a/b、d/e 因 JVM 单测无法直接 boot ObjectBox / preferencesManager，改用源码字符串扫描守卫（参考 `DeepseekProviderTest` 模式），把"接线契约"固化进源码；运行时正确性由 §3 E2E + manual smoke 兜底。241-a 同时覆盖 update 流程（241-a' 子方法）。

覆盖"持久指令"的写入/读取/注入/删除/抗侵蚀全链路。所有 TC 默认在 active Profile 的 Memory 库里直接造数据，不走 LLM 调用（避免外部依赖）。

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-AGENT-240-a | R-AGENT-009 | Memory 库无任何带 `#persistent_instruction` tag 的节点 | `ConversationService.prepareConversationHistory` 输出的 system prompt 不含 `[Persistent user instructions]` 段 | unit/source | `PersistentInstructionInjectionTest#TC-AGENT-240-ab ConversationService defines and invokes buildPersistentInstructionsText` 🟢 |
| TC-AGENT-240-b | R-AGENT-009 | Memory 库有 1 条带 tag 的节点，content="回复用 Markdown 列表" | system prompt 末尾包含 `[Persistent user instructions]\n- 回复用 Markdown 列表` | unit/source | 与 240-a 合并到 `TC-AGENT-240-ab`（源码扫描验证 `buildPersistentInstructionsText` 定义 + 在 finalSystemPrompt 块内被调用 + 输出 `[Persistent user instructions]` literal）🟢 |
| TC-AGENT-240-c | R-AGENT-009 | Memory 库有 3 条带 tag 的节点，updatedAt 不同 | system prompt 段内按 `updatedAt desc` 拼成 3 个 bullet | unit/source | `PersistentInstructionInjectionTest#TC-AGENT-240-c buildPersistentInstructionsText emits correct header bullet and sort` 🟢 |
| TC-AGENT-240-d | R-AGENT-009 | 一条带 tag 的节点 → 通过 `updateMemory` 改 content → 再次拼 prompt | 拼接的内容为更新后的 content（不是旧的） | unit/source | `PersistentInstructionInjectionTest#TC-AGENT-240-de buildPersistentInstructionsText queries findMemoriesByTag with correct tag` 🟢（每次调用都重新查 repository，自动反映最新数据） |
| TC-AGENT-240-e | R-AGENT-009 | 一条带 tag 的节点 → `removeTag(memory, "#persistent_instruction")` → 再次拼 prompt | system prompt 不再包含该 content | unit/source | 与 240-d 合并到 `TC-AGENT-240-de`（查询基于 `findMemoriesByTag` 实时结果，tag 移除即排除）🟢 |
| TC-AGENT-241-a | R-AGENT-009 | 带 tag 的节点参与 `MemoryLibrary.saveMemory` 的自动合并 / 更新流程 | 合并源命中 tag → 整组跳过；更新目标命中 tag → 跳过更新 | unit/source | `MemoryLibraryPersistentInstructionGuardTest#TC-AGENT-241-a merge skips persistent_instruction sources` + `#TC-AGENT-241-a' update skips persistent_instruction targets` 🟢 |
| TC-AGENT-241-b | R-AGENT-009 | 带 tag 的节点参与自动 folder 重分类（`autoCategorizeMemories`） | uncategorizedMemories filter 排除带 tag 节点 | unit/source | `MemoryLibraryPersistentInstructionGuardTest#TC-AGENT-241-b autoCategorize skips persistent_instruction nodes` 🟢 |
| TC-AGENT-242-a | R-AGENT-009 | gateway 路径调 `prepareConversationHistory(chatId="gw:feishu:xxx")` | 拼出的 system prompt 与 UI 路径同 Profile 时一致（共用全局指令池） | unit/source | `PersistentInstructionInjectionTest#TC-AGENT-242-a buildPersistentInstructionsText reads active profile globally not per-chat` 🟢（验证读 `preferencesManager.activeProfileIdFlow` 而非 chatId） |
| TC-AGENT-243-a | R-AGENT-009 | `MemoryRepository.pickNodeColor(memory)` —— memory 带 `#persistent_instruction` tag（含/不含其他 tag、与 `Person`/`Concept` 并存、含 `isDocumentNode=true` 也优先金色） | 返回金色 `Color(0xFFFFB300)`，覆盖文档紫和 Person/Concept 颜色 | unit | `MemoryNodeColorTest#persistentInstructionTakesPrecedence` 🟢 |
| TC-AGENT-243-b | R-AGENT-009 | `MemoryRepository.pickNodeColor(memory)` —— 不带 tag / 仅 `Person` / 仅 `Concept` / 仅 `isDocumentNode` | 颜色与改动前一致（绿/蓝/紫/灰），保证既有节点视觉不回归 | unit | `MemoryNodeColorTest#existingColorsPreserved` 🟢 |
| TC-AGENT-244-a | R-AGENT-009 | `MemoryInfoDialog` 渲染一条带 `#persistent_instruction` + `Person` 两个 tag 的记忆 | 详情对话框文本里能找到 `#persistent_instruction` 和 `Person` 字样 | manual/visual | 手测：装包后点带 tag 节点 → 详情对话框 → tags 行可见 🟡 |
| TC-AGENT-245-a | R-AGENT-009 | `SystemToolPromptsInternal.kt` 的 EN + CN `create_memory` ToolPrompt description | description 显式 mention `#persistent_instruction` tag 与持久规则触发场景 | unit/source | `PersistentInstructionAgentHintTest#TC-AGENT-245-a create_memory descriptions instruct agent to use persistent_instruction tag` 🟢 |
| TC-AGENT-245-b | R-AGENT-009 | `SystemPromptConfig.kt` 的 `GATEWAY_AWARENESS_EN` 中 `MEMORY USAGE GUIDANCE` 段 | 含 `#persistent_instruction` + `EXCEPTION` 限定词（避免与"无需手动保存"全局指令矛盾）| unit/source | `PersistentInstructionAgentHintTest#TC-AGENT-245-b EN memory usage guidance mentions persistent_instruction with exception clause` 🟢 |
| TC-AGENT-245-c | R-AGENT-009 | `SystemPromptConfig.kt` 的 `GATEWAY_AWARENESS_CN` 中"记忆库使用指导"段 | 含 `#persistent_instruction` + "例外/主动调用"限定词 | unit/source | `PersistentInstructionAgentHintTest#TC-AGENT-245-c CN memory usage guidance mentions persistent_instruction with exception clause` 🟢 |

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

---

## 域 AGENT — Gateway 路径每轮强制保存对话摘要到长期记忆 (R-AGENT-010)

测试类: `app/src/test/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayControllerMemoryAutosaveWiringTest.kt`

**背景**：APP 内聊天路径的自动总结保存 (`EnhancedAIService.handleTaskCompletion` → `MemoryLibrary.saveMemoryAsync`) 只在 agent 输出 `<complete>` 时触发。飞书 / 微信 gateway 路径的 agent 是被动应答，几乎不写 `<complete>`，导致这些对话从不进入长期记忆。修复策略：在 `HermesGatewayController.runHermesAgent` 即将 return aiText 给 `GatewayRunner` 前，强制调一次 `MemoryLibrary.saveMemoryAsync`。中断 / 异常 / 空回复不存。受 `ApiPreferences.enableMemoryQueryFlow` 开关控制。

测试策略与 R-GW-003 (TC-GW-175-a) 一致：`HermesGatewayController.runHermesAgent` 是 suspend + 重度依赖 Android Context / multiServiceManager / ChatHistoryManager，JVM mock ROI 太低，走源码字符串扫描守住 wiring。运行时正确性由手测 + §3 E2E 兜底。

| ID | R-ID | 输入 / 触发条件 | 期望输出 | 类型 | 测试方法引用 |
|---|---|---|---|---|---|
| TC-AGENT-246-a | R-AGENT-010 | 源码扫描：`HermesGatewayController.kt` | `runHermesAgent` 函数体必须 reference `MemoryLibrary.saveMemoryAsync` —— 否则 gateway 路径永远不主动总结。 | unit-scan | `HermesGatewayControllerMemoryAutosaveWiringTest#TC-AGENT-246-a runHermesAgent invokes saveMemoryAsync` 🟢 |
| TC-AGENT-246-b | R-AGENT-010 | 源码扫描：`HermesGatewayController.kt` | `runHermesAgent` 必须读 `enableMemoryQueryFlow`（与 APP 内路径同一开关），开关 false 时跳过保存。 | unit-scan | `HermesGatewayControllerMemoryAutosaveWiringTest#TC-AGENT-246-b runHermesAgent gates on enableMemoryQueryFlow` 🟢 |
| TC-AGENT-246-c | R-AGENT-010 | 源码扫描：`HermesGatewayController.kt` | `saveMemoryAsync` 必须用 `multiServiceManager.getServiceForFunction(FunctionType.MEMORY)` 取 MEMORY 模型，与 APP 内路径一致。 | unit-scan | `HermesGatewayControllerMemoryAutosaveWiringTest#TC-AGENT-246-c runHermesAgent uses MEMORY function service` 🟢 |
| TC-AGENT-246-d | R-AGENT-010 | 源码扫描：`HermesGatewayController.kt` | `runHermesAgent` 的 conversationHistory 来源必须是 `ChatHistoryManager.loadChatMessages(historyChatId)`（取 gateway 会话历史，而非空 list 或硬编码）。 | unit-scan | `HermesGatewayControllerMemoryAutosaveWiringTest#TC-AGENT-246-d runHermesAgent reads gateway chat history` 🟢 |
| TC-AGENT-246-e | R-AGENT-010 | 源码扫描：`HermesGatewayController.kt` | `saveMemoryAsync` 调用点必须位于"agent 回复非空 + 未中断"分支内（即 `aiText` 非空、`interruptCheck()` 未真）；中断 / 异常 / 空回复路径不调用。 | unit-scan | `HermesGatewayControllerMemoryAutosaveWiringTest#TC-AGENT-246-e runHermesAgent skips save on empty or interrupted` 🟢 |
| TC-AGENT-246-f | R-AGENT-010 | 源码扫描：`HermesGatewayController.kt` | （bugfix 2026-06-06）`runHermesAgent` 取 MEMORY service 必须复用 `EnhancedAIService.getInstance(...).multiServiceManager` —— 禁止 `MultiServiceManager(...)` 构造调用。否则每轮 gateway 都重建 service / 冷缓存 / 与 APP UI 的 token counter 脱离，且配置变更后 `refreshServiceForFunction` 不会失效缓存。 | unit-scan | `HermesGatewayControllerMemoryAutosaveWiringTest#TC-AGENT-246-f runHermesAgent reuses EnhancedAIService singleton multiServiceManager` 🟢 |

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

---

## 域 AGENT — Gateway 保存的记忆节点强制打 `#gateway:<platform>` tag (R-AGENT-011)

测试类:
- `app/src/test/java/com/ai/assistance/operit/api/chat/library/MemoryLibrarySaveExtraTagsApiTest.kt`（MemoryLibrary 签名 + 注入点）
- `app/src/test/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayControllerGatewayTagWiringTest.kt`（gateway 调用点）
- `app/src/test/java/com/ai/assistance/operit/api/chat/EnhancedAIServiceMemoryAutosaveTagsTest.kt`（APP UI 路径不受影响）

**背景**：R-AGENT-010 让 gateway 路径每轮自动保存记忆，但 LLM 决定的 tag 与 APP UI 内 `create_memory` 产出的 tag 同质化，用户无法在 `MemoryScreen` 区分/过滤。修复策略：`MemoryLibrary.saveMemoryAsync` 加可选 `extraTags: List<String> = emptyList()` 参数，gateway 调用点显式传 `listOf("#gateway:$platform")`（platform 从 sessionKey 派生），APP UI 路径保持默认 emptyList。

测试策略与 R-AGENT-010 一致——`runHermesAgent` / `handleTaskCompletion` mock ROI 低，走源码字符串扫描守住 wiring；`MemoryLibrary` API 表面则走源码扫描验证签名与两个创建分支的 `forEach extraTags` 注入。

| ID | R-ID | 输入 / 触发条件 | 期望输出 | 类型 | 测试方法引用 |
|---|---|---|---|---|---|
| TC-AGENT-247-a | R-AGENT-011 | 源码扫描：`MemoryLibrary.kt` | `saveMemoryAsync` 函数签名必须含 `extraTags: List<String> = emptyList()` 参数（默认空 list 保 APP UI 路径不被影响）。 | unit-scan | `MemoryLibrarySaveExtraTagsApiTest#TC-AGENT-247-a saveMemoryAsync signature contains extraTags parameter with default emptyList` 🔴 |
| TC-AGENT-247-b | R-AGENT-011 | 源码扫描：`MemoryLibrary.kt` | `saveMemory` 主问题创建分支必须 `extraTags.forEach { addTagToMemory(memory, it) }`，与 `mainProblem.tags.forEach` 并列；不能只对 LLM tags 加而漏 extraTags。 | unit-scan | `MemoryLibrarySaveExtraTagsApiTest#TC-AGENT-247-b saveMemory main problem branch injects extraTags` 🔴 |
| TC-AGENT-247-c | R-AGENT-011 | 源码扫描：`MemoryLibrary.kt` | `saveMemory` 实体创建分支必须 `extraTags.forEach { addTagToMemory(memory, it) }`，与 `entity.tags.forEach` 并列；保证 gateway 一轮总结产出的实体节点同样带 `#gateway:` tag。 | unit-scan | `MemoryLibrarySaveExtraTagsApiTest#TC-AGENT-247-c saveMemory entity branch injects extraTags` 🔴 |
| TC-AGENT-247-d | R-AGENT-011 | 源码扫描：`HermesGatewayController.kt` | `runHermesAgent` 调 `saveMemoryAsync` 时必须显式传 `extraTags = listOf("#gateway:...")`（前缀 `#gateway:` 固定），不能传空 list 或漏传。 | unit-scan | `HermesGatewayControllerGatewayTagWiringTest#TC-AGENT-247-d runHermesAgent passes gateway tag to saveMemoryAsync` 🔴 |
| TC-AGENT-247-e | R-AGENT-011 | 源码扫描：`HermesGatewayController.kt` | gateway tag 中 `platform` 必须从 `sessionKey` 派生（`sessionKey.substringBefore(':')` 或等价 split），不能硬编码 `"unknown"` / `""` / `"gateway"`；保证飞书 → `#gateway:feishu`、微信 → `#gateway:wechat` 等可区分。 | unit-scan | `HermesGatewayControllerGatewayTagWiringTest#TC-AGENT-247-e gateway tag platform derives from sessionKey` 🔴 |
| TC-AGENT-247-f | R-AGENT-011 | 源码扫描：`EnhancedAIService.kt` | `handleTaskCompletion`（APP UI 路径）调用 `saveMemoryAsync` 时**不得**传 `extraTags` 参数，走默认 emptyList()——APP 内聊天记忆不应被打 `#gateway:` tag。 | unit-scan | `EnhancedAIServiceMemoryAutosaveTagsTest#TC-AGENT-247-f handleTaskCompletion saveMemoryAsync does not pass extraTags` 🔴 |

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

---

## 域 AGENT — MemoryScreen Gateway 可视化 + 过滤 (R-AGENT-012)

测试类:
- `app/src/test/java/com/ai/assistance/operit/data/repository/MemoryRepositoryGatewayColorTest.kt` (TC-248-a)
- `app/src/test/java/com/ai/assistance/operit/ui/features/memory/viewmodel/MemoryViewModelGatewayFilterTest.kt` (TC-248-b/c/d)
- `app/src/test/java/com/ai/assistance/operit/ui/features/memory/screens/MemoryScreenGatewayFilterChipWiringTest.kt` (TC-248-e/f)

R-AGENT-012 是 R-AGENT-011 的 UI 兜底：R-011 已把 `#gateway:<platform>` tag 写进了 Memory，但 MemoryScreen 整条读链对 tag 无感知。本组用例覆盖三个层：
1. **颜色策略层** (`pickNodeColorByAttributes`)：纯函数，pure-logic 测试可直接 mock `Memory` 的 tag 列表验证返回色
2. **ViewModel 状态/过滤层** (`MemoryViewModel.gatewayFilter` + `availableGatewayPlatforms`)：依赖 Repository / ObjectBox / Android Context，走源码扫描守住 wiring 契约（filter 字段存在 + refreshGraph 应用 filter）
3. **UI 接线层** (`MemoryScreen.kt` chip 行)：Composable 重度依赖 Compose runtime + Android，走源码扫描守住"chip 行紧贴 SearchBar 之后、调 viewModel.onGatewayFilterChange"等契约

运行时正确性由手测 + §3 E2E 兜底（gateway 跑一轮后开 MemoryScreen 看 chip 是否出现 + 选 chip 是否过滤生效）。

| ID | R-ID | 输入 / 触发条件 | 期望输出 | 类型 | 测试方法引用 |
|---|---|---|---|---|---|
| TC-AGENT-248-a | R-AGENT-012 | 调 `pickNodeColorByAttributes(memory)`，输入 5 种 memory tag 组合：(1) 仅 `#gateway:feishu` (2) `#gateway:wechat` + 普通 tag (3) `#persistent_instruction` + `#gateway:feishu`（极端共存） (4) 仅普通 tag (5) `isDocumentNode = true` + `#gateway:feishu` | (1)(2) 返回 gateway 色 `0xFF26A69A`；(3) 返回金色 `0xFFFFB300`（persistent_instruction 优先）；(4) 返回原默认色（按现有 Person/Concept/其他分支）；(5) 返回紫色 `0xFF9575CD`（isDocumentNode 优先于 gateway 色，与现有优先级一致） | unit-logic | `MemoryRepositoryGatewayColorTest#TC-AGENT-248-a pickNodeColorByAttributes handles gateway tag priority` 🟢 |
| TC-AGENT-248-b | R-AGENT-012 | 源码扫描：`MemoryViewModel.kt` / `MemoryUiState` | `MemoryUiState` 必须含 `availableGatewayPlatforms: List<String> = emptyList()` 和 `gatewayFilter:` 字段（类型为 `GatewayFilter`，默认 `GatewayFilter.All`） | unit-scan | `MemoryViewModelGatewayFilterTest#TC-AGENT-248-b MemoryUiState contains gateway filter fields` 🟢 |
| TC-AGENT-248-c | R-AGENT-012 | 源码扫描：`MemoryViewModel.kt` | 必须定义 sealed class / sealed interface `GatewayFilter`，含三种 case：`All` / `OnlyGateway` / `ExcludeGateway`；`OnlyGateway` 含 `platforms: Set<String>` 字段 | unit-scan | `MemoryViewModelGatewayFilterTest#TC-AGENT-248-c GatewayFilter sealed class has three variants` 🟢 |
| TC-AGENT-248-d | R-AGENT-012 | 源码扫描：`MemoryViewModel.kt` | 必须存在 `onGatewayFilterChange(filter: GatewayFilter)` public 方法，且在 `refreshGraph()` / 搜索路径中按 `gatewayFilter` 过滤 `memory.tags`（源码中应出现 `startsWith("#gateway:")` 字面字符串） | unit-scan | `MemoryViewModelGatewayFilterTest#TC-AGENT-248-d refreshGraph applies gatewayFilter` 🟢 |
| TC-AGENT-248-e | R-AGENT-012 | 源码扫描：`MemoryScreen.kt` | `MemoryScreen` Composable 中 `MemorySearchBar` 调用之后 / `GraphVisualizer` 调用之前必须存在 `FilterChip` 调用（chip 行渲染入口）；且引用 `uiState.availableGatewayPlatforms` 与 `uiState.gatewayFilter` 决定 chip 选中态 | unit-scan | `MemoryScreenGatewayFilterChipWiringTest#TC-AGENT-248-e MemoryScreen wires gateway filter chip row between SearchBar and GraphVisualizer` 🟢 |
| TC-AGENT-248-f | R-AGENT-012 | 源码扫描：`MemoryScreen.kt` | chip 点击 callback 必须调用 `viewModel.onGatewayFilterChange(...)`；`availableGatewayPlatforms.isEmpty()` 分支必须有条件渲染（chip 行隐藏，避免老用户看到空 chip 行） | unit-scan | `MemoryScreenGatewayFilterChipWiringTest#TC-AGENT-248-f MemoryScreen hides chip row when no gateway platforms` 🟢 |
| TC-AGENT-249-a | R-AGENT-012 | 源码扫描：`GraphVisualizer.kt` | `drawNode` 函数体（接收 `node: Node` 参数）渲染节点 fill 时**必须读 `node.color`**，不能直接硬编码 `nodePalette.fillColor`；允许默认色 (`Color.LightGray`) 时 fallback 到 `nodePalette.fillColor` 以保持暗色主题对比度。bugfix 2026-06-07：先前实现把 Repository 算好的 gateway 蓝绿/persistent_instruction 金色全部丢弃，导致 R-AGENT-005/R-AGENT-012 颜色策略在 UI 层完全不可见。 | unit-scan | `GraphVisualizerNodeColorWiringTest#TC-AGENT-249-a drawNode reads node color for fill` 🟢 |
| TC-AGENT-250-a | R-AGENT-012 | 源码扫描：`GraphVisualizer.kt` | TC-AGENT-249 修复后引入的视觉 regression：节点 fill 用了固定 hex 色（金/蓝绿/绿/蓝/紫），但 `getNodeLayoutMetrics` 仍然用按主题算的 `nodePalette.textColor`，暗色主题下"浅文字 vs 浅黄/浅绿 fill"对比度 1.3~1.8:1（WCAG 严重不达标），文字几乎看不清。`getNodeLayoutMetrics` 必须接收 `nodeFillColor: Color` 参数并按 `nodeFillColor.luminance()` 动态选文字色（亮 fill → 深文字 `#1F2937`，暗 fill → 浅文字 `#E5E7EB`）；`drawNode` 必须把算好的 `nodeFillColor` 透传进 `getNodeLayoutMetrics`；cache key 必须含 fillColor.hashCode() 避免缓存污染。bugfix 2026-06-07。 | unit-scan | `GraphVisualizerTextContrastWiringTest#TC-AGENT-250-a getNodeLayoutMetrics picks text color by fill luminance` 🟢 |

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

---

## 域 AGENT — APP 内自动摘要强行写入长期记忆（绕过判官）(R-AGENT-013)

测试类: `app/src/test/java/com/ai/assistance/operit/services/core/MessageCoordinationDelegateSummaryStripWiringTest.kt`（保留 013-j / 015-g 两条）

**背景**: R-AGENT-013 要求 `MessageCoordinationDelegate.launchAsyncSummaryForSend` / `summarizeHistory` 在成功 `addSummaryMessage` 之后强制把摘要文本直接写入长期记忆（绕过 `MemoryLibrary.saveMemoryAsync` / `generateAnalysis` LLM 判官）。

**R-AGENT-038 (2026-06-16) 取代**: phase 1 (R-AGENT-038) 把"独立 `#auto_summary` 节点 + `addTagToMemory(#auto_summary)` + per-summary saveMemory + 写入侧 dedup（R-AGENT-023）"的核心写入路径**全部迁移**到 `MemoryArchiver` 单一 root 节点（`#auto_summary_root`）。原 TC-AGENT-013-a..i + R-AGENT-023 / R-AGENT-026 keepDecision **以外**的 R-AGENT-016 fact 抽取细则（TC-AGENT-016-a..i）由 TC-AGENT-038-a..g 接管。

仍然保留：
- `TC-AGENT-013-j`：落库前裁掉"对话回顾"/"工具包预热"两段拼接块
- `TC-AGENT-013-k`：`SUMMARY_PROMPT` 短重点风格
- `TC-AGENT-015-g`：`sanitizeContext` 剥 fence
- `TC-AGENT-026-a..c`：keepDecision=false 早返回（仍由 archiver 路径前的 gate 完成）

| ID | R-ID | 输入 / 触发条件 | 期望输出 | 类型 | 测试方法引用 |
|---|---|---|---|---|---|
| ~~TC-AGENT-013-a~~ | R-AGENT-013 | [SUPERSEDED by TC-AGENT-038-f @ R-AGENT-038, 2026-06-16] 写入路径不再调 `MemoryRepository.saveMemory` 落 `#auto_summary` 节点；改走 `MemoryArchiver.appendToRoot(SUMMARY,...)` 维护 `#auto_summary_root` 单一根。 | — | — | _测试已撤回_ |
| ~~TC-AGENT-013-b~~ | R-AGENT-013 | [SUPERSEDED by TC-AGENT-038-f @ R-AGENT-038, 2026-06-16] 同上：`summarizeHistory` 路径也走 archiver。 | — | — | _测试已撤回_ |
| ~~TC-AGENT-013-c~~ | R-AGENT-013 | [SUPERSEDED by TC-AGENT-038-f @ R-AGENT-038, 2026-06-16] 红线由 archiver 路径继承（archiver 内部不调 `MemoryLibrary.saveMemoryAsync`）。 | — | — | _测试已撤回_ |
| ~~TC-AGENT-013-d~~ | R-AGENT-013 | [SUPERSEDED by TC-AGENT-038-a @ R-AGENT-038, 2026-06-16] 不再打 `#auto_summary` 到独立节点；改在 `#auto_summary_root` 上挂 `#auto_root` + `#auto_summary_root` 两 tag。 | — | — | _测试已撤回_ |
| ~~TC-AGENT-013-e~~ | R-AGENT-013 | [SUPERSEDED by TC-AGENT-038 @ R-AGENT-038, 2026-06-16] `#chat:<chatId>` 改为按行内 `(chat=<chatId>)` 元数据保留——不再以 tag 形式落到 root（避免 root 节点 tag 数量爆炸）。 | — | — | _测试已撤回_ |
| TC-AGENT-013-f | R-AGENT-013 | [REMAINS VALID] 源码扫描：`MessageCoordinationDelegate.kt` —— `forcePersistSummaryToMemory` 调用点应仍受 `enableMemoryQueryFlow` gate 保护。本 TC 在 R-AGENT-038 后**仍有效**但门控位置可能在 archiver 之外（调用方层），新测试目前未单独覆盖；待 R-AGENT-039 phase 2 一并加强（gate 应**包住** archiver 路径）。 | unit-scan | _暂无测试_ 🔴 |
| ~~TC-AGENT-013-g~~ | R-AGENT-013 | [SUPERSEDED by TC-AGENT-038-e @ R-AGENT-038, 2026-06-16] `try-catch` 现在在 archiver 内部（jsonl IO 路径），调用方简化。 | — | — | _测试已撤回_ |
| ~~TC-AGENT-013-h~~ | R-AGENT-013 | [SUPERSEDED] `addSummaryMessage` 与 archiver 写入的相对顺序仍由 `forcePersistSummaryToMemory` 调用站点位置约束（同函数内），但已不再以独立 saveMemory 锚点检查。 | — | — | _测试已撤回_ |
| ~~TC-AGENT-013-i~~ | R-AGENT-013 | [SUPERSEDED by TC-AGENT-038-a @ R-AGENT-038, 2026-06-16] root memory 的 `source` 字段在 archiver 内部固定为 `"auto_summary"`（保持向 EditMemoryDialog 兼容）。 | — | — | _测试已撤回_ |
| TC-AGENT-013-j | R-AGENT-013 | 源码扫描：`MessageCoordinationDelegate.kt` | **bugfix（2026-06-08）**：`forcePersistSummaryToMemory` 落库前**必须裁掉**"对话回顾"和"工具包预热"两段拼接块。期望：源码含按 `"对话回顾"` / `"Dialogue review"` / `"【工具包预热】"` / `"[Package Warmup]"` 等分隔符做 `substringBefore` 或 `indexOf`+`substring` 裁剪的代码。 | unit-scan | `MessageCoordinationDelegateSummaryStripWiringTest#TC-AGENT-013-j strips dialogue review and package warmup before persist` 🟢 |
| TC-AGENT-013-k | R-AGENT-013 | 源码扫描：`FunctionalPrompts.kt` | **bugfix（2026-06-08）**：`SUMMARY_PROMPT` (CN) 和 `SUMMARY_PROMPT_EN` 必须采用**短重点风格**——禁止包含强制扩写指令；必须含"精简" / "重点" / "concise" / "key facts" 等约束词。 | unit-scan | `FunctionalPromptsSummaryConcisenessWiringTest#TC-AGENT-013-k summary prompt enforces concise style` 🟢 |

### R-AGENT-014: Agent 感知 `#auto_summary` + `query_memory` tag 过滤（2026-06-07）

| TC ID | R-ID | 输入 / 触发 | 期望 | 测试类型 | 实现 / 状态 |
|---|---|---|---|---|---|
| TC-AGENT-014-a | R-AGENT-014 | 源码扫描：`SystemPromptConfig.kt` | `GATEWAY_AWARENESS_EN` 常量内 `MEMORY USAGE GUIDANCE` 段必须含 `"#auto_summary"` 字面 + `"query_memory"` 引用 + `"tags="` 或 `"tags ="` 用法示例 —— agent 才知道日记本存在且能用 tag 精准查询。 | unit-scan | `SystemPromptConfigAutoSummaryGuidanceWiringTest#TC-AGENT-014-a english guidance mentions auto_summary tag` 🔴 |
| TC-AGENT-014-b | R-AGENT-014 | 源码扫描：`SystemPromptConfig.kt` | `GATEWAY_AWARENESS_CN` 常量内 `记忆库使用指导` 段必须含 `"#auto_summary"` 字面 + `"query_memory"` 引用 + `"tags="` 或 `"tags="` 用法示例（中文版同英文版语义对齐）。 | unit-scan | `SystemPromptConfigAutoSummaryGuidanceWiringTest#TC-AGENT-014-b chinese guidance mentions auto_summary tag` 🔴 |
| TC-AGENT-014-c | R-AGENT-014 | 源码扫描：`SystemToolPrompts.kt` | `memoryTools`（EN）的 `query_memory` ToolPrompt `parametersStructured` 列表必须含 `name = "tags"` 的 `ToolParameterSchema` 条目，description 含 `"#auto_summary"` 示例 + `"|"` 分隔约定说明。 | unit-scan | `QueryMemoryToolPromptsTagsWiringTest#TC-AGENT-014-c english tool prompt declares tags parameter` 🔴 |
| TC-AGENT-014-d | R-AGENT-014 | 源码扫描：`SystemToolPrompts.kt` | `memoryToolsCn`（CN）的 `query_memory` ToolPrompt `parametersStructured` 列表必须含 `name = "tags"` 的 `ToolParameterSchema` 条目，description 含 `"#auto_summary"` 示例 + `"|"` 分隔约定说明（中文版与英文版语义对齐）。 | unit-scan | `QueryMemoryToolPromptsTagsWiringTest#TC-AGENT-014-d chinese tool prompt declares tags parameter` 🔴 |
| TC-AGENT-014-e | R-AGENT-014 | 源码扫描：`SystemToolPrompts.kt` | `tags` 参数必须 `required = false` —— 不传时必须与既有行为完全一致，向后兼容所有既有 `query_memory` 调用方。 | unit-scan | `QueryMemoryToolPromptsTagsWiringTest#TC-AGENT-014-e tags parameter is optional` 🔴 |
| TC-AGENT-014-f | R-AGENT-014 | 源码扫描：`MemoryQueryToolExecutor.kt` | `executeQueryMemory` 函数体必须含 `tool.parameters.find { it.name == "tags" }` 风格的解析 + 把解析结果传入 `searchMemories(...)` 调用（参数名 `tags`）—— 否则 tool description 加了参数但执行器忽略。 | unit-scan | `MemoryQueryToolExecutorTagsWiringTest#TC-AGENT-014-f executor parses tags param and forwards to searchMemories` 🔴 |
| TC-AGENT-014-g | R-AGENT-014 | 源码扫描：`MemoryQueryToolExecutor.kt` | `tags` 参数解析必须按 `"\|"` 切分支持多 tag（与 `query` 参数 `\|` 分隔关键词风格一致）—— 源码含 `split('\|')` 或 `split("\\|")` 等等价调用。 | unit-scan | `MemoryQueryToolExecutorTagsWiringTest#TC-AGENT-014-g executor splits tags by pipe` 🔴 |
| TC-AGENT-014-h | R-AGENT-014 | 源码扫描：`MemoryRepository.kt` | `searchMemories` 公开签名必须含 `tags: List<String>?` 参数（默认值 `null`），且 `runSearchMemoriesWithDebug` 函数体含按 `tags` 做硬过滤的代码（`tags.all { ... mem.tags.any ...}` 或等价 ObjectBox 查询风格） —— 必须是前置过滤，不是 tagWeight 打分混合。 | unit-scan | `MemoryRepositorySearchTagsFilterWiringTest#TC-AGENT-014-h searchMemories adds tags filter parameter` 🔴 |

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

---

### R-AGENT-015: 调 LLM 前自动注入 `<memory-context>` 围栏到当前轮 user message（2026-06-08）

测试类: `app/src/test/java/com/ai/assistance/operit/api/chat/EnhancedAIServiceMemoryContextInjectionWiringTest.kt`（新增）+ `app/src/test/java/com/ai/assistance/operit/services/core/MessageCoordinationDelegateSummaryStripWiringTest.kt`（扩展，加 TC-AGENT-015-g）

**背景**: R-AGENT-014 让 agent **可以**主动用 `query_memory tags=#auto_summary` 查日记，但依赖 agent 自己想到要查（lazy 路径）。Python Hermes 的 `run_agent.py:9087-9107` 的做法是 eager prefetch：每轮在调 LLM 之前后台用 `original_user_message` 当 query 跑 `prefetch_all`，结果用 `<memory-context>` fence 包好直接拼到当轮 user message 末尾喂给模型——模型读了 fence 内容就能"自然"用上记忆，无需主动调工具。Kotlin 侧 `hermes-android/.../MemoryManager.kt:339-362` 已 1:1 翻译完 `_INTERNAL_CONTEXT_RE` / `sanitizeContext` / `buildMemoryContextBlock` 三个 helper，但 agent loop 里**无人调用**——本需求把空挡补上：在 `EnhancedAIService.runAgentLoopViaHermes` 内 `openAiMessages = requestHistory.toOpenAiMessages()`（行 1064）之后、首次发请求之前，对末尾 user OpenAI message 原地拼 `buildMemoryContextBlock(...)`。

死循环防御：`forcePersistSummaryToMemory` 落库前必须 `sanitizeContext` 剥 fence（防御性代码，理论上 summarizeMemory 用 ChatMessage 不带 fence 但作为兜底）+ prefetch 强制 `limit ≤ 5` + 单条 content `take(800)` 截断。

| TC ID | R-ID | 输入 / 触发 | 期望 | 测试类型 | 实现 / 状态 |
|---|---|---|---|---|---|
| TC-AGENT-015-a | R-AGENT-015 | 源码扫描：`EnhancedAIService.kt` | `runAgentLoopViaHermes` 函数体必须含 `enableMemoryQuery` gate（`if (enableMemoryQuery)` 或等价）+ 对 `MemoryRepository(...)` 实例化或 `memoryRepository` field 引用 + 对 `searchMemories(...)` 调用 + 对 `buildMemoryContextBlock(` 调用——否则 prefetch 注入完全没接通。 | unit-scan | `EnhancedAIServiceMemoryContextInjectionWiringTest#TC-AGENT-015-a runAgentLoopViaHermes wires prefetch and fence` 🔴 |
| TC-AGENT-015-b | R-AGENT-015 | 源码扫描：`EnhancedAIService.kt` | `runAgentLoopViaHermes` 函数体必须修改 `openAiMessages` 末尾 user message 的 content（拼上 fence）—— 源码含对 `openAiMessages` 的索引赋值 / `set(` / `replaceAll {` / `add(` 末尾追加之类操作，且操作位置在对 `buildMemoryContextBlock(` 调用之后。 | unit-scan | `EnhancedAIServiceMemoryContextInjectionWiringTest#TC-AGENT-015-b openAiMessages last user message gets fence appended` 🔴 |
| TC-AGENT-015-c | R-AGENT-015 | 源码扫描：`EnhancedAIService.kt` | prefetch 必须强制 `limit ≤ 5`（防 token 爆炸）—— 源码 prefetch 块内含 `coerceAtMost(5)` 或 `minOf(..., 5)` 或字面值 `5` 作为 limit 上限。 | unit-scan | `EnhancedAIServiceMemoryContextInjectionWiringTest#TC-AGENT-015-c prefetch caps limit at 5` 🔴 |
| TC-AGENT-015-d | R-AGENT-015 | 源码扫描：`EnhancedAIService.kt` | 单条 memory content 必须按 800 字符截断防止长摘要把 user message 撑爆—— 源码 prefetch 块内含 `take(800)` 字面调用或等价常量定义。 | unit-scan | `EnhancedAIServiceMemoryContextInjectionWiringTest#TC-AGENT-015-d prefetch truncates content at 800 chars` 🔴 |
| TC-AGENT-015-e | R-AGENT-015 | 源码扫描：`EnhancedAIService.kt` | `#persistent_instruction` 节点必须从 prefetch 结果剔除（已通过 R-AGENT-009/245 system prompt 注入，避免 token 重复）—— 源码含 `"#persistent_instruction"` 字面字符串 + `filter` / `filterNot` 调用引用该 tag。 | unit-scan | `EnhancedAIServiceMemoryContextInjectionWiringTest#TC-AGENT-015-e prefetch excludes persistent_instruction tag` 🔴 |
| TC-AGENT-015-f | R-AGENT-015 | 源码扫描：`EnhancedAIService.kt` | `runAgentLoopViaHermes` 函数体内不得修改 `requestHistory` / `chatHistoryDelegate` / `ChatMessage`——只在 `openAiMessages` 上原地拼接，确保聊天历史持久化层不被污染。源码 prefetch 块附近不出现 `chatHistoryDelegate` / `saveCurrentChat` / `addMessage`（这些字面字符串若在 prefetch 块的同 50 行内出现即 fail）。 | unit-scan | `EnhancedAIServiceMemoryContextInjectionWiringTest#TC-AGENT-015-f prefetch never touches persisted chat history` 🔴 |
| TC-AGENT-015-g | R-AGENT-015 | 源码扫描：`MessageCoordinationDelegate.kt` | **死循环防御**：`forcePersistSummaryToMemory` 函数体在 `memoryRepository.saveMemory(` 调用之前必须对 `summaryText` 调用一次 `sanitizeContext` 或等价 fence 剥离（剥 `<memory-context>` / `[System note: ...]` / fence tag）—— 防止未来路径变化让 fence 漏进 ChatMessage 后被无差别落库扩散。 | unit-scan | `MessageCoordinationDelegateSummaryStripWiringTest#TC-AGENT-015-g forcePersistSummaryToMemory sanitizes memory context before save` 🔴 |
| TC-AGENT-015-h | R-AGENT-015 | 源码扫描：`EnhancedAIService.kt` | prefetch 流程必须用 try-catch 包围（ObjectBox / embedding 异常不能拖垮 agent loop）—— 源码 prefetch 块外侧有 `try {` + 对应 `catch`。 | unit-scan | `EnhancedAIServiceMemoryContextInjectionWiringTest#TC-AGENT-015-h prefetch wrapped in try catch` 🔴 |

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

---

### R-AGENT-016: APP 内自动摘要时一并把【关键事实】拆成独立 memory 节点（事实自我学习）（2026-06-08）

测试类: _原 `MessageCoordinationDelegateFactExtractionWiringTest.kt` 已撤回 @ R-AGENT-038, 2026-06-16_

**R-AGENT-038 (2026-06-16) 取代**: phase 1 把 fact 抽取后的"逐条独立 saveMemory + addTagToMemory(#auto_extracted)"路径**全部迁移**到 `MemoryArchiver.appendToRoot(EXTRACTED, ...)`，单一根节点 `#auto_extracted_root` 维护。dedup（3-gram jaccard 0.75）由 archiver 内部完成，不再在 delegate 层 prefetch+比对。原 TC-AGENT-016-a..i 由 TC-AGENT-038-a..g（行为）+ TC-AGENT-038-f（delegate wiring）接管。fact 抽取的 prompt 解析逻辑（bullet 切分、800 字截断、10 条上限、双语段头、try-catch）仍保留在 `extractAndPersistFacts` 函数体内，但守护方式变为"行为单测 + archiver 行为单测"，不再以独立 unit-scan 守每个常量。

| TC ID | R-ID | 输入 / 触发 | 期望 | 测试类型 | 实现 / 状态 |
|---|---|---|---|---|---|
| ~~TC-AGENT-016-a~~ | R-AGENT-016 | [SUPERSEDED by TC-AGENT-038-f @ R-AGENT-038, 2026-06-16] `forcePersistSummaryToMemory` 调 `extractAndPersistFacts` 仍然成立，但守护落到"delegate 文件含 `memoryArchiver.appendToRoot(` 字面值"。 | — | — | _测试已撤回_ |
| ~~TC-AGENT-016-b~~ | R-AGENT-016 | [SUPERSEDED] bullet 解析逻辑仍在 `extractAndPersistFacts`，但**行为**由 archiver 路径下整体 verify。 | — | — | _测试已撤回_ |
| ~~TC-AGENT-016-c~~ | R-AGENT-016 | [SUPERSEDED by TC-AGENT-038-f @ R-AGENT-038, 2026-06-16] 不再"独立 saveMemory + addTagToMemory(#auto_extracted)"；改走 archiver。 | — | — | _测试已撤回_ |
| ~~TC-AGENT-016-d~~ | R-AGENT-016 | [SUPERSEDED] 800 字截断逻辑保留在 delegate 层，但通过行为而非 unit-scan 守护。 | — | — | _测试已撤回_ |
| ~~TC-AGENT-016-e~~ | R-AGENT-016 | [SUPERSEDED] 10 条上限逻辑保留在 delegate 层，但通过行为而非 unit-scan 守护。 | — | — | _测试已撤回_ |
| ~~TC-AGENT-016-f~~ | R-AGENT-016 | [SUPERSEDED] `#chat:` 改为按行内 `(chat=<chatId>)` 元数据；不再 tag。R-AGENT-027 红线（不得写 `#auto_summary_id:`）由 archiver 路径自然继承——archiver 完全不打 `#auto_summary_id:` 任何 tag。 | — | — | _测试已撤回_ |
| ~~TC-AGENT-016-g~~ | R-AGENT-016 | [SUPERSEDED by TC-AGENT-038-d @ R-AGENT-038, 2026-06-16] dedup（3-gram jaccard 0.75）现在由 archiver 内部完成。 | — | — | _测试已撤回_ |
| ~~TC-AGENT-016-h~~ | R-AGENT-016 | [SUPERSEDED by TC-AGENT-038-e @ R-AGENT-038, 2026-06-16] try-catch 同时存在于 delegate 层（fact 解析）和 archiver 层（IO）。delegate 层的 try-catch 仍是 `extractAndPersistFacts` 的 wrap，行为继承不变。 | — | — | _测试已撤回_ |
| ~~TC-AGENT-016-i~~ | R-AGENT-016 | [SUPERSEDED] i18n 段头选择仍然在 `extractAndPersistFacts` 内部，未变。 | — | — | _测试已撤回_ |

### TC-AGENT-017 — R-AGENT-017 让 agent 知道自己有 memory 维护职责

| ID | 关联 R-ID | 输入 / 调用 | 期望 | 类型 | 状态 |
|---|---|---|---|---|---|
| TC-AGENT-017-a | R-AGENT-017 | 源码扫描：`SystemPromptConfig.kt` 常量 `GATEWAY_AWARENESS_EN` | 必须同时包含 `update_memory` + `link_memories` + `delete_memory` 三个工具名字面值（"维护工具三件套被明确点名"），任一缺失即未告知 agent 维护职责。 | unit-scan | `SystemPromptMemoryMaintenanceWiringTest#TC-AGENT-017-a english prompt names three maintenance tools` 🟢 |
| TC-AGENT-017-b | R-AGENT-017 | 源码扫描：`SystemPromptConfig.kt` 常量 `GATEWAY_AWARENESS_EN` | 必须含 `contradicts` 字面值（鼓励 `link_type="contradicts"` 的 hint）+ `conflict resolution` 或 `consistency maintenance` 任一字面值（"维护责任"语义已下沉到 prompt）。 | unit-scan | `SystemPromptMemoryMaintenanceWiringTest#TC-AGENT-017-b english prompt mentions contradicts and maintenance duty` 🟢 |
| TC-AGENT-017-c | R-AGENT-017 | 源码扫描：`SystemPromptConfig.kt` 常量 `GATEWAY_AWARENESS_EN` | **不得**再包含字面字符串 `you do not need to save memories manually`（堵路的老句子必须删/替换 —— 否则 R-AGENT-017 维护语义会被它直接抵消）。 | unit-scan | `SystemPromptMemoryMaintenanceWiringTest#TC-AGENT-017-c english prompt removes do-not-save-manually anti-pattern` 🟢 |
| TC-AGENT-017-d | R-AGENT-017 | 源码扫描：`SystemPromptConfig.kt` 常量 `GATEWAY_AWARENESS_CN` | 必须同时包含 `update_memory` + `link_memories` + `delete_memory` 三个工具名字面值（中文 prompt 也点名维护工具）。 | unit-scan | `SystemPromptMemoryMaintenanceWiringTest#TC-AGENT-017-d chinese prompt names three maintenance tools` 🟢 |
| TC-AGENT-017-e | R-AGENT-017 | 源码扫描：`SystemPromptConfig.kt` 常量 `GATEWAY_AWARENESS_CN` | 必须含 `contradicts` 字面值（中英 link_type 字面值保留英文）+ `矛盾` 字面字符 + （`维护` 或 `职责` 任一）字面字符（"维护责任"中文语义已下沉）。 | unit-scan | `SystemPromptMemoryMaintenanceWiringTest#TC-AGENT-017-e chinese prompt mentions contradiction and maintenance duty` 🟢 |
| TC-AGENT-017-f | R-AGENT-017 | 源码扫描：`SystemPromptConfig.kt` 常量 `GATEWAY_AWARENESS_CN` | **不得**再包含字面字符串 `无需你手动保存`（中文堵路老句子必须删/替换）。 | unit-scan | `SystemPromptMemoryMaintenanceWiringTest#TC-AGENT-017-f chinese prompt removes do-not-save-manually anti-pattern` 🟢 |
| TC-AGENT-017-g | R-AGENT-017 | 源码扫描：`SystemPromptConfig.kt` 整文件 | 必须**不得**含字面字符串 `auto_extracted`（机制泄漏黑名单：R-AGENT-016 内部 tag 名不得出现在 agent-facing prompt 里 —— 防 prompt 污染：agent 知道 fact 来源后会回避具体表述/故意多输出 bullet/递归把自己幻觉当 fact）。 | unit-scan | `SystemPromptMemoryMaintenanceWiringTest#TC-AGENT-017-g prompt does not leak auto_extracted mechanism` 🟢 |

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

---

### R-AGENT-026: AI 价值判官 keepDecision=false 时**全段** skip（含 fact 抽取）（2026-06-15）

**说明**：R-AGENT-026 是 R-AGENT-013 流程末尾的一个分支（解析 SUMMARY_PROMPT 末尾 `【保留判断】=不值得` / `[Persistence Decision]=not worth`），原本只跳父 `#auto_summary` 整段落库、保留 fact 抽取。**2026-06-15** 用户反馈"记忆库数量太多"——经盘点发现 keepDecision=false 路径是漏洞：AI 已判定整段不值得保存，从中抽 bullet 当独立 fact 自相矛盾，且让该路径变成绕过父 dedup 的后门。改为 keepDecision=false 时**整段 skip**（连 fact 抽取也不跑）。

| TC ID | R-ID | 输入 / 触发 | 期望 | 测试类型 | 实现 / 状态 |
|---|---|---|---|---|---|
| TC-AGENT-026-a | R-AGENT-026 | 源码扫描：`MessageCoordinationDelegate.kt` `forcePersistSummaryToMemory` 函数体 | keepDecision=false 分支必须**只 log + return**，不得调用 `extractAndPersistFacts(`。窗口扫描：从 `keepDecision == false` 起到下一个 `}` 或 `return` 关闭块为止。 | unit-scan | `MessageCoordinationDelegateKeepDecisionWiringTest#TC-AGENT-026-a keepDecision false branch skips fact extraction` 🟢 |
| TC-AGENT-026-b | R-AGENT-026 | 源码扫描：`MessageCoordinationDelegate.kt` | keepDecision=false 分支日志必须含 `chatId` + `len=` 字面值（保持原有诊断能力——用户报记忆库异常时能从 logcat 复原 AI 判定历史）。 | unit-scan | `MessageCoordinationDelegateKeepDecisionWiringTest#TC-AGENT-026-b keepDecision false branch log carries chatId and len` 🟢 |
| TC-AGENT-026-c | R-AGENT-026 | 源码扫描：`MessageCoordinationDelegate.kt` | parseAutoSummaryKeepDecision 函数必须存在；`forcePersistSummaryToMemory` 必须调用它（保证 R-AGENT-026 入口存在）。 | unit-scan | `MessageCoordinationDelegateKeepDecisionWiringTest#TC-AGENT-026-c parser function exists and is invoked` 🟢 |

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

---

## 域 AGENT — Orphan Tag Migration (R-AGENT-029)

测试类:
- `app/src/test/java/com/ai/assistance/operit/data/repository/MemoryRepositoryOrphanTagCleanupTest.kt`
- `app/src/test/java/com/ai/assistance/operit/core/application/OperitApplicationOrphanTagMigrationWiringTest.kt`

**背景**: R-AGENT-027 已删除 `extractAndPersistFacts` 写 `#auto_summary_id:<parentId>` 的代码路径，但**历史 APK**（含 `app-release-r026-aikeep-ba814a70.apk`）装机的 ObjectBox 里仍有该 tag 残留 + R-AGENT-026 keepDecision=false 路径产生的 `#auto_summary_id:-1` 孤儿。R-AGENT-029 = 启动时一次性迁移清理。

测试策略：
- 仓储层行为断言走 source-scan（`MemoryRepository.kt` 重度依赖 ObjectBox / Android Context，纯 JVM 单测构造 BoxStore 复杂度极高，与 R-AGENT-013/014/015/016/017 同策略走源码字符串扫描守 wiring）。
- Application 层钩子断言走 source-scan（`OperitApplication` 不易在 JVM 测试里实例化）。
- 行为正确性由手测兜底（旧 APK 残留 → 升级 → logcat 看清理日志 + MemoryScreen 看 tag 列表）。

| TC ID | R-ID | 输入 / 触发 | 期望 | 测试类型 | 实现 / 状态 |
|---|---|---|---|---|---|
| TC-AGENT-029-a | R-AGENT-029 | 源码扫描：`MemoryRepository.kt` | 必须新增 `findTagsByNamePrefix` 字面值（suspend 函数签名）+ `cleanupOrphanTagsByPrefix` 字面值。 | unit-scan | `MemoryRepositoryOrphanTagCleanupTest#TC-AGENT-029-a repository exposes prefix-based tag query and cleanup api` 🟢 |
| TC-AGENT-029-b | R-AGENT-029 | 源码扫描：`MemoryRepository.kt` `cleanupOrphanTagsByPrefix` 函数体 | 必须含 `runInTx` 调用（单事务保证）+ `tagBox.remove(` 调用（删 tag 实体）+ `memoryBox.put(` 调用（解 ToMany 后 put memory）+ `memory.tags.remove(` 调用（解 ToMany 关系）。 | unit-scan | `MemoryRepositoryOrphanTagCleanupTest#TC-AGENT-029-b cleanup function uses transaction and correct delete order` 🟢 |
| TC-AGENT-029-c | R-AGENT-029 | 源码扫描：`MemoryRepository.kt` `findTagsByNamePrefix` + `cleanupOrphanTagsByPrefix` 函数体 | 必须含 `MemoryTag_.name.startsWith(` 字面值（按 prefix 查 tag 的 ObjectBox condition）+ 空 prefix 守卫（`if (prefix.isEmpty())`）。 | unit-scan | `MemoryRepositoryOrphanTagCleanupTest#TC-AGENT-029-c prefix query uses startsWith and guards empty prefix` 🟢 |
| TC-AGENT-029-d | R-AGENT-029 | 源码扫描：`OperitApplication.kt` | 必须含 `launchOrphanTagMigrationsIfNeeded` 字面值（方法名）+ `"#auto_summary_id:"` 字面值（清理目标 prefix）+ `"hermes_data_migrations"` 字面值（SharedPreferences 名）+ `R_AGENT_029` 字面值（防重入键前缀）。 | unit-scan | `OperitApplicationOrphanTagMigrationWiringTest#TC-AGENT-029-d application source declares migration constants` 🟢 |
| TC-AGENT-029-e | R-AGENT-029 | 源码扫描：`OperitApplication.kt::onCreate` 函数体 | `onCreate` 函数体内必须调用 `launchOrphanTagMigrationsIfNeeded()`（顺序无要求，但必须出现）。 | unit-scan | `OperitApplicationOrphanTagMigrationWiringTest#TC-AGENT-029-e onCreate invokes orphan tag migration hook` 🟢 |
| TC-AGENT-029-f | R-AGENT-029 | 源码扫描：`OperitApplication.kt` `launchOrphanTagMigrationsIfNeeded` 函数体 | 必须含 `profileListFlow.first()` 字面值（多 profile 全遍历）+ `cleanupOrphanTagsByPrefix(` 字面值（调仓储 API）+ `try {` + `catch` 包裹（失败容忍）+ `prefs.edit().putBoolean(` 调用（成功才写完成标记）。 | unit-scan | `OperitApplicationOrphanTagMigrationWiringTest#TC-AGENT-029-f migration iterates all profiles with try-catch and writes done flag` 🟢 |

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

---

## 域 AGENT — Auto-Fragment Bucket Roots + Cold Archive (R-AGENT-038)

R-AGENT-038 把"对话压缩摘要 (`#auto_summary`) + 自动抽取碎片 (`#auto_extracted`) + 历史编号碎片 (`#auto_summary_id:NNN`)"三大碎片来源**结构化合并**为 3 个根节点，并把溢出条目按桶+日期归档到 `Context.filesDir/hermes/memory_archive/<bucket>/<YYYY-MM-DD>.jsonl`。本 R = phase 1 = 骨架接入（新写入走 archiver；旧节点保留不动），R-AGENT-039 = phase 2 = 历史迁移 + UI + 召回改造。

测试策略：
- 仿 `MemoryRepositoryOrphanTagCleanupTest` / `OperitApplicationOrphanTagMigrationWiringTest` 用 source-scan 做接入守护（关键字面值 / 调用顺序）。
- `MemoryArchiver` 行为层用 unit test：fake `MemoryRepository` + 临时目录承接 jsonl 写入；断言 root content 形态、dedup 路径、rollover slice 行为、IO 失败容错。

| TC ID | R-ID | 输入 / 触发 | 期望 | 测试类型 | 实现 / 状态 |
|---|---|---|---|---|---|
| TC-AGENT-038-a | R-AGENT-038 | 第一次调 `MemoryArchiver.appendToRoot(SUMMARY, chatId="c1", content="hello", ts=…)`，repository 中无现成 `#auto_summary_root` tag | 仓库新建一条 Memory，tags = `{#auto_summary_root, #auto_root}`；content 为单行 `[<ISO ts>] (chat=c1) hello\n`；返回 `AppendResult.Created` | unit | `MemoryArchiverTest#TC-AGENT-038-a first append lazily creates root with bucket and shared auto tag` 🔴 |
| TC-AGENT-038-b | R-AGENT-038 | 在已有 root（含 1 行旧内容）上连续 append 2 条新内容 | content 行序为 newest-first：第 0 行是最后一次 append、第 1 行是上一次 append、第 2 行是最早那条；返回 `AppendResult.Appended` | unit | `MemoryArchiverTest#TC-AGENT-038-b subsequent appends prepend newest first preserving prior lines` 🔴 |
| TC-AGENT-038-c | R-AGENT-038 | root 已有 `MAX_HOT_LINES_SUMMARY=200` 行，再 append 一条让总数达到 201 | rollover：oldest 20 行 (index 181..200) 被写入 `<filesDir>/hermes/memory_archive/auto_summary/<YYYY-MM-DD>.jsonl`，每行是 `{ts, chat_id, content, source}` JSON；root content 截断为最新 181 行（200-20+1）；返回 `AppendResult.AppendedWithRollover(20)` | unit | `MemoryArchiverTest#TC-AGENT-038-c overflow rolls oldest 20 lines to dated jsonl and trims root` 🔴 |
| TC-AGENT-038-d | R-AGENT-038 | root 已含 `"今天天气真好"`；append 新内容 `"今天 天气 真好"`（jaccard ≥ 0.75） | dedup 命中：root content 不变（仍为 1 行），不写 jsonl，返回 `AppendResult.SkippedDuplicate` | unit | `MemoryArchiverTest#TC-AGENT-038-d high-similarity append is dropped without modifying root or archive` 🔴 |
| TC-AGENT-038-e | R-AGENT-038 | rollover 时 archive 目录不可写（fake `File.outputStream()` 抛 IOException） | root content 保持 rollover 前状态（不被截断也不丢内容）；返回 `AppendResult.Failed`；archiver 内部 `try/catch` + `AppLogger.w` 记录但不抛 | unit | `MemoryArchiverTest#TC-AGENT-038-e archive io failure leaves root content intact` 🔴 |
| TC-AGENT-038-f | R-AGENT-038 | 源码扫描：`MessageCoordinationDelegate.kt` `forcePersistSummaryToMemory` + `extractAndPersistFacts` 函数体 | 函数体必须包含 `memoryArchiver.appendToRoot(` 字面值；**不得**再出现 `repository.addTagToMemory(...#auto_summary` / `...#auto_extracted` 字面值（写入路径已切到 archiver） | unit-scan | `MessageCoordinationDelegateAutoNodeWiringTest#TC-AGENT-038-f delegate routes auto-summary and auto-extracted writes through archiver` 🔴 |
| TC-AGENT-038-g | R-AGENT-038 | 源码扫描：`MemoryArchiver.kt` 文件文本 | 必须含字面值 `MAX_HOT_LINES_SUMMARY = 200` + `MAX_HOT_LINES_EXTRACTED = 100` + `MAX_HOT_LINES_SUMMARY_ID = 50` + `"hermes/memory_archive"` 路径前缀 + `try {` / `catch` 守住 IO + `appendText(` 或 `outputStream(` 调用（jsonl append-only 写入） | unit-scan | `MemoryArchiverTest#TC-AGENT-038-g archiver source declares thresholds path and io guard` 🔴 |

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

---

## 域 AGENT — `session_search` 工具：agent 端主动召回会话历史 (R-AGENT-039)

R-AGENT-039 给 agent 暴露一个名为 `session_search` 的工具（与 Python 上游 `tools/session_search_tool.py` 工具名一致），用户说"翻翻之前聊过的 X" 时 agent 可主动调；底层先接 ObjectBox `MemoryRepository.searchMemories`（R-AGENT-038 root 节点 + 老 `#auto_summary` 老节点都能命中）。本阶段**不**读 jsonl 冷归档（留给 R-AGENT-042）。

测试策略：
- 工具暴露 / 调度路由 / prompt 教学 / 描述不泄露内部 tag → source-scan（仿 R-AGENT-017 / R-AGENT-030 同范式）。
- 输出截断 / 空 query / 0 命中 / 异常容错 → 用 fake `MemoryRepository` 跑行为 unit。
- 不做 agent-level 真调测试（属 §3 E2E 的 `test_tool_call_e2e.sh` 覆盖范围）。

| TC ID | R-ID | 输入 / 触发 | 期望 | 测试类型 | 实现 / 状态 |
|---|---|---|---|---|---|
| TC-AGENT-039-a | R-AGENT-039 | 源码扫描：`core/tools/ToolRegistration.kt` | 必须含 `"session_search"` 字面值（工具注册名）+ category 为 `MEMORY` 字面值 / 等价枚举（位于 `session_search` 注册块内）+ danger = `LOW` 字面 / `Tool.Danger.LOW` 枚举。 | unit-scan | `SessionSearchToolWiringTest#TC-AGENT-039-a tool registration declares session_search with memory category and low danger` 🔴 |
| TC-AGENT-039-b | R-AGENT-039 | 源码扫描：`core/tools/defaultTool/standard/MemoryQueryToolExecutor.kt::invoke` 函数体 | 函数体内 `when` / `switch` 块必须含 `"session_search" -> ` 字面分支，分支体调用 `executeSessionSearch(` 字面（私有 suspend 函数名）。 | unit-scan | `SessionSearchToolWiringTest#TC-AGENT-039-b executor dispatches session_search to executeSessionSearch branch` 🔴 |
| TC-AGENT-039-c | R-AGENT-039 | 源码扫描：`core/config/SystemToolPrompts.kt` | 必须含 `session_search` EN 工具描述段（内含 `query` + `limit` 两个参数说明字面）+ CN 描述段（含 `query` + `limit` 字面）。 | unit-scan | `SessionSearchToolWiringTest#TC-AGENT-039-c system tool prompts describe session_search params in both locales` 🔴 |
| TC-AGENT-039-d | R-AGENT-039 | 源码扫描：`core/config/SystemToolPrompts.kt` `session_search` 描述段 | **不**得含 `auto_extracted` / `auto_summary` 字面值（守 R-AGENT-017-g 红线：prompt 不泄露内部 tag 机制）。 | unit-scan | `SessionSearchToolWiringTest#TC-AGENT-039-d session_search description does not leak internal tag names` 🔴 |
| TC-AGENT-039-e | R-AGENT-039 | 源码扫描：`core/config/SystemPromptConfig.kt` `GATEWAY_AWARENESS_EN` + `GATEWAY_AWARENESS_CN`（或等价 system prompt 段） | 两段都必须各含一处 `session_search` 字面（教 agent "翻找历史"时主动调本工具）。 | unit-scan | `SessionSearchToolWiringTest#TC-AGENT-039-e prompt teaches session_search in both locales` 🔴 |
| TC-AGENT-039-f | R-AGENT-039 | 源码扫描：`MemoryQueryToolExecutor.kt` `executeSessionSearch` 函数体 | 必须含 8000 字符截断逻辑（字面值 `8000` + `take(` 或 `substring(` 调用）+ `…[truncated]` 字面 / 等价后缀；必须含 `limit` 参数 clamp 逻辑（`coerceIn(` 或 `coerceAtMost(50)` 等价表达）。 | unit-scan | `MemoryQueryToolExecutorSessionSearchTest#TC-AGENT-039-f session_search truncates output and clamps limit` 🔴 |
| TC-AGENT-039-g | R-AGENT-039 | 源码扫描：`MemoryQueryToolExecutor.kt` `executeSessionSearch` 函数体 | 必须含三种边界守卫：(1) 空 query 走 `ToolResult(... success = false ...)` 路径（含 `query` + `isBlank` / `isEmpty` 字面）；(2) 0 命中走 success 路径（含 `"No matching memories found"` 字面）；(3) `try { ... } catch` 包裹 `searchMemories` 调用 + `AppLogger.w` 或 `AppLogger.e` 记录 + `ToolResult(... success = false ...)` 不穿透异常。 | unit-scan | `MemoryQueryToolExecutorSessionSearchTest#TC-AGENT-039-g session_search guards empty query empty result and io exception` 🔴 |

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

---

## 域 AGENT — 启动迁移：存量散节点合并到 root (R-AGENT-040)

R-AGENT-040 = R-AGENT-038 phase 2 第一步。app cold start 时一次性把旧 APK 装机产生的散 `#auto_summary` / `#auto_extracted` / `#auto_summary_id:NNN` 节点合并到 R-AGENT-038 phase 1 的 3 个 root 节点（复用 archiver `appendToRoot` 内置 dedup + rollover）。**不删旧节点**（保险起见 phase 2 留着，R-AGENT-041 才删）；**不动写入侧**（archiver / delegate / repository 一行不动，只在 `OperitApplication.onCreate` 加一个迁移 hook）。

测试策略：仿 R-AGENT-029 / R-AGENT-038 同款，全部 source-scan。`OperitApplication.onCreate` 触发的 IO 协程涉及 ObjectBox + Context.filesDir + SharedPreferences，纯 JVM mock ROI 极低；运行时正确性由 §3 E2E + 用户带历史散节点设备的手测兜底。

| TC ID | 关联 R | 输入 / 现状 | 期望 | 测试类型 | 测试落地 |
|---|---|---|---|---|---|
| TC-AGENT-040-a | R-AGENT-040 | 源码扫描：`OperitApplication.kt` | 必须含 `launchAutoNodeArchiverMigrationIfNeeded` 字面值（方法名）+ `R_AGENT_040_auto_node_consolidation_done` 字面（done flag key）+ `"hermes_data_migrations"` 字面（SharedPreferences 名，与 R-AGENT-029 共用）。 | unit-scan | `OperitApplicationAutoNodeArchiverMigrationWiringTest#TC-AGENT-040-a application source declares migration constants` 🔴 |
| TC-AGENT-040-b | R-AGENT-040 | 源码扫描：`OperitApplication.kt::onCreate` 函数体 | `onCreate` 函数体内必须调用 `launchAutoNodeArchiverMigrationIfNeeded()`（顺序无要求，但必须出现）。 | unit-scan | `OperitApplicationAutoNodeArchiverMigrationWiringTest#TC-AGENT-040-b onCreate invokes migration hook` 🔴 |
| TC-AGENT-040-c | R-AGENT-040 | 源码扫描：`OperitApplication.kt::launchAutoNodeArchiverMigrationIfNeeded` 函数体 | 必须含：(1) `applicationScope.launch` / 等价后台协程；(2) `getSharedPreferences("hermes_data_migrations"` 字面；(3) `getBoolean(` + done flag key 的短路返回；(4) `prefs.edit().putBoolean(` + done flag key + `true` 字面（成功路径置位）。 | unit-scan | `OperitApplicationAutoNodeArchiverMigrationWiringTest#TC-AGENT-040-c migration hook uses background scope and done flag short-circuit` 🔴 |
| TC-AGENT-040-d | R-AGENT-040 | 源码扫描：`launchAutoNodeArchiverMigrationIfNeeded` 函数体 | 必须扫描三段 tag：(1) `"#auto_summary"` 字面 + `findMemoriesByTag(` 调用；(2) `"#auto_extracted"` 字面 + `findMemoriesByTag(` 调用；(3) `"#auto_summary_id:"` 字面 + `findTagsByNamePrefix(` 调用（变长后缀走 prefix 扫）。必须引用 `ArchiveBucket.SUMMARY` / `ArchiveBucket.EXTRACTED` / `ArchiveBucket.SUMMARY_ID` 三个枚举。必须调 `appendToRoot(` 至少一次（迁移落库动作）。必须调 `MemoryArchiver(` 构造（per-profile 实例化）。 | unit-scan | `OperitApplicationAutoNodeArchiverMigrationWiringTest#TC-AGENT-040-d migration scans three tag families and writes via archiver` 🔴 |
| TC-AGENT-040-e | R-AGENT-040 | 源码扫描：`launchAutoNodeArchiverMigrationIfNeeded` 函数体 | 必须含：(1) `profileListFlow.first()` 调用（遍历所有 profile）；(2) `try {` + `catch (` 包住主体；(3) catch 路径含 `AppLogger.w(` 调用；(4) catch 路径**不**写 `prefs.edit().putBoolean(...true)`（done flag 只在 try 末尾置位）—— 等价表达：done flag 置位语句必须出现在 catch 块**之外**。 | unit-scan | `OperitApplicationAutoNodeArchiverMigrationWiringTest#TC-AGENT-040-e migration iterates profiles guards exceptions and skips done flag on failure` 🔴 |
| TC-AGENT-040-f | R-AGENT-040 | 源码扫描：`launchAutoNodeArchiverMigrationIfNeeded` 函数体 | 必须含 chatId 提取逻辑：从 Memory 的 `tags` ToMany 找 `#chat:` 前缀的 tag，取后缀作为 chatId 传给 `appendToRoot`；找不到时传 `""`。验收点：函数体含 `"#chat:"` 字面 + `removePrefix(` 或 `substringAfter(` 或 `drop(` 等价表达。**不**得直接传 `Memory.uuid` / `Memory.id` 作为 chatId（因为 Memory 没有 chatId 字段，旧节点的 chatId 在 tag 上）。 | unit-scan | `OperitApplicationAutoNodeArchiverMigrationWiringTest#TC-AGENT-040-f migration extracts chatId from chat-prefixed tag with empty fallback` 🔴 |

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

---

## 域 AGENT — 启动迁移：删除存量散 auto-* 节点 (R-AGENT-041-a)

R-AGENT-041-a = R-AGENT-038 phase 2 第二步，紧跟 R-AGENT-040 之后。app cold start 时一次性把已被 R-AGENT-040 合并到 root 的旧 `#auto_summary` / `#auto_extracted` / `#auto_summary_id:NNN` 散节点从 ObjectBox 删除（root 节点身上挂的是 `#auto_summary_root` / `#auto_extracted_root` / `#auto_summary_id_root` + `#auto_root` 标识 tag，与散节点 tag 字面不同；SUMMARY_ID 走 prefix 扫，必须排除带 `#auto_root` 的 root，防止误删）。**前置门禁**：必须先确认 R-AGENT-040 done flag (`R_AGENT_040_auto_node_consolidation_done`) 为 `true`，否则跳过本次执行（不写 041-a done flag），下次冷启再试，避免在合并完成前就把数据删掉。

测试策略：仿 R-AGENT-029 / R-AGENT-040 同款，全部 source-scan。`OperitApplication.onCreate` 触发的 IO 协程涉及 ObjectBox + SharedPreferences，纯 JVM mock ROI 极低；运行时正确性由 §3 E2E + 用户带历史散节点设备的手测兜底（升级 → logcat `R-AGENT-041-a: deletion done` + MemoryScreen 看散节点已消失，root 节点保留）。

| TC ID | 关联 R | 输入 / 现状 | 期望 | 测试类型 | 测试落地 |
|---|---|---|---|---|---|
| TC-AGENT-041-a-a | R-AGENT-041-a | 源码扫描：`OperitApplication.kt` | 必须含 `launchLegacyAutoNodeDeletionIfNeeded` 字面值（方法名）+ `R_AGENT_041_legacy_node_deletion_done` 字面（done flag key）+ `"hermes_data_migrations"` 字面（SharedPreferences 名，与 R-AGENT-029 / R-AGENT-040 共用）。 | unit-scan | `OperitApplicationLegacyAutoNodeDeletionWiringTest#TC-AGENT-041-a-a application source declares deletion migration constants` 🔴 |
| TC-AGENT-041-a-b | R-AGENT-041-a | 源码扫描：`OperitApplication.kt::onCreate` 函数体 | `onCreate` 函数体内必须调用 `launchLegacyAutoNodeDeletionIfNeeded()`，并且这次调用必须出现在 `launchAutoNodeArchiverMigrationIfNeeded()` 之后（顺序：先合并、再删除；用 indexOf 比较两个调用字面位置）。 | unit-scan | `OperitApplicationLegacyAutoNodeDeletionWiringTest#TC-AGENT-041-a-b onCreate invokes deletion hook after archiver migration hook` 🔴 |
| TC-AGENT-041-a-c | R-AGENT-041-a | 源码扫描：`OperitApplication.kt::launchLegacyAutoNodeDeletionIfNeeded` 函数体 | 必须含前置门禁：(1) `getBoolean(` + `R_AGENT_040_auto_node_consolidation_done` 字面（读 040 done flag）；(2) 040 done flag 为 `false` 时短路 `return` 且**不**写 041-a done flag（041-a done flag 写入语句必须出现在「040 done flag = true」分支内）；(3) 041-a 自己的短路：`getBoolean(` + `R_AGENT_041_legacy_node_deletion_done` 字面 + 早 return；(4) 后台协程 `applicationScope.launch`；(5) `getSharedPreferences("hermes_data_migrations"` 字面；(6) 成功路径写 `putBoolean(R_AGENT_041_legacy_node_deletion_done, true)`。 | unit-scan | `OperitApplicationLegacyAutoNodeDeletionWiringTest#TC-AGENT-041-a-c deletion hook gates on R-AGENT-040 done flag and uses background scope` 🔴 |
| TC-AGENT-041-a-d | R-AGENT-041-a | 源码扫描：`launchLegacyAutoNodeDeletionIfNeeded` 函数体 | 必须含三段删除动作：(1) `deleteByTag("#auto_summary")` 字面调用（root 标识是 `#auto_summary_root`，字面不同所以安全）；(2) `deleteByTag("#auto_extracted")` 字面调用；(3) SUMMARY_ID 走 `findTagsByNamePrefix("#auto_summary_id:")` + `deleteMemories(` + `cleanupOrphanTagsByPrefix("#auto_summary_id:")` 三步组合（变长后缀必须 prefix 扫）。必须 `MemoryRepository(` 构造（per-profile 实例化）。 | unit-scan | `OperitApplicationLegacyAutoNodeDeletionWiringTest#TC-AGENT-041-a-d deletion scans three tag families and deletes via repository` 🔴 |
| TC-AGENT-041-a-e | R-AGENT-041-a | 源码扫描：`launchLegacyAutoNodeDeletionIfNeeded` 函数体 | 必须含：(1) `profileListFlow.first()` 调用（遍历所有 profile）；(2) `try {` + `catch (` 包住主体；(3) catch 路径含 `AppLogger.w(` 调用；(4) 每个 catch 块**之内**不得出现 `putBoolean(R_AGENT_041_legacy_node_deletion_done, true)`（done flag 失败路径不置位，下次冷启重试）。 | unit-scan | `OperitApplicationLegacyAutoNodeDeletionWiringTest#TC-AGENT-041-a-e deletion iterates profiles guards exceptions and skips done flag on failure` 🔴 |
| TC-AGENT-041-a-f | R-AGENT-041-a | 源码扫描：`launchLegacyAutoNodeDeletionIfNeeded` SUMMARY_ID 删除分支 | 函数体必须含 `#auto_root` 字面值，且必须出现 `none {` / `!` + `any {` / `filter` 等价排除表达式（即"持有 `#auto_root` tag 的节点不进入待删 id 列表"）—— 防御性守门：万一 `#auto_summary_id_root` 标识 tag 也以 `#auto_summary_id:` 开头（实际上不是，但任何未来命名漂移都不能误删 root），强制以 `#auto_root` 二级 tag 排除。**反向红线**：函数体不得无条件把 `findTagsByNamePrefix` 返回的所有 owner ids 直接传给 `deleteMemories`。 | unit-scan | `OperitApplicationLegacyAutoNodeDeletionWiringTest#TC-AGENT-041-a-f deletion excludes nodes carrying auto_root tag from SUMMARY_ID prefix scan` 🔴 |

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

---

## 域 AGENT — root 节点详情页冷归档 jsonl 展示 (R-AGENT-041-c)

R-AGENT-041-c = R-AGENT-038 数据闭环最后一公里。`MemoryArchiver` 已经把每条 summary / extracted / summary_id 写到 `<filesDir>/hermes/memory_archive/<bucket.dirName>/<yyyy-MM-DD>.jsonl` 冷归档，但 UI 端没有读路径，用户在 MemoryScreen 点开 root 节点只看到聚合 content 看不到底层原始行。本条把 root 节点详情页接通冷归档读路径：判定为 root → 拉对应 bucket 的 jsonl → 按 chatId 分组 + ts 倒序展示。

测试策略（混合）：
- **真单测** 覆盖 `parseArchiveJsonl` 纯函数（输入 String / 输出 List，ROI 极高）。
- **源码字符串扫描** 覆盖 IO / VM / UI 三层 wiring：`MemoryArchiver` 读 API、`MemoryViewModel` 冷归档 state、`MemoryDialogs` root 分支渲染都涉及 Android Context / Compose / Flow，纯 JVM mock ROI 极低，沿用 R-AGENT-029/038/039/040/041-a 同范式。

| TC ID | 关联 R | 输入 / 现状 | 期望 | 测试类型 | 测试落地 |
|---|---|---|---|---|---|
| TC-AGENT-041-c-a | R-AGENT-041-c | `parseArchiveJsonl` 输入：3 行有效 jsonl 文本（每行 `{ts, chat_id, content, source}`） | 返回 size = 3 的 `List<ArchiveEntry>`，每行字段映射正确（ts Long、chatId String、content String、source String）。 | unit | `MemoryArchiverColdArchiveParseTest#TC-AGENT-041-c-a parses well-formed jsonl into archive entries` 🟢 |
| TC-AGENT-041-c-b | R-AGENT-041-c | `parseArchiveJsonl` 输入：5 行混杂文本（2 行有效 + 1 行非 JSON 乱码 + 1 行 JSON 缺 `chat_id` 字段 + 1 行空白） | 返回 size = 2 的列表（只含 2 行有效行）；坏行不抛异常、不污染列表。 | unit | `MemoryArchiverColdArchiveParseTest#TC-AGENT-041-c-b skips malformed lines without throwing` 🟢 |
| TC-AGENT-041-c-c | R-AGENT-041-c | `parseArchiveJsonl` 输入：空字符串 / 全空白 / 仅换行 | 返回 emptyList，不抛。 | unit | `MemoryArchiverColdArchiveParseTest#TC-AGENT-041-c-c handles empty and whitespace-only input` 🟢 |
| TC-AGENT-041-c-d | R-AGENT-041-c | 源码扫描：`data/repository/MemoryArchiver.kt` | 必须含 (1) data class `ArchiveEntry(`（嵌套或顶层）字面，含 `ts` / `chatId` / `content` / `source` 四字段；(2) 顶层 `fun parseArchiveJsonl(` 签名（接受 `String`，返回 `List<`）；(3) `fun loadColdArchive(` instance method 签名（参数 `ArchiveBucket`，返回 `List<`）；(4) `fun bucketForRootMemory(` 签名（参数含 `Memory`，返回类型含 `ArchiveBucket?` 或等价 nullable）。 | unit-scan | `MemoryArchiverColdArchiveReadWiringTest#TC-AGENT-041-c-d archiver declares cold archive read api surface` 🟢 |
| TC-AGENT-041-c-e | R-AGENT-041-c | 源码扫描：`MemoryArchiver.loadColdArchive` 函数体 | 必须含 (1) `archiveDir(` 调用拿到 bucket 对应目录；(2) `.jsonl` 字面（用文件名后缀过滤）；(3) `sortedByDescending` 或 `sortedDescending` 等价（最近日期在前）；(4) `readText(` 字面（读文件内容）；(5) `parseArchiveJsonl(` 调用；(6) `try {` + `catch (` 包裹（IO 失败不能拖垮 UI）。 | unit-scan | `MemoryArchiverColdArchiveReadWiringTest#TC-AGENT-041-c-e loadColdArchive lists jsonl files sorts desc reads parses with try-catch` 🟢 |
| TC-AGENT-041-c-f | R-AGENT-041-c | 源码扫描：`ui/features/memory/screens/dialogs/MemoryDialogs.kt::MemoryInfoDialog` Composable | 必须含 (1) `coldArchiveEntries` 参数（默认 `emptyList`）；(2) `"#auto_root"` 字面（root 节点判定）；(3) `LazyColumn(` 字面（列表性能）；(4) `groupBy` 等价表达 + `chatId` 引用（按 chatId 分组）；(5) `sortedByDescending` 等价表达 + `ts` 引用（组内 ts 倒序）。 | unit-scan | `MemoryDialogsColdArchiveWiringTest#TC-AGENT-041-c-f info dialog renders cold archive section grouped by chatId on root nodes` 🟢 |
| TC-AGENT-041-c-g | R-AGENT-041-c | 源码扫描：`ui/features/memory/viewmodel/MemoryViewModel.kt` | 必须含 (1) `coldArchiveEntries` StateFlow 字段（含 `StateFlow<` 字面 + `ArchiveEntry` 引用）；(2) `selectNode` 函数体内 `bucketForRootMemory(` 调用 + `loadColdArchive(` 调用；(3) `Dispatchers.IO` 字面（后台 IO 协程不阻塞主线程）；(4) `clearSelection` 函数体内对 `_coldArchiveEntries` 重置 emptyList 的写入（防止 root 节点关闭后旧数据残留）。 | unit-scan | `MemoryViewModelColdArchiveWiringTest#TC-AGENT-041-c-g viewmodel exposes cold archive state and loads on root selection` 🟢 |

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

---

## 域 AGENT — MemoryScreen chip 过滤族 + root 配色 (R-AGENT-041-b)

R-AGENT-041-b = R-AGENT-038/040/041-a 链路视觉收尾。三 root 节点（`#auto_summary_root` /
`#auto_extracted_root` / `#auto_summary_id_root` 共享 `#auto_root` family tag）落地后，MemoryScreen
上 root 节点和 Person/Concept 节点视觉无差，难以一眼区分；同时缺一个三态过滤切换让用户在"看用户原创"和
"看自动归档枢纽"之间切。本条把两件事做完：(1) `pickNodeColorByAttributes` 增加三 bucket 专属色
（红/橘/紫红），(2) `MemoryViewModel` 加 `AutoRootFilter` 三态 + 与现有 `GatewayFilter` 正交并行，
(3) `MemoryScreen` 加 `AutoRootFilterChipRow` 渲染在 `GatewayFilterChipRow` 之后。

测试策略：混合 —— 配色是 pure-logic 函数（ROI 高，走真单测，仿 `MemoryRepositoryGatewayColorTest` 同款）；
ViewModel filter 链是 in-memory 转换（ROI 高，走真单测）；UI 接线是 Compose（ROI 低，走 source-scan）。
**不**跑 §3 E2E（UI-only 视觉/交互改动，不触及 agent loop / API / 工具派发）。

| TC ID | 关联 R-ID | 输入 | 期望 | 类型 | 文件位置 / 状态 |
|---|---|---|---|---|---|
| TC-AGENT-041-b-a | R-AGENT-041-b | `pickNodeColorByAttributes(listOf("#auto_summary_root", "#auto_root"), false)` | 返回红色 `Color(0xFFEF5350)`（auto_summary 专属色）。 | unit | `MemoryRepositoryAutoRootColorTest#TC-AGENT-041-b-a auto_summary_root tag returns red` 🟢 |
| TC-AGENT-041-b-b | R-AGENT-041-b | `pickNodeColorByAttributes(listOf("#auto_extracted_root", "#auto_root"), false)` 与 `pickNodeColorByAttributes(listOf("#auto_summary_id_root", "#auto_root"), false)` | 分别返回橘色 `Color(0xFFFFA726)` 和紫红 `Color(0xFFAB47BC)`，三 bucket 各自一色不互窜。 | unit | `MemoryRepositoryAutoRootColorTest#TC-AGENT-041-b-b auto_extracted and auto_summary_id roots return their own colors` 🟢 |
| TC-AGENT-041-b-c | R-AGENT-041-b | `pickNodeColorByAttributes(listOf("#persistent_instruction", "#auto_summary_root", "#auto_root"), false)` 与 `pickNodeColorByAttributes(listOf("#auto_summary_root", "#auto_root", "#gateway:feishu"), false)` | 第一个返回 GOLD（`#persistent_instruction` 优先于 root）；第二个返回红色 root 色（root 优先于 `#gateway:*`）。守优先级排序：persistent_instruction > isDocumentNode > root > gateway > Person/Concept > LightGray。 | unit | `MemoryRepositoryAutoRootColorTest#TC-AGENT-041-b-c root tag priority sits between persistent_instruction and gateway` 🟢 |
| TC-AGENT-041-b-d | R-AGENT-041-b | `applyAutoRootFilterToGraph` 输入：手搓 graph 含 5 节点（1 个 `#auto_summary_root` / 1 个 `#auto_extracted_root` / 1 个 `#auto_summary_id_root` / 1 个 `#gateway:feishu` / 1 个 `Person`），filter = `AutoRootFilter.HideAuto` | 返回 size = 2 的 nodes（只剩 gateway + Person，三 root 全部被屏蔽）；edges 同步剪悬挂边。 | unit | `MemoryViewModelAutoRootFilterTest#TC-AGENT-041-b-d HideAuto removes all auto_root nodes` 🟢 |
| TC-AGENT-041-b-e | R-AGENT-041-b | 同上 graph，filter = `AutoRootFilter.OnlyAuto(setOf("#auto_summary_root"))` | 返回 size = 1 的 nodes（只剩 `#auto_summary_root`）；空 set 时（`OnlyAuto(emptySet())`）返回 size = 3（三 root 全部）。 | unit | `MemoryViewModelAutoRootFilterTest#TC-AGENT-041-b-e OnlyAuto with bucket subset filters to chosen buckets` 🟢 |
| TC-AGENT-041-b-f | R-AGENT-041-b | 同上 graph，filter = `AutoRootFilter.HideAuto` 且 `gatewayFilter = GatewayFilter.ExcludeGateway` | 两个 filter 正交叠加：返回 size = 1 的 nodes（只剩 Person，gateway 被 gateway filter 屏蔽 + 三 root 被 auto-root filter 屏蔽）。守"两个 filter 互不干扰、可叠加"红线。 | unit | `MemoryViewModelAutoRootFilterTest#TC-AGENT-041-b-f auto_root filter and gateway filter compose orthogonally` 🟢 |
| TC-AGENT-041-b-g | R-AGENT-041-b | 源码扫描：`ui/features/memory/screens/MemoryScreen.kt` | 必须含 (1) `AutoRootFilterChipRow` composable 声明（函数 / private fun 字面）；(2) MemoryScreen 主 layout 内对 `AutoRootFilterChipRow(` 的调用（与 `GatewayFilterChipRow(` 平行渲染，二者**同时存在**）；(3) 4 条新 string 资源引用：`memory_filter_auto_root_all` / `memory_filter_auto_root_hide` / `memory_filter_auto_root_summary` / `memory_filter_auto_root_extracted` / `memory_filter_auto_root_summary_id`（5 个，覆盖 All / Hide / 三 bucket）；(4) chip row 早返回 `isEmpty()` 判定（graph 没有任何 `#auto_root` 节点时整 row 不显示）。 | unit-scan | `MemoryScreenAutoRootChipWiringTest#TC-AGENT-041-b-g screen renders auto_root chip row alongside gateway chip row` 🟢 |

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

---

## 域 AGENT — App Self-Awareness Prompt Injection (R-AGENT-030)

R-AGENT-030 让主 agent 的 system prompt 注入「应用自我感知」段，告诉 agent HermesApp 内置了哪些用户视角的 UI 入口（工具箱 / Memory hub / Settings / Skill Recorder / Terminal 等），方便 agent 在被问"我去哪里 X"时给出导航式回答而非自己代劳或瞎猜。

测试策略：
- 仿 `SystemPromptMemoryMaintenanceWiringTest` 同范式做 source-scan：直接对 `core/config/SystemPromptConfig.kt` 文件文本断言关键字面值 / 占位符 / replace 调用，**不**依赖 Android Context / Compose / LLM。
- 不做行为层（要求 agent 实际给出"打开工具箱"建议）的单测——属于 prompt 行为层，由手测兜底（参考 §3 三个 E2E 已经覆盖 agent-level TOKEN echo 的回答正确性框架）。

| TC ID | R-ID | 输入 / 触发 | 期望 | 测试类型 | 实现 / 状态 |
|---|---|---|---|---|---|
| TC-AGENT-030-a | R-AGENT-030 | 源码扫描：`core/config/SystemPromptConfig.kt` | 必须含 `const val APP_SELF_AWARENESS_EN` 和 `const val APP_SELF_AWARENESS_CN` 两个声明（字面值）。 | unit-scan | `SystemPromptAppSelfAwarenessWiringTest#TC-AGENT-030-a config exposes APP_SELF_AWARENESS constants for both languages` 🟢 |
| TC-AGENT-030-b | R-AGENT-030 | 源码扫描：`SYSTEM_PROMPT_TEMPLATE` + `SYSTEM_PROMPT_TEMPLATE_CN` 两个常量体 | 两者都必须各含一处字面 `APP_SELF_AWARENESS_SECTION` 占位符；位置必须在 `GATEWAY_AWARENESS_SECTION` 之后、`TOOL_USAGE_GUIDELINES_SECTION` 之前。 | unit-scan | `SystemPromptAppSelfAwarenessWiringTest#TC-AGENT-030-b both system prompt templates contain APP_SELF_AWARENESS placeholder between gateway and tool usage` 🟢 |
| TC-AGENT-030-c | R-AGENT-030 | 源码扫描：`getSystemPrompt(...)` 函数体 | 必须含 `replace("APP_SELF_AWARENESS_SECTION", ...)` 调用，三元根据 `useEnglish` 选 EN/CN 常量。 | unit-scan | `SystemPromptAppSelfAwarenessWiringTest#TC-AGENT-030-c getSystemPrompt replaces APP_SELF_AWARENESS placeholder with locale-appropriate constant` 🟢 |
| TC-AGENT-030-d | R-AGENT-030 | 源码扫描：`APP_SELF_AWARENESS_EN` 与 `APP_SELF_AWARENESS_CN` 字符串体 | 中文版必须同时含 `工具箱` / `记忆` / `设置` / `技能录制` / `终端` 五个核心导航关键字；英文版必须同时含 `Toolbox` / `Memory` / `Settings` / `Skill` / `Terminal` 五个对应关键字（守 prompt 内容不被空段或单语段意外提交）。 | unit-scan | `SystemPromptAppSelfAwarenessWiringTest#TC-AGENT-030-d both prompt sections list the core navigation entry points` 🟢 |
| TC-AGENT-030-e | R-AGENT-030 | 源码扫描：`SUBTASK_AGENT_PROMPT_TEMPLATE` 常量体 | 必须**不**含 `APP_SELF_AWARENESS_SECTION` 字面值（守"只主 agent 注入"红线，与 GATEWAY_AWARENESS 同处理）。 | unit-scan | `SystemPromptAppSelfAwarenessWiringTest#TC-AGENT-030-e subtask agent prompt does not get app self-awareness section` 🟢 |

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

---

## 域 AGENT — Memory Dedup (R-AGENT-003 bugfix)

测试类: `app/src/test/java/com/ai/assistance/operit/data/repository/MemoryDedupTest.kt`

**Bugfix 背景**：R-AGENT-003 要求 memory 行为与 Python 上游 `memory_provider.py` 1:1，上游 `MemoryStore.add` 有 `if content in entries: return` 的去重逻辑。Kotlin 侧 `MemoryRepository.createMemory()`（`MemoryRepository.kt:2245-2280`）漏了这一步，且 retrieve 侧的 `// deduplicateBySemantics(sortedMemories)`（`MemoryRepository.kt:1637`）一直被注释。导致 agent 反复写入语义相似的节点。

修复策略：
- **写入侧**：写入前先用 `searchMemories(content)` 找疑似重复 → 命中即返错附候选 → 给 agent `force=true` 后门
- **读出侧**：在 `runSearchMemoriesWithDebug` 末尾启用 `deduplicateBySemantics`，相似条目折叠返回（数据库不动，解决存量噪声污染新写入判定的问题）
- **不**做：自动合并 / 一次性迁移清理 / Settings 手动清理 UI（后者单独 commit）

所有 TC 用 pure-logic 提取（参考 TC-AGENT-243 的 `pickNodeColorByAttributes` 模式）：核心去重判定剥到独立 `MemoryDedup.kt` 顶层函数，不依赖 ObjectBox / Robolectric / Android Context；再用源码扫描固化 wiring 契约（参考 LaunchAppToolTest / AgentStatusOverlayWiringTest 模式）。

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-AGENT-260-a | R-AGENT-003 | `decideDedupOnCreate(newContent="用户喜欢吃辣", candidates=[Memory(content="用户口味偏辣")])`（embedding=null，走 jaccard 回退） | `DedupDecision.blocked=true`，`similarMemories` 含该候选 | unit-pure | `MemoryDedupTest#TC-AGENT-260-a jaccard fallback blocks high-overlap content` 🔴 |
| TC-AGENT-260-b | R-AGENT-003 | `decideDedupOnCreate(newEmbedding=[1,0,0], candidates=[Memory(embedding=[0.99,0.01,0])])` | `blocked=true`（余弦 > 0.85 阈值） | unit-pure | `MemoryDedupTest#TC-AGENT-260-b cosine similarity above threshold blocks` 🔴 |
| TC-AGENT-260-c | R-AGENT-003 | `decideDedupOnCreate(newContent="今天去爬山", candidates=[Memory(content="用户口味偏辣")])` | `blocked=false`（jaccard 远低于阈值且 embedding 兜底也不命中） | unit-pure | `MemoryDedupTest#TC-AGENT-260-c unrelated content allows creation` 🔴 |
| TC-AGENT-260-d | R-AGENT-003 | `decideDedupOnCreate(newContent="完全一字不差", candidates=[Memory(content="完全一字不差")])` | `blocked=true`，`reason` 标 `exact_duplicate`（对齐 Python `if content in entries: return`） | unit-pure | `MemoryDedupTest#TC-AGENT-260-d exact content match marked exact_duplicate` 🔴 |
| TC-AGENT-260-e | R-AGENT-003 | `deduplicateBySemantics([m1(content="A"), m2(content="A"), m3(content="B")])` | 返回长度 2 的列表（保留首个 A + B；重复 A 被折叠） | unit-pure | `MemoryDedupTest#TC-AGENT-260-e read-side dedup folds duplicates preserving order` 🔴 |
| TC-AGENT-260-f | R-AGENT-003 | 源码扫描：`MemoryRepository.kt` | `createMemory` 签名含 `force: Boolean = false` 参数；函数体调用 `decideDedupOnCreate`；`runSearchMemoriesWithDebug` 在过去注释处调用 `deduplicateBySemantics(sortedMemories)` 而非保留 `//` 注释 | unit-scan | `MemoryDedupTest#TC-AGENT-260-f source contract wires dedup in repo` 🔴 |
| TC-AGENT-260-g | R-AGENT-003 | 源码扫描：`MemoryQueryToolExecutor.kt` | `executeCreateMemory` 解析 `force` 参数；将其传给 `createMemory(...)` | unit-scan | `MemoryDedupTest#TC-AGENT-260-g executor parses force param` 🔴 |
| TC-AGENT-260-h | R-AGENT-003 | 源码扫描：`SystemToolPromptsInternal.kt` | `create_memory` 的 EN + CN ToolPrompt 各声明 `force` 参数并在描述中说明 dedup 行为 | unit-scan | `MemoryDedupTest#TC-AGENT-260-h prompts declare force param with dedup hint` 🔴 |

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

### R-AGENT-003 后续: 存量重复手动清理 UI（TC-AGENT-261-a..f）

**背景**：写入侧 + 读出侧 dedup 解决"未来不再产生重复"，但**存量**已被污染节点仍在数据库里——agent 看不到（已被读出侧折叠），但 Memory 图谱上肉眼可见。需要给用户一个"扫描 + 勾选 + 批量删除"的手动清理入口。

设计取舍：
- **不自动迁移**：旧库内容用户可能有偏好，自动合并风险大于收益；只让用户看见、自己决定删谁
- **只删不合并**：UI 强约束首条不可去勾（保留首个出现的），其余默认勾选；agent 不再用旧节点 id，删除安全
- **不进 settings，进 Memory 屏幕**：MemoryAppBar 加扫帚图标，与 Memory 操作上下文一致

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-AGENT-261-a | R-AGENT-003 | `findDuplicateGroups([])` 与 `findDuplicateGroups([m])` | 返回 emptyList（无重复组） | unit-pure | `MemoryDedupTest#TC-AGENT-261-a findDuplicateGroups degenerate inputs` 🔴 |
| TC-AGENT-261-b | R-AGENT-003 | `findDuplicateGroups([m1(content="A"), m2(content="A"), m3(content="B")])` | 返回 1 个组：`[m1, m2]`；m3 不在任何组里 | unit-pure | `MemoryDedupTest#TC-AGENT-261-b findDuplicateGroups groups exact duplicates` 🔴 |
| TC-AGENT-261-c | R-AGENT-003 | `findDuplicateGroups` 输入 m1~m2（cosine ≥ 0.92）+ m2~m3（content 完全一致）+ m1 与 m3 表面不像 | 返回 1 个组：`{m1, m2, m3}`（union-find 传递性） | unit-pure | `MemoryDedupTest#TC-AGENT-261-c findDuplicateGroups transitively merges via union-find` 🔴 |
| TC-AGENT-261-d | R-AGENT-003 | 源码扫描：`MemoryRepository.kt` | 必须有 `suspend fun scanDuplicateGroups()` 与 `suspend fun deleteMemories(ids: List<Long>): Int`；`deleteMemories` 内部循环调用既有 `deleteMemory(id)`（复用级联删除链路） | unit-scan | `MemoryDedupTest#TC-AGENT-261-d repository wires scan and batch delete` 🔴 |
| TC-AGENT-261-e | R-AGENT-003 | 源码扫描：`MemoryScreen.kt` | `MemorySearchBar` 新增 `onCleanupClick` 参数，使用 `CleaningServices` icon；接 `viewModel.scanDuplicates()` | unit-scan | `MemoryDedupTest#TC-AGENT-261-e app bar wires cleanup icon` 🔴 |
| TC-AGENT-261-f | R-AGENT-003 | 源码扫描：`MemoryDialogs.kt` + `MemoryViewModel.kt` | `DedupCleanupDialog` 存在；ViewModel 有 `scanDuplicates()` / `deleteSelectedDuplicates(ids)` / `dismissDedupDialog()` 三个方法且 UiState 含 `dedupScan` 字段 | unit-scan | `MemoryDedupTest#TC-AGENT-261-f dialog and viewmodel wire dedup cleanup` 🔴 |

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

### R-AGENT-002 bugfix: DeepseekProvider 不应给 DeepSeek 官方塞空 `reasoning_content`（TC-AGENT-262-a..e）

**Bug 背景**: 用户报告"DeepSeek 官方 API 无响应；同一条 key 在其他 agent 应用能正常工作"。
排查锁定 `DeepseekProvider.buildMessagesWithReasoning` 5 处 `put("reasoning_content", ...)` 无条件写入：
- 即便 `reasoningContent == ""` 也 put 空串（line 209/292/318/405/415）
- DeepSeek 官方 V3 schema 严格，空 `reasoning_content` 触发拒绝/挂死；其他 platform（OpenRouter/SiliconFlow/`OPENAI_GENERIC` 路径）走 `OpenAIProvider`（line 1011-1014 的 `takeIf { it.isNotEmpty() }`），不会塞空，所以"其他平台没问题"
- 历史 commit `024a3185` 给 MiMo 加的"无条件 put"过度防御泄漏到 DeepSeek（MiMo 实际返回 reasoning_content 时**有内容**，非空分支照常走，零回归）

**修复**: 5 处统一对齐 `OpenAIProvider` 模式：`reasoning?.takeIf { it.isNotEmpty() }?.let { put("reasoning_content", it) }`。

**TC-AGENT-262-e 背景**（2026-06-03 第二次 bugfix）: 上一次修守卫后，DeepSeek 官方 tool_call 第二轮报 400
`"The reasoning_content in the thinking mode must be passed back to the API."`。
根因：`buildMessagesWithReasoning` 在 ASSISTANT / TOOL_CALL 分支只信 `originalContent` 里的 `<think>` 标签
(`ChatUtils.extractThinkingContent`)，但上游 `OperitChatCompletionServer` /
`EnhancedAIService.toPromptTurnsForHistory` 早已把 reasoning 拆到 `PromptTurn.reasoningContent` 带外字段并把
`<think>` 从 content 里剥光 → 解构出来必然是空串 → 被 takeIf 守卫剥掉 → DeepSeek 报 400。
**修复**: 4 处分支（useToolCall true/false × ASSISTANT/TOOL_CALL）必须优先读 `turn.reasoningContent` 带外字段，
inline `<think>` 提取仅作 fallback（兼容老历史）。

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-AGENT-262-a | R-AGENT-002 | `buildMessagesWithReasoning` 处理 ASSISTANT turn，content 不含 `<think>` 标签（reasoningContent 为空） | 输出 JSON 不含 `reasoning_content` 键（不是空串） | unit-pure | `DeepseekProviderTest#TC-AGENT-262-a assistant without thinking omits reasoning_content` 🟢 |
| TC-AGENT-262-b | R-AGENT-002 | `buildMessagesWithReasoning` 处理 ASSISTANT turn，content 含 `<think>some reasoning</think>actual answer` | 输出 JSON 含 `reasoning_content: "some reasoning"`（非空才塞） | unit-pure | `DeepseekProviderTest#TC-AGENT-262-b assistant with thinking keeps reasoning_content` 🟢 |
| TC-AGENT-262-c | R-AGENT-002 | `buildMessagesWithReasoning` 处理 TOOL_CALL turn（含 xml tool_call 但无 thinking） | 输出 JSON 不含 `reasoning_content` 键（既不塞空也不塞 null） | unit-pure | `DeepseekProviderTest#TC-AGENT-262-c tool_call without thinking omits reasoning_content` 🟢 |
| TC-AGENT-262-d | R-AGENT-002 | 源码扫描：`DeepseekProvider.kt` | 禁止 `put("reasoning_content", "")` / `put("reasoning_content", ...orEmpty())` 这两种"塞空"模式复活（防呆 wiring） | unit-scan | `DeepseekProviderTest#TC-AGENT-262-d source contract forbids empty reasoning_content put` 🟢 |
| TC-AGENT-262-e | R-AGENT-002 | 源码扫描：`DeepseekProvider.kt` | 4 处分支（ASSISTANT/TOOL_CALL × useToolCall true/false）必须优先读 `turn.reasoningContent` 带外字段（≥3 处引用），inline `extractThinkingContent` 仅作 fallback | unit-scan | `DeepseekProviderTest#TC-AGENT-262-e branches must read PromptTurn reasoningContent out-of-band` 🟢 |

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

---

## 域 ACP

测试类: `hermes-android/src/test/java/com/xiaomo/hermes/hermes/acp/AcpToolsTest.kt` + `AcpAuthTest.kt`

### TOOL_KIND_MAP + getToolKind

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-ACP-001-a | R-ACP-002 | `TOOL_KIND_MAP has canonical file-op bindings` | ✅ |
| TC-ACP-002-a | R-ACP-002 | `TOOL_KIND_MAP has execute bindings` | ✅ |
| TC-ACP-003-a | R-ACP-002 | `TOOL_KIND_MAP has web fetch bindings` | ✅ |
| TC-ACP-004-a | R-ACP-002 | `TOOL_KIND_MAP has thinking binding` | ✅ |
| TC-ACP-005-a | R-ACP-002 | `getToolKind returns mapping when present` + `getToolKind falls back to other for unknown tool` | ✅ |

### makeToolCallId

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-ACP-006-a | R-ACP-001 | `makeToolCallId produces prefixed id` | ✅ |
| TC-ACP-007-a | R-ACP-001 | `makeToolCallId returns fresh value each call` | ✅ |

### buildToolTitle

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-ACP-010-a | R-ACP-003 | `buildToolTitle terminal short command` | ✅ |
| TC-ACP-011-a | R-ACP-003 | `buildToolTitle terminal long command truncates at 80` | ✅ |
| TC-ACP-012-a | R-ACP-003 | `buildToolTitle terminal with missing command` | ✅ |
| TC-ACP-013-a | R-ACP-003 | `buildToolTitle read_file and write_file use path` + `...with missing path uses question mark` | ✅ |
| TC-ACP-014-a | R-ACP-003 | `buildToolTitle patch includes mode` + `buildToolTitle patch defaults mode to replace` | ✅ |
| TC-ACP-015-a | R-ACP-003 | `buildToolTitle search_files` + `buildToolTitle web_search` | ✅ |
| TC-ACP-016-a | R-ACP-003 | `buildToolTitle web_extract single url` + `... multiple urls shows count` + `... empty falls back` | ✅ |
| TC-ACP-017-a | R-ACP-003 | `buildToolTitle delegate_task short goal` + `... long goal truncates at 60` + `... missing goal uses generic label` | ✅ |
| TC-ACP-018-a | R-ACP-003 | `buildToolTitle execute_code` + `buildToolTitle vision_analyze truncates question` + `... missing uses placeholder` | ✅ |
| TC-ACP-019-a | R-ACP-003 | `buildToolTitle falls back to tool name when unhandled` | ✅ |

### extractLocations

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-ACP-020-a | R-ACP-003 | `extractLocations returns empty when no path` | ✅ |
| TC-ACP-021-a | R-ACP-003 | `extractLocations picks up path only` | ✅ |
| TC-ACP-022-a | R-ACP-003 | `extractLocations picks up path plus offset as line` | ✅ |
| TC-ACP-023-a | R-ACP-003 | `extractLocations prefers offset over line when both present` | ✅ |
| TC-ACP-024-a | R-ACP-003 | `extractLocations accepts bare line when no offset` | ✅ |

### buildToolStart

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-ACP-030-a | R-ACP-003 | `buildToolStart wires toolCallId title kind locations rawInput` | ✅ |
| TC-ACP-031-a | R-ACP-003 | `buildToolStart write_file produces diff content` | ✅ |
| TC-ACP-032-a | R-ACP-003 | `buildToolStart patch replace mode produces diff content` | ✅ |
| TC-ACP-033-a | R-ACP-003 | `buildToolStart terminal renders command with dollar prefix` | ✅ |
| TC-ACP-034-a | R-ACP-003 | `buildToolStart read_file renders reading message` | ✅ |
| TC-ACP-035-a | R-ACP-003 | `buildToolStart search_files renders searching message` + `... defaults target to content` | ✅ |
| TC-ACP-036-a | R-ACP-003 | `buildToolStart generic tool falls through to json dump` | ✅ |

### buildToolComplete

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-ACP-040-a | R-ACP-003 | `buildToolComplete wires id kind status` | ✅ |
| TC-ACP-041-a | R-ACP-003 | `buildToolComplete null result becomes empty text block` | ✅ |
| TC-ACP-042-a | R-ACP-003 | `buildToolComplete truncates long result` | ✅ |
| TC-ACP-043-a | R-ACP-003 | `buildToolComplete short result passes through` | ✅ |

### Auth

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-ACP-050-a | R-ACP-001 | `detectProvider returns null when no credentials configured` | ✅ |
| TC-ACP-051-a | R-ACP-001 | `hasProvider mirrors detectProvider null` | ✅ |
| TC-ACP-052-a | R-ACP-001 | `detectProvider stays null across multiple invocations` | ✅ |

### Copilot ACP client (R-ACP-004)

测试类: `CopilotAcpClientTest` (19 tests) — handshake 默认常量、tool 格式化、`<tool_call>` 解析、`close()` 幂等、`_ensurePathWithinCwd` 安全。真正的 E2E 握手走 `copilot` 子进程需 integration 环境；这里覆盖 JVM 可验部分。

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-ACP-060-a | R-ACP-004 | 默认构造 | `apiKey=copilot-acp`, `baseUrl=acp://copilot` | unit | `CopilotAcpClientTest#default client has acp marker base url and sentinel key` 🟢 |
| TC-ACP-061-a | R-ACP-004 | 远端 tool schema 传给 `_formatMessagesAsPrompt` | prompt 含 `Available tools` + 工具名 | unit | `CopilotAcpClientTest#formatMessagesAsPrompt includes tool schemas when tools provided` 🟢 |
| TC-ACP-062-a | R-ACP-004 | `<tool_call>{...}</tool_call>` 文本 | 解出 tool call + 剩余文本 | unit | `CopilotAcpClientTest#extractToolCallsFromText parses tool_call block` 🟢 |
| TC-ACP-063-a | R-ACP-004 | `close()` 重入 | `isClosed` 翻转一次；第二次无异常 | unit | `CopilotAcpClientTest#close flips isClosed and is idempotent` 🟢 |

---

## 域 TOOL

测试类: 散落在 `hermes-android/src/test/java/com/xiaomo/hermes/hermes/tools/*.kt`（Registry / Approval / BudgetConfig / TodoTool / ... ）+ `tools/FileOperations`、`tools/FileTools`、`tools/MemoryTool` 相关 Robolectric 补测。

### Registry.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-001-a | R-TOOL-001 | `register("foo", "a", h1)` 再 `register("foo", "b", h2)` | ERROR 日志；`get("foo").toolset=="a"` | unit | `RegistryTest#register same name different toolset keeps first` ✅ |
| TC-TOOL-002-a | R-TOOL-001 | 两次 `register("mcp-x", "mcp", h)` | 无 ERROR；后写覆盖 | unit | `RegistryTest#mcp tools allow re-register` ✅ |
| TC-TOOL-003-a | R-TOOL-001 | 同 toolset 内两次 register，后一次 checkFn 不覆盖 | `_toolsetChecks` 保留首次函数 | unit | `RegistryTest#toolset checkFn is first-write-wins` ✅ |
| TC-TOOL-004-a | R-TOOL-001 | `dispatch("nonexistent", {})` | JSON `{"error":"Unknown tool: nonexistent"}` | unit | `RegistryTest#dispatch unknown returns structured error` ✅ |
| TC-TOOL-005-a | R-TOOL-001 | 注册 entry handler=null，`dispatch(name, {})` | `{"error":"Tool '<name>' has no handler"}` | unit | `RegistryTest#dispatch null handler returns error` ✅ |
| TC-TOOL-006-a | R-TOOL-001 | handler 抛 `RuntimeException("boom")` | `{"error":"Tool execution failed: RuntimeException: boom"}` | unit | `RegistryTest#dispatch handler exception wraps` ✅ |
| TC-TOOL-007-a | R-TOOL-001 | `deregister` 最后一条 | `_toolsetChecks[toolset]` 与 alias 清空 | unit | `RegistryTest#deregister last entry clears toolset` ✅ |
| TC-TOOL-008-a | R-TOOL-001 | `getDefinitions` 当 checkFn 抛异常 | 工具被静默跳过 | unit | `RegistryTest#getDefinitions skips failing checkFn` ✅ |
| TC-TOOL-009-a | R-TOOL-001 | 多工具共享同一 toolset | checkFn 单次调用内只执行一次 | unit | `RegistryTest#getDefinitions memoizes per-call` ✅ |
| TC-TOOL-010-a | R-TOOL-001 | 默认 entry 无 pinned | 返回 default 或 50000 | unit | `RegistryTest#getMaxResultSize default hierarchy` ✅ |
| TC-TOOL-010-b | R-TOOL-001 | `read_file` 默认 | 返回 `Double.POSITIVE_INFINITY` | unit | `RegistryTest#getMaxResultSize read_file is unlimited` ✅ |
| TC-TOOL-011-a | R-TOOL-001 | `toolError("e", mapOf("k" to 1))` | 合法 JSON with `error` + `k` 字段 | unit | `RegistryTest#toolError serializes extra fields` ✅ |
| TC-TOOL-012-a | R-TOOL-001 | `toolResult(mapOf("a" to 1))` | JSON `{"a":1}`，数字不转字符串 | unit | `RegistryTest#toolResult preserves numeric types` ✅ |
| TC-TOOL-013-a | R-TOOL-001 | `registerToolsetAlias("old","new")` 再重写 alias | WARN 日志 + alias 改写 | unit | `RegistryTest#registerToolsetAlias overwrite warns` ✅ |
| TC-TOOL-014-a | R-TOOL-001 | `isToolsetAvailable` checkFn 抛异常 | 返回 false（非 throw） | unit | `RegistryTest#isToolsetAvailable exception returns false` ✅ |
| TC-TOOL-015-a | R-TOOL-001 | `getDefinitions` 结构 | 顶层 `type=="function"`、`function.name/description/parameters` | unit | `RegistryTest#getDefinitions emits OpenAI schema` ✅ |

### Approval.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-020-a | R-TOOL-002 | `_normalizeCommandForDetection("  rm   -rf  /x")` | `"rm -rf /x"` | unit | `ApprovalTest#normalize collapses whitespace` ✅ |
| TC-TOOL-020-b | R-TOOL-002 | 全宽输入 `ｒｍ　－ｒｆ　／` | NFKC 归一后被 `detectDangerousCommand` 命中 | unit | `ApprovalTest#detectDangerousCommand detects with fullwidth normalization (NFKC)` ✅ |
| TC-TOOL-021-a | R-TOOL-002 | `detectDangerousCommand("rm -rf /")` | Triple(true, pattern, action) | unit | `ApprovalTest#detectDangerousCommand classifies rm -rf` ✅ |
| TC-TOOL-022-a | R-TOOL-002 | `promptDangerousApproval(cmd, cb=null)` | 返回 false（拒绝） | unit | `ApprovalTest#promptDangerousApproval no callback denies` ✅ |
| TC-TOOL-023-a | R-TOOL-002 | 设置 `HERMES_SANDBOX_ENV=1` | approval 直接通过 | unit | `ApprovalTest#sandbox env skips approval` ✅ |
| TC-TOOL-024-a | R-TOOL-002 | cron session approveMode != "approve" | 阻塞并返回 false | unit | `ApprovalTest#cron session denies when approval disabled` ✅ |
| TC-TOOL-025-a | R-TOOL-002 | gateway approval 5 分钟未响应 | 超时拒绝 | unit | `ApprovalTest#gateway approval times out at 5 min` ✅ |
| TC-TOOL-026-a | R-TOOL-002 | 用户选 "always" | 写入 YAML 文件中 `commands.always` 列表 | unit | `ApprovalTest#always choice persists to yaml` ✅ |
| TC-TOOL-027-a | R-TOOL-002 | `_smartApprove` 未命中规则 | 调用 escalate callback | unit | `ApprovalTest#smartApprove falls through to escalate` ✅ |
| TC-TOOL-028-a | R-TOOL-002 | `isApproved(cmd)` 用别名 key | 命中 pre-approved 列表 | unit | `ApprovalTest#isApproved matches alias keys` ✅ |
| TC-TOOL-029-a | R-TOOL-002 | Tirith 返回 "always" | 降级为 "allow" | unit | `ApprovalTest#tirith always downgrades to allow` ✅ |

### BudgetConfig.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-045-a | R-TOOL-003 | `PINNED_THRESHOLDS["read_file"]` | `Double.POSITIVE_INFINITY` | unit | `BudgetConfigTest#read_file is unlimited` ✅ |
| TC-TOOL-045-b | R-TOOL-003 | `resolveThreshold("read_file", toolOverrides=mapOf("read_file" to 100.0))` | 仍返回 `POSITIVE_INFINITY`（pinned 不可被覆盖） | unit | `BudgetConfigTest#resolveThreshold returns pinned value for read_file regardless of overrides` ✅ |
| TC-TOOL-046-a | R-TOOL-003 | pinned 有值 → 优先 pinned | 返回 pinned | unit | `BudgetConfigTest#pinned overrides default` ✅ |
| TC-TOOL-047-a | R-TOOL-003 | 任意合法 threshold | 返回 Double | unit | `BudgetConfigTest#returns Double type` ✅ |
| TC-TOOL-048-a | R-TOOL-003 | 默认 fallback | 50000 | unit | `BudgetConfigTest#default is 50000` ✅ |

### TodoTool.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-055-a | R-TOOL-001 | `VALID_STATUSES` | `{"pending", "in_progress", "completed", "cancelled"}` | unit | `TodoToolTest#VALID_STATUSES has four values` ✅ |
| TC-TOOL-056-a | R-TOOL-001 | `merge=false`，2 条新 list | 整表被替换 | unit | `TodoToolTest#merge false replaces all` ✅ |
| TC-TOOL-057-a | R-TOOL-001 | `merge=true` 按 id 更新 | 同 id 的被覆盖，其他保留 | unit | `TodoToolTest#merge true updates by id` ✅ |
| TC-TOOL-058-a | R-TOOL-001 | `_validate` 输入异常 map | 静默跳过条目 | unit | `TodoToolTest#_validate never throws` ✅ |
| TC-TOOL-059-a | R-TOOL-001 | `_dedupeById` 同 id 两条 | 保留最后一条 | unit | `TodoToolTest#_dedupeById keeps last` ✅ |
| TC-TOOL-060-a | R-TOOL-001 | merge 模式下 status=`"foo"` | 忽略该字段（不是整条） | unit | `TodoToolTest#merge invalid status silent ignore` ✅ |
| TC-TOOL-061-a | R-TOOL-001 | `formatForInjection([])` | null | unit | `TodoToolTest#formatForInjection empty returns null` ✅ |
| TC-TOOL-062-a | R-TOOL-001 | marker 映射 pending/in_progress/etc | 对应符号 | unit | `TodoToolTest#display markers match python` ✅ |
| TC-TOOL-063-a | R-TOOL-001 | store==null | 返回 toolError | unit | `TodoToolTest#null store returns error` ✅ |
| TC-TOOL-064-a | R-TOOL-001 | set action | 返回 `{"todos":[...]}` shape | unit | `TodoToolTest#set action returns canonical shape` ✅ |

### FileOperations.kt + FileTools.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-070-a | R-TOOL-001 | `_isWriteDenied("~/.ssh/id_rsa")` | true | unit | `FileToolsTest#_isWriteDenied ssh private key` 🟢 |
| TC-TOOL-071-a | R-TOOL-001 | `_isWriteDenied("~/.ssh/config")` | true (prefix) | unit | `FileToolsTest#_isWriteDenied ssh prefix` 🟢 |
| TC-TOOL-072-a | R-TOOL-001 | 设置 `HERMES_WRITE_SAFE_ROOT=/tmp`，写 `/var/x` | 拒绝 | unit | `FileToolsTest#safe root enforces write jail` 🟢 |
| TC-TOOL-073-a | R-TOOL-001 | `readFile` 文件超行上限 | 返回 truncated 标记 | unit | `FileToolsTest#readFile honors offset and limit` 🟢 |
| TC-TOOL-074-a | R-TOOL-001 | `readFile` 扩展名 `.bin` | 拒绝读 | unit | `FileToolsTest#readFile rejects binary ext` 🟢 |
| TC-TOOL-075-a | R-TOOL-001 | `readFile` 不存在路径 | error 含 suggestions | unit | `FileToolsTest#readFile missing gives suggestions` 🟢 |
| TC-TOOL-076-a | R-TOOL-001 | `writeFile("a/b/c.txt")` 不存在父目录 | 自动创建 | unit | `FileToolsTest#writeFile creates parents` 🟢 |
| TC-TOOL-077-a | R-TOOL-001 | `moveFile` 跨分区 | 复制+删除回退 | unit | `FileToolsTest#moveFile cross-device fallback` 🟢 |
| TC-TOOL-078-a | R-TOOL-001 | `search` 超结果上限 | truncated 标记 | unit | `FileToolsTest#search truncates at cap` 🟢 |
| TC-TOOL-079-a | R-TOOL-001 | `_exec` 命令卡 30s（timeout=1s） | destroyForcibly + exit 124 | unit | `FileToolsTest#_exec timeout destroys` 🟢 |
| TC-TOOL-080-a | R-TOOL-001 | `_checkLint` 缺 linter | 静默跳过 | unit | `FileToolsTest#_checkLint skip when absent` 🟢 |

### FileOperations.kt (Android 额外)

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-095-a | R-TOOL-001 | `_isBlockedDevice("/dev/null")` | true | unit | `FileOperationsTest#blocks /dev character device` 🟢 |
| TC-TOOL-096-a | R-TOOL-001 | `_checkSensitivePath("/etc/shadow")` | 拒绝 | unit | `FileOperationsTest#checkSensitivePath etc shadow` 🟢 |
| TC-TOOL-097-a | R-TOOL-001 | 调 `notifyOtherToolCall` | read dedup 计数清零 | unit | `FileOperationsTest#notifyOtherToolCall resets dedup` 🟢 |
| TC-TOOL-098-a | R-TOOL-001 | 读后外部 `touch` mtime | `_checkFileStaleness` 报告 true | unit | `FileOperationsTest#_checkFileStaleness detects external write` 🟢 |
| TC-TOOL-099-a | R-TOOL-001 | 同 task 内 2 次 readFile 同文件 | hit cache | unit | `FileOperationsTest#per-task cache on same file` 🟢 |
| TC-TOOL-100-a | R-TOOL-001 | 塞 dedup map 超 cap | 老条目被 LRU 驱逐 | unit | `FileOperationsTest#LRU eviction bounds caches` 🟢 |
| TC-TOOL-101-a | R-TOOL-001 | handler(args=nonMap) | toolError，不抛 | unit | `FileOperationsTest#handler tolerates non-map args` 🟢 |

### MemoryTool.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-110-a | R-TOOL-001 | `ENTRY_DELIMITER` 值 | 完全等于 `"\n§\n"` | unit | `MemoryToolTest#delimiter constant` 🟢 |
| TC-TOOL-111-a | R-TOOL-001 | `_scanMemoryContent(U+202E)` | 返回 violation | unit | `MemoryToolTest#invisible unicode trips scan` 🟢 |
| TC-TOOL-112-a | R-TOOL-001 | `_scanMemoryContent("IGNORE PREVIOUS")` | 返回 violation | unit | `MemoryToolTest#injection pattern trips scan` 🟢 |
| TC-TOOL-113-a | R-TOOL-001 | 两 target {conversation_memory, user_memory} 并写 | 文件分离 | integration | `MemoryToolTest#two targets separate files` 🟢 |
| TC-TOOL-114-a | R-TOOL-001 | 重复 `add` 同一 token | 仅一条存在 | unit | `MemoryToolTest#add is idempotent` 🟢 |
| TC-TOOL-115-a | R-TOOL-001 | 累计 add 超 token 上限 | 拒绝并返错 | unit | `MemoryToolTest#add enforces token ceiling` 🟢 |
| TC-TOOL-116-a | R-TOOL-001 | `replace(needle,new)` 有多个匹配不同上下文 | 返回 "be more specific" 错误 | unit | `MemoryToolTest#replace ambiguous refuses` 🟢 |
| TC-TOOL-117-a | R-TOOL-001 | `replace(needle, "")` | 拒绝 | unit | `MemoryToolTest#replace empty new refused` 🟢 |
| TC-TOOL-118-a | R-TOOL-001 | 并发两次 add | 每次都独占写锁，无 race | integration | `MemoryToolTest#file lock serializes` 🟢 |
| TC-TOOL-119-a | R-TOOL-001 | system prompt snapshot 之后 再 add | snapshot 未变 | unit | `MemoryToolTest#prompt snapshot frozen` 🟢 |

### PathSecurity.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-130-a | R-TOOL-002 | `validateWithinDir("/a", "/a/../etc")` | 错误字符串 | unit | `PathSecurityTest#validateWithinDir rejects traversal` ✅ |
| TC-TOOL-131-a | R-TOOL-002 | 任意非法输入 | 返回 string，不抛 | unit | `PathSecurityTest#never throws on invalid input` ✅ |
| TC-TOOL-132-a | R-TOOL-002 | `hasTraversalComponent("a/../b")` | true，不触 IO | unit | `PathSecurityTest#hasTraversalComponent is pure` ✅ |

### SkillsHub.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-140-a | R-TOOL-001 | `normalizeBundlePath("../out")` | 拒绝 | unit | `SkillsHubTest#rejects traversal path` 🟢 |
| TC-TOOL-141-a | R-TOOL-001 | 设 env `HERMES_GITHUB_TOKEN` + session token | 优先 env | unit | `SkillsHubTest#_resolveToken env wins` 🟢 |
| TC-TOOL-142-a | R-TOOL-001 | 短时间内 5 次请求 | 第 >N 次被限流 | unit | `SkillsHubTest#_rateLimited trips` 🟢 |
| TC-TOOL-143-a | R-TOOL-001 | 同名 skill 不同 trust | 按 trust 去重 | unit | `SkillsHubTest#GitHubSource search dedupe by trust` 🟢 |
| TC-TOOL-144-a | R-TOOL-001 | fetch 无 SKILL.md | 返回错误 | unit | `SkillsHubTest#fetch requires SKILL.md` 🟢 |
| TC-TOOL-145-a | R-TOOL-001 | GitHub API `truncated=true` | 拒绝全树下载 | unit | `SkillsHubTest#truncated tree refused` 🟢 |
| TC-TOOL-146-a | R-TOOL-001 | 多 source 搜索 | Android 顺序执行 | unit | `SkillsHubTest#parallel search is sequential on android` 🟢 |
| TC-TOOL-147-a | R-TOOL-001 | `installFromQuarantine` | Android stub 返回 toolError | unit | `SkillsHubTest#install quarantine stub` 🟢 |
| TC-TOOL-148-a | R-TOOL-001 | 同 bundle 不同顺序文件 | `bundleContentHash` 相同 | unit | `SkillsHubTest#bundleContentHash deterministic` 🟢 |

### SkillManagerTool.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-160-a | R-TOOL-001 | dispatch skill CRUD action | 返回 `{"error":"not supported on Android"}` | unit | `SkillManagerToolTest#all CRUD android denied` 🟢 |
| TC-TOOL-161-a | R-TOOL-001 | `_validateName("a b")` 含空格 | 拒绝 | unit | `SkillManagerToolTest#name regex rejects space` 🟢 |
| TC-TOOL-161-b | R-TOOL-001 | name 超长 | 拒绝 | unit | `SkillManagerToolTest#name length cap` 🟢 |
| TC-TOOL-162-a | R-TOOL-001 | 单文件 content 超 cap | 拒绝 | unit | `SkillManagerToolTest#content size cap` 🟢 |
| TC-TOOL-163-a | R-TOOL-001 | 写中途模拟 crash | 旧文件保留（原子替换） | integration | `SkillManagerToolTest#atomic write crash safe` 🟢 |

### SkillsGuard.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-170-a | R-TOOL-001 | scanFile(`.md`) | 进入扫描 | unit | `SkillsGuardTest#scan accepts md` 🟢 |
| TC-TOOL-170-b | R-TOOL-001 | scanFile(`.zip`) | 跳过 | unit | `SkillsGuardTest#scan rejects binary` 🟢 |
| TC-TOOL-171-a | R-TOOL-001 | 同行命中两次同 pattern | 去重 | unit | `SkillsGuardTest#findings dedupe per line` 🟢 |
| TC-TOOL-172-a | R-TOOL-001 | 长匹配 > 120 char | 截断 | unit | `SkillsGuardTest#long match truncates` 🟢 |
| TC-TOOL-173-a | R-TOOL-001 | 同行多不可见字符 | 仅报一条 | unit | `SkillsGuardTest#invisible chars per-line single entry` 🟢 |
| TC-TOOL-174-a | R-TOOL-001 | 安装内含 symlink 指向 bundle 外 | 拒绝 | unit | `SkillsGuardTest#symlink escape blocked` 🟢 |
| TC-TOOL-175-a | R-TOOL-001 | `.sh` 有 exec 位 | 通过（白名单） | unit | `SkillsGuardTest#exec bit allowed for whitelist` 🟢 |
| TC-TOOL-175-b | R-TOOL-001 | `.py` 有 exec 位 | 拒绝 | unit | `SkillsGuardTest#exec bit rejected for non-whitelist` 🟢 |
| TC-TOOL-176-a | R-TOOL-001 | critical finding | verdict=block | unit | `SkillsGuardTest#_determineVerdict blocks critical` 🟢 |
| TC-TOOL-177-a | R-TOOL-001 | 策略 matrix 各组合 | 决策一致 | unit | `SkillsGuardTest#shouldAllowInstall matrix` 🟢 |
| TC-TOOL-178-a | R-TOOL-001 | trustLevel 大小写混写前缀 | 归一小写 | unit | `SkillsGuardTest#_resolveTrustLevel normalizes` 🟢 |

### SkillsSync.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-185-a | R-TOOL-001 / R-SKILL-003 | env `HERMES_BUNDLED_SKILLS=/custom/path` | 用该路径替代默认 `getHermesHome()/bundled_skills` 作为 bundled 源（Python `tools/skills_sync.py:46-48`：**路径覆盖**，非 CSV allow-list；TC 原期望被修正为与 Python 一致） | unit | `SkillsSyncTest#respects env filter` 🟢 |
| TC-TOOL-186-a | R-TOOL-001 / R-SKILL-003 | manifest 非预期格式 | 报错拒绝 | unit | `SkillsSyncTest#manifest format enforced` 🟢 |
| TC-TOOL-187-a | R-TOOL-001 / R-SKILL-003 | `_readSkillName` 目标超 4000 byte | 只读前 4000 | unit | `SkillsSyncTest#frontmatter read capped` 🟢 |
| TC-TOOL-188-a | R-TOOL-001 / R-SKILL-003 | 用户改过 skill 再 sync | 不覆盖 | integration | `SkillsSyncTest#user modification protected` 🟢 |
| TC-TOOL-189-a | R-TOOL-001 / R-SKILL-003 | dest 首次存在 | skip | unit | `SkillsSyncTest#skip when dest exists first time` 🟢 |
| TC-TOOL-190-a | R-TOOL-001 / R-SKILL-003 | 复制中途异常 | `.bak` 回滚 | integration | `SkillsSyncTest#copy failure rolls back bak` 🟢 |
| TC-TOOL-191-a | R-TOOL-001 / R-SKILL-003 | `resetBundledSkill(name)` | stub 返回错误 | unit | `SkillsSyncTest#reset is stubbed on android` 🟢 |

### SkillsTool.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-195-a | R-TOOL-001 | `loadEnv` 空行 / 注释 / `a=b` | 仅 `a=b` 入表 | unit | `SkillsToolTest#loadEnv parses` 🟢 |
| TC-TOOL-196-a | R-TOOL-001 | `_parseFrontmatter` 无前导 `---` | 返回空 | unit | `SkillsToolTest#frontmatter must start with ---` 🟢 |
| TC-TOOL-197-a | R-TOOL-001 | env var 名非法 char | 过滤 | unit | `SkillsToolTest#env var name regex` 🟢 |
| TC-TOOL-198-a | R-TOOL-001 | env 文件有 key | 满足 | unit | `SkillsToolTest#env file satisfies` 🟢 |
| TC-TOOL-198-b | R-TOOL-001 | 进程 env 有 key | 满足 | unit | `SkillsToolTest#process env satisfies` 🟢 |
| TC-TOOL-199-a | R-TOOL-001 | skills dir 含 `node_modules/` | 扫描跳过 | unit | `SkillsToolTest#findAllSkills excludes noise dirs` 🟢 |

### TerminalTool.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-200-a | R-TOOL-001 | `_getSessionPlatform` 无 session | 默认 "android" | unit | `TerminalToolTest#platform defaults android` 🟢 |
| TC-TOOL-201-a | R-TOOL-001 | 远端 env 字段被中性化 | 返回固定 stub | unit | `TerminalToolTest#remote env neutralized` 🟢 |
| TC-TOOL-210-a | R-TOOL-001 | `background=true` | toolError | unit | `TerminalToolTest#background not supported on android` 🟢 |
| TC-TOOL-211-a | R-TOOL-001 | timeout=99999 | clamp 到上限 | unit | `TerminalToolTest#timeout double clamp` 🟢 |
| TC-TOOL-212-a | R-TOOL-001 | shell 路径 | 固定 `/system/bin/sh` | unit | `TerminalToolTest#android shell path fixed` 🟢 |
| TC-TOOL-213-a | R-TOOL-001 | 超时进程 | exit=124 | unit | `TerminalToolTest#timeout returns 124` 🟢 |
| TC-TOOL-214-a | R-TOOL-001 | `_validateWorkdir("a; b")` | 拒绝 | unit | `TerminalToolTest#validateWorkdir rejects shell chars` 🟢 |
| TC-TOOL-215-a | R-TOOL-001 | `_parseEnvVar("bad input")` | 默认 | unit | `TerminalToolTest#parseEnvVar tolerates bad` 🟢 |
| TC-TOOL-216-a | R-TOOL-001 | 调用 sudo 路径 | toolError stub | unit | `TerminalToolTest#sudo stub` 🟢 |

### CodeExecutionTool.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-225-a | R-TOOL-001 | 任意 `executeCode` 入参 | 返回 toolError | unit | `CodeExecutionToolTest#always toolError on android` 🟢 |
| TC-TOOL-226-a | R-TOOL-001 | `_rpcServerLoop()` | 抛 `UnsupportedOperation` | unit | `CodeExecutionToolTest#rpc loops throw` 🟢 |
| TC-TOOL-227-a | R-TOOL-001 | `buildExecuteCodeSchema("python")` / `("bash")` | description 不同 | unit | `CodeExecutionToolTest#schema description varies by mode` 🟢 |
| TC-TOOL-228-a | R-TOOL-001 | 设 env `TERMINAL_CWD=/tmp/x` | 返回 `/tmp/x`（Python `tools/code_execution_tool.py:1417-1428`：env 名为 TERMINAL_CWD，原 TC 写 HERMES_CHILD_CWD 是错的，以 Python 上游为准） | unit | `CodeExecutionToolTest#_resolveChildCwd env wins` 🟢 |

### ClarifyTool.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-235-a | R-TOOL-001 | `question=""` | toolError | unit | `ClarifyToolTest#empty question refused` ✅ |
| TC-TOOL-236-a | R-TOOL-001 | `choices=[""," "]` | 视作 null | unit | `ClarifyToolTest#all-blank choices become null` ✅ |
| TC-TOOL-237-a | R-TOOL-001 | 超 `MAX_CHOICES` | 截断 | unit | `ClarifyToolTest#MAX_CHOICES truncates` ✅ |
| TC-TOOL-238-a | R-TOOL-001 | callback=null | error | unit | `ClarifyToolTest#no callback returns error` ✅ |
| TC-TOOL-239-a | R-TOOL-001 | callback throw | toolError 包装 | unit | `ClarifyToolTest#callback exception wraps` ✅ |

### Interrupt.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-245-a | R-TOOL-001 | 两线程各 `setInterrupted` | 独立状态 | unit | `InterruptTest#thread-local state` ✅ |
| TC-TOOL-246-a | R-TOOL-001 | 线程 A 设，线程 B 查 | B 得 false | unit | `InterruptTest#isInterrupted only current thread` ✅ |
| TC-TOOL-247-a | R-TOOL-001 | `_ThreadAwareEventProxy.wait(timeout=0)` | 立即返回 | unit | `InterruptTest#wait is non-blocking when disabled` ✅ |

### CheckpointManager.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-255-a | R-TOOL-001 | `enabled` 默认 | false；所有接口 no-op | unit | `CheckpointManagerTest#default disabled noop` ✅ |
| TC-TOOL-256-a | R-TOOL-001 | 首次探 `git` 再次探 | 进程级缓存命中 | unit | `CheckpointManagerTest#_gitAvailable cached` ✅ |
| TC-TOOL-257-a | R-TOOL-001 | cwd=`/` 启用 | 拒绝 | unit | `CheckpointManagerTest#rejects root cwd` ✅ |
| TC-TOOL-257-b | R-TOOL-001 | cwd=home | 拒绝 | unit | `CheckpointManagerTest#rejects home cwd` ✅ |
| TC-TOOL-258-a | R-TOOL-001 | 同 turn 对同文件两次 checkpoint | 第二次 skip | unit | `CheckpointManagerTest#per-turn dedupe` ✅ |
| TC-TOOL-259-a | R-TOOL-001 | 一次 checkpoint 超文件数上限 | 拒绝 | unit | `CheckpointManagerTest#file count ceiling` ✅ |
| TC-TOOL-260-a | R-TOOL-001 | 空 diff | 不 commit | unit | `CheckpointManagerTest#no diff no commit` ✅ |
| TC-TOOL-261-a | R-TOOL-001 | 环境隔离：GIT_*、config author | 使用内部常量 | unit | `CheckpointManagerTest#git env isolated` ✅ |
| TC-TOOL-262-a | R-TOOL-001 | `_validateCommitHash("notahash")` | false | unit | `CheckpointManagerTest#_validateCommitHash strict` ✅ |
| TC-TOOL-263-a | R-TOOL-001 | `_validateFilePath("/etc/passwd")` | 拒绝 | unit | `CheckpointManagerTest#absolute path rejected` ✅ |
| TC-TOOL-264-a | R-TOOL-001 | 相同 project path 两次启动 | shadow repo path 相同 | unit | `CheckpointManagerTest#shadow repo deterministic` ✅ |
| TC-TOOL-265-a | R-TOOL-001 | `restore(id)` 先调 snapshot | snapshot 先于切换 | unit | `CheckpointManagerTest#restore snapshots first` ✅ |
| TC-TOOL-266-a | R-TOOL-001 | `listCheckpoints()` | 附带 --shortstat 数字 | unit | `CheckpointManagerTest#list includes shortstat` ✅ |

### Browser / Delegate / Cron / HomeAssistant / Discord / SendMessage / Feishu Docs / AI services（Mid / Low tier）

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-280-a | R-TOOL-001 | `_mergeBrowserPath("/opt:/usr/bin")` | 追加 SANE_PATH 条目 | unit | `BrowserToolTest#merges browser path` 🟢 |
| TC-TOOL-281-a | R-TOOL-001 | 缺 env `BROWSER_CDP_URL` | toolError | unit | `BrowserCdpToolTest#requires env` 🟢 |
| TC-TOOL-282-a | R-TOOL-001 | Camofox 任意接口 | toolError | unit | `BrowserCamofoxToolTest#android toolError` 🟢 |
| TC-TOOL-283-a | R-TOOL-001 | 构造 state identity | 确定性 hash | unit | `BrowserCamofoxStateTest#identity deterministic` ✅ |
| TC-TOOL-290-a | R-TOOL-001 | `DelegateTool.invoke` 内部允许集 | 仅白名单 | unit | `DelegateToolTest#blocks non-allowed tools` ✅ |
| TC-TOOL-291-a | R-CRON-001 | CronjobTools.create | toolError | unit | `CronjobToolsTest#create denied on android` ✅ |
| TC-TOOL-291-b | R-CRON-001 | CronjobTools.list | toolError | unit | `CronjobToolsTest#list denied on android` ✅ |
| TC-TOOL-292-a | R-TOOL-001 | entity id `switch.foo` | 接受 | unit | `HomeassistantToolTest#entity id valid` ✅ |
| TC-TOOL-292-b | R-TOOL-001 | entity id `bad id` | 拒绝 | unit | `HomeassistantToolTest#entity id invalid` ✅ |
| TC-TOOL-293-a | R-TOOL-001 | domain=`shell_command` | 拒绝 | unit | `HomeassistantToolTest#blocked domains` ✅ |
| TC-TOOL-300-a | R-TOOL-001 | Discord intent flag 位值 | 四个位正确 | unit | `DiscordToolTest#intent flags` 🟢 |
| TC-TOOL-301-a | R-TOOL-001 | topic=`main/1234` | 接受 | unit | `SendMessageToolTest#telegram topic valid` 🟢 |
| TC-TOOL-301-b | R-TOOL-001 | topic=`main/abc` | 拒绝 | unit | `SendMessageToolTest#telegram topic invalid` 🟢 |
| TC-TOOL-302-a | R-TOOL-001 | feishu target `oc_xx` | 接受 | unit | `SendMessageToolTest#feishu target valid` 🟢 |
| TC-TOOL-303-a | R-TOOL-001 | weixin target 任意字符串 | 宽松接受 | unit | `SendMessageToolTest#weixin target permissive` 🟢 |
| TC-TOOL-304-a | R-TOOL-001 | `_PHONE_PLATFORMS` | `{signal, sms, whatsapp}` | unit | `SendMessageToolTest#_PHONE_PLATFORMS equals signal sms whatsapp` 🟢 |
| TC-TOOL-310-a | R-TOOL-001 | ImageGenerationTool 常量 | 等于 Python | unit | `ImageGenerationToolTest#constants match python` ✅ |
| TC-TOOL-311-a | R-TOOL-001 | 默认 tts 提供商 | 匹配 Python | unit | `TtsToolTest#default provider` 🟢 |
| TC-TOOL-312-a | R-TOOL-001 | 文件尺寸超上限 | 拒绝 | unit | `TranscriptionToolsTest#file size cap` 🟢 |
| TC-TOOL-313-a | R-TOOL-001 | `NeuTtsSynth.writeWav` | WAV 头 + PCM | unit | `NeuTtsSynthTest#writes wav header` ✅ |
| TC-TOOL-320-a | R-TOOL-001 | SessionSearchTool 并发调用 | 受 semaphore 限 | unit | `SessionSearchToolTest#concurrent bounded` ✅ |
| TC-TOOL-321-a | R-TOOL-001 | `AnsiStrip("\u001B[31mx\u001B[0m")` | `"x"` | unit | `AnsiStripTest#strips ansi` ✅ |
| TC-TOOL-322-a | R-TOOL-001 | `BinaryExtensions.contains(".pdf")` | false | unit | `BinaryExtensionsTest#pdf not in set` ✅ |
| TC-TOOL-323-a | R-TOOL-001 | `FuzzyMatch("ﬁle", "file")` | 匹配成功 | unit | `FuzzyMatchTest#unicode normalization` ✅ |
| TC-TOOL-324-a | R-TOOL-001 | V4A `*** Begin Patch` 解析 | 正确拆分 | unit | `PatchParserTest#V4A structure` ✅ |
| TC-TOOL-325-a | R-TOOL-001 | OpenrouterClient 两次调用 | 同一 instance；timeout=120s | unit | `OpenrouterClientTest#singleton + timeout` 🟢 |
| TC-TOOL-326-a | R-TOOL-001 | OsvCheck 网络 fail | 返回 ok 结果 | unit | `OsvCheckTest#fail-open on network error` ✅ |
| TC-TOOL-327-a | R-TOOL-001 | ProcessRegistry 塞满 cap | 最早的被驱逐 | unit | `ProcessRegistryTest#capacity cap` ✅ |
| TC-TOOL-328-a | R-TOOL-001 | ToolResultStorage 三层 | 每层在各自失败分支生效 | unit | `ToolResultStorageTest#three tier fallback` ✅ |
| TC-TOOL-329-a | R-TOOL-001 | MixtureOfAgentsTool 默认模型配置 | 4 reference + 1 aggregator | unit | `MixtureOfAgentsToolTest#4+1 defaults` 🟢 |
| TC-TOOL-330-a | R-TOOL-001 | WebTools schema | 字段 descriptions 匹配 Python | unit | `WebToolsTest#schema text matches` 🟢 |
| TC-TOOL-331-a | R-TOOL-001 | `UrlSafety.isSafe("http://10.0.0.1")` | false | unit | `UrlSafetyTest#private IP rejected` ✅ |
| TC-TOOL-332-a | R-TOOL-001 | `UrlSafety.isSafe("http://metadata.google.internal")` | false | unit | `UrlSafetyTest#cloud metadata rejected` ✅ |
| TC-TOOL-333-a | R-TOOL-001 | WebsitePolicy 默认 | disabled；30s cache | unit | `WebsitePolicyTest#default disabled + cache` ✅ |
| TC-TOOL-334-a | R-TOOL-001 | XaiHttp UA 字符串 | 含 `Hermes-Android/` | unit | `XaiHttpTest#user agent` ✅ |
| TC-TOOL-335-a | R-TOOL-001 | `EnvPassthrough("  ")` | 跳过 | unit | `EnvPassthroughTest#blank name skipped` ✅ |
| TC-TOOL-336-a | R-TOOL-001 | `CredentialFiles.loadAll` | no-op 空 map | unit | `CredentialFilesTest#android no-op` ✅ |

### LaunchApp.kt — R-TOOL-016 (Android 平台特供：monkey 突破 BAL)

测试类: `app/src/test/java/com/ai/assistance/operit/core/tools/system/LaunchAppToolTest.kt`。源码契约扫描风格（参照 `AgentStatusOverlayWiringTest`），避免依赖 `AndroidShellExecutor` 真正 fork 子进程——只验证 wiring 与 monkey 命令字符串正确。

| ID | R-ID | 输入 | 期望 | 类型 | 落地 |
|---|---|---|---|---|---|
| TC-TOOL-400-a | R-TOOL-016 | `StandardSystemOperationTools.launchApp` 源码 | 调用 `AndroidShellExecutor.executeShellCommand(...)` 且命令包含 `monkey -p` 与 `android.intent.category.LAUNCHER` | unit (源码扫描) | `LaunchAppToolTest#standard tool uses monkey LAUNCHER` |
| TC-TOOL-400-b | R-TOOL-016 | `DebuggerSystemOperationTools.launchApp` 源码 | 同 a；DEBUGGER 层走 Shizuku binder（由 `ShellExecutorFactory` 自动路由，无需此类显式调用） | unit (源码扫描) | `LaunchAppToolTest#debugger tool uses monkey LAUNCHER` |
| TC-TOOL-400-c | R-TOOL-016 | `ToolRegistration.kt` 源码 | 注册了 `name = "launch_app"` 工具且 executor 委派 `systemOperationTools.launchApp` | unit (源码扫描) | `LaunchAppToolTest#registration wires launch_app to launchApp` |
| TC-TOOL-400-d | R-TOOL-016 | `SystemToolPromptsInternal.kt` 源码 | EN+CN 双 prompt 各包含一条 `name = "launch_app"` 的 `ToolPrompt`；描述里出现 BAL / monkey / 兜底关键词 | unit (源码扫描) | `LaunchAppToolTest#prompt declares launch_app with BAL hint` |
| TC-TOOL-400-e | R-TOOL-016 | `SystemOperationTools` 接口 | 接口必须声明 `suspend fun launchApp(tool: AITool): ToolResult`，否则各实现没有共同契约 | unit (源码扫描) | `LaunchAppToolTest#interface declares launchApp` |
| TC-TOOL-400-f | R-TOOL-016 | 反向防呆 | `launch_app` 与 `start_app` **并存**——`start_app` 注册仍保留（不被覆盖）；且 prompt 里 `launch_app` 描述不包含错误的 "replaces start_app" / "instead of start_app" 字样（必须是兜底关系） | unit (源码扫描) | `LaunchAppToolTest#start_app still registered alongside launch_app` |

---

## 域 GATEWAY

测试类: `hermes-android/src/test/java/com/xiaomo/hermes/hermes/gateway/**`。Feishu / Weixin / Telegram / Helpers / WecomCrypto / qqbot 已覆盖；其他 stub 级别补 `BaseTest`。

### Base.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-GW-001-a | R-GW-001 | `handleMessage` 未注入 handler | logcat WARN + 返回 fallback | unit | `BaseTest#handleMessage no handler logs WARN` ✅ |
| TC-GW-002-a | R-GW-001 | fatal error 触发 | 状态由 RUNNING → FAILED | unit | `BaseTest#fatal error state transition` ✅ |
| TC-GW-003-a | R-GW-001 | 两并发 `acquirePlatformLock(same key)` | 第二者等待 | unit | `BaseTest#platform lock is mutex` ✅ |
| TC-GW-004-a | R-GW-001 | callback generation 不匹配 | 丢弃 | unit | `BaseTest#callback generation enforced` ✅ |
| TC-GW-005-a | R-GW-001 | `isCommand("/help x")` | true | unit | `BaseTest#isCommand detects slash prefix` ✅ |
| TC-GW-005-b | R-GW-001 | `getCommand("/reset y")` | `"reset"` | unit | `BaseTest#getCommand name` ✅ |
| TC-GW-005-c | R-GW-001 | `getCommandArgs("/foo a b c")` | `"a b c"` | unit | `BaseTest#getCommandArgs tail` ✅ |
| TC-GW-006-a | R-GW-001 | `interruptSessionActivity` | 同时中断 loop + 流 | unit | `BaseTest#interrupt double action` ✅ |
| TC-GW-007-a | R-GW-001 | `utf16Len("🫥")` | 2 | unit | `BaseTest#utf16Len surrogate pair` ✅ |
| TC-GW-007-b | R-GW-001 | `prefixWithinUtf16Limit` 截断在代理对中间 | 回退到前一 codepoint | unit | `BaseTest#prefix never splits surrogate` ✅ |
| TC-GW-008-a | R-GW-001 | `safeUrlForLog("https://a.b?token=xxx")` | 掩码 token | unit | `BaseTest#safeUrlForLog masks` ✅ |
| TC-GW-009-a | R-GW-001 | PNG magic bytes | true | unit | `BaseTest#looksLikeImage png` ✅ |
| TC-GW-009-b | R-GW-001 | 纯文本 | false | unit | `BaseTest#looksLikeImage text` ✅ |
| TC-GW-010-a | R-GW-001 | `cacheImageFromUrl` 重复写 | 清旧落新 | unit | `BaseTest#cacheImageFromUrl dedup` ✅ |

### Helpers.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-GW-015-a | R-GW-001 | MessageDeduplicator(`""`) | 不 dedup | unit | `HelpersTest#dedup empty id passes` ✅ |
| TC-GW-016-a | R-GW-001 | 超 cap 插入 | LRU 驱逐最老 | unit | `HelpersTest#dedup LRU eviction` ✅ |
| TC-GW-017-a | R-GW-001 | TextBatchAggregator 两条 | delay 内合并为一 | unit | `HelpersTest#batch aggregator coalesces` ✅ |
| TC-GW-018-a | R-GW-001 | 单条超 split threshold | 立即 flush | unit | `HelpersTest#split threshold forces flush` ✅ |
| TC-GW-019-a | R-GW-001 | `cancelAll()` 后继续 append | 已调度的 job 被撤 | unit | `HelpersTest#cancelAll stops scheduled` ✅ |
| TC-GW-020-a | R-GW-001 | ThreadParticipationTracker 持久化读回 | 状态一致 | integration | `HelpersTest#thread participation roundtrip` ✅ |
| TC-GW-021-a | R-GW-001 | stripMarkdown 列表 / 代码块 / link | 剥净 | unit | `HelpersTest#stripMarkdown covers syntax` ✅ |
| TC-GW-022-a | R-GW-001 | redactPhone 各 shape | 掩码一致 Python | unit | `HelpersTest#redactPhone gradient` ✅ |

### Feishu.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-GW-030-a | R-GW-002 | 空 `appId`/`appSecret` 调 connect | 返回 false | unit | `FeishuCoercionTest#empty credentials fail` ✅ |
| TC-GW-031-a | R-GW-002 | token 5 分钟内到期前 refresh | 刷新触发 | unit | `FeishuCoercionTest#token refreshes before expiry` ✅ |
| TC-GW-032-a | R-GW-002 | URL 模板常量 | 与 Python 对齐 | unit | `FeishuConstantsTest#url templates match python` ✅ |
| TC-GW-033-a | R-GW-002 | 同 message_id 两次 | 第二次丢弃 | unit | `FeishuNormalizeTest#dedup by event_id` ✅ |
| TC-GW-033-b | R-GW-002 | sender 不在 allowlist | 丢弃 | unit | `FeishuNormalizeTest#allowlist gate` ✅ |
| TC-GW-034-a | R-GW-002 | text / image / file / audio | 映射到 Normalized 对象 | unit | `FeishuNormalizeTest#parses content types` ✅ |
| TC-GW-035-a | R-GW-002 | 40000 字符输入 | 切成 2 块 ≤30000 | unit | `FeishuNormalizeTest#splitMessage bounds` ✅ |
| TC-GW-036-a | R-GW-002 | add/remove reaction 生命周期 | 处理中添加、完成后移除 | integration | `FeishuCoercionTest#reaction lifecycle` ✅ |
| TC-GW-037-a | R-GW-002 | PNG / JPEG / GIF 字节 | 正确 mime | unit | `FeishuMediaTypeTest#mime sniff` ✅ |
| TC-GW-038-a | R-GW-002 | 同 chat 连发 2 条 | 后者等前者完成 | integration | `FeishuCoercionTest#per-chat serial` ✅ |
| TC-GW-039-a | R-GW-002 | markdown `# title` → feishu post | 正确 element 树 | unit | `FeishuMarkdownTest#post rendering` ✅ |
| TC-GW-040-a | R-GW-002 | `probeBot(appId,secret)` 成功 | 返回 BotInfo | integration | `FeishuQrOnboardingTest#probeBot ok` ✅ |
| TC-GW-040-b | R-GW-002 | `qrRegister` 失败 | 明确错误消息 | unit | `FeishuQrOnboardingTest#qrRegister error` ✅ |

### FeishuComment.kt + FeishuCommentRules.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-GW-065-a | R-GW-002 | URL 模板替换 `{doc_id}` | 替换成功 | unit | `FeishuCommentTest#url template replace` ✅ |
| TC-GW-066-a | R-GW-002 | doc URL match regex | 捕获 doc_id | unit | `FeishuCommentTest#doc url regex captures` ✅ |
| TC-GW-067-a | R-GW-002 | chunk size 常量 | 同 Python | unit | `FeishuCommentTest#chunk size match` ✅ |
| TC-GW-068-a | R-GW-002 | prompt 超 cap | 截断 | unit | `FeishuCommentTest#prompt truncation` ✅ |
| TC-GW-069-a | R-GW-002 | agent 回 `NO_REPLY` | 不发送 | unit | `FeishuCommentTest#NO_REPLY sentinel` ✅ |
| TC-GW-070-a | R-GW-002 | session store 过期条目 | 被清理 | unit | `FeishuCommentTest#session store TTL` ✅ |
| TC-GW-071-a | R-GW-002 | notice_type=unsupported | 丢弃 | unit | `FeishuCommentTest#notice_type filter` ✅ |
| TC-GW-085-a | R-GW-002 | FeishuCommentRules 文件改动后 load | 重新解析 | unit | `FeishuCommentRulesTest#mtime cache invalidates` ✅ |
| TC-GW-086-a | R-GW-002 | 3-tier 规则解析 | 按 priority 胜出 | unit | `FeishuCommentRulesTest#three tier resolves` ✅ |
| TC-GW-087-a | R-GW-002 | policy 合法值 | `{"allow","block","ask"}` | unit | `FeishuCommentRulesTest#policy value set` ✅ |
| TC-GW-088-a | R-GW-002 | `isUserAllowed(rule, uid)` | 基于 deny/allow 列表 | unit | `FeishuCommentRulesTest#isUserAllowed` ✅ |

### Weixin.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-GW-095-a | R-GW-003 | token="" 调 connect | false | unit | `WeixinHelpersTest#empty token fails connect` ✅ |
| TC-GW-096-a | R-GW-003 | `_headers(token)` | 含 Authorization / Content-Type | unit | `WeixinHelpersTest#headers shape` ✅ |
| TC-GW-097-a | R-GW-003 | 同 msg_id 两次 | 第二次 dedupe | unit | `WeixinCachesTest#LRU dedupe` ✅ |
| TC-GW-098-a | R-GW-003 | long poll timeout 触发 | 重试退避 | integration | `WeixinDeliveryTest#long poll retry` ✅ |
| TC-GW-099-a | R-GW-003 | typing ticket 缓存命中 | 复用 ticket | unit | `WeixinCachesTest#typing ticket cache` ✅ |
| TC-GW-100-a | R-GW-003 | AES-128-ECB encrypt/decrypt | 回环 | unit | `WeixinHelpersTest#aes128ecb roundtrip` ✅ |
| TC-GW-101-a | R-GW-003 | `_assertWeixinCdnUrl(bad)` | 抛异常 | unit | `WeixinHelpersTest#cdn url assert` ✅ |
| TC-GW-102-a | R-GW-003 | markdown >1 段 | 按段拆分 | unit | `WeixinMarkdownTest#chunk split` ✅ |
| TC-GW-103-a | R-GW-003 | `qrLogin()` 成功 | 账号持久化 + 返 state | integration | `WeixinPersistenceTest#qr login persists` ✅ |

### Weixin.kt — bugfix-1：4 处 Python 上游对齐偏差（无新需求）

背景: bugfix territory（§0.1：bug 是代码没满足既有需求 R-GW-003 "消息收发对齐 Python 上游"）。本节只动 ②③，不动 ①。

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-GW-111-a | R-GW-003 | session expired 分支 sleep 时长 | `delay(10 * 60_000L)` 对齐 Python `weixin.py:1258` `asyncio.sleep(600)`；不再含 `5 * 60_000L` | unit | `WeixinSessionExpirySleepTest#session expired sleeps 10 min` 🟢 |
| TC-GW-112-a | R-GW-003 | `extra={"account_id": "abc"}` + 无 `login_token` + hermesHome 含持久化 token 的 account file | 构造 Weixin 后 `_loginToken` / `_baseUrl` 从持久化文件回填（对齐 Python `weixin.py:1166-1170`） | unit | `WeixinAccountFallbackTest#fallback loads persisted token` 🟢 |
| TC-GW-113-a | R-GW-003 | `connect()` 在 `checkWeixinRequirements()=false` 时（mock 重写为 false） | 立即返 false 且发出 fatal log（对齐 Python `weixin.py:1183-1187`），不启 poll job | unit | `WeixinConnectRequirementsTest#connect rejects when requirements fail` 🟢 |
| TC-GW-114-a | R-GW-003 | `send(chatId, content)` 构造的 `msg` JSON | 含 `from_user_id=""` 字段（对齐 Python `weixin.py:432`） | unit | `WeixinSendPayloadTest#payload includes empty from_user_id` 🟢 |

### Weixin.kt — 入站媒体采集（bugfix：图片消息丢失）

背景: 用户在微信里发图后，agent 端只收到空文本（或纯文字部分）。Python 上游 `gateway/platforms/weixin.py:1325-1357` 在 `_process_incoming_event` 里对 `item_list` 同时做 `_extract_text` 与 `_collect_media`，把下载并解密后的本地路径塞进 `MessageEvent.media_urls`/`media_types`。Kotlin `Weixin._handleInbound`（`Weixin.kt:310-356`）只调 `_extractText`，且当 `text.isBlank()` 时直接 `return`，导致纯图片入站被无声丢弃；即便 `MessageEvent.mediaUrls`/`mediaTypes` 字段存在（`Base.kt:131,133`），也从不被填充。

修复方向（合规于 R-GW-003 既有需求"消息收发对齐 Python 上游"）: 1) 翻译 Python `_collect_media` / `_download_image` / `_download_video` / `_download_file` / `_download_voice` 子集（首批至少 image，覆盖最常见用例）到 Kotlin；2) `_handleInbound` 在 text 为空但 mediaPaths 非空时也建 `MessageEvent`；3) `MessageEvent` 携带 `mediaUrls`/`mediaTypes` 透传给 `agentRunner`。本节 TC 是 bugfix 的失败侧捕获 + 修复后回归保险。

> **状态说明（2026-06-16）**: 374889b6 commit 留下的 WIP 红测 (`WeixinMediaCollectTest` / `RunAgentRunnerMediaTest`) 引用了尚未实现的 top-level 符号 (`ITEM_*` / `_collectMedia` / `MediaDownloader` 接口) 与 7 参版本的 `agentRunner`，在后续 commit 让 `agentRunner` 签名前进后成了**编译毒丸**——卡住整个 `:hermes-android:testDebugUnitTest`。本会话已撤回这两个测试文件（保留 TC 描述作为活档），状态退回 🔴。R-GW-003 媒体修复立项时按 §0.1 ③ 重新落地测试代码 + 生产代码并双绿退出。

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-GW-104-a | R-GW-003 | `ITEM_*` 常量值 | `ITEM_TEXT=1, ITEM_IMAGE=2, ITEM_VOICE=3, ITEM_FILE=4, ITEM_VIDEO=5`（与 Python `weixin.py:121-125` 一致） | unit | 🔴 待重写（撤回 374889b6） |
| TC-GW-105-a | R-GW-003 | `_handleInbound` 收到只含 `ITEM_IMAGE` item 的 `item_list`（无 text item） | 不再在 `text.isBlank()` 处提前 return；`handleMessage` 收到 `MessageEvent` 且 `mediaUrls` 包含 1 个本地 cache 路径，`mediaTypes=["image/jpeg"]` | unit | 🔴 待重写（撤回 374889b6） |
| TC-GW-106-a | R-GW-003 | `_handleInbound` 收到 text + 1 张图（混合 item_list） | `MessageEvent.text` = 文本部分；`mediaUrls.size==1`；`mediaTypes==["image/jpeg"]` | unit | 🔴 待重写（撤回 374889b6） |
| TC-GW-107-a | R-GW-003 | `_handleInbound` 收到 `ref_msg.message_item.type==ITEM_IMAGE`（被引用的图） | ref 图也进 `mediaUrls`（与 Python 上游 `weixin.py:1331-1334` 行为一致） | unit | 🔴 待重写（撤回 374889b6） |
| TC-GW-108-a | R-GW-003 | `_collectMedia` 给 `ITEM_VIDEO` / `ITEM_FILE` / `ITEM_VOICE` 分别构造 stub item | 各类型 mediaType 字符串与 Python `_collect_media` 表对齐（`video/mp4` / 文件 mime / `audio/silk`）；缺少必填字段时 path 为 null 不入列表 | unit | 🔴 待重写（撤回 374889b6） |
| TC-GW-109-a | R-GW-003 | `_downloadImage` 解密失败抛异常 | 不传播给 `_handleInbound`；返回 null；event 仍可被构造（媒体只是少一项） | unit | 🔴 待重写（撤回 374889b6） |
| TC-GW-110-a | R-GW-003 | `MessageEvent` 经 `Run.kt` `agentRunner` 调用点 | `event.mediaUrls` / `event.mediaTypes` 被透传到 `agentRunner` 的对应参数（验证 Run.kt 的 dispatch lambda 不再丢弃媒体字段） | unit | 🔴 待重写（撤回 374889b6） |

### WeCom 簇

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-GW-120-a | R-GW-005 | WeCom 凭证空 connect | false | unit | `WecomAdapterTest#connect returns false when corp_id and corp_secret are empty` 🟢 |
| TC-GW-121-a | R-GW-005 | WeCom token URL | `/cgi-bin/gettoken?...` | unit | `WecomAdapterTest#token url` 🟢 |
| TC-GW-122-a | R-GW-005 | send 超 2048 字符 | 截断 | unit | `WecomAdapterTest#send truncates` 🟢 |
| TC-GW-123-a | R-GW-005 | `agent_id="abc"` | 归 0 | unit | `WecomAdapterTest#agent_id non-numeric becomes 0` 🟢 |
| TC-GW-124-a | R-GW-005 | WeComCallback 所有入口 | toolError Android | unit | `WecomCallbackTest#android denies all` 🟢 |
| TC-GW-125-a | R-GW-005 | crypto 构造缺参 | 抛 | unit | `WecomCryptoTest#constructor validates` ✅ |
| TC-GW-126-a | R-GW-005 | signature 已知向量 | 匹配 Python | unit | `WecomCryptoTest#signature known answer` ✅ |
| TC-GW-127-a | R-GW-005 | decrypt payload layout | 正确 receiverId / msg | unit | `WecomCryptoTest#decrypt layout` ✅ |
| TC-GW-128-a | R-GW-005 | PKCS7 块大小 | 32 | unit | `WecomCryptoTest#PKCS7 block size` ✅ |

### Telegram.kt + TelegramNetwork.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-GW-135-a | R-GW-005 | 空 token | connect 失败 | unit | `TelegramAdapterTest#empty token false` ✅ |
| TC-GW-136-a | R-GW-005 | Telegram URL 常量 | `api.telegram.org/bot{token}/` | unit | `TelegramAdapterTest#url constants` ✅ |
| TC-GW-137-a | R-GW-005 | offset 单调 | 第二次 ≥ 第一次 | unit | `TelegramAdapterTest#offset monotonic` ✅ |
| TC-GW-138-a | R-GW-005 | caption 超 1024 | 截断 | unit | `TelegramAdapterTest#caption cap` ✅ |
| TC-GW-139-a | R-GW-005 | `allowed_groups="a,b, ,c"` extras | 解析为 `{"a","b","c"}`（trim + drop empty） | unit | `TelegramAdapterTest#allowed_groups parse shape` ✅ |
| TC-GW-140-a | R-GW-005 | `drop_pending_updates=true` | 首次启动时 skip | unit | `TelegramAdapterTest#drop pending flag` ✅ |
| TC-GW-141-a | R-GW-005 | 429 response | 分类为 retryable | unit | `TelegramNetworkTest#classifies 429` ✅ |
| TC-GW-141-b | R-GW-005 | 403 response | non-retryable | unit | `TelegramNetworkTest#classifies 403` ✅ |
| TC-GW-142-a | R-GW-005 | 瞬发 10 个请求 | rate limiter 串行化 | unit | `TelegramNetworkTest#rate limiter serializes` ✅ |

### Discord.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-GW-150-a | R-GW-005 | JWT token 解析 bot id | 正确 snowflake | unit | `DiscordAdapterTest#bot id parse` ✅ |
| TC-GW-151-a | R-GW-005 | `send(reply_to=msg_id)` | 消息带 `message_reference` | unit | `DiscordAdapterTest#send reply_to wired` ✅ |
| TC-GW-152-a | R-GW-005 | typing 状态 | POST `/typing` 无 body | unit | `DiscordAdapterTest#typing endpoint` ✅ |

### Run.kt — 出站失败可观测性 (R-GW-001 bugfix, 2026-06-06)

**背景**：用户报告飞书双向偶发丢消息（亮屏前台也发生）。静态审查发现 `Run.kt:425` 出站重试失败仅 `Log.w` 一行短消息（chat 不知是谁、文本不知是啥、错误细节也没），release 包还把关键路径的 `Log.d` strip 掉了——下次再丢，gateway.log 里没证据可查。Commit 3 只升级日志可见度，不改控制流。

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-GW-170-a | R-GW-001 | `Run.kt` 出站重试失败的 `Log.w` | 必须包含 `chatId` + `len=` + `error=`（不只是错误字符串） | unit/source | `RunSendFailureLoggingTest#TC-GW-170-a final send-failure log carries chatId and length` 🟢 |
| TC-GW-170-b | R-GW-001 | `Run.kt` 第一次重试前的 `Log.w` | 必须包含 `chatId` + `error=` | unit/source | `RunSendFailureLoggingTest#TC-GW-170-b first attempt failure log carries chatId` 🟢 |
| TC-GW-171-a | R-GW-002 | `Feishu.kt` dedup 命中分支 | 必须升级到 `Log.i`（release 包要能看到证据） | unit/source | `FeishuDiagnosticLoggingTest#TC-GW-171-a duplicate hit logged at INFO` 🟢 |
| TC-GW-171-b | R-GW-002 | `Feishu.kt` allowlist 拒绝分支 | 必须升级到 `Log.i` | unit/source | `FeishuDiagnosticLoggingTest#TC-GW-171-b allowlist rejection logged at INFO` 🟢 |
| TC-GW-171-c | R-GW-002 | `Feishu.kt` `Starting official Feishu WS client` 后 5 行内 | 必须存在 `WS event received` 或等价的"WS 已连"证据日志（用于诊断假说 #2 的 SDK 静默断线） | unit/source | `FeishuDiagnosticLoggingTest#TC-GW-171-c WS lifecycle has post-start liveness log` 🟢 |

### Run.kt + UndeliveredReplyStore — 出站失败救命包 (R-GW-003 bugfix, 2026-06-06)

**背景**：commit 3 让 release 包能看到"发失败了"的证据，但 agent 算出来的回复还是丢了——用户看不见、没法手动补救。Commit 4 补齐救命包：

1. `GatewayRunner` 加 `onSendFailed: (platform, chatId, text, error) -> Unit` 回调（仿 `agentRunner` 模式）
2. `Run.kt:425` 最终失败分支调用 `onSendFailed`（第一次失败不调，避免 retry 成功的误报）
3. `UndeliveredReplyStore` 把失败回复追加到 `/sdcard/Download/Hermes/undelivered.jsonl`（JSONL = 每行一个 JSON，原子写）
4. `UndeliveredReplyNotifier` 通过 Android NotificationManager 弹本地通知；点击 → 把全文复制到剪贴板（Toast 提示"已复制"）
5. `HermesGatewayController.start()` 把 Store + Notifier 接到 `runner.onSendFailed`

**测试策略**：JVM 单测里 Notifier / NotificationManager 强依赖 Android，走源码字符串扫描；Store 的 append + read 路径用真文件（`File.createTempFile`）。Run.kt 回调挂载点用源码扫描确认。

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-GW-172-a | R-GW-003 | `GatewayRunner` 类定义 | 必须有 `onSendFailed: (platform, chatId, text, error) -> Unit` 类型的 `@Volatile var` 属性 | unit/source | `RunOnSendFailedCallbackTest#TC-GW-172-a defines onSendFailed callback property` 🟢 |
| TC-GW-172-b | R-GW-003 | `Run.kt` 最终失败分支 | 必须调用 `onSendFailed?.invoke(platformName, currentEvent.source.chatId, sendText, result.error ?: "unknown")` —— 第一次失败那个分支**不**调（retry 可能救活） | unit/source | `RunOnSendFailedCallbackTest#TC-GW-172-b invokes onSendFailed only on final failure` 🟢 |
| TC-GW-173-a | R-GW-003 | `UndeliveredReplyStore.append(platform, chatId, text, error)` 再 `read()` | 返回单条 entry：platform/chatId/text/error/timestampMs 全部正确；文件每行一个 JSON | unit | `UndeliveredReplyStoreTest#TC-GW-173-a append then read returns same entry` 🟢 |
| TC-GW-173-b | R-GW-003 | `Store.append` 调用 3 次 → `Store.read()` | 返回 3 条按时间顺序排列；文件正好 3 行（不重写、不损坏） | unit | `UndeliveredReplyStoreTest#TC-GW-173-b appends are durable and ordered` 🟢 |
| TC-GW-173-c | R-GW-003 | `Store.clear()` 后 `read()` | 返回空列表；文件被截断或删除 | unit | `UndeliveredReplyStoreTest#TC-GW-173-c clear truncates store` 🟢 |
| TC-GW-174-a | R-GW-003 | `UndeliveredReplyNotifier.kt` 源码 | 必须创建 NotificationChannel id 含 `undelivered`；必须用 `NotificationManager.notify` 弹通知；点击 PendingIntent 必须把 text 写入剪贴板（`ClipboardManager`） | unit/source | `UndeliveredReplyNotifierTest#TC-GW-174-a notifies on local channel and copies text on click` 🟢 |
| TC-GW-175-a | R-GW-003 | `HermesGatewayController.start()` 源码 | `agentRunner = ...` 后必须紧跟设置 `instance.onSendFailed`，内部调用 Store.append + Notifier.notify | unit/source | `HermesGatewayControllerSendFailedWiringTest#TC-GW-175-a wires Store and Notifier into onSendFailed` 🟢 |
| TC-GW-176-a | R-GW-003 | 源码扫描：`Run.kt` pending-event 失败分支 | 与正常分支对称，必须调 `onSendFailed?.invoke(platformName, pendingEvent.source.chatId, pendingResult` —— 否则 pending 路径上发送失败时静默丢消息（不进 UndeliveredReplyStore，用户永远收不到 agent 回复）。 | unit/source | `RunPendingSendFailureWiringTest#TC-GW-176-a pending failure branch invokes onSendFailed` 🟢 |
| TC-GW-176-b | R-GW-003 | 源码扫描：`Run.kt` pending-event 失败分支 `Log.w` | 与正常分支对称，必须含 `chatId` + `len=` + `error=` 字面值（不只是错误字符串） —— 对齐 TC-GW-170-a 的 release 包诊断要求。 | unit/source | `RunPendingSendFailureWiringTest#TC-GW-176-b pending failure log carries chatId and length` 🟢 |

### Stub adapters (Signal/Slack/Matrix/WhatsApp/SMS/Email/Homeassistant/Mattermost/Webhook/BlueBubbles)

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-GW-160-a | R-GW-005 | 任意 stub adapter `connect()` | false | unit | `StubAdapterTest#all stubs return false from connect` 🟢 |
| TC-GW-161-a | R-GW-005 | `send(...)` | err 消息 "not available on Android" | unit | `StubAdapterTest#send on each stub returns unsuccessful SendResult mentioning Android` 🟢 |
| TC-GW-162-a | R-GW-005 | 两次调 `disconnect()` | 幂等无异常 | unit | `StubAdapterTest#disconnect on each stub is idempotent` 🟢 |
| TC-GW-163-a | R-GW-005 | `adapter.platform` | 与 Python 枚举一致 | unit | `StubAdapterTest#each stub's platform enum and name string match Python wire value` 🟢 |

### Dingtalk.kt + Homeassistant.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-GW-180-a | R-GW-005 | Dingtalk token URL 常量 | `/gettoken?...` | unit | `DingtalkAdapterTest#token url` ✅ |
| TC-GW-181-a | R-GW-005 | Dingtalk send endpoint | `/topapi/message/corpconversation/asyncsend_v2` | unit | `DingtalkAdapterTest#send endpoint` ✅ |
| TC-GW-182-a | R-GW-005 | HomeAssistant 重连退避阶梯（Python `_BACKOFF_STEPS`） | `[5, 10, 30, 60]`（4 档秒数，末档后 stick） | unit | `HomeAssistantAdapterTest#backoff ladder` ✅ |
| TC-GW-183-a | R-GW-005 | auth header | `Bearer <token>` | unit | `HomeAssistantAdapterTest#auth header` ✅ |
| TC-GW-184-a | R-GW-005 | 发送通知走 `persistent_notification.create`（Python 上游选择） | POST 到 `/api/services/persistent_notification/create` | unit | `HomeAssistantAdapterTest#notify path` ✅ |

### qqbot/

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-GW-190-a | R-GW-004 | QQAdapter.connect | Android stub false | unit | `AdapterHelpersTest#connect stub` ✅ |
| TC-GW-191-a | R-GW-004 | `_guessChatType("C:xxx")` | channel | unit | `UtilsTest#_guessChatType prefixes` ✅ |
| TC-GW-192-a | R-GW-004 | `_stripAtMention` | 移除 @ 片段 | unit | `UtilsTest#_stripAtMention` ✅ |
| TC-GW-193-a | R-GW-004 | `_isVoiceContentType("audio/silk")` | true | unit | `UtilsTest#_isVoiceContentType` ✅ |
| TC-GW-193-b | R-GW-004 | `_isVoiceContentType("image/png")` | false | unit | `UtilsTest#_isVoiceContentType image false` ✅ |
| TC-GW-194-a | R-GW-004 | `_guessExtFromData(JPEG bytes)` | `.jpg` | unit | `UtilsTest#_guessExtFromData jpeg` ✅ |
| TC-GW-195-a | R-GW-004 | `_parseQqTimestamp("bad")` | fallback | unit | `UtilsTest#parseQqTimestamp fallback` ✅ |
| TC-GW-196-a | R-GW-004 | AES-256-GCM encrypt/decrypt | 回环 | unit | `UtilsTest#aes256gcm roundtrip` ✅ |
| TC-GW-197-a | R-GW-004 | `createBindTask` retcode!=0 | 返回错误 | integration | `OnboardTest#createBindTask checks retcode` ✅ |
| TC-GW-198-a | R-GW-004 | `buildConnectUrl` 带特殊字符 | URL 编码 | unit | `OnboardTest#buildConnectUrl encodes` ✅ |
| TC-GW-199-a | R-GW-004 | `buildUserAgent(null)` | fallback 字符串 | unit | `OnboardTest#buildUserAgent fallback` ✅ |
| TC-GW-200-a | R-GW-004 | `coerceList(null)` | `[]` | unit | `OnboardTest#coerceList null` ✅ |
| TC-GW-200-b | R-GW-004 | `coerceList("s")` | `["s"]` | unit | `OnboardTest#coerceList scalar` ✅ |

---

## 域 STATE

测试类: `HermesStateTest.kt`（已覆盖核心 CRUD）；`SessionDB` / `ContextCompressor` / `MemoryManager` 需 Robolectric 补测。

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-STATE-001-a | R-STATE-001 | `get("no", default=42)` | 42 | unit | `HermesStateTest#get missing returns default` ✅ |
| TC-STATE-002-a | R-STATE-001 | `set("k","v")` | dirty=true；autoSave 触发 | unit | `HermesStateTest#set marks dirty and autosaves` ✅ |
| TC-STATE-003-a | R-STATE-001 | `delete("k")` 存在 | 返 true | unit | `HermesStateTest#delete returns existence` ✅ |
| TC-STATE-003-b | R-STATE-001 | `delete("nope")` | 返 false | unit | `HermesStateTest#delete missing false` ✅ |
| TC-STATE-004-a | R-STATE-001 | `keys` / `size` / `contains` | 反映当前 map | unit | `HermesStateTest#view operations consistent` ✅ |
| TC-STATE-005-a | R-STATE-001 | 并发两进程 `save` | FileChannel.lock 串行 | integration | `HermesStateTest#save uses file lock` 🟢 |
| TC-STATE-006-a | R-STATE-001 | `merge` map→非 map | 替换 | unit | `HermesStateTest#merge replaces non-map` ✅ |
| TC-STATE-007-a | R-STATE-001 | `snapshot()` 修改 | 不影响源 | unit | `HermesStateTest#snapshot is copy` ✅ |
| TC-STATE-008-a | R-STATE-001 | `clear()` | size=0 | unit | `HermesStateTest#clear empties` ✅ |
| TC-STATE-009-a | R-STATE-001 | `getGlobalState()` 首次 + 二次 | 单例返回同实例 | unit | `HermesStateTest#global state singleton` ✅ |
| TC-STATE-015-a | R-STATE-003 | SessionDB FTS5 索引表存在 | schema 正确 | integration | `SessionDBTest#fts5 index present` 🟢 |
| TC-STATE-016-a | R-STATE-003 | 并发写 2 session | 串行成功、无 race | integration | `SessionDBTest#concurrent writes serialized` 🟢 |
| TC-STATE-017-a | R-STATE-003 | `getGlobalSessionDB()` 多次 | 同 instance | unit | `SessionDBTest#global singleton lazy` 🟢 |
| TC-STATE-025-a | R-STATE-002 | 长度触阈值 | 压缩启动 | integration | `ContextCompressorTest#needsCompression true when over threshold` 🟢 |
| TC-STATE-026-a | R-STATE-002 | 压缩后 | system + tail 完整保留 | integration | `ContextCompressorTest#keepRecent strategy returns at least minRecent when possible` 🟢 |
| TC-STATE-027-a | R-STATE-002 | tool 结果在中段 | 优先压缩 | integration | `ContextCompressorTest#drop tool results strategy removes a middle tool_result` 🟢 |
| TC-STATE-028-a | R-STATE-002 | 压缩 API 异常 | 回退不压 + 日志 | integration | `ContextCompressorTest#_generateSummary returns null on Android without LLM` 🟢 |
| TC-STATE-035-a | R-STATE-003 | memory 文件有内容 | loadIntoPrompt 拼到 system | integration | `MemoryManagerTest#buildSystemPrompt concatenates nonempty provider blocks` 🟢 |
| TC-STATE-036-a | R-STATE-003 | memory 更新 | 触发 persist | integration | `MemoryManagerTest#onMemoryWrite fans out to external providers` 🟢 |

---

## 域 SKILL

测试类: 新建 `DiskCleanupPluginTest`, `MemoryProviderHolographicTest`, `MemoryProviderHonchoTest`, `SkillCommandsTest`。

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-SKILL-010-a | R-SKILL-001 | `quick()` 跑一遍含 category=test 的 tracked 条目 | test 文件立删；返回 map `deleted>=1` | integration | `DiskCleanupPluginTest#quick deletes test category immediately` ✅ |
| TC-SKILL-011-a | R-SKILL-001 | `isSafePath()` 传入 HERMES_HOME 外路径 | false；HERMES_HOME 内 / `/tmp/hermes-*` 下 true | unit | `DiskCleanupPluginTest#isSafePath scope bounds` ✅ |
| TC-SKILL-012-a | R-SKILL-001 | `dryRun()` 含 test + temp + research | 返 Pair(auto,prompt)；磁盘上文件仍在 | unit | `DiskCleanupPluginTest#dryRun is read-only` ✅ |
| TC-SKILL-020-a | R-SKILL-001 | MemoryProvider 接口 | 6 方法 `initialize/store/retrieve/delete/list/close` + `providerName` 属性（对齐 Python `hermes/plugins/memory/__init__.py`）| unit | `MemoryProviderTest#interface surface` ✅ |
| TC-SKILL-021-a | R-SKILL-001 | Holographic provider 写读 | 回环相等 | integration | `MemoryProviderHolographicTest#roundtrip` 🟢 |
| TC-SKILL-022-a | R-SKILL-001 | Honcho provider `save` | POST 请求有效签名 | integration | `MemoryProviderHonchoTest#save signed` 🟢 |
| TC-SKILL-030-a | R-SKILL-002 | `resolveSkillCommandKey("foo_bar")` 且 `/foo-bar` 已扫描 | 返回 `/foo-bar`（下划线归一 + 前缀加斜杠）| unit | `SkillCommandsTest#resolveSkillCommandKey underscore normalization` ✅ |
| TC-SKILL-031-a | R-SKILL-002 | `buildPlanPath(userInstruction="Fix the Android bug!")` | 返回 `.hermes/plans/<ts>-fix-the-android-bug.md`（slug: 小写/非 `a-z0-9` 转 `-`/最多 8 段/长度 ≤48；单参 string — Python 上游同样不拆 argv）| unit | `SkillCommandsTest#buildPlanPath slug rules` ✅ |
| TC-SKILL-040-a | R-SKILL-003 | skills 增量同步（`tools/skills_sync.py` 对应路径） | 参照 TOOL 段 TC-TOOL-185..191（已双指 R-SKILL-003） | unit/integration | `SkillsSyncTest` 全套（7 条） ✅ |
| TC-SKILL-041-a | R-SKILL-003 | 同步后 hub 索引刷新 | R-SKILL-001 的 SkillsHub 重新扫描 | integration | `SkillsHubTest#refresh after sync` 🟢 |
| TC-SKILL-042-a | R-SKILL-003 | 冲突 / 签名失败 | 拒绝覆盖 | unit | `SkillsSyncTest#signature mismatch rejects` 🟢 |

---

## 域 MCP

测试类: 新建 `McpToolTest`, `McpOAuthTest`, `ManagedToolGatewayTest`(已存在 — 补覆盖)。

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-MCP-001-a | R-MCP-001 | MCP server 列表 2 tools | Registry 多 2 条 | integration | `McpToolTest#registerMcpServers returns empty when SDK unavailable`（Android stub 路径） ✅ |
| TC-MCP-002-a | R-MCP-001 | server A 连不上 server B OK | B 的工具仍注册 | integration | `McpToolTest#isolation on failure` 🟢 |
| TC-MCP-003-a | R-MCP-001 | 调用 MCP 工具 | 代理到远端 server | integration | `McpToolTest#makeToolHandler returns error json`（Android 降级路径） ✅ |
| TC-MCP-004-a | R-MCP-001 | MCP schema | 转 OpenAI schema 格式一致 | unit | `McpToolTest#normalizeMcpInputSchema returns input when present` ✅ |
| TC-MCP-005-a | R-MCP-001 | stdio 传输 | Android stub toolError | unit | `McpToolTest#mcp availability flags are all false on android` ✅ |
| TC-MCP-010-a | R-MCP-002 | `mcp_serve.py` 对应的 server 侧暴露工具 | Android 平台上为 stub（无 local server）；保留类/方法对齐 | unit | `McpToolTest#buildSafeEnv returns empty map` + `McpServerTask default state is disconnected` ✅ |
| TC-MCP-010-b | R-MCP-002 | 方法签名与 `mcp_serve.py` 1:1 | 由 verify_align 守卫 | alignment | §2 三件套 ✅ |
| TC-MCP-020-a | R-MCP-003 | OAuth token 已存在 | 从磁盘重放为 cached | integration | `McpOAuthTest#token storage persists and reloads` ✅ |
| TC-MCP-021-a | R-MCP-003 | `parseBaseUrl` 归一化 MCP server URL | 正确归一化 | unit | `McpOAuthTest#parseBaseUrl normalises mcp server url` + `parseBaseUrl falls back on malformed` ✅ |
| TC-MCP-022-a | R-MCP-003 | `buildClientMetadata` + overrides / defaults | 生成符合 OAuth 动态注册的 metadata | unit | `McpOAuthTest#buildClientMetadata applies overrides` + `buildClientMetadata default values` ✅ |
| TC-MCP-023-a | R-MCP-003 | `safeFilename` 清洗 host 为文件名 | 非法字符被替换 | unit | `McpOAuthTest#safeFilename strips unsafe chars` ✅ |
| TC-MCP-024-a | R-MCP-003 | Android 无 interactive browser | `interactive and browser checks return false` | unit | `McpOAuthTest#interactive and browser checks return false on android` ✅ |
| TC-MCP-025-a | R-MCP-003 | Android 无法交互完成 OAuth | `buildOauthAuth is null on android` + `waitForCallback returns null stub` | unit | `McpOAuthTest#buildOauthAuth is null on android` + `waitForCallback returns null stub` ✅ |
| TC-MCP-026-a | R-MCP-003 | 损坏的 token 文件 | 被忽略（而非抛异常） | unit | `McpOAuthTest#corrupt tokens file is ignored` ✅ |
| TC-MCP-027-a | R-MCP-003 | `OAuthNonInteractiveError` | 默认消息存在 | unit | `McpOAuthTest#OAuthNonInteractiveError has default message` ✅ |
| TC-MCP-030-a | R-MCP-001 | ManagedToolGateway 调 gateway tool | Android 降级到 local | unit | `ManagedToolGatewayTest#android downgrade` ✅ |
| TC-MCP-031-a | R-MCP-001 | 请求签名 | HMAC match | unit | `ManagedToolGatewayTest#request signature` ✅ |

---

## 域 CRON

测试类: `SchedulerTest`（已覆盖）、新建 `JobsTest`、`CronjobToolsTest`（已覆盖）。

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-CRON-001-a | R-CRON-001 | CronjobTools.create | toolError | unit | `CronjobToolsTest#create denied` ✅ |
| TC-CRON-001-b | R-CRON-001 | CronjobTools.delete | toolError | unit | `CronjobToolsTest#delete denied` ✅ |
| TC-CRON-002-a | R-CRON-001 | requirements() | `{skipped:"android"}` | unit | `CronjobToolsTest#requirements skipped` ✅ |
| TC-CRON-010-a | R-CRON-001 | `"*/5 * * * *"` 解析 | every 5 min | unit | `SchedulerTest#parses every-5-min` ✅ |
| TC-CRON-010-b | R-CRON-001 | `"0 9 * * 1-5"` | weekdays 9am | unit | `SchedulerTest#parses weekdays 9am` ✅ |
| TC-CRON-011-a | R-CRON-001 | `addJob` 再读回 | 持久化 | integration | `JobsTest#persistence roundtrip` ✅ |
| TC-CRON-012-a | R-CRON-001 | approve mode=`"no"` | 阻塞 | unit | `JobsTest#approval mode enforced` ✅ |
| TC-CRON-013-a | R-CRON-001 | Android 启动 | daemon 不起 | integration | `JobsTest#parseSchedule cron-expression is rejected on Android` 🟢 |

---

## 域 AGENT — Cron Wiring (R-AGENT-031)

R-AGENT-031 把 hermes-android 已存在的 cron 数据层（`Jobs.kt` CRUD / 状态机已 1:1 对齐 Python 上游）和 agent 执行链路接通，让 agent 真的能注册定时任务，每 15 分钟由 WorkManager tick 一次扫描到期任务，由 app 模块的 `CronAgentRunner` headlessly 调起 agent loop（复用 `ExternalChatRequestExecutor`），最终把回执写回原 chat（走 `ChatHistoryManager.addMessage` 持久化层 + 发 `GatewayChatEventBus.Event.ProcessingCompleted` 触发 UI 刷新）。

**架构合规（按用户决策 1+3+4）**：
- **路径 1**：hermes-android 模块只持有数据层，不引入 Python 上游没有的注入点（如 Scheduler 静态 lambda）。所有调度 / agent 调用 / 写回 chat 都在 app 模块（`cron/CronTickWorker.kt`、`cron/CronAgentRunner.kt`）。`Scheduler.runJob()` / `deliverResult()` 在 hermes-android 里**保持 stub**（带 KDoc 注明：Android 平台由 app 模块通过 WorkManager 接管，等价于 Python upstream daemon 的 platform-specific 替代品）。
- **路径 3**：递归 cronjob 软防（prompt-injection）。CronAgentRunner 在调起 agent 前给 prompt 追加 `[CRON CONTEXT] / [CRON 上下文]` 前缀，告知 agent 这是被 cron 触发的回合、避免在此回合再注册 cronjob。
- **路径 4**：写回路径走持久化层 `ChatHistoryManager.addMessage(chatId, ChatMessage)`（已被 `HermesGatewayController` / `WebChatHttpBridge` / `StandardChatManagerTool` 等 6+ 处 headless 调用方使用），不走 UI 绑定的 `ChatHistoryDelegate`。Worker 写完 DB 再 `GatewayChatEventBus.events.emit(Event.ProcessingCompleted(chatId))` 触发活动 chat 面板的 `reloadChatMessagesSmart`（`ChatHistoryDelegate.kt:225`）。

测试策略：
- **源码扫描**（unit-scan）覆盖 wiring 关键字面值：CronjobTools 的 action 分发分支、CronTickWorker 的 PeriodicWork 配置、CronAgentRunner 的 prompt 前缀 + ChatHistoryManager 路径、OperitApplication 的 enqueue 调用、SystemPromptConfig 的 cronjob 提及。
- **行为单测**（unit）覆盖 CronjobTools 的 dispatch 路径：create / list / get / update / pause / resume / trigger / remove / run（同步触发）/ logs。Min-interval 15 分钟门禁守在 create 入口前。
- 不做 WorkManager 集成测试（依赖 Robolectric workmanager fixture，成本高）；E2E 由手测兜底（用户在 chat 里 "每 15 分钟 ping 我一次" → 等 ≥15 分钟看是否真的发回 chat）。

| TC ID | R-ID | 输入 / 触发 | 期望 | 测试类型 | 实现 / 状态 |
|---|---|---|---|---|---|
| TC-AGENT-031-a | R-AGENT-031 | 源码扫描：`hermes-android/.../tools/CronjobTools.kt` | `checkCronjobRequirements()` 函数体必须 `return true`（Android 现已有 cron 数据层 + app 模块 Worker 执行链路）。 | unit-scan | `CronjobToolsWiringTest#TC-AGENT-031-a checkCronjobRequirements returns true` 🔴 |
| TC-AGENT-031-b | R-AGENT-031 | 源码扫描：`CronjobTools.cronjob(...)` 函数体 | 必须**不**含 `"cronjob tool is not available on Android"` 字面值；必须含 `when` 分支字面值 `"create"` / `"list"` / `"get"` / `"update"` / `"pause"` / `"resume"` / `"trigger"` / `"remove"` / `"run"`（dispatch 到 `Jobs.kt` 的对应 CRUD 函数）。 | unit-scan | `CronjobToolsWiringTest#TC-AGENT-031-b cronjob dispatcher covers all CRUD actions` 🔴 |
| TC-AGENT-031-c | R-AGENT-031 | 源码扫描：`CronjobTools.cronjob` 的 `"create"` 分支 | 必须含 15 分钟最小间隔守卫字面值（如 `15` + `minimum interval` / `min interval` 任一），任何 schedule 解析后小于 15 分钟的 interval 都返回 `toolError`。 | unit-scan | `CronjobToolsWiringTest#TC-AGENT-031-c create branch enforces 15 minute minimum interval` 🔴 |
| TC-AGENT-031-d | R-AGENT-031 | 源码扫描：`hermes-android/.../cron/Scheduler.kt` 文件头注释或顶部 KDoc | 必须含字面值 `app module` 或 `WorkManager` 任一（注明 Android 平台的真实执行链路在 app 模块的 CronTickWorker，避免后续 reviewer 误以为 Scheduler.runJob 是 Android 上的真实入口）。 | unit-scan | `CronWiringSchedulerStubTest#TC-AGENT-031-d Scheduler file documents app-module override` 🔴 |
| TC-AGENT-031-e | R-AGENT-031 | 源码扫描：`app/.../cron/CronTickWorker.kt` | 必须存在 `class CronTickWorker(...) : CoroutineWorker(...)`；必须含 `companion object` 暴露 `enqueue(context: Context)`；必须用 `PeriodicWorkRequest` + `15` + `TimeUnit.MINUTES` 字面值；必须用 `ExistingPeriodicWorkPolicy.UPDATE` 字面值（2026-06-18 R-AGENT-031 bugfix：从 KEEP 改为 UPDATE，否则 broken state 残留时新 enqueue 不生效，详见 TC-AGENT-031-o）。 | unit-scan | `CronTickWorkerWiringTest#TC-AGENT-031-e CronTickWorker is a CoroutineWorker with 15-minute periodic schedule` 🟢 |
| TC-AGENT-031-f | R-AGENT-031 | 源码扫描：`CronTickWorker.doWork()` 函数体 | 必须调 `Jobs.getDueJobs()`；对每个 due job 必须先 `Jobs.advanceNextRun(jobId)` 再调 `CronAgentRunner` 的 run 方法；必须用 try/catch 包住单个 job 的执行（一个 job 失败不影响其他）。 | unit-scan | `CronTickWorkerWiringTest#TC-AGENT-031-f doWork iterates getDueJobs and isolates failures` 🔴 |
| TC-AGENT-031-g | R-AGENT-031 | 源码扫描：`app/.../cron/CronAgentRunner.kt` | 必须存在 `CronAgentRunner` 类 / object；必须有 `run(context, job)` 入口；调起 agent 前的 prompt 必须含字面值 `[CRON CONTEXT]` 与 `[CRON 上下文]`（双语前缀，告知 agent 当前是被 cron 触发的回合）。 | unit-scan | `CronAgentRunnerWiringTest#TC-AGENT-031-g run prepends bilingual cron context tags` 🔴 |
| TC-AGENT-031-h | R-AGENT-031 | 源码扫描：`CronAgentRunner` 的 deliver 路径 | 必须含字面值 `ChatHistoryManager.getInstance(` 和 `.addMessage(`（走持久化层）；**不**得含 `ChatHistoryDelegate.` 字面值（守 UI-bound 边界）；必须含 `GatewayChatEventBus.events.emit(` + `Event.ProcessingCompleted(` 字面值（触发 UI 刷新）。 | unit-scan | `CronAgentRunnerWiringTest#TC-AGENT-031-h deliver writes via ChatHistoryManager and emits ProcessingCompleted` 🔴 |
| TC-AGENT-031-i | R-AGENT-031 | 源码扫描：`CronAgentRunner` 的 deliver 路径 | 必须调 `Jobs.markJobRun(jobId, success, error, deliveryError)` 把执行结果写回数据层；必须调 `Jobs.saveJobOutput(jobId, output)` 持久化 agent 回复全文。 | unit-scan | `CronAgentRunnerWiringTest#TC-AGENT-031-i deliver records run via markJobRun and saveJobOutput` 🔴 |
| TC-AGENT-031-j | R-AGENT-031 | 源码扫描：`app/.../OperitApplication.kt` 的 `onCreate()` | 必须 `import com.ai.assistance.operit.core.cron.CronTickWorker` 并含 `CronTickWorker.enqueue(this)` 字面值调用。 | unit-scan | `CronWiringApplicationStartupTest#TC-AGENT-031-j OperitApplication enqueues CronTickWorker on startup` 🔴 |
| TC-AGENT-031-k | R-AGENT-031 | 源码扫描：`core/config/SystemPromptConfig.kt` 的 `APP_SELF_AWARENESS_EN` / `APP_SELF_AWARENESS_CN` 常量体 | 两个常量都必须 mention `cronjob` 工具名 + `15` 字面值（告知 agent 平台最小间隔限制）；中文段必须含「定时」或「计划任务」任一字面字符。 | unit-scan | `SystemPromptAppSelfAwarenessWiringTest#TC-AGENT-031-k self-awareness mentions cronjob tool with 15-minute interval` 🔴 |
| TC-AGENT-031-l | R-AGENT-031 | 行为单测：调 `CronjobTools.cronjob(action="create", prompt="ping me", schedule="every 30 minutes", ...)` | 返回字符串里含创建后 job 的 `id` / `next_run`；`Jobs.listJobs()` 应能查到该 job。**Deferred (needs Robolectric)** —— `createJob` 经 `getHermesHome() → getAppContext().filesDir`，纯单测无 Context；与 `JobsTest` 同处理，靠 §3 E2E + 手测兜底。 | unit | `CronjobToolsBehaviorTest#TC-AGENT-031-l create then list roundtrips through Jobs (deferred)` 🔴 |
| TC-AGENT-031-m | R-AGENT-031 | 行为单测：调 `CronjobTools.cronjob(action="create", schedule="every 5 minutes", ...)` | 必须返回 `toolError`，错误消息含 `15` 字面值（min interval 守卫）。Guard 在 `createJob()` 调用前就 return，所以**不**需要 Context。 | unit | `CronjobToolsBehaviorTest#TC-AGENT-031-m create denies sub-15-minute interval` 🔴 |
| TC-AGENT-031-n | R-AGENT-031 | 行为单测：调 `CronjobTools.cronjob(action="list")` 但 Jobs 表为空 | 必须返回空列表的人类可读字符串（`No jobs` / `没有定时任务` 任一），**不**得抛异常或返回 toolError。**Deferred (needs Robolectric)** —— `listJobs()` 经 `getHermesHome()`，纯单测无 Context；靠 §3 E2E + 手测兜底。 | unit | `CronjobToolsBehaviorTest#TC-AGENT-031-n list on empty store returns human-readable empty result (deferred)` 🔴 |
| TC-AGENT-031-o | R-AGENT-031 | 源码扫描：`CronTickWorker.enqueue` 函数体（2026-06-18 bugfix） | 必须含 `ExistingPeriodicWorkPolicy.UPDATE`，**不**得含 `ExistingPeriodicWorkPolicy.KEEP` —— KEEP 会保留上次安装/崩溃残留的 broken/cancelled unique work record，让新 enqueue 不生效，cron 永久失效；UPDATE 强制替换 stale state。 | unit-scan | `CronTickWorkerEnqueueWiringTest#TC-AGENT-031-o enqueue uses UPDATE policy` 🟢 |
| TC-AGENT-031-p | R-AGENT-031 | 源码扫描：`CronTickWorker.enqueue` 的 catch 块（2026-06-18 bugfix） | catch 块内必须含 `throw` 字面值（重抛或包装抛）；调用方在 `OperitApplication.onCreate` 包一层 log-only catch。否则 enqueue 失败被静默吞掉，用户感知"app 启动正常"但 cron 实际死了。 | unit-scan | `CronTickWorkerEnqueueWiringTest#TC-AGENT-031-p enqueue re-throws on failure` 🟢 |

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

---

## 域 AGENT — Cron→IM Delivery Loop (R-AGENT-033)

R-AGENT-033 补 R-AGENT-031 设计层就没做的 cron→IM 投递回路。R-AGENT-031 验收 D 只要求"写 Room DB + 通知活动 chat UI"——结果飞书/Telegram bot 触发的 cron 任务到点后，回复永远到不了 IM。本 R 修 3 个独立 bug：(A) Run.kt::_handleMessage 没调 `setSessionVars`，(B) CronjobTools._originFromEnv 用 `System.getenv` 在 Android 永远返 null，(C) Scheduler.deliverResult 没人接 cron→IM 直投通道。

**架构合规**：
- **app→hermes-android 单向依赖**：Scheduler 在 hermes-android 不能 import app 的 HermesGatewayController；改用 hermes-android 顶层 `var cronOutboundDispatcher` 注入点，由 OperitApplication 启动时注入指向 `dispatchOutgoing` 的 lambda。这是对 R-AGENT-031 路径 1 决策"不引入注入点"的必要偏离。
- **ThreadLocal finally-clear 红线**：所有 setSessionVars / setCronAutoDeliverVars 必须有对应 clear 在 finally 块里，避免协程切线程残留。
- 复用既有 outbound 基础设施：`GatewayRunner.deliveryRouter.getAdapter` + 各 platform `send(...)` 已是 R-GW 系列稳定接口。

测试策略：全部走源码扫描（unit-scan）—— ThreadLocal 行为与 platform adapter dispatch 是 Android Context-bound，纯 JVM 单测难起；与 R-AGENT-031 同策略，行为正确性靠 §3 E2E + 手测兜底。

| TC ID | R-ID | 输入 / 触发 | 期望 | 测试类型 | 实现 / 状态 |
|---|---|---|---|---|---|
| TC-AGENT-033-a | R-AGENT-033 | 源码扫描：`Run.kt::_handleMessage` 函数体 | 必须含 `setSessionVars(` 调用，参数串内出现 `event.source.platform` + `event.source.chatId` + `event.source.threadId` 三处引用（对齐 Python `gateway/run.py:3964`）。 | unit-scan | `RunSessionVarsWiringTest#TC-AGENT-033-a _handleMessage sets session vars from event source` 🔴 |
| TC-AGENT-033-b | R-AGENT-033 | 源码扫描：`Run.kt::_handleMessage` 函数体 | 必须含 `clearSessionVars()` 调用，且**位于 finally 块内**（`finally\s*\{[\s\S]{0,500}clearSessionVars` 跨行 regex 验证；对齐 Python `gateway/run.py:4772`）。 | unit-scan | `RunSessionVarsWiringTest#TC-AGENT-033-b clearSessionVars called in finally block` 🔴 |
| TC-AGENT-033-c | R-AGENT-033 | 源码扫描：`hermes-android/.../gateway/SessionContext.kt` | 必须含 `HERMES_CRON_AUTO_DELIVER_PLATFORM` + `HERMES_CRON_AUTO_DELIVER_CHAT_ID` + `HERMES_CRON_AUTO_DELIVER_THREAD_ID` 三个字面值；必须含 `fun setCronAutoDeliverVars(` + `fun clearCronAutoDeliverVars(` 函数声明（对齐 Python `gateway/session_context.py:61-63,73-75`）。 | unit-scan | `SessionContextCronVarsWiringTest#TC-AGENT-033-c cron auto-deliver vars registered` 🔴 |
| TC-AGENT-033-d | R-AGENT-033 | 源码扫描：`CronjobTools.kt::_originFromEnv` 函数体 | 必须含 `getSessionEnv(` 至少 3 次调用（platform / chat_id / thread_id 三个 session var 读取）；**不**得含 `System.getenv("HERMES_SESSION_` 字面值（红线：旧 OS env 路径必须移除，对齐 Python `cronjob_tools.py:73-86`）。 | unit-scan | `CronjobOriginFromEnvWiringTest#TC-AGENT-033-d _originFromEnv reads ThreadLocal not OS env` 🔴 |
| TC-AGENT-033-e | R-AGENT-033 | 源码扫描：`hermes-android/.../cron/Scheduler.kt` 顶层 | 必须含 `cronOutboundDispatcher` 字面值，类型签名含 `suspend` + `Boolean`（顶层注入点变量声明）。 | unit-scan | `SchedulerCronOutboundDispatcherWiringTest#TC-AGENT-033-e Scheduler exposes cronOutboundDispatcher injection point` 🟡 [SUPERSEDED-BY-035 runtime path; 保险栓保留] |
| TC-AGENT-033-f | R-AGENT-033 | 源码扫描：`Scheduler.kt::deliverResult` 函数体 | 必须含 `cronOutboundDispatcher` 引用 + `target.platform` + `target.chatId` 引用（按 platform+chatId 直投到 IM adapter）；**不**得含 `TODO: Route through Android platform adapters` 字面值（红线：原 stub TODO 必须移除）。 | unit-scan | `SchedulerCronOutboundDispatcherWiringTest#TC-AGENT-033-f deliverResult invokes dispatcher per target` 🟡 [SUPERSEDED-BY-035 runtime path; 源码层 true 但 Android runtime bypass，真实投递走 CronAgentRunner.deliver] |
| TC-AGENT-033-g | R-AGENT-033 | 源码扫描：`app/.../hermes/HermesGatewayController.kt` | 必须含 `suspend fun dispatchOutgoing(` 函数声明，参数列表含 `platform` + `chatId` + `text` + `threadId` 四处；函数体含 `deliveryRouter.getAdapter(` 调用 + `adapter.send(` 调用；含 Telegram 分支 `message_thread_id` 字面值。 | unit-scan | `HermesGatewayControllerDispatchOutgoingWiringTest#TC-AGENT-033-g dispatchOutgoing exposed and threads metadata` 🔴 |
| TC-AGENT-033-h | R-AGENT-033 | 源码扫描：`HermesGatewayController.kt` | 必须含 `cronOutboundDispatcher` 字面值至少 2 次（启动注入 set 为 lambda + stop 置空 null），证明回调生命周期管理对称。 | unit-scan | `HermesGatewayControllerDispatchOutgoingWiringTest#TC-AGENT-033-h dispatcher injected on start and cleared on stop` 🔴 |

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

---

## 域 AGENT — Cronjob Tool LLM Registration (R-AGENT-034)

R-AGENT-034 把 R-AGENT-031 已经接通底层 CRUD 的 `cronjob` 工具暴露给 LLM 工具表——`SystemToolPrompts.getAIAllCategoriesEn/Cn` 硬编码 4 个 category（basic/file/http/memory）漏了 cronjob，agent system prompt 提到工具但 OpenAI tools array 不下发 schema → dispatch 拒。本 R 改 2 个 app 模块文件接通注册。依赖 R-AGENT-033 已落地，否则 IM 触发场景仍是死信。

**架构合规**：只动 app 模块；executor 桥接 try/catch 包围，dispatch 失败必返结构化 ToolResult。

| TC ID | R-ID | 输入 / 触发 | 期望 | 测试类型 | 实现 / 状态 |
|---|---|---|---|---|---|
| TC-AGENT-034-a | R-AGENT-034 | 源码扫描：`app/.../core/config/SystemToolPrompts.kt` | `getAIAllCategoriesEn` 函数体或同 file 顶层含 `cronjob` 字面值；`getAIAllCategoriesCn` 函数体含 `cronjob` 字面值；文件含 `CRONJOB_SCHEMA` 引用（来自 hermes-android tools 包）或等价 schema-list 引用。 | unit-scan | `SystemToolPromptsCronjobWiringTest#TC-AGENT-034-a cronjob category appears in EN and CN tool registries` 🔴 |
| TC-AGENT-034-b | R-AGENT-034 | 源码扫描：`app/.../core/tools/ToolRegistration.kt::registerAllTools` 函数体 | 必须含 `"cronjob"` 字面值（registerTool name 参数）；必须含 `com.xiaomo.hermes.hermes.tools.cronjob` 引用 或 `CronjobTools.cronjob` 引用；含 `try {` + `catch` 包围 dispatch（异常不炸 handler）；含 `ToolResult(` 构造（异常路径返回结构化错误）。 | unit-scan | `CronjobToolRegistrationWiringTest#TC-AGENT-034-b ToolRegistration registers cronjob executor with try-catch` 🔴 |
| TC-AGENT-034-c | R-AGENT-034 | 源码扫描（红线）：`SystemToolPrompts.kt` 既有 4 个 category 不被误删 | `getAIAllCategoriesEn` / `getAIAllCategoriesCn` 函数体仍含 `basicTools` + `fileSystemTools` + `httpTools` + `memoryTools` 四个变量名（或既有 ToolCategory 引用），证本 R 是**追加**而非替换。 | unit-scan | `SystemToolPromptsCronjobWiringTest#TC-AGENT-034-c existing 4 categories preserved` 🔴 |
| TC-AGENT-034-d | R-AGENT-034 | 端到端验证：飞书 bot 跟 agent 说"每 15 分钟提醒我喝水" | agent 真调 `cronjob(action="create", ...)` 成功（不再回"工具不可用"）；16 分钟后飞书原会话收到 ai 消息（证 R-AGENT-033+034 闭环）；同步验 `cronjob(action="list")` / `remove`。**Deferred to §3 E2E + 手测**。 | manual / E2E | `(no unit test; manual verification required)` 🔴 |

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

---

## 域 AGENT — Cron Tick Real Path Origin Delivery (R-AGENT-035)

R-AGENT-035 是 R-AGENT-033 落地后端到端测试发现的修补：R-AGENT-033 把 `cronOutboundDispatcher` 注入到 `Scheduler.kt::deliverResult`，但 Android 实际 cron tick 走 `CronTickWorker` → `CronAgentRunner.run()` → `CronAgentRunner.deliver()`，**完全 bypass** `Scheduler.deliverResult`（Scheduler.kt 头注释 line 6-8 已写明），导致 dispatcher 永不触达，飞书端永远收不到 cron 消息。本 R 把 origin → IM 投递分支搬到 `CronAgentRunner.deliver()`，复用 R-AGENT-033 已建好的 `HermesGatewayController.dispatchOutgoing` 链。

**架构合规**：只动 1 个 app 模块文件 `CronAgentRunner.kt`；不破坏 R-AGENT-033 已落地的 4.1/4.3/4.4/4.5 改动；保留 `Scheduler.cronOutboundDispatcher` 作 Python 1:1 parity + 保险栓。

**TC-AGENT-033-c/d/e/f 处理**：原断言"`Scheduler.deliverResult` 含 dispatcher 调用"在源码层为 true 但运行时不可达。**不删 TC**（ID 不回收），在表格状态列里加 `[SUPERSEDED-BY-035 runtime path]` 标注；真正的 runtime 投递断言转移到 TC-AGENT-035-a..d。`SchedulerCronOutboundDispatcherWiringTest` 不删（保险栓还在）。

| TC ID | R-ID | 输入 / 触发 | 期望 | 测试类型 | 实现 / 状态 |
|---|---|---|---|---|---|
| TC-AGENT-035-a | R-AGENT-035 | 源码扫描：`app/.../core/cron/CronAgentRunner.kt::deliver` 函数体 | 必须能读 `job["origin"]`（含 `"origin"` 字面值或 job 参数引用）+ `job["deliver"]`（`"deliver"` 字面值）；必须含 `"local"` 和 `"origin"` 两个 deliver 模式字面值。 | unit-scan | `CronAgentRunnerOriginDeliveryWiringTest#TC-AGENT-035-a deliver reads origin and deliver fields` 🔴 |
| TC-AGENT-035-b | R-AGENT-035 | 源码扫描：`CronAgentRunner.kt` 整文件 | 必须含 `HermesGatewayController` reference + `dispatchOutgoing(` 调用（origin 路径委托给 R-AGENT-033 已建的 IM 投递桥）。 | unit-scan | `CronAgentRunnerOriginDeliveryWiringTest#TC-AGENT-035-b deliver invokes HermesGatewayController dispatchOutgoing` 🔴 |
| TC-AGENT-035-c | R-AGENT-035 | 源码扫描（红线）：`CronAgentRunner.kt::deliver` 函数体保留 ChatHistoryManager fallback | 函数体仍含 `ChatHistoryManager` reference + `addMessage(` 调用 + `GatewayChatEventBus` reference；证本 R 是**新增 origin 分支**而非删除本地 chat 写入路径（用户即便用 IM 也能在 app 里看到记录）。 | unit-scan | `CronAgentRunnerOriginDeliveryWiringTest#TC-AGENT-035-c local fallback path preserved` 🔴 |
| TC-AGENT-035-d | R-AGENT-035 | 端到端验证：飞书 bot 跟 agent 说"每 15 分钟提醒我喝水" → 16 分钟后 | jobs.json 内对应 job 含 `origin = {platform: "feishu", chat_id: ..., thread_id: ...}` + `deliver = "origin"`；GatewayFileLogger 内出现 `dispatchOutgoing: delivered platform=feishu chatId=... len=...` INFO 行；**飞书原会话真的收到 ai 消息**（这是 R-AGENT-033 + R-AGENT-034 + R-AGENT-035 三 R 闭环的最终验收）。 | manual / E2E | `(no unit test; manual verification required)` 🔴 |

## 域 AGENT — Steer Interface (R-AGENT-036)

R-AGENT-036 给 `HermesAgentLoop` 加 `steer()` 接口和 `_pendingSteer` 字段，作为 P4 插话功能的内核。对齐 Python `run_agent.py:945-953, 3608-3658, 3660-3721, 3599-3606`。本 R **只加接口**，6 个消费点在 R-AGENT-037。

**注入语义**：`steer(text)` 把文本暂存在 `_pendingSteer`；下游某个 drain 点从最近的 `role:"tool"` 消息 content 末尾追加 `"\n\nUser guidance: {text}"`。**不**插入新的 user message（保持 OpenAI message-role alternation 不破）。

| TC ID | R-ID | 输入 / 触发 | 期望 | 测试类型 | 实现 / 状态 |
|---|---|---|---|---|---|
| TC-AGENT-036-a | R-AGENT-036 | 调 `loop.steer("")` 和 `loop.steer("   ")` | 都返 `false`；`_drainPendingSteer()` 返 null（pending 不变） | unit | `HermesAgentLoopSteerTest#TC-AGENT-036-a empty or whitespace steer is rejected` 🟢 |
| TC-AGENT-036-b | R-AGENT-036 | `loop.steer("hello")` | 返 `true`；`_drainPendingSteer()` 返 `"hello"` | unit | `HermesAgentLoopSteerTest#TC-AGENT-036-b basic steer stores trimmed text` 🟢 |
| TC-AGENT-036-c | R-AGENT-036 | 连续 `loop.steer("a"); loop.steer("b"); loop.steer("c")` | `_drainPendingSteer()` 返 `"a\nb\nc"` | unit | `HermesAgentLoopSteerTest#TC-AGENT-036-c multiple steers concatenate with newline` 🟢 |
| TC-AGENT-036-d | R-AGENT-036 | `loop.steer("x")` 后连续两次调 `_drainPendingSteer()` | 第一次返 `"x"`，第二次返 null（原子读清空） | unit | `HermesAgentLoopSteerTest#TC-AGENT-036-d drain returns and clears atomically` 🟢 |
| TC-AGENT-036-e | R-AGENT-036 | 多线程并发 `loop.steer(text_i)` 100 次（不同 text） | drain 出来的合并文本含全部 100 个 text（不丢字符不交错）；行数 = 100 | unit (multithread) | `HermesAgentLoopSteerTest#TC-AGENT-036-e concurrent steer is thread-safe` 🟢 |
| TC-AGENT-036-f | R-AGENT-036 | 构造 messages = `[{role:"user"...}, {role:"assistant", tool_calls:[...]}, {role:"tool", content:"r1"}]`；`loop.steer("hi")`；调 `loop._applyPendingSteerToToolResults(messages, 1)` | `messages[2]["content"] == "r1\n\nUser guidance: hi"` | unit | `HermesAgentLoopSteerTest#TC-AGENT-036-f apply injects to last role tool with marker` 🟢 |
| TC-AGENT-036-g | R-AGENT-036 | tail 内**无** `role:"tool"` 消息（如全 user/assistant）；`loop.steer("hi")`；调 `_applyPendingSteerToToolResults(messages, 0)` | messages 不被修改；`_drainPendingSteer()` 返 `"hi"`（回填） | unit | `HermesAgentLoopSteerTest#TC-AGENT-036-g apply with no tool tail re-stashes text` 🟢 |
| TC-AGENT-036-h | R-AGENT-036 | tool message content 是 `List<Map>`（多模态 block）：`[{type:"text", text:"r1"}]`；`loop.steer("hi")`；调 `_applyPendingSteerToToolResults(messages, 1)` | tool message content 仍为 List；新增了一个 `{type:"text", text:"User guidance: hi"}` block | unit | `HermesAgentLoopSteerTest#TC-AGENT-036-h apply preserves multimodal content blocks` 🟢 |
| TC-AGENT-036-i | R-AGENT-036 | `loop.steer("x")` 后调 `loop.clearPendingSteer()` | `_drainPendingSteer()` 返 null（hard cancel 清掉 pending steer） | unit | `HermesAgentLoopSteerTest#TC-AGENT-036-i clearPendingSteer drops pending text` 🟢 |

## 域 AGENT — Steer Loop Consumption Points (R-AGENT-037)

R-AGENT-037 把 R-AGENT-036 的 `_applyPendingSteerToToolResults` / `_drainPendingSteer` 接到 turn-loop 的 4 个消费点：per-tool drain、post-batch drain、pre-API-call drain、leftover handoff（via `AgentResult.pendingSteer`）。对齐 Python `run_agent.py:8029, 8040, 8397, 8432, 9032, 11828`（6 点 → Kotlin 4 点合并）。

| ID | 来源 | 输入 | 期望输出 | 类型 | 状态 |
|---|---|---|---|---|---|
| TC-AGENT-037-a | R-AGENT-037 | fake server 第 1 turn 返 1 个 tool_call；fake dispatcher 在执行 tool 前 `loop.steer("hi")`；第 2 turn 返 final response | per-tool drain 落地：messages 中第一个 `role:"tool"` 的 content 末尾含 `"\n\nUser guidance: hi"`；`AgentResult.pendingSteer` == null | unit | `HermesAgentLoopSteerLoopTest#TC-AGENT-037-a per-tool drain injects steer mid-batch` 🟢 |
| TC-AGENT-037-b | R-AGENT-037 | 源码扫描：`AgentLoop.kt` 内 tool 派发循环必须同时含 per-tool drain (`_applyPendingSteerToToolResults(messages, 1)` 在 `for (prep in preps)` 循环体内) 和 post-batch drain (`_applyPendingSteerToToolResults(messages, preps.size)` 在循环外，且被 `preps.isNotEmpty()` 守卫) | 两个 drain 调用都存在；纯单测无法精确驱动"per-tool 全空 + post-batch 才捕获"的瞬时窗口（无 yield 点 + 并行派发 timing 不确定），post-batch drain 是冗余 safety net，对齐 Python 8040-8045 + 8432-8436 即可 | unit-scan | `HermesAgentLoopSteerLoopTest#TC-AGENT-037-b post-batch drain catches late steer` 🟢 |
| TC-AGENT-037-c | R-AGENT-037 | fake server 第 1 turn 返 1 个 tool；fake dispatcher 在 tool 跑完之后但 server 第 2 次 chatCompletion 之前 `loop.steer("pre-api")`；第 2 turn 返 final | pre-API-call drain 落地：第 2 turn 进 chatCompletion 前 messages 中最后一个 `role:"tool"` 的 content 末尾含 `"\n\nUser guidance: pre-api"`；`pendingSteer` == null | unit | `HermesAgentLoopSteerLoopTest#TC-AGENT-037-c pre-API-call drain injects before next chatCompletion` 🟢 |
| TC-AGENT-037-d | R-AGENT-037 | fake server 第 1 turn 直接返 final response（无 tool_calls）；run() 完成前 `loop.steer("orphan")` | 所有 turn 跑完时 messages 中无 `role:"tool"`，无地方 inject；`AgentResult.pendingSteer == "orphan"`（leftover handoff，对齐 Python `:11828-11833`） | unit | `HermesAgentLoopSteerLoopTest#TC-AGENT-037-d leftover steer surfaces in pendingSteer` 🟢 |
| TC-AGENT-037-e | R-AGENT-037 | fake server 第 1 turn 返 1 个 tool；fake dispatcher 在 tool 跑完前 `loop.steer("doomed")` + 立刻 `loop.clearPendingSteer()`；第 2 turn 返 final | hard-cancel 清空：messages 任何 `role:"tool"` content 都不含 `"User guidance: doomed"`；`pendingSteer` == null（对齐 Python hard-interrupt 路径，clear 优先于所有 drain） | unit | `HermesAgentLoopSteerLoopTest#TC-AGENT-037-e clearPendingSteer beats all drain points` 🟢 |

跑已落地 TC：

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

---

## 域 AGENT — Cron Immediate Trigger (R-AGENT-043)

R-AGENT-043 给 cron 子系统加"即时触发"路径——绕过 `CronTickWorker` 的 15min WorkManager 周期，让 agent 通过 `cronjob(action="run")` 工具或 UI 端的"立即触发"按钮在当前进程内立刻把指定 job 跑起来。架构对齐 R-AGENT-033 已立的 `cronOutboundDispatcher` 闭包注入模式（`hermes-android` 单向依赖 `app` 模块时通过 lambda 注入跨模块）。Python 上游对应 `cron/scheduler.py:702 run_job(job)` 的同步进程内执行语义。

**架构合规要点**：
- `Scheduler.kt` 加 `@Volatile var cronImmediateRunner: (suspend (job: Map<String, Any?>) -> Unit)?`（与 L82-84 `cronOutboundDispatcher` 同形态）
- `CronjobTools.kt` `"run", "run_now", "trigger"` 分支调注入的 runner 而不是只更新 `next_run_at`；JSON 返回里加 `triggered_immediately: true/false`
- 模块级 `_immediateTriggerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`，`launch` fire-and-forget（agent 工具调用不能 block 在 cron job 全部跑完）
- `OperitApplication.onCreate` 在 `CronTickWorker.enqueue(this)` 之后注入 `Scheduler.cronImmediateRunner = { job -> CronAgentRunner.run(applicationContext, job) }`

| TC ID | R-ID | 输入 / 触发 | 期望 | 测试类型 | 实现 / 状态 |
|---|---|---|---|---|---|
| TC-AGENT-043-a | R-AGENT-043 | 源码扫描 `Scheduler.kt` | 文件包含 `var cronImmediateRunner` 声明，类型形态为 `(suspend (job: Map<String, Any?>) -> Unit)?`，含 `@Volatile`，初值 `null`（与 `cronOutboundDispatcher` 平行） | unit (源码扫描) | `SchedulerImmediateRunnerTest#TC-AGENT-043-a cronImmediateRunner field declared` 🔴 |
| TC-AGENT-043-b | R-AGENT-043 | 源码扫描 `CronjobTools.kt` | 文件含 `_immediateTriggerScope`（模块级 `CoroutineScope`）+ `cronImmediateRunner` 引用 + `_immediateTriggerScope.launch` 调用 + `triggered_immediately` JSON key；都出现在 `"run", "run_now", "trigger"` 分支内 | unit (源码扫描) | `CronjobToolsImmediateTriggerTest#TC-AGENT-043-b run branch invokes runner via scope` 🔴 |
| TC-AGENT-043-c | R-AGENT-043 | 源码扫描 `OperitApplication.kt` | 文件含 `Scheduler.cronImmediateRunner = ` 赋值，lambda body 含 `CronAgentRunner.run(applicationContext, ` 调用；赋值出现在 `CronTickWorker.enqueue(this)` 之后（保证 worker 已 enqueue 再注入 immediate path） | unit (源码扫描) | `OperitApplicationCronInjectionTest#TC-AGENT-043-c immediate runner injected after worker enqueue` 🔴 |
| TC-AGENT-043-d | R-AGENT-043 | 设 `Scheduler.cronImmediateRunner = recordingLambda`；先 `addJob(...)` 写入测试 job；调 `cronjobToolImpl(action="run", job_id=<id>)` | recordingLambda 被调 1 次（job map 与 addJob 写入的内容一致）；返回 JSON 含 `"triggered_immediately": true`；`Jobs` 表里 job `next_run_at` 仍按 `triggerJob` 行为被推前到 now（对齐 Python `cron/scheduler.py:702`） | unit | `CronjobToolsImmediateTriggerTest#TC-AGENT-043-d run action invokes injected runner` 🔴 |
| TC-AGENT-043-e | R-AGENT-043 | `Scheduler.cronImmediateRunner = null`（注入未发生）；`addJob(...)`；调 `cronjobToolImpl(action="run", job_id=<id>)` | 不抛异常；返回 JSON `"triggered_immediately": false`；`success: true`（仅落 `triggerJob` 走 next-tick 路径作为兜底） | unit | `CronjobToolsImmediateTriggerTest#TC-AGENT-043-e run action without runner falls back gracefully` 🔴 |
| TC-AGENT-043-f | R-AGENT-043 | `Scheduler.cronImmediateRunner = { throw RuntimeException("boom") }`；调 `cronjobToolImpl(action="run", job_id=<id>)` | runner 抛异常**不**冒泡到工具调用方（`_immediateTriggerScope.launch` fire-and-forget 隔离）；工具返回 `"triggered_immediately": true` + `success: true`（agent 看到的是触发成功，runner 异常由 `CronAgentRunner.run` 内部 try/catch + `markJobRun(... success=false)` 处理） | unit | `CronjobToolsImmediateTriggerTest#TC-AGENT-043-f runner exception does not propagate` 🔴 |
| TC-AGENT-043-g | R-AGENT-043 | 源码扫描 `CronjobTools.kt` 的 `CRONJOB_SCHEMA` description + `action` 参数 description | schema description 必须明示 `action='run'` 是即时触发（不等 15min worker tick）+ 列出 `run_now`/`trigger` 同义词 + 提及 `triggered_immediately` 返回字段（让 agent 通过 schema 即可完整推导新能力，不依赖外部文档） | unit (源码扫描) | `CronjobToolsImmediateTriggerTest#TC-AGENT-043-g schema advertises immediate trigger semantics` 🔴 |

跑已落地 TC：

```bash
./gradlew :hermes-android:testDebugUnitTest --tests "com.xiaomo.hermes.hermes.cron.SchedulerImmediateRunnerTest"
./gradlew :hermes-android:testDebugUnitTest --tests "com.xiaomo.hermes.hermes.tools.CronjobToolsImmediateTriggerTest"
./gradlew :app:testDebugUnitTest --tests "com.ai.assistance.operit.core.application.OperitApplicationCronInjectionTest"
```

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

---

## 域 AGENT — Cron Self-Diagnostic Tool (R-AGENT-044)

R-AGENT-044 给 cron 子系统加自检工具 `cronjob(action="health")`，让 agent 主动诊断 worker 是否存活、是否 enqueue 失败、最近 N 次 tick 时间、有没有 due 但未跑的任务、immediate runner 是否被注入。Bug A（KEEP→UPDATE+throw）虽然能让 enqueue 失败时崩出来，但 agent 端缺一个"我现在能不能用 cron"的查询入口；本 R 是观测面（observability），与 R-AGENT-031 的执行面互补。无 Python 上游对应（Android 平台特有的诊断面，对接 WorkManager）。

| TC ID | R-ID | 输入 | 期望输出 | 类型 | 状态 |
|---|---|---|---|---|---|
| TC-AGENT-044-a | R-AGENT-044 | 源码扫描 `CronjobTools.kt` `CRONJOB_SCHEMA` `action` enum | enum 含 `"health"`；schema description 列出 `health` 返回的所有字段（`worker_registered` / `worker_state` / `last_tick_at` / `next_scheduled_at` / `pending_due_jobs` / `recent_runs` / `immediate_runner_wired` / `enqueue_last_error`） | unit (源码扫描) | `CronjobToolsHealthTest#TC-AGENT-044-a schema advertises health action and payload fields` 🟢 |
| TC-AGENT-044-b | R-AGENT-044 | 源码扫描 `cronjob()` 的 `"health" -> {...}` when-branch | branch 引用 `cronHealthProbe`，emit `worker_registered` / `worker_state` / `next_scheduled_at` 字段，`success=true` | unit (源码扫描) | `CronjobToolsHealthTest#TC-AGENT-044-b health branch invokes cronHealthProbe and merges worker fields` 🟢 |
| TC-AGENT-044-c | R-AGENT-044 | 源码扫描 health when-branch 的 enqueue 错误处理 | branch 读取 `last_enqueue_error`，emit `enqueue_last_error`，对 probe=null 走 MISSING 路径（含 null-check） | unit (源码扫描) | `CronjobToolsHealthTest#TC-AGENT-044-c health branch surfaces enqueue_last_error when probe reports failure` 🟢 |
| TC-AGENT-044-d | R-AGENT-044 | 源码扫描 health when-branch 的 jobs 聚合逻辑 | branch 调 `listJobs(`，引用 `next_run_at`，过滤 `state` / `enabled`，emit `pending_due_jobs` + `recent_runs` | unit (源码扫描) | `CronjobToolsHealthTest#TC-AGENT-044-d health branch builds pending_due_jobs from listJobs filtered + sorted` 🟢 |
| TC-AGENT-044-e | R-AGENT-044 | 源码扫描 health when-branch 的 immediate runner 探测 | branch 引用 `cronImmediateRunner`，emit `immediate_runner_wired` 含非 null 检查，**不**硬编码 true | unit (源码扫描) | `CronjobToolsHealthTest#TC-AGENT-044-e health branch reflects cronImmediateRunner state` 🟢 |

跑已落地 TC：

```bash
./gradlew :hermes-android:testDebugUnitTest --tests "com.xiaomo.hermes.hermes.tools.CronjobToolsHealthTest"
```

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

---

## 域 AGENT — App-Chat Origin for Cron (R-AGENT-045)

R-AGENT-045 把 cron 的"来源会话"（`origin_platform` / `origin_chat_id`）从只支持 gateway-platform（Telegram/Weixin/...）扩展到也支持 in-app chat（`origin_platform="app"` + `origin_chat_id=<chatId>`）。当前 `CronAgentRunner.deliver()` 只走 `HermesGatewayController.send`，对 `app` origin 没分支，导致 in-app 注册的 cron 任务跑完后回复无法回到原 chat。本 R 把 `app` 平台落地为：cron 触发时通过 `R-AGENT-033` 的 `HERMES_SESSION_*` ThreadLocal 把 chat_id 透传给 agent loop，agent 回复直接走 `ChatViewModel` 的 in-app pipeline，与用户主动发消息的路径一致。Python 上游无对应（Android 特有，对接 in-app chat session）。

| TC ID | R-ID | 输入 | 期望输出 | 类型 | 状态 |
|---|---|---|---|---|---|
| TC-AGENT-045-a | R-AGENT-045 | 源码扫描 `ExternalChatRequestExecutor.kt::execute()` | 函数体在调 agent loop 之前必须 `setSessionVars(platform = "app", ...)` + `setCronAutoDeliverVars(platform = "app", ...)`（R-AGENT-033 ThreadLocal API），import 4 件套；finally 块调 `clearSessionVars()` + `clearCronAutoDeliverVars()` | unit (源码扫描) | `ExternalChatRequestExecutorSessionVarsTest#TC-AGENT-045-a app-chat sets session vars before agent loop` 🟢 |
| TC-AGENT-045-b | R-AGENT-045 | 源码扫描 `CronAgentRunner.kt::deliver()` | `originPlatform == "app"` 短路必须出现在 `gateway.dispatchOutgoing(` 调用之前；in-app origin 不进 IM 派发路径，避免 `dispatchOutgoing` 返回 false → 抛 IllegalStateException | unit (源码扫描) | `CronAgentRunnerAppOriginTest#TC-AGENT-045-b deliver short-circuits IM dispatch when origin platform is app` 🟢 |
| TC-AGENT-045-c | R-AGENT-045 | 源码扫描 `deliver()` 顶部 | `writeLocalChatNote(` 必须无条件出现在第一个 `if (` 之前 —— 所有 origin 类型（含 app）都要写本地 chat note，cron 输出可在 in-app chat 历史看到 | unit (源码扫描) | `CronAgentRunnerAppOriginTest#TC-AGENT-045-c app origin still writes local chat note` 🟢 |
| TC-AGENT-045-d | R-AGENT-045 | 源码扫描 `deliver()` null origin 处理 | 含 `origin != null` null check + `if (!originMatched)` 短路 return —— 兼容旧 jobs.json 没 origin 字段的记录 | unit (源码扫描) | `CronAgentRunnerAppOriginTest#TC-AGENT-045-d deliver gracefully handles null origin` 🟢 |
| TC-AGENT-045-e | R-AGENT-045 | 源码扫描 `deliver()` non-app origin 路径 | `gateway.dispatchOutgoing(` 调用必须保留；app 短路必须 `originPlatform == "app"` 形式（specific），不是无条件 return —— telegram/weixin 等仍走 IM | unit (源码扫描) | `CronAgentRunnerAppOriginTest#TC-AGENT-045-e non-app origin still routes via gateway dispatchOutgoing` 🟢 |
| TC-AGENT-045-f | R-AGENT-045 | ThreadLocal 设 `(platform="app", chat_id="chat-123")`；调 `_originFromEnv()` | 返回 `mapOf("platform"="app", "chat_id"="chat-123", "chat_name"=...)`；`thread_id=""` 被识别为 null —— R-AGENT-033 ThreadLocal 路径透传 app sentinel | unit (behavior) | `CronjobToolsAppOriginTest#TC-AGENT-045-f originFromEnv reads app session origin from ThreadLocal` 🟢 |

跑已落地 TC：

```bash
./gradlew :app:testDebugUnitTest --tests "com.ai.assistance.operit.external.chat.ExternalChatRequestExecutorSessionVarsTest"
./gradlew :hermes-android:testDebugUnitTest --tests "com.xiaomo.hermes.hermes.cron.CronAgentRunnerAppOriginTest"
./gradlew :hermes-android:testDebugUnitTest --tests "com.xiaomo.hermes.hermes.tools.CronjobToolsAppOriginTest"
```

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

---

## 域 GATEWAY — Busy-Input Mode (R-GATEWAY-035)

R-GATEWAY-035 给 `GatewayRunner` 加 `_busyInputMode: String` 字段（默认 `"interrupt"`，可切 `"queue"`），并把现有 `queueDuringDrainEnabled()` 实现切到读这个字段。对齐 Python `gateway/run.py:608, 631, 1230-1231, 1389-1402`。本 R **只做字段加载 + getter 暴露 + queueDuringDrainEnabled 重写**——drain 路径完整 reject/queue 行为在 R-GATEWAY-037。

| TC ID | R-ID | 输入 | 期望输出 | 类型 | 状态 |
|---|---|---|---|---|---|
| TC-GATEWAY-035-a | R-GATEWAY-035 | 既无 env `HERMES_GATEWAY_BUSY_INPUT_MODE`，也无 `config.extra["busy_input_mode"]`，构造 `GatewayRunner` | `busyInputMode() == "interrupt"`；`queueDuringDrainEnabled() == false` | unit | `GatewayBusyInputModeTest#TC-GATEWAY-035-a default mode is interrupt` 🟢 |
| TC-GATEWAY-035-b | R-GATEWAY-035 | `config.extra["busy_input_mode"] = "queue"`，无 env，构造 runner | `busyInputMode() == "queue"`；`queueDuringDrainEnabled() == true` | unit | `GatewayBusyInputModeTest#TC-GATEWAY-035-b config queue flips mode` 🟢 |
| TC-GATEWAY-035-c | R-GATEWAY-035 | env `HERMES_GATEWAY_BUSY_INPUT_MODE=queue`，且 `config.extra["busy_input_mode"]="interrupt"` | env 优先：`busyInputMode() == "queue"`；`queueDuringDrainEnabled() == true` | unit | `GatewayBusyInputModeTest#TC-GATEWAY-035-c env overrides config` 🟢 |
| TC-GATEWAY-035-d | R-GATEWAY-035 | 非法值（`config.extra["busy_input_mode"] = "INVALID"` / `"interrupt"` / 大写 `"QUEUE"`、其它 `"foo"`） | 非 `"queue"`（lowercase trim 后） → 全部归类为 `"interrupt"`；`"QUEUE"` 大写 → trim+lowercase 后命中 `"queue"` 切档 | unit | `GatewayBusyInputModeTest#TC-GATEWAY-035-d only literal queue flips mode` 🟢 |

跑已落地 TC：

```bash
./gradlew :hermes-android:testDebugUnitTest --tests "com.xiaomo.hermes.hermes.gateway.GatewayBusyInputModeTest"
```

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

---

## 域 GATEWAY — Command Routing (R-GATEWAY-036)

R-GATEWAY-036 在 active session busy 期间识别 slash 命令。最小骨架：3 个直接服务于"插话功能"的命令（`/steer`、`/queue`、`/stop`）走具体路径；其它 11 个 `ACTIVE_SESSION_BYPASS_COMMANDS` 命令礼貌拒绝；非命令文本 fall-through 到既有 busy 路径。对齐 Python `hermes_cli/commands.py:267-284` 与 `gateway/run.py:3225-3395`。

| TC-ID | 关联 R | 输入 | 期望输出 | 测试类型 | 状态 |
|---|---|---|---|---|---|
| TC-GATEWAY-036-a | R-GATEWAY-036 | `resolveCommand("/steer hello world")` | 返 `("steer", "hello world")` | unit | `GatewayCommandRoutingTest#TC-GATEWAY-036-a resolveCommand parses slash plus args` 🟢 |
| TC-GATEWAY-036-b | R-GATEWAY-036 | `resolveCommand("hello /steer x")` / `resolveCommand("")` / `resolveCommand("/unknownCmd")` | 全部返 null（不以 `/` 开头 / 空 / 不在白名单） | unit | `GatewayCommandRoutingTest#TC-GATEWAY-036-b resolveCommand rejects non-bypass tokens` 🟢 |
| TC-GATEWAY-036-c | R-GATEWAY-036 | `resolveCommand("  /STEER   Hello  ")`（前导空白 + 大写 + 多空白） | 返 `("steer", "Hello")`（lowercase + trim） | unit | `GatewayCommandRoutingTest#TC-GATEWAY-036-c resolveCommand normalizes case and whitespace` 🟢 |
| TC-GATEWAY-036-d | R-GATEWAY-036 | active busy session 收到 `/steer 加个限制`，回调 `steerActiveAgent` 返 true | 调 `steerActiveAgent(sessionKey, "加个限制")` 一次；不入 `_pendingEvents`；不打断 | unit | `GatewayCommandRoutingTest#TC-GATEWAY-036-d steer dispatches to callback` 🟢 |
| TC-GATEWAY-036-e | R-GATEWAY-036 | active busy session 收到 `/queue 待会儿处理` | 入 `_pendingEvents`；不调 `steerActiveAgent`；不调 `cancelActiveAgent` | unit | `GatewayCommandRoutingTest#TC-GATEWAY-036-e queue merges into pending events` 🟢 |
| TC-GATEWAY-036-f | R-GATEWAY-036 | active busy session 收到 `/stop`，回调 `cancelActiveAgent` 返 true | 调 `cancelActiveAgent(sessionKey)` 一次；不入 `_pendingEvents` | unit | `GatewayCommandRoutingTest#TC-GATEWAY-036-f stop dispatches to callback` 🟢 |
| TC-GATEWAY-036-g | R-GATEWAY-036 | active busy session 收到 `/agents`（已识别但本 R 未实现） | 不调 steer/cancel；不入 `_pendingEvents`；不打断（礼貌拒绝） | unit | `GatewayCommandRoutingTest#TC-GATEWAY-036-g unhandled bypass commands are rejected` 🟢 |

跑已落地 TC：

```bash
./gradlew :hermes-android:testDebugUnitTest --tests "com.xiaomo.hermes.hermes.gateway.GatewayCommandRoutingTest"
```

## 域 GATEWAY — Drain Reject/Queue (R-GATEWAY-037)

R-GATEWAY-037 把 R-GATEWAY-035 漏掉的 `_restart_requested` 守卫补齐（Python `gateway/run.py:1230-1231`），并在 `_handleMessage` busy 分支顶部加 drain 检查（`:1515-1533`）。drain 期间：restart + mode=queue 走队列接力；其它走 reject ack。命令路由（R-036）让位给 drain ack——agent 即将停掉时 `/steer` 等命令无意义。

| TC-ID | 关联 R | 输入 | 期望输出 | 测试类型 | 状态 |
|---|---|---|---|---|---|
| TC-GATEWAY-037-a | R-GATEWAY-037 | `_draining=true && _restartRequested=true && _busyInputMode="queue"` 时 busy session 收新消息 | 入 `_pendingEvents`；ack 含 "restarting" + "queued for the next turn"；不调命令路由；不调 steer/cancel | unit | `GatewayDrainBehaviorTest#TC-GATEWAY-037-a restart with queue mode queues and acks` 🟢 |
| TC-GATEWAY-037-b | R-GATEWAY-037 | `_draining=true && _restartRequested=false && _busyInputMode="queue"` 时 busy session 收新消息（普通 stop 不接力） | **不**入 `_pendingEvents`；ack 含 "shutting down" + "not accepting"；不调命令路由 | unit | `GatewayDrainBehaviorTest#TC-GATEWAY-037-b plain stop with queue mode rejects` 🟢 |
| TC-GATEWAY-037-c | R-GATEWAY-037 | `_draining=true && _restartRequested=true && _busyInputMode="interrupt"` 时 busy session 收新消息 | 不入 `_pendingEvents`；ack 含 "restarting" + "not accepting"；不调命令路由 | unit | `GatewayDrainBehaviorTest#TC-GATEWAY-037-c restart with interrupt mode rejects` 🟢 |
| TC-GATEWAY-037-d | R-GATEWAY-037 | `_draining=false` 时 busy session 收 `/steer hi` | drain 检查 fall through，命令路由生效（与 R-036 行为一致）；调 steer callback | unit | `GatewayDrainBehaviorTest#TC-GATEWAY-037-d non draining lets command routing work` 🟢 |
| TC-GATEWAY-037-e | R-GATEWAY-037 | `queueDuringDrainEnabled()` 矩阵：(restart, mode) ∈ {(F,interrupt), (F,queue), (T,interrupt), (T,queue)} | 仅 (T, "queue") → true；其它三组 → false | unit | `GatewayDrainBehaviorTest#TC-GATEWAY-037-e queueDuringDrainEnabled requires both flags` 🟢 |

跑已落地 TC：

```bash
./gradlew :hermes-android:testDebugUnitTest --tests "com.xiaomo.hermes.hermes.gateway.GatewayDrainBehaviorTest"
```

## 域 GATEWAY — Busy Default Path Replay (R-GATEWAY-038)

R-GATEWAY-038 守住"agent busy 时收到非命令文本 → 当前 turn 通过 `INTERRUPTED_SENTINEL` abort → 新消息作为下一 turn user input replay 出去"这条端到端语义。Kotlin 早已实现（`Run.kt:328-356` default busy 分支 + `Run.kt:596-688` pending-event 循环），过去被 R-GW-001 / R-GW-011 / R-036 / R-037 各切一刀，本 R 把它当显式对齐守护点立项，让任何后续重构动这两段时第一时间被测试守护拉住。**无生产代码改动**，纯测试守护。

| TC-ID | 关联 R | 输入 | 期望输出 | 测试类型 | 状态 |
|---|---|---|---|---|---|
| TC-GATEWAY-038-a | R-GATEWAY-038 | session 已 busy（`_processingSessions` 已含 sessionKey）+ 收到非命令文本"二号消息"；agentRunner 第一次返回 `INTERRUPTED_SENTINEL`，第二次返回正常 reply | agentRunner 被调 2 次；第二次 `text` 参数 = "二号消息"；adapter 收到 1 条最终 reply（content=正常 reply、replyTo=二号消息的 message_id）；`_pendingEvents` 最终为空 | unit | `GatewayBusyPendingReplayTest#TC-GATEWAY-038-a busy non-command text aborts current turn and replays as next turn user input` 🟢 |
| TC-GATEWAY-038-b | R-GATEWAY-038 | busy 期间 `mergePendingMessageEvent` 入队 + `_interruptFlags[key].set(true)` 路径完整存在（源码扫描守住） | `Run.kt` busy 分支必须包含 `mergePendingMessageEvent(_pendingEvents` + `_interruptFlags[event.sessionKey]?.set(true)` + `_sendBusyAck(event)` 三行，且全部位于 drain 检查与命令路由之后、return 之前 | unit (源码扫描) | `GatewayBusyPendingReplayTest#TC-GATEWAY-038-b busy default branch wires pending+interrupt+ack` 🟢 |
| TC-GATEWAY-038-c | R-GATEWAY-038 | pending-event 循环：连续 5 条 busy 消息（每次 agentRunner 返 `INTERRUPTED_SENTINEL` 并塞下一条 pending） | agentRunner 总调用次数被封顶 = 1 (initial) + `MAX_INTERRUPT_DEPTH=3` = 4；finally 块清空 `_pendingEvents`（深度溢出消息直接丢，不残留供重启复活，对齐 Python `gateway/run.py:601-604`） | unit | `GatewayBusyPendingReplayTest#TC-GATEWAY-038-c pending replay caps at MAX_INTERRUPT_DEPTH` 🟢 |
| TC-GATEWAY-038-d | R-GATEWAY-038 | Weixin adapter `_runPollLoop` 单轮拿到 N 条入站 msg：busy default 分支只有在 adapter 把 inbound dispatch 放到独立协程时才能触发；源码扫描守住 `Weixin.kt` 的 inbound 派发**不得**直接 await `_handleInbound` / `handleMessage`，必须经 `scope.launch` 或 per-chat `Channel` 解耦 | `Weixin.kt` 内 `_runPollLoop` 取出 `msgs` 数组后，对每条消息的 dispatch 必须出现 `_queueForProcessing` / `scope.launch` / `Channel` 任一并发结构关键字；不得在 for 循环里直接 `_handleInbound(msg)` 同栈 await（与 `Telegram.kt:528-547` per-chat `Channel` 模型 + `Feishu.kt:782-795` `scope.launch` 模型保持契约一致） | unit (源码扫描) | `WeixinPollDispatchTest#TC-GATEWAY-038-d weixin inbound dispatch decouples from poll loop` 🟢 |
| TC-GATEWAY-038-e | R-GATEWAY-038 | Weixin adapter 同一 `from_user_id` 连发两条 inbound msg：第一条对应的 `messageHandler` 协程被人为阻塞 1.5s；运行时验证第二条 `messageHandler` 在第一条**仍未释放前**就已经被进入 | 两次 `messageHandler` 进入时间差 < 200ms（窗口内并发派发）；不是"等第一条完成才派第二条"的串行行为；这是 R-GATEWAY-038 要求的"同一 chat 内 mid-turn 插话"在 wechat 适配器层的 runtime 验收 | unit (coroutines-test) | `WeixinPollDispatchTest#TC-GATEWAY-038-e weixin per-chat dispatch overlaps in time` 🟢 |

跑已落地 TC：

```bash
./gradlew :hermes-android:testDebugUnitTest --tests "com.xiaomo.hermes.hermes.gateway.GatewayBusyPendingReplayTest"
./gradlew :hermes-android:testDebugUnitTest --tests "com.xiaomo.hermes.hermes.gateway.platforms.WeixinPollDispatchTest"
```

## 域 UI — Cancel-then-resend (R-UI-061)

R-UI-061 把 `MessageProcessingDelegate.sendUserMessage:452-459` 的"isLoading 时静默丢弃"改成"先 cancel 再 send"。同时保留：空消息/无附件早返、`isLoading=false` 时正常路径。无 Python 上游（仅 Android UI 体验）。

| TC-ID | 关联 R | 输入 / 操作 | 期望 | 类型 | 实现 |
|---|---|---|---|---|---|
| TC-UI-061-a | R-UI-061 | `isLoading=false` 时调 `sendUserMessage(text="hi")` | 走正常路径：`_userMessage` 清空、`isLoading=true`、`sendJob` 启动；不调 cancelMessageInternal | unit | `MessageProcessingDelegateInsertTest#TC-UI-061-a idle path unchanged` 🔴 |
| TC-UI-061-b | R-UI-061 | `isLoading=true` 时调 `sendUserMessage(text="new")` | 1) 调 `cancelMessageInternal` 一次；2) 等取消生效（`isLoading=false`）；3) 重新进入 sendUserMessage 处理 "new"（最终 `isLoading=true`，sendJob 启动） | unit | `MessageProcessingDelegateInsertTest#TC-UI-061-b busy triggers cancel then resend` 🔴 |
| TC-UI-061-c | R-UI-061 | 模拟 `cancelMessageInternal` 卡 15s（>10s 超时） | 不挂起调用方；超时分支 log warn；不抛；不重发（避免在 cancel 没完成的情况下并发跑两个 send） | unit | `MessageProcessingDelegateInsertTest#TC-UI-061-c cancel timeout drops resend` 🔴 |
| TC-UI-061-d | R-UI-061 | `isLoading=true` + 空消息 + 空附件 + 非 autoContinuation/group | 走原本的早返（空消息丢弃），不触发 cancel-then-resend | unit | `MessageProcessingDelegateInsertTest#TC-UI-061-d empty message still early returns` 🔴 |

跑已落地 TC：

```bash
./gradlew :app:testDebugUnitTest --tests "com.ai.assistance.operit.services.core.MessageProcessingDelegateInsertTest"
```

## 域 UI — Insert button + active-loop weakref + gateway wiring (R-UI-062)

R-UI-062 是本组合特性的终端整合：把 R-AGENT-036/037 做的 `HermesAgentLoop.steer()` 内核 + R-GATEWAY-036 的 `steerActiveAgent`/`cancelActiveAgent` 回调骨架真正接到 caller 端。涉及 4 个文件：`EnhancedAIService` 加 weakref + `steerActiveLoop` + 在 `cancelConversation` 调 `clearPendingSteer`；`ChatServiceCore` 透传；`HermesGatewayController.start()` 把两个回调接到 GATEWAY-slot core；`AgentChatInputSection` 加插话按钮（仅 `isProcessing=true` 可见）。Python 上游 `gateway/run.py:3290-3334` + `:3225-3245`。测试是源码扫描——SUT 实例化太重（Service + 数十依赖 + Compose），结构性保证用 source-scan 锁定即可。

| TC-ID | 关联 R | 输入 / 操作 | 期望 | 类型 | 实现 |
|---|---|---|---|---|---|
| TC-UI-062-a | R-UI-062 | EnhancedAIService 源码扫描 | `activeAgentLoopRef: WeakReference<HermesAgentLoop>?` 字段存在；`runAgentLoopViaHermes` 内 `loop.run(` 之前出现 `activeAgentLoopRef = WeakReference(loop)`；`finally` 块内出现 `activeAgentLoopRef = null` | unit | `EnhancedAIServiceSteerWiringTest#TC-UI-062-a weakref field and lifecycle` 🟢 |
| TC-UI-062-b | R-UI-062 | EnhancedAIService 源码扫描 | `fun steerActiveLoop(text: String): Boolean` 存在；body 解 weakref `?.get()` 并调 `loop.steer(text)`；缺 loop 返 false | unit | `EnhancedAIServiceSteerWiringTest#TC-UI-062-b steerActiveLoop method` 🟢 |
| TC-UI-062-c | R-UI-062 | EnhancedAIService 源码扫描 | `cancelConversation()` body 末尾包含 `activeAgentLoopRef?.get()?.clearPendingSteer()` 对齐 Python `:3599-3606` | unit | `EnhancedAIServiceSteerWiringTest#TC-UI-062-c cancelConversation clears pending steer` 🟢（外加 `TC-UI-062 clearPendingSteer is only reached from cancelConversation` 反向断言）|
| TC-UI-062-d | R-UI-062 | ChatServiceCore 源码扫描 | `fun steerActiveLoop(chatId: String, text: String): Boolean` 存在；委托到 `enhancedAiService?.steerActiveLoop(text)`；`enhancedAiService == null` 时返 false | unit | `ChatServiceCoreSteerLoopTest#TC-UI-062-d steerActiveLoop delegates` 🟢 |
| TC-UI-062-e | R-UI-062 | HermesGatewayController.start() 源码扫描 | `instance.steerActiveAgent = ` 与 `instance.cancelActiveAgent = ` 两个赋值都出现；都出现在 `runner = instance` 之前；body 引用 `ChatRuntimeHolder` + `ChatRuntimeSlot.GATEWAY` + `steerActiveLoop`/`cancelMessage` | unit | `HermesGatewayControllerSteerWiringTest#TC-UI-062-e start wires both callbacks` 🟢（外加 `TC-UI-062-e-2 callback guards against session mismatch`）|
| TC-UI-062-f | R-UI-062 | AgentChatInputSection 源码扫描 | Composable 参数包含 `onInsertMessage`；body 使用 `Icons.Default.Edit`（或等价 insert 图标）+ `contentDescription` 引用 `R.string.chat_insert_message`；点击体调 `onInsertMessage`；visibility 在 `isProcessing` 为 true 时显示（即 `showCancelAction \|\| showQueueAction`） | unit | `AgentChatInputSectionInsertButtonTest#TC-UI-062-f *`（5 子测试：参数存在 / Edit 图标 import / 图标 + 文案 / 点击体 / 可见性门控）🟢 |

跑已落地 TC：

```bash
./gradlew :app:testDebugUnitTest --tests "com.ai.assistance.operit.api.chat.EnhancedAIServiceSteerWiringTest"
./gradlew :app:testDebugUnitTest --tests "com.ai.assistance.operit.services.ChatServiceCoreSteerLoopTest"
./gradlew :app:testDebugUnitTest --tests "com.ai.assistance.operit.hermes.gateway.HermesGatewayControllerSteerWiringTest"
./gradlew :app:testDebugUnitTest --tests "com.ai.assistance.operit.ui.features.chat.components.style.input.agent.AgentChatInputSectionInsertButtonTest"
```

---

## 域 UI — Cron Jobs Sidebar Management (R-UI-063)

R-UI-063 在侧边栏（`OperitApp.kt::navGroups` 的 "工具" 组）加 `NavItem.CronJobs` 入口，进入新写的 `CronJobsScreen.kt`，从 `Jobs.listJobs()` 读取所有定时任务并以列表呈现，每行支持 4 个操作：暂停（`Jobs.pauseJob`）/ 恢复（`Jobs.resumeJob`）/ 删除（`Jobs.removeJob`）/ 立即触发（直接调 `CronAgentRunner.run(context, job)`，UI 在 app 模块所以无需走 R-AGENT-043 的注入）。本 R **不**提供 UI 创建入口——按用户决策，创建 cron 任务保留通过 agent 自然语言对话注册的路径（"每天 9 点提醒我看新闻"）。Python 上游无对应（仅 Android UI 体验）。

**架构合规要点**：
- `NavItem.kt` 加 `object CronJobs : NavItem("cronjobs", R.string.nav_cron_jobs, Icons.Default.Schedule)`
- `OperitScreens.kt` 加 `data object CronJobs : Screen(navItem = NavItem.CronJobs, titleRes = R.string.nav_cron_jobs)`
- `OperitScreens.kt::OperitRouter.getScreenForNavItem` when 表加 `NavItem.CronJobs -> Screen.CronJobs`
- `OperitApp.kt::navGroups` 在 `R.string.nav_group_tools` 组的 `listOf(...)` 中加 `NavItem.CronJobs`
- `strings.xml` 加 `<string name="nav_cron_jobs">定时任务</string>`
- 新文件 `app/src/main/java/com/ai/assistance/operit/ui/features/cron/screens/CronJobsScreen.kt`

| TC ID | R-ID | 输入 / 触发 | 期望 | 测试类型 | 实现 / 状态 |
|---|---|---|---|---|---|
| TC-UI-063-a | R-UI-063 | 源码扫描 `NavItem.kt` | 文件含 `object CronJobs : NavItem(`，3 参数为 `"cronjobs"` + `R.string.nav_cron_jobs` + `Icons.Default.Schedule`（与同组 `Workflow` / `Toolbox` 形态一致） | unit (源码扫描) | `NavItemCronJobsTest#TC-UI-063-a CronJobs nav item declared` 🔴 |
| TC-UI-063-b | R-UI-063 | 源码扫描 `OperitScreens.kt` | 文件含 `data object CronJobs : Screen(navItem = NavItem.CronJobs`；`OperitRouter.getScreenForNavItem` 的 when 表含 `NavItem.CronJobs -> Screen.CronJobs` 分支 | unit (源码扫描) | `OperitScreensCronJobsTest#TC-UI-063-b screen and router mapping declared` 🔴 |
| TC-UI-063-c | R-UI-063 | 源码扫描 `OperitApp.kt` | `navGroups` 列表中 `R.string.nav_group_tools` 组的 `listOf(...)` 含 `NavItem.CronJobs`（与 `NavItem.Workflow` / `NavItem.Toolbox` / `NavItem.ShizukuCommands` 同组）；不在其他两组 | unit (源码扫描) | `OperitAppNavGroupsCronJobsTest#TC-UI-063-c CronJobs registered in tools group` 🔴 |
| TC-UI-063-d | R-UI-063 | 源码扫描 `CronJobsScreen.kt` | 新文件存在；body 引用 `Jobs.listJobs(` + `Jobs.pauseJob(` + `Jobs.resumeJob(` + `Jobs.removeJob(` + `CronAgentRunner.run(`（5 个核心调用全部出现，对应"列表 + 4 个操作"） | unit (源码扫描) | `CronJobsScreenContractTest#TC-UI-063-d screen wires all 5 actions` 🔴 |
| TC-UI-063-e | R-UI-063 | 源码扫描 `app/src/main/res/values/strings.xml` | 文件含 `<string name="nav_cron_jobs">定时任务</string>` | unit (源码扫描) | `CronJobsScreenContractTest#TC-UI-063-e nav_cron_jobs string resource declared` 🔴 |
| TC-UI-063-f | R-UI-063 | 源码扫描 `CronJobsScreen.kt` | 文件**不**含 `addJob(` / `cronjob(action = "create"`（拒绝在 UI 提供创建入口；保留通过 agent 自然语言对话注册的路径）；只读 + 操作既有 job 的语义 | unit (源码扫描) | `CronJobsScreenContractTest#TC-UI-063-f screen does not expose create action` 🔴 |

跑已落地 TC：

```bash
./gradlew :app:testDebugUnitTest --tests "com.ai.assistance.operit.ui.common.NavItemCronJobsTest"
./gradlew :app:testDebugUnitTest --tests "com.ai.assistance.operit.ui.main.screens.OperitScreensCronJobsTest"
./gradlew :app:testDebugUnitTest --tests "com.ai.assistance.operit.ui.main.OperitAppNavGroupsCronJobsTest"
./gradlew :app:testDebugUnitTest --tests "com.ai.assistance.operit.ui.features.cron.screens.CronJobsScreenContractTest"
```

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

---

## 域 AGENT — Telegram inbound voice/audio + STT (R-GW-008 + R-AGENT-032)

R-GW-008 + R-AGENT-032 是一对孪生需求，目的是把 Telegram 入站的 voice / audio 消息**真正下载到本地**并通过 OpenAI Whisper STT **自动转写为文本**，让 agent 不再只看到 `[Voice: <fileId>]` 占位字符串。本轮**只动 voice / audio 两个分支**——photo / document / video / sticker 维持现状（继续塞 fileId），后续在新 R 中处理（用户决策："本轮只动 audio/voice STT，图片下次 R"）。

**架构合规（按用户决策）**:
- **STT provider 简化**：仅 OpenAI Whisper（用户决策："仅 OpenAI Whisper 起步"）。Python 上游有 5 个 provider（local / local_command / groq / openai / mistral），其余在 R-AGENT-032 文档里登记为"已知偏离上游"，由后续 R 追加。
- **不暴露独立 transcribe 工具**：用户决策："Telegram 入站自动转写"，对齐 Python `gateway/run.py:_enrich_message_with_transcription` 上游行为——平台层直接调，不让 agent 在对话里主动调。
- **缓存路径专属分层**（用户决策"Telegram 专属缓存路径分层"）：写盘走 `BasePlatformAdapter.cacheAudioFromBytes(context, bytes, ext)`（既有 helper，落到 `<context.cacheDir>/media/audio/`），不裸 `File.writeBytes`。
- **图片 / 视频 / 文档 / sticker 不动**（守"本轮只做语音"红线，由 TC-GW-008-c 守住）。

测试策略：
- **源码扫描**（unit-scan）覆盖 wiring 关键字面值：`getFile?file_id=` URL 拼接、`/file/bot` 下载 URL、`cacheAudioFromBytes` 调用、`TranscriptionTools.transcribeAudio` 调用、双语前缀 `[The user sent a voice message~ Here's what they said:`、OpenAI Whisper endpoint / multipart 字段。
- **Robolectric 行为测**（unit + Robolectric）：用 `MockWebServer` mock Telegram getFile + download，断言 `_downloadTelegramFile` 真发出对应请求；mock OpenAI `/v1/audio/transcriptions`，断言 `transcribeAudio` 真发出 multipart POST。
- **手测**：在真实 Telegram bot 上发语音，看 chat 内是否真的收到转写文本。

| TC ID | R-ID | 输入 / 触发 | 期望 | 测试类型 | 实现 / 状态 |
|---|---|---|---|---|---|
| TC-AGENT-032-a | R-AGENT-032 | 源码扫描：`hermes-android/.../tools/TranscriptionTools.kt` 是否存在 + 文件签名 | 文件存在；含顶层 `fun transcribeAudio(filePath: String, model: String? = null)` 函数声明；含 `data class TranscribeResult(val success: Boolean, val transcript: String, val error: String?, val provider: String?)` 声明。 | unit-scan | `TranscriptionToolsWiringTest#TC-AGENT-032-a TranscriptionTools file and public API exist` 🔴 |
| TC-AGENT-032-b | R-AGENT-032 | 源码扫描：OpenAI Whisper provider 实现关键字面值 | 必须含 `whisper-1` / `https://api.openai.com/v1` / `/audio/transcriptions` / `Authorization` / `Bearer ` / `multipart/form-data` 或 `MultipartBody.Builder` / `response_format` / `text` 共 8 处字面值（multipart POST 形状对齐 OpenAI SDK `client.audio.transcriptions.create`）。 | unit-scan | `TranscriptionToolsWiringTest#TC-AGENT-032-b OpenAI Whisper request shape literals` 🔴 |
| TC-AGENT-032-c | R-AGENT-032 | 源码扫描：API key 读取 fallback | 必须含字面值 `VOICE_TOOLS_OPENAI_KEY` 与 `OPENAI_API_KEY`（fallback 顺序对齐 Python `tool_backend_helpers.py:104`）；missing key 路径返回的 error 文案含 `No STT API key` 字面值。 | unit-scan | `TranscriptionToolsWiringTest#TC-AGENT-032-c key resolution falls back from VOICE_TOOLS_OPENAI_KEY to OPENAI_API_KEY` 🔴 |
| TC-AGENT-032-d | R-AGENT-032 | 源码扫描：文件大小 + 格式校验 | 必须含 `25` + `1024` 字面值（25 MB 上限，对齐上游 `transcription_tools.py:79`）或 `MAX_FILE_SIZE` 常量；必须含 `SUPPORTED_FORMATS` 常量声明，set 中至少含 `.mp3` / `.ogg` / `.wav` / `.m4a` 四个扩展名字面值。 | unit-scan | `TranscriptionToolsWiringTest#TC-AGENT-032-d file size cap and supported formats` 🔴 |
| TC-AGENT-032-e | R-AGENT-032 | 行为单测（纯 JVM）：调 `transcribeAudio("/nonexistent/file.mp3")`（文件不存在）| 函数应返回 `TranscribeResult(success=false, transcript="", error 含 "not found" 或 "does not exist"）`，不抛异常、**不**真发网络请求。 | unit | `TranscriptionToolsBehaviorTest#TC-AGENT-032-e missing file returns error without throwing` 🔴 |
| TC-AGENT-032-f | R-AGENT-032 | 行为单测（纯 JVM）：调 `transcribeAudio` 但环境无 `VOICE_TOOLS_OPENAI_KEY` 也无 `OPENAI_API_KEY` | 函数应返回 `TranscribeResult(success=false, error 含 "No STT API key", provider="openai")`，**不**真发网络请求。其它路径（200 success / 401 / multipart shape 验证）由 §3 E2E + 手测兜底（hermes-android testImpl 无 MockWebServer 依赖，**Deferred to E2E**）。 | unit | `TranscriptionToolsBehaviorTest#TC-AGENT-032-f missing key short-circuits before network call` 🔴 |
| TC-GW-008-a | R-GW-008 | 源码扫描：`hermes-android/.../gateway/platforms/Telegram.kt` 中 voice 分支（`message.has("voice")`） | 必须调 `_downloadTelegramFile(` + `TranscriptionTools.transcribeAudio(`（不再裸塞 fileId 到 mediaUrls）；text 改造必须含字面值 `[The user sent a voice message~ Here's what they said:`（对齐 Python `gateway/run.py:8218`）；mediaUrls 应为 `listOf(localPath)` 而非 `listOf(fileId)`。 | unit-scan | `TelegramVoiceAudioWiringTest#TC-GW-008-a voice branch downloads then transcribes` 🔴 |
| TC-GW-008-b | R-GW-008 | 源码扫描：Telegram.kt 中 audio 分支（`message.has("audio")`） | 同 TC-GW-008-a，但 ext 字面值为 `mp3`、mediaTypes 为 `audio/mpeg`；必须保留 `caption` 读取（audio 与 voice 不同：voice 没 caption，audio 有 caption）。 | unit-scan | `TelegramVoiceAudioWiringTest#TC-GW-008-b audio branch downloads then transcribes with caption` 🔴 |
| TC-GW-008-c | R-GW-008 | 源码扫描（红线守卫）：Telegram.kt photo / document / video / sticker 四个分支 | 这四个分支的 `mediaUrls` 仍必须是 `listOf(<fileId>)` 而非 `listOf(<localPath>)` —— 守"本轮只动 voice/audio"红线，防误改。 | unit-scan | `TelegramVoiceAudioWiringTest#TC-GW-008-c photo document video sticker branches stay placeholder` 🔴 |
| TC-GW-008-d | R-GW-008 | 源码扫描：`_downloadTelegramFile` 函数 | Telegram.kt 必须含 `private fun _downloadTelegramFile` 或 `private suspend fun _downloadTelegramFile` 函数声明；函数体含字面值 `getFile?file_id=`（步骤 1 拿 file_path）+ `/file/bot`（步骤 2 拼下载 URL）+ `cacheAudioFromBytes(`（写盘）。 | unit-scan | `TelegramVoiceAudioWiringTest#TC-GW-008-d _downloadTelegramFile fetches getFile then downloads then caches` 🔴 |
| TC-GW-008-e | R-GW-008 | 源码扫描：失败降级文案 | voice 与 audio 分支的失败路径必须含字面值 `[The user sent a voice message but I had trouble transcribing it~`（对齐 Python `gateway/run.py:8222-8252`）；转写失败时仍下载并保留 `mediaUrls = listOf(localPath)`，**不**让 agent 完全感知不到媒体存在。 | unit-scan | `TelegramVoiceAudioWiringTest#TC-GW-008-e transcription failure falls back to bilingual notice` 🔴 |
| TC-GW-008-f | R-GW-008 | 端到端验证：在真 Telegram bot 上发语音 | bot 收到后 chat 内显示 `[The user sent a voice message~ Here's what they said: "..."]` 前置 + 原文转写。**Deferred to §3 E2E + 手测**（hermes-android testImpl 无 MockWebServer，行为完整性由 E2E 兜底）。 | manual / E2E | `(no unit test; manual verification required)` 🔴 |
| TC-GW-008-g | R-GW-008 | 端到端验证：bot 发语音但 OpenAI key 错 | chat 内显示 `[The user sent a voice message but I had trouble transcribing it~ ...]` 降级文案，不崩溃。**Deferred to §3 E2E + 手测**。 | manual / E2E | `(no unit test; manual verification required)` 🔴 |
| TC-GW-009-a | R-GW-009 | 源码扫描：`HermesGatewayPreferences.kt` 含 Telegram 平台常量 | 必须含 `PLATFORM_TELEGRAM` 常量声明（值为 `"telegram"`）；必须含 `TELEGRAM_FIELDS` 常量，至少包含 `"token"` 与 `"allowed_chat_ids"` 两个字段名字面值。 | unit-scan | `TelegramGatewayPreferencesWiringTest#TC-GW-009-a Telegram platform constants exist` 🔴 |
| TC-GW-009-b | R-GW-009 | 源码扫描：`HermesGatewayConfigBuilder.kt` 含 `buildTelegram` 分支 | 必须含 `private fun buildTelegram(` 或 `fun buildTelegram(` 函数声明；`build(` 主入口必须调 `buildTelegram(`（与 `buildFeishu` / `buildWeixin` 同级）；`buildTelegram` 函数体必须读 `PLATFORM_TELEGRAM` + `"token"` + `"allowed_chat_ids"` 三个字面值。 | unit-scan | `TelegramGatewayConfigBuilderWiringTest#TC-GW-009-b buildTelegram wired into build()` 🔴 |
| TC-GW-009-c | R-GW-009 | 源码扫描：`HermesGatewayCredentialsScreen.kt` 含 Telegram 卡片 | 必须含 `PLATFORM_TELEGRAM` 引用与 `TELEGRAM_FIELDS` 引用；必须含 `"Telegram"` 字面值（卡片标题）；卡片渲染语句应是 `PlatformCredentialsCard(` 的第三次出现（前两次是 Feishu / Weixin），证明已加而非替换。 | unit-scan | `TelegramGatewayCredentialsScreenWiringTest#TC-GW-009-c Telegram card rendered alongside Feishu and Weixin` 🔴 |
| TC-GW-009-d | R-GW-009 | 源码扫描（红线）：Feishu 与 Weixin 接线不被误改 | `HermesGatewayConfigBuilder.kt` 必须仍含 `buildFeishu(` 与 `buildWeixin(` 调用；`HermesGatewayCredentialsScreen.kt` 必须仍含 `PLATFORM_FEISHU` 与 `PLATFORM_WEIXIN` 引用。 | unit-scan | `TelegramGatewayConfigBuilderWiringTest#TC-GW-009-d Feishu and Weixin wiring untouched` 🔴 |
| TC-GW-009-e | R-GW-009 | 端到端验证：app 设置 → Hermes Gateway → 凭证页 | 可见 Telegram 卡片，能填 token + allowed_chat_ids；启用后 logcat 见 telegram adapter 启动行；token 留空时不启动 telegram 不崩溃。**Deferred to 手测**（app 模块未配 ComposeTestRule，行为由手测兜底）。 | manual | `(no unit test; manual verification required)` 🔴 |
| TC-GW-011-a | R-GW-011 | 源码扫描：`Run.kt` 主入口 `runner(...)` 调用被 `_keepTyping(` 包裹 | `_handleMessage` 函数体内（`onProcessingStart` 之后、`runner(event.text,` 之前）必须出现 `_keepTyping(` 字面值；同函数体含 `coroutineScope` + `typingJob` + `cancel(` 字面值，证明 `launch { _keepTyping }` + `finally { typingJob.cancel() }` 配对接通。 | unit-scan | `RunTypingIndicatorWiringTest#TC-GW-011-a main runner call wrapped with _keepTyping` 🔴 |
| TC-GW-011-b | R-GW-011 | 源码扫描：`Run.kt` pending-event 循环 `runner(...)` 也被包住 | `_handleMessage` 中第二次 `runner(pendingEvent.text,` 之前（约 line 485 后）必须出现第二次 `_keepTyping(` 字面值；该处必须引用 `pendingEvent.source.chatId`（不是初始 `event.source.chatId`），证明 typing 跟随当前正在处理的事件。 | unit-scan | `RunTypingIndicatorWiringTest#TC-GW-011-b pending event runner call wrapped with _keepTyping` 🔴 |
| TC-GW-011-c | R-GW-011 | 源码扫描（红线）：`Telegram.kt` 的 `sendTyping` override 不被误改 | `Telegram.kt` 仍含 `override suspend fun sendTyping(chatId: String, metadata: JSONObject?` 函数声明；函数体仍含 `sendChatAction` 字面值与 `"typing"` 字面值（POST 路径与 action）。 | unit-scan | `RunTypingIndicatorWiringTest#TC-GW-011-c Telegram sendTyping override intact` 🔴 |
| TC-GW-011-d | R-GW-011 | 源码扫描（红线）：`Base.kt` 的 `_keepTyping` 扩展不被误改 | `Base.kt` 仍含 `suspend fun BasePlatformAdapter._keepTyping(` 函数签名；函数体含 `sendTyping(` 调用 + `delay(intervalMs)` + `finally` 块 + `stopTyping(` 自清理；默认 `intervalMs: Long = 2000L`（2s 刷新，对齐上游 `_keep_typing` 默认值）。 | unit-scan | `RunTypingIndicatorWiringTest#TC-GW-011-d Base _keepTyping extension intact` 🔴 |
| TC-GW-011-e | R-GW-011 | 源码扫描（红线）：不引入新 typing 网络调用 + 生命周期单点管 | 若 `Feishu.kt` / `Weixin.kt` 已有 `override suspend fun sendTyping`（实际 Feishu 已有显式 no-op，对齐上游 "API doesn't support" 注释），其函数体**不得**含 Telegram 专有的 `sendChatAction` 字面值（防止误把 Telegram 实现复制到别的平台）。`HermesGatewayController` 等高层入口**不得**直接调 `_keepTyping(` —— typing 生命周期归 `Run.kt::_handleMessage` 单点管。 | unit-scan | `RunTypingIndicatorWiringTest#TC-GW-011-e other platforms untouched, typing lifecycle owned by Run kt` 🔴 |
| TC-GW-011-f | R-GW-011 | 端到端验证：真 Telegram bot 发会让 agent 思考 ≥3s 的消息 | bot 在 chat 内 2s 内出现 "正在输入… / is typing…" 提示并持续刷新；agent 回复后 5s 内提示消失；多轮对话不残留。**Deferred to §3 E2E + 手测**（hermes-android testImpl 无 MockWebServer，`sendChatAction` HTTP 行为由真 bot E2E 验证）。 | manual / E2E | `(no unit test; manual verification required)` 🔴 |
| TC-GW-011-g | R-GW-011 | 端到端验证：agent 抛错时 typing 仍能干净停 | 让 agent loop 抛异常或返回 `INTERRUPTED_SENTINEL`；typing 提示在 5s 内消失（typingJob 在 finally 被 cancel + `_keepTyping` 自身 finally 调 `stopTyping`），不留"幽灵 typing"。**Deferred to 手测**。 | manual | `(no unit test; manual verification required)` 🔴 |
| TC-GW-013-a | R-GW-013 | 源码扫描：`Telegram.kt::send` 含 3-attempt 重试循环 + retry_after 解析 + Markdown fallback | `send` 函数体必须含字面值 `for ` + ` in 0 until 3` + `attempt` + `429` + `retry_after` + `parameters` + `parse` + `Markdown` 共 8 个字面值（对齐 Python `telegram.py:1023-1106` 的 send 重试循环）；同时含 `thread_not_found` 与 `replied message not found` 字面值（自愈分支）。 | unit-scan | `TelegramSendRetryWiringTest#TC-GW-013-a send wraps post in 3-attempt loop with retry_after and markdown fallback` 🟢 |
| TC-GW-013-b | R-GW-013 | 源码扫描：`Telegram.kt` 含 `_stripMarkdownToPlain` + `_splitForTelegram` helpers；不含静默截断 | 必须含 `private fun _stripMarkdownToPlain(` 与 `private fun _splitForTelegram(` 函数声明；`send` 函数体**不得**含 `content.take(MAX_MESSAGE_LENGTH)` —— 长消息走 `_splitForTelegram` 分段并以 ` (k/N)` 后缀标号（对齐 Python `telegram.py:951-1020`）。 | unit-scan | `TelegramSendRetryWiringTest#TC-GW-013-b helpers exist and silent take is gone` 🟢 |
| TC-GW-013-c | R-GW-013 | 源码扫描（红线）：`SocketTimeoutException` 不重试（防重复发送）；其他错误才走重试 | `send` 函数体必须含 `SocketTimeoutException` 字面值并紧邻 `return` —— 对齐 Python `telegram.py:1097` 的 `if isinstance(e, telegram.error.TimedOut): raise` 防重复发送红线。 | unit-scan | `TelegramSendRetryWiringTest#TC-GW-013-c socket timeout does not retry` 🟢 |
| TC-GW-013-d | R-GW-013 | 端到端验证：真 Telegram bot 在群里被 flood control（429）后 agent 回复仍能送达 | 配 Telegram bot + 启用 gateway，在群里给 bot 连发 ≥10 条 trigger，每条回复都应送达 chat（即使有些消息间隔 10s+ 因 retry_after），无静默丢消息。**Deferred to §3 E2E + 手测**（hermes-android testImpl 无 MockWebServer，HTTP 行为由真 bot 验证）。 | manual / E2E | `(no unit test; manual verification required)` 🔴 |
| TC-GW-013-e | R-GW-013 | 端到端验证：agent 输出含未配对 `_*[` 的 markdown，chat 应能收到（plain text fallback） | 让 agent 输出含 ` ``` ` / `_` / `*` 的 markdown，chat 应能收到（Markdown 渲染或 plain text fallback 都算成功），不能因 `can't parse entities` 丢消息。**Deferred to 手测**。 | manual | `(no unit test; manual verification required)` 🔴 |

状态图例: 🔴 = 无测试（待落地） / 🟡 = 有测试未验证 / 🟢 = 已绿

---

## 域 SAFETY

SAFETY 大多通过引用其它域的 TC 覆盖；此处列集成层 smoke。

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-SAFETY-020-a | R-SAFETY-001 | PathSecurity canonicalPath `/a/./b/../c` | `/a/c` | unit | 复用 `PathSecurityTest` ✅ |
| TC-SAFETY-021-a | R-SAFETY-001 | dangerous pattern 列表长度 | 与 Python 一致 | unit | 复用 `ApprovalTest#pattern count` ✅ |
| TC-SAFETY-022-a | R-SAFETY-001 | Tirith 降级 | 视作 "ask" | unit | 复用 `TirithSecurityTest` ✅ |
| TC-SAFETY-023-a | R-SAFETY-002 | OsvCheck 网络失败 | fail-open | unit | 复用 `OsvCheckTest#fail-open on network error` ✅ |
| TC-SAFETY-024-a | R-SAFETY-002 | `UrlSafety.isSafe("http://127.0.0.1")` | false | unit | 复用 `UrlSafetyTest#private IP rejected` ✅ |
| TC-SAFETY-025-a | R-SAFETY-002 | WebsitePolicy 默认 | disabled + 30s cache | unit | 复用 `WebsitePolicyTest#default disabled + cache` ✅ |
| TC-SAFETY-026-a | R-SAFETY-002 | readFile `.exe` | 拒绝 | unit | 复用 `BinaryExtensionsTest` ✅ |
| TC-SAFETY-027-a | R-SAFETY-001 | write `~/.ssh/id_rsa` | 拒绝 | unit | 复用 `FileSafetyTest#SSH denied` ✅ |
| TC-SAFETY-028-a | R-SAFETY-001 | `HERMES_WRITE_SAFE_ROOT` 设 | 约束所有 write | unit | 复用 `FileSafetyTest#safe root jail` ✅ |
| TC-SAFETY-029-a | R-SAFETY-001 | read `/dev/urandom` | 拒绝 | unit | `FileOperationsTest#blocks /dev character device` 🟢 |

---

## 域 CONFIG

测试类: 新建 `HermesGatewayPreferencesTest`、`HermesGatewayConfigBuilderTest`、`HermesGatewayControllerTest`（均在 `HermesApp/app/src/test/`，使用 Robolectric）。

### HermesGatewayPreferences.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-CONFIG-001-a | R-GW-001 | `HermesGatewayPreferences.getInstance(ctx)` 两次 | 同实例 | unit | `HermesGatewayPreferencesTest#singleton` 🟢 |
| TC-CONFIG-002-a | R-GW-001 | 写 `appId` | 进 EncryptedPrefs | integration | `HermesGatewayPreferencesTest#dual store writes` 🟢 |
| TC-CONFIG-002-b | R-GW-001 | 写 policy | 进 DataStore | integration | `HermesGatewayPreferencesTest#policy goes to datastore` 🟢 |
| TC-CONFIG-003-a | R-GW-001 | saveAgentMaxTurns(0) | clamp 到 1 | unit | `HermesGatewayPreferencesTest#maxTurns clamp low` 🟢 |
| TC-CONFIG-003-b | R-GW-001 | saveAgentMaxTurns(9999) | clamp 到 cap | unit | `HermesGatewayPreferencesTest#maxTurns clamp high` 🟢 |
| TC-CONFIG-004-a | R-GW-001 | `clearSecrets("feishu")` | 仅 `feishu_*` key 清 | integration | `HermesGatewayPreferencesTest#clearSecrets prefix only` 🟢 |
| TC-CONFIG-005-a | R-GW-001 | 首次读 Flow 无值 | emit 默认 | unit | `HermesGatewayPreferencesTest#default flow emit` 🟢 |
| TC-CONFIG-006-a | R-GW-001 | 写后 restart process 读 | 数据仍在 | integration | `HermesGatewayPreferencesTest#roundtrip persistence` 🟢 |
| TC-CONFIG-007-a | R-GW-001 | 常量 key 名 | 与 Python 对齐 | unit | `HermesGatewayPreferencesTest#constant names` 🟢 |

### HermesGatewayConfigBuilder.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-CONFIG-010-a | R-GW-001 | Feishu creds 缺 `appSecret` | `config.platforms` 不含 feishu | unit | `HermesGatewayConfigBuilderTest#incomplete creds skipped` 🟢 |
| TC-CONFIG-011-a | R-GW-001 | 两平台一有一无 | 过滤 null | unit | `HermesGatewayConfigBuilderTest#null platforms filtered` 🟢 |
| TC-CONFIG-012-a | R-GW-001 | extra 字段部分空 | 仅非空入 map | unit | `HermesGatewayConfigBuilderTest#extra non-null only` 🟢 |
| TC-CONFIG-013-a | R-GW-001 | `readCsv(" a, b ,c")` | `["a","b","c"]` | unit | `HermesGatewayConfigBuilderTest#readCsv normalizes` 🟢 |
| TC-CONFIG-014-a | R-GW-001 | 默认 maxConcurrentSessions | `5` | unit | `HermesGatewayConfigBuilderTest#maxConcurrent default 5` 🟢 |
| TC-CONFIG-015-a | R-GW-001 | 策略默认 | dm/group 等默认值与 Python 一致 | unit | `HermesGatewayConfigBuilderTest#policy defaults` 🟢 |

### HermesGatewayController.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-CONFIG-020-a | R-GW-006 | start()→RUNNING | Status 转换 STOPPED → STARTING → RUNNING | integration | `HermesGatewayControllerTest#status FSM happy path` 🟡 |
| TC-CONFIG-021-a | R-GW-006 | 空 platforms 下 start() | Status → FAILED + errorMessage | integration | `HermesGatewayControllerRobolectricTest#empty platforms fails` 🟢 |
| TC-CONFIG-022-a | R-GW-006 | RUNNING 下 start() | no-op 返回 true | unit | `HermesGatewayControllerTest#start idempotent RUNNING` 🟢 |
| TC-CONFIG-023-a | R-GW-006 | stop() 过程抛 | Status 仍 STOPPED | integration | `HermesGatewayControllerTest#stop exception still STOPPED` 🟡 |
| TC-CONFIG-024-a | R-GW-006 | `stripInternalMarkup("<tool>...</tool>")` | 剥净 | unit | `HermesGatewayControllerTest#stripInternalMarkup removes xml` 🟢 |
| TC-CONFIG-025-a | R-GW-006 | `gatewayChatTitle` 长 100 字符 | 截断 24 | unit | `HermesGatewayControllerTest#chat title truncation` 🟢 |
| TC-CONFIG-026-a | R-GW-006 | agent 返空 reply | 使用 fallback 文本 | integration | `HermesGatewayControllerTest#empty reply fallback` 🟡 |
| TC-CONFIG-027-a | R-GW-006 | persist 抛 IOException | 吞 + logcat ERROR | integration | `HermesGatewayControllerTest#persist swallow` 🟡 |

### HermesGatewayAutoStarter.kt — 应用启动时自恢复前台服务（bugfix：用户开启网关后下次冷启动失效）

背景: R-GW-006 已经规定"随应用/开机自启策略"应当生效。但生产代码里仅 `GatewayBootReceiver` 监听 BOOT_COMPLETED + 同时要求 `autoStartOnBoot && serviceEnabled` 才启动；`OperitApplication.onCreate()` 与 `ActivityLifecycleManager` 没有任何 `serviceEnabledFlow` 读取或 `GatewayForegroundService.start(...)` 调用（与 `ExternalChatHttpAutoStarter` 在 `ActivityLifecycleManager.kt:130` 进入前台时被触发的对照路径不同）。结果: 用户在设置页打开网关 → 杀进程 / 系统重启服务 → 下次打开 app 网关不会回来；只有真正"重启手机 + autoStartOnBoot=true"的小众路径会复活。这是 R-GW-006 既有需求未被代码满足的盲区，按 §0.1 走 bugfix 流程（不动 ①，加 TC + 测试 + 修代码）。

修复方向: 仿 `ExternalChatHttpAutoStarter`（`integrations/http/ExternalChatHttpAutoStarter.kt:1-55`）写 `HermesGatewayAutoStarter.ensureRunningIfEnabled(ctx, reason)`，仅当 `serviceEnabledFlow` 当前值为 true 且服务尚未运行时调用 `GatewayForegroundService.start(ctx)`。在 `ActivityLifecycleManager` 的"应用进入前台"hook 里和 `ExternalChatHttpAutoStarter` 并列调用。`autoStartOnBootFlow` 维持只控 `GatewayBootReceiver` 的语义不变。

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-GW-220-a | R-GW-006 | `serviceEnabledFlow` 当前值 = true，service 当前未运行，调 `HermesGatewayAutoStarter.ensureRunningIfEnabled(ctx, reason)` | 调用 `GatewayForegroundService.start(ctx)` 一次（捕获通过 fake context / spy） | unit | `HermesGatewayAutoStarterTest#starts when enabled` 🟡 |
| TC-GW-220-b | R-GW-006 | `serviceEnabledFlow` 当前值 = false | **不**调 `GatewayForegroundService.start` | unit | `HermesGatewayAutoStarterTest#skips when disabled` 🟡 |
| TC-GW-220-c | R-GW-006 | `serviceEnabledFlow` = true，但 `HermesGatewayController.status.value == RUNNING`（已运行） | **不**重复 start | unit | `HermesGatewayAutoStarterTest#noop when already running` 🟡 |
| TC-GW-220-d | R-GW-006 | 在同一个进程里连续调 `ensureRunningIfEnabled` 两次 | 第二次因为 `ensureInProgress` 互斥被跳过（与 `ExternalChatHttpAutoStarter` 同款保护） | unit | `HermesGatewayAutoStarterTest#reentrancy guard` 🟡 |
| TC-GW-221-a | R-GW-006 | `ActivityLifecycleManager` 应用进入前台时 | 同时触发 `ExternalChatHttpAutoStarter.ensureRunningIfEnabled` **和** `HermesGatewayAutoStarter.ensureRunningIfEnabled`（双 starter 并列） | unit | `ActivityLifecycleManagerHermesGatewayTest#foreground_triggers_gateway_autostart` 🟡 |
| TC-GW-222-a | R-GW-006 | 用户在设置页 toggle 网关 ON → 退出 app → 冷启动重新打开 app | 应用前台 hook 调 `HermesGatewayAutoStarter` → `serviceEnabledFlow=true` → 起前台服务 → `controller.status` 走 `STARTING → RUNNING` | integration | `HermesGatewayAutoStarterRobolectricTest#cold_start_restores_running_gateway` 🟡 |
| TC-GW-223-a | R-GW-006 | `autoStartOnBootFlow` 仅控 `GatewayBootReceiver`（独立维度，不和应用内自启耦合） | `HermesGatewayAutoStarter` 不读 `autoStartOnBootFlow`；改 `autoStartOnBootFlow=false` 不影响应用内自启 | unit | `HermesGatewayAutoStarterTest#does_not_read_auto_start_on_boot` 🟡 |


---

## 域 UI

测试类: `HermesSettings*ScreenTest.kt` + ViewModel unit tests。使用 Compose 测试 rule + Robolectric。

### HermesSettingsScreen.kt（hub）

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-UI-001-a | R-UI-001 | 屏幕渲染 | 5 个 tile 可见 | ui | `HermesSettingsScreenTest#five tiles visible` 🟢 |
| TC-UI-002-a | R-UI-001 | 每 tile 图标 | Icon 匹配资源 | ui | `HermesSettingsScreenTest#tile icons` 🟢 |
| TC-UI-003-a | R-UI-001 | 点击 tile | callback 触发 | ui | `HermesSettingsScreenTest#tile click triggers callback` 🟢 |
| TC-UI-004-a | R-UI-001 | 只打开不做任何 tap | 无 prefs / service 写 | ui | `HermesSettingsScreenTest#no side effect on open` 🟢 |
| TC-UI-005-a | R-UI-001 | tile 文本超宽 | 截断显示 | ui | `HermesSettingsScreenTest#tile text ellipsis` 🟢 |

### HermesGatewayCredentialsScreen.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-UI-010-a | R-UI-001 | Feishu 卡片 | 字段数 4（app_id/app_secret/verification_token/encrypt_key；production FEISHU_FIELDS） | ui | `HermesGatewayCredentialsScreenTest#feishu field count` 🟢 |
| TC-UI-010-b | R-UI-001 | Weixin 卡片 | 字段数 2（account_id/login_token；production WEIXIN_FIELDS） | ui | `HermesGatewayCredentialsScreenTest#weixin field count` 🟢 |
| TC-UI-011-a | R-UI-001 | 密码字段 | textVisible=false 默认 | ui | `HermesGatewayCredentialsScreenTest#password masked default` 🟢 |
| TC-UI-012-a | R-UI-001 | 切 enable toggle | 立刻 saveEnable 调用 | ui | `HermesGatewayCredentialsScreenTest#enable toggle immediate save` 🟢 |
| TC-UI-013-a | R-UI-001 | 按 Save | 每字段各一次 write | ui | `HermesGatewayCredentialsScreenTest#save per field write` 🟢 |
| TC-UI-014-a | R-UI-001 | Save 后 | savedFlash 显示 1500ms 后消失 | ui | `HermesGatewayCredentialsScreenTest#saved flash 1500ms` 🟢 |
| TC-UI-015-a | R-UI-001 | 打开屏幕 | 初始值等 prefs | ui | `HermesGatewayCredentialsScreenTest#initial values from prefs` 🟢 |

### HermesGatewayPoliciesScreen.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-UI-020-a | R-UI-001 | dm_policy chip | Feishu 选项 `{open, pairing, allowlist}`；Weixin `{open, allowlist, disabled}`（对齐 Python 上游） | ui | `HermesGatewayPoliciesScreenTest#dm policy chips` 🟢 |
| TC-UI-020-b | R-UI-001 | group_policy chip | 两个平台都为 `{open, allowlist, disabled}` | ui | `HermesGatewayPoliciesScreenTest#group policy chips` 🟢 |
| TC-UI-021-a | R-UI-001 | 改 chip 后不按 Save 退出 | prefs 未变 | ui | `HermesGatewayPoliciesScreenTest#only save persists` 🟢 |
| TC-UI-022-a | R-UI-001 | 预置 `FIELD_REQUIRE_MENTION="false"`/`"true"` → 挂载 → 读 Switch 状态 | 字符串 `"false"` 渲染为 Off、`"true"` 渲染为 On（string↔boolean 双向映射） | ui | `HermesGatewayPoliciesScreenTest#string-boolean match` 🟢 |
| TC-UI-023-a | R-UI-001 | 未设定过 | Feishu 默认 dm=`pairing`/group=`allowlist`/mention=`true`；Weixin `open`/`disabled`/`false` | unit | `HermesGatewayPoliciesScreenTest#defaults` 🟢 |
| TC-UI-024-a | R-UI-001 | 首次 state 初始化后 prefs 外部变更 | state map 不覆盖（仅首次 emit 初始化） | ui | `HermesGatewayPoliciesScreenTest#first-emit only init` 🟢 |

### HermesAgentParamsScreen.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-UI-030-a | R-UI-001 | TextField 输入字母 | 被过滤 | ui | `HermesAgentParamsScreenTest#digits only filter` 🟢 |
| TC-UI-031-a | R-UI-001 | 输入 9999 → Save | clamp 后写入 prefs | ui | `HermesAgentParamsScreenTest#save clamps value` 🟢 |
| TC-UI-032-a | R-UI-001 | 输入空 → Save | no-op | ui | `HermesAgentParamsScreenTest#empty no-op save` 🟢 |
| TC-UI-033-a | R-UI-001 | prefs 外部写 | 屏幕 resync 显示 | ui | `HermesAgentParamsScreenTest#external change resync` 🟢 |

### HermesGatewayServiceScreen.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-UI-040-a | R-UI-001 | 打开 run switch | 同时 `saveRunSwitch(true)` + `startService` | ui | `HermesGatewayServiceScreenTest#run switch double write` 🟢 |
| TC-UI-040-b | R-UI-001 | 关 run switch | 同时 `saveRunSwitch(false)` + `stopService` | ui | `HermesGatewayServiceScreenTest#run switch off` 🟢 |
| TC-UI-041-a | R-UI-001 | autostart toggle | 仅写 prefs，不碰 service | ui | `HermesGatewayServiceScreenTest#autostart pref only` 🟢 |
| TC-UI-042-a | R-UI-001 | Status=RUNNING | 显示 "运行中" 文案 | ui | `HermesGatewayServiceScreenTest#status RUNNING mapped` 🟢 |
| TC-UI-042-b | R-UI-001 | Status=FAILED | 显示 "启动失败" | ui | `HermesGatewayServiceScreenTest#status FAILED mapped` 🟢 |
| TC-UI-043-a | R-UI-001 | errorMessage != null | 错误条渲染 | ui | `HermesGatewayServiceScreenTest#error bar visible` 🟢 |

### HermesGatewayQrBindScreen.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-UI-050-a | R-UI-001 | Feishu QR 流程成功（re-scoped：pre-seed appId + botName + domain）| "已绑定: ..." / Bot / Domain 信息卡渲染 | ui | `HermesGatewayQrBindScreenTest#feishu success writes creds` 🟢 |
| TC-UI-051-a | R-UI-001 | Weixin QR 成功（re-scoped：pre-seed accountId）| "已绑定账号: ..." 渲染 | ui | `HermesGatewayQrBindScreenTest#weixin success writes creds` 🟢 |
| TC-UI-052-a | R-UI-001 | QR 生成失败（反射调 `generateQrBitmap` size=0）| 返回 null（UI 依据该契约显示 "⚠️ 二维码生成失败"）| ui | `HermesGatewayQrBindScreenTest#qr gen failure` 🟢 |
| TC-UI-053-a | R-UI-001 | idle 状态按钮不变量（re-scoped：无法驱动 qrRegister 网络路径）| "取消" 按钮不渲染 + "开始扫码注册" / "开始扫码登录" 渲染 | ui | `HermesGatewayQrBindScreenTest#cancel clears state` 🟢 |
| TC-UI-054-a | R-UI-001 | 按 "清除凭证" | `clearSecrets(Feishu/Weixin)` 同步清空；状态 "已清除凭证" | ui | `HermesGatewayQrBindScreenTest#clear credentials invokes clear` 🟢 (Feishu + Weixin) |
| TC-UI-055-a | R-UI-001 | Weixin 常驻描述文案（re-scoped：静态 fallback 文案）| "微信 (Weixin) iLink 扫码登录" 标题 + "qr_login 协议" 描述常驻 | ui | `HermesGatewayQrBindScreenTest#weixin fallback message` 🟢 |

### R-UI-002: 记忆详情页手动 toggle 持久化指令

| ID | R-ID | 输入 | 期望 | 类型 | 实现 |
|---|---|---|---|---|---|
| TC-UI-060-a | R-UI-002 | memory 不带任何 tag → `addTagToMemory(memory, "#persistent_instruction")` | `memory.tags.map { it.name }` 含 `#persistent_instruction`；`findMemoriesByTag(...)` 返回该 memory | instrumentation | 🟡 ObjectBox 真实 BoxStore 依赖，留 androidTest |
| TC-UI-060-b | R-UI-002 | memory 已带 `#persistent_instruction` → 再次 `addTagToMemory` | tag 关系数仍为 1（无重复挂载） | instrumentation | 🟡 ObjectBox 真实 BoxStore 依赖，留 androidTest |
| TC-UI-061-a | R-UI-002 | memory 带 `#persistent_instruction` + 其它 tag → `removeTagFromMemory(memory, "#persistent_instruction")` | tag 列表只剩其它 tag；`findMemoriesByTag("#persistent_instruction")` 不含该 memory | instrumentation | 🟡 ObjectBox 真实 BoxStore 依赖，留 androidTest |
| TC-UI-061-b | R-UI-002 | memory 不带 `#persistent_instruction` → `removeTagFromMemory` | no-op，无异常，tag 列表不变；MemoryTag 实体保留 | instrumentation | 🟡 ObjectBox 真实 BoxStore 依赖，留 androidTest |
| TC-UI-062-a | R-UI-002 | `ViewModel.togglePersistentInstruction(memoryId, true)`；mock `repository.findMemoryById` 返回不带该 tag 的 memory | `repository.addTagToMemory(memory, "#persistent_instruction")` 被调用一次；`repository.removeTagFromMemory` 零调用；`uiState.isLoading` 复位 | unit | `MemoryViewModelPersistentToggleTest#toggle on adds tag` 🟡 已写未跑（:app baseline 编译挂：HermesGatewayAutoStarterTest 引用不存在的生产类） |
| TC-UI-062-b | R-UI-002 | `ViewModel.togglePersistentInstruction(memoryId, false)`；mock 返回带该 tag 的 memory | `repository.removeTagFromMemory(memory, "#persistent_instruction")` 被调用一次；`repository.addTagToMemory` 零调用；`uiState.isLoading` 复位 | unit | `MemoryViewModelPersistentToggleTest#toggle off removes tag` 🟡 已写未跑（同上） |
| TC-UI-063-a | R-UI-002 | toggle 仅改 tag — 不调任何其它 mutate 方法（saveMemory / updateMemory / linkMemories 等） | 仅 `addTagToMemory` / `removeTagFromMemory` / `findMemoryById` / `searchMemories` 被调；其它写入方法零调用 | unit | `MemoryViewModelPersistentToggleTest#toggle does not call other mutators` 🟡 已写未跑（同上） |
| TC-UI-063-b | R-UI-002 | `togglePersistentInstruction` 在 `findMemoryById` 返回 null 时 | 安全 no-op；不抛异常；`uiState.isLoading` 复位为 false；不调 add/remove tag | unit | `MemoryViewModelPersistentToggleTest#toggle noop when memory missing` 🟡 已写未跑（同上） |

### R-UI-003: Gateway agent 运行时悬浮球

| ID | R-ID | 输入 | 期望 | 类型 | 实现 |
|---|---|---|---|---|---|
| TC-UI-070-a | R-UI-003 | 启动 service 后 emit `GatewayChatEventBus.Event.ProcessingStarted(chatId="gw:feishu:c1")` | `activeChats` 含 `"gw:feishu:c1"`；`isOverlayShowing == true` | unit | `AgentStatusOverlayServiceTest#started event shows overlay` 🟡 待写 |
| TC-UI-070-b | R-UI-003 | Started 后 emit `ProcessingCompleted(chatId="gw:feishu:c1")` | `activeChats` 为空；`isOverlayShowing == false` | unit | `AgentStatusOverlayServiceTest#completed event hides overlay` 🟡 待写 |
| TC-UI-070-c | R-UI-003 | 同时 Started 两个 chatId → Completed 其中一个 | activeChats size=1；`isOverlayShowing == true`（剩一个仍要显示） | unit | `AgentStatusOverlayServiceTest#completed one of two keeps overlay` 🟡 待写 |
| TC-UI-070-d | R-UI-003 | Started 后 emit `ProcessingFailed` 并 advance 时间 < `ERROR_FLASH_MS` | overlay 仍可见且 `errorFlash` UI 状态为 true | unit | `AgentStatusOverlayServiceTest#failed triggers error flash` 🟡 待写 |
| TC-UI-070-e | R-UI-003 | Failed → advance 时间 > `ERROR_FLASH_MS` | overlay 隐藏；errorFlash 归 false | unit | `AgentStatusOverlayServiceTest#error flash auto hides after timeout` 🟡 待写 |
| TC-UI-071-a | R-UI-003 | overlay 显示中 → `setOverlayVisible(false)` | `isOverlayShowing == false`；activeChats 不变 | unit | `AgentStatusOverlayServiceTest#setOverlayVisible false hides without clearing` 🟡 待写 |
| TC-UI-071-b | R-UI-003 | 上面之后 → `setOverlayVisible(true)` | `isOverlayShowing == true`（因还有活跃 chat） | unit | `AgentStatusOverlayServiceTest#setOverlayVisible true reshows when active` 🟡 待写 |
| TC-UI-071-c | R-UI-003 | activeChats 空 → `setOverlayVisible(true)` | `isOverlayShowing == false`（无活跃 chat 不复显） | unit | `AgentStatusOverlayServiceTest#setOverlayVisible true noop when idle` 🟡 待写 |
| TC-UI-072-a | R-UI-003 | `AgentEventBus.emit(chatId, AgentEvent.ToolCallStart(turn=2, name="search"))` | activeChats[chatId].turn == 2 且 lastToolName == "search" | unit | `AgentStatusOverlayServiceTest#tool call start updates turn and tool` 🟡 待写 |
| TC-UI-072-b | R-UI-003 | 紧接 `ToolCallEnd(name="search")` | lastToolName 被清空（null） | unit | `AgentStatusOverlayServiceTest#tool call end clears tool name` 🟡 待写 |
| TC-UI-073-a | R-UI-003 | `AndroidManifest.xml` 包含 `<service android:name=".services.AgentStatusOverlayService" ... foregroundServiceType="dataSync" />` | manifest 文本断言通过 | unit | `AgentStatusOverlayWiringTest#manifest registers service` 🟡 待写 |
| TC-UI-073-b | R-UI-003 | `GatewayForegroundService.kt` 源码中包含 `AgentStatusOverlayService.start(this)` 与 `AgentStatusOverlayService.stop(this)` | 两处源码断言通过 | unit | `AgentStatusOverlayWiringTest#gateway service starts and stops overlay` 🟡 待写 |
| TC-UI-073-c | R-UI-003 | `EnhancedAIService.kt` 源码包含 `AgentEventBus.emit(` 与 `AgentTokenBus.emit(` | 源码断言通过 | unit | `AgentStatusOverlayWiringTest#enhanced ai service emits to bus` 🟡 待写 |
| TC-UI-073-d | R-UI-003 | `HermesAdapter.kt` 源码包含 `AgentEventBus.emit(chatId,` | 源码断言通过 | unit | `AgentStatusOverlayWiringTest#hermes adapter emits to bus` 🟡 待写 |
| TC-UI-073-e | R-UI-003 | `ToolRegistration.kt` 源码包含 `agentStatusOverlay?.setOverlayVisible(false)` 与 `setOverlayVisible(true)` | 源码断言通过 | unit | `AgentStatusOverlayWiringTest#tool registration toggles overlay during ui tools` 🟡 待写 |

### R-UI-004: EditMemoryDialog content 高度抬高 + `#auto_summary` 节点 hint

测试类: `app/src/test/java/com/ai/assistance/operit/ui/features/memory/screens/dialogs/EditMemoryDialogAutoSummaryHintWiringTest.kt`

**背景**: R-UI-004 是 R-AGENT-013 的 UI 兜底——自动摘要节点 content 通常 500~2000 字，现有 `EditMemoryDialog.kt:111` 的 `.heightIn(min=100.dp, max=200.dp)` 严重限制编辑舒适度，且用户在编辑界面无法识别"这条是自动摘要"。Composable 重度依赖 Compose runtime + Android resources，走源码扫描守住 wiring；视觉效果由手测验证。

| ID | R-ID | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-UI-004-a | R-UI-004 | 源码扫描：`EditMemoryDialog.kt` | content `OutlinedTextField` 的 `Modifier.heightIn(...)` 必须含 `min = 160.dp`（短摘要也舒展） | unit-scan | `EditMemoryDialogAutoSummaryHintWiringTest#TC-UI-004-a content field min height raised to 160dp` 🔴 |
| TC-UI-004-b | R-UI-004 | 源码扫描：`EditMemoryDialog.kt` | content `OutlinedTextField` 的 `Modifier.heightIn(...)` 必须含 `max = 480.dp`（长摘要可舒展，配合外层 verticalScroll 不破坏布局） | unit-scan | `EditMemoryDialogAutoSummaryHintWiringTest#TC-UI-004-b content field max height raised to 480dp` 🔴 |
| TC-UI-004-c | R-UI-004 | 源码扫描：`EditMemoryDialog.kt` | 源码必须包含 `"#auto_summary"` 字面字符串作为 tag 判断条件（标识 chip 渲染分支） | unit-scan | `EditMemoryDialogAutoSummaryHintWiringTest#TC-UI-004-c references auto_summary tag literal` 🔴 |
| TC-UI-004-d | R-UI-004 | 源码扫描：`EditMemoryDialog.kt` | 必须存在 `AssistChip` 或 `SuggestionChip` 调用（auto_summary hint chip 的渲染入口） | unit-scan | `EditMemoryDialogAutoSummaryHintWiringTest#TC-UI-004-d uses AssistChip or SuggestionChip for hint` 🔴 |
| TC-UI-004-e | R-UI-004 | 源码扫描：`EditMemoryDialog.kt` | chip 渲染必须被 `if`（或等价条件分支）包裹，且条件引用 `tags` 列表 + `"#auto_summary"` —— 防止普通节点 / gateway 节点 / persistent_instruction 节点也显示该 chip | unit-scan | `EditMemoryDialogAutoSummaryHintWiringTest#TC-UI-004-e chip rendered conditionally on auto_summary tag` 🔴 |
| TC-UI-004-f | R-UI-004 | 源码扫描：`EditMemoryDialog.kt` | chip 文案必须走 `stringResource(R.string.memory_auto_summary_chip)`（i18n 不得硬编码）；且 `strings.xml` 含 `memory_auto_summary_chip` 键 | unit-scan | `EditMemoryDialogAutoSummaryHintWiringTest#TC-UI-004-f chip label uses string resource` 🔴 |
| TC-UI-004-g | R-UI-004 | 源码扫描：`EditMemoryDialog.kt` | 文档节点限制保留 —— content `OutlinedTextField` 仍含 `enabled = memory?.isDocumentNode != true`（R-UI-004 不解锁文档节点编辑） | unit-scan | `EditMemoryDialogAutoSummaryHintWiringTest#TC-UI-004-g document node remains disabled` 🔴 |



---

## 统计

| 域 | R 数 | TC 数 | 已落地 ✅ | 待写 🟡 |
|---|---|---|---|---|
| CORE | 2 | 5 | 5 (alignment) | 0 |
| PARSER | 10 | 36 | 36 | 0 |
| AGENT (ErrorClassifier) | 1 | 48 | 48 | 0 |
| AGENT (Helpers) | 1 | 29 | 29 | 0 |
| AGENT (FileSafety) | 1 | 10 | 10 | 0 |
| AGENT (TurnLoop) | 1 | 9 | 9 (3 E2E + 6 unit) | 0 |
| AGENT (CredentialPool) | 1 | 7 | 0 | 7 |
| ACP | 4 | 53 | 49 | 4 |
| TOOL | 3 | 186 | 52 | 134 |
| GATEWAY | 6 | 91 | 65 | 26 |
| STATE | 3 | 19 | 9 | 10 |
| SKILL | 3 | 11 | 0 | 11 |
| MCP | 3 | 17 | 16 | 1 |
| CRON | 1 | 8 | 5 | 3 |
| SAFETY | 2 | 10 | 9 | 1 |
| CONFIG | (删除) | 23 | 0 | 23 |
| UI | 2 | 38 | 0 | 38 |
| **合计** | **43** | **600** | **342** | **258** |

> CONFIG 域 23 条 TC 在 requirements.md 三轮 prune 后归并到 R-GW-001 / R-GW-006 / R-UI-001，保留 TC 行以便 Phase 3 落地（测试类本身不受域归并影响）。

跑已落地 TC：
```bash
./gradlew :hermes-android:testDebugUnitTest \
  --tests "com.xiaomo.hermes.hermes.MissingParsersTest" \
  --tests "com.xiaomo.hermes.hermes.agent.*" \
  --tests "com.xiaomo.hermes.hermes.acp.*" \
  --tests "com.xiaomo.hermes.hermes.tools.*" \
  --tests "com.xiaomo.hermes.hermes.gateway.*" \
  --tests "com.xiaomo.hermes.hermes.cron.*" \
  --tests "com.xiaomo.hermes.hermes.HermesStateTest"
```

— 250 条 🟡 是 Phase 3 的产品代码 + 新测试类；以下按 §9.1 Top→Mid→Low 在 Phase 3 迭代中逐批落地。
