# AndroidForClaw ↔ OpenClaw 对齐度报告

> 生成时间：2026-03-15
> 对比版本：AndroidForClaw v1.0.2 vs OpenClaw 2026.3.11

---

## 总体对齐度：**72%**

| 模块 | 对齐度 | 说明 |
|------|--------|------|
| 常量/阈值 | 85% | 4个常量偏差 |
| System Prompt | 55% | 缺 8 个段落 |
| Agent Loop 核心 | 80% | 缺 failover/compaction |
| Tools | 75% | 缺 process/apply_patch |
| Context 管理 | 90% | 位置差异，逻辑对齐 |
| History 清洗 | 95% | 基本完全对齐 |
| Skills 体系 | 90% | 对齐，skill-creator 已移植 |
| Loop Detection | 85% | 逻辑对齐，hook 方式不同 |
| Channel 架构 | 60% | 4个 channel 框架就绪 |
| Memory | 70% | 有 search/get，缺 auto flush |

---

## 一、常量/阈值对齐度：85%

| 常量 | OpenClaw | AndroidForClaw | 对齐度 |
|------|---------|----------------|--------|
| `CHARS_PER_TOKEN_ESTIMATE` | 4 | 4 | 100% |
| `CONTEXT_INPUT_HEADROOM_RATIO` | 0.75 | 0.75 | 100% |
| `SINGLE_TOOL_RESULT_CONTEXT_SHARE` | 0.5 | 0.5 | 100% |
| `MAX_OVERFLOW_COMPACTION_ATTEMPTS` | 3 | 3 | 100% |
| `keepLastAssistants` | 3 | 3 | 100% |
| `hardClearRatio` | 0.5 | 0.5 | 100% |
| `softTrimRatio` | 0.3 | 0.3 | 100% |
| `minPrunableToolChars` | 50,000 | 50,000 | 100% |
| `softTrim.headChars` | 1,500 | 1,500 | 100% |
| `softTrim.tailChars` | 1,500 | 1,500 | 100% |
| `softTrim.maxChars` | 4,000 | 4,000 | 100% |
| `TOOL_CALL_NAME_MAX_CHARS` | 64 | 64 | 100% |
| `TOOL_CALL_NAME_RE` | `^[A-Za-z0-9_-]+$` | `^[A-Za-z0-9_-]+$` | 100% |
| `LOOP_WARNING_BUCKET_SIZE` | 10 | 10 | 100% |
| `TOOL_RESULT_MAX_CHARS` | 8,000 | 4,000 | **50%** |
| `COMPACTION_TIMEOUT_MS` | 300,000 | 120,000 | **40%** |
| `HARD_MAX_TOOL_RESULT_CHARS` | 400,000 | 缺失 | **0%** |
| `MAX_TOOL_RESULT_CONTEXT_SHARE` | 0.3 | 缺失 | **0%** |
| `DEFAULT_CONTEXT_TOKENS` | 待确认 | 128,000 | 待确认 |

**修复项**：
- [ ] `TOOL_RESULT_MAX_CHARS` 4000 → 8000
- [ ] `COMPACTION_TIMEOUT_MS` 120000 → 300000
- [ ] 添加 `HARD_MAX_TOOL_RESULT_CHARS = 400_000`
- [ ] 添加 `MAX_TOOL_RESULT_CONTEXT_SHARE = 0.3`

---

## 二、System Prompt 对齐度：55%

| Section | OpenClaw | AndroidForClaw | 对齐度 |
|---------|---------|----------------|--------|
| Identity | 有 | 有（Playwright 模式） | 80% |
| Tooling (tool list) | 有 | 有 | 95% |
| Skills (mandatory) | 有 | 有 | 95% |
| Project Context (bootstrap files) | 有 | 有 | 90% |
| Inbound Context (metadata) | 有 | 有 | 90% |
| Group Chat Context | 有 | 有 | 85% |
| Messaging | 有 | 有 | 80% |
| Runtime info | 有 | 有 | 75% |
| Subagent Context | 有 | 有 | 70% |
| **Silent Replies** | 有 | 缺失 | **0%** |
| **Reply Tags** | 有 | 缺失 | **0%** |
| **Heartbeats** | 有 | 缺失 | **0%** |
| **Safety** | 有 | 缺失（在 SOUL.md 里） | **30%** |
| **Memory Recall** | 有 | 缺失 | **0%** |
| **Model Aliases** | 有 | 缺失 | **0%** |
| **Current Date & Time** | 有 | 缺失 | **0%** |
| **Documentation** | 有 | 缺失 | **0%** |
| **Output Format** | 有 | 缺失 | **0%** |
| **Reasoning Format** | 有 | 缺失 | **0%** |
| Sandbox | 有 | N/A（不适用 Android） | — |
| OpenClaw CLI Quick Ref | 有 | N/A（不适用） | — |

**修复项**：
- [ ] 添加 Silent Replies 段（NO_REPLY 机制）
- [ ] 添加 Current Date & Time 段
- [ ] 添加 Memory Recall 段（memory_search 指引）
- [ ] 添加 Safety 段（从 SOUL.md 提取核心规则到 prompt）
- [ ] 添加 Reply Tags 段
- [ ] 添加 Heartbeats 段
- [ ] 添加 Output Format 段
- [ ] 添加 Reasoning Format 段

---

## 三、Agent Loop 核心对齐度：80%

| 功能 | OpenClaw | AndroidForClaw | 对齐度 |
|------|---------|----------------|--------|
| 主循环 (iterate until done) | 有 | 有 | 95% |
| limitHistoryTurns | 有 | 有 | 95% |
| Context Pruning (cache-ttl) | plugin 级 | loop 内实现 | 80% |
| ToolResultContextGuard | 有 | 有 | 90% |
| Block reply (中间文本) | 有 (blockReplyBreak) | 有 | 85% |
| Tool call execution | 有 | 有 | 90% |
| Tool loop detection | beforeToolCallHook | 内置检测 | 85% |
| History sanitize | 完整 repair pipeline | 完整 repair pipeline | 95% |
| dropThinkingBlocks | 有 | 有 | 95% |
| validateAnthropicTurns | 有 | 有 (validateTurnOrder) | 90% |
| validateGeminiTurns | 有 | 缺失 | **0%** |
| Error tracking | 有 | 有 (consecutive error) | 80% |
| Context overflow recovery | 有 | 有 | 85% |
| **自动 Compaction (LLM 摘要)** | 有（独立 runner） | 缺失 | **0%** |
| **Memory Flush** | 有 | 缺失 | **0%** |
| **模型 Failover** | 有 (runWithModelFallback) | 缺失 | **0%** |
| **Transient error retry** | 有 (retryAsync) | 仅 timeout retry | **30%** |
| **Abort signal** | 有 (AbortController) | 仅 shouldStop flag | **40%** |
| **Session lock** | 有 (acquireSessionWriteLock) | 缺失 | **0%** |
| **Token usage tracking** | 有 | 缺失 | **0%** |

**修复项**：
- [ ] 添加 validateGeminiTurns
- [ ] 实现自动 Compaction（context 满时 LLM 摘要）
- [ ] 实现模型 Failover（主模型失败切备用）
- [ ] 完善 transient error retry（429/5xx 指数退避）
- [ ] 添加 token usage 统计

---

## 四、Tools 对齐度：75%

| Tool | OpenClaw | AndroidForClaw | 对齐度 |
|------|---------|----------------|--------|
| read | 有 (adaptive paging) | 有 (无 paging) | **70%** |
| write | 有 (workspace guard) | 有 | 85% |
| edit | 有 (Claude compat: old_string) | 有 (无 param normalization) | **70%** |
| exec | 有 (background, process管理) | 有 (无后台管理) | **60%** |
| process | 有 | 缺失 | **0%** |
| web_search | 有 (Brave/Perplexity/Gemini/Grok) | 有 (仅 Brave) | **40%** |
| web_fetch | 有 | 有 | 90% |
| browser | 有 (Playwright CDP) | 有 (device tool) | 75% |
| message | 有 (跨渠道) | 缺失 | **0%** |
| tts | 有 | 缺失 | **0%** |
| pdf | 有 | 缺失 | **0%** |
| canvas | 有 | 缺失 | **0%** |
| sessions_* | 有 (4个) | 缺失 | **0%** |
| subagents | 有 | 缺失 | **0%** |
| session_status | 有 | 缺失 | **0%** |
| agents_list | 有 | 缺失 | **0%** |
| apply_patch | 有 | 缺失 | **0%** |
| memory_search | 有 | 有 | 85% |
| memory_get | 有 | 有 | 85% |
| skills_search | clawhub CLI | 有 (直接 API) | 80% |
| skills_install | clawhub CLI | 有 (直接 API) | 80% |
| **Tool param normalization** | 有 (file_path→path) | 缺失 | **0%** |
| **Adaptive read paging** | 有 | 缺失 | **0%** |
| **Image sanitization** | 有 | 缺失 | **0%** |
| **Workspace root guard** | 有 | 缺失 | **0%** |

**修复项**：
- [ ] read: 添加 adaptive paging（大文件自动分页）
- [ ] edit: 添加 param normalization（file_path→path, old_string→oldText）
- [ ] exec: 添加 process tool（后台进程管理）
- [ ] web_search: 支持更多 provider（Perplexity, Gemini）
- [ ] 添加 message tool（跨渠道发消息）
- [ ] 添加 apply_patch tool
- [ ] 添加 workspace root guard（防止逃逸）

---

## 五、Context 管理对齐度：90%

| 功能 | OpenClaw | AndroidForClaw | 对齐度 |
|------|---------|----------------|--------|
| limitHistoryTurns | 有 | 有 | 95% |
| ToolResultContextGuard | 有 | 有 | 90% |
| Context pruning (soft trim) | 有 (plugin) | 有 (loop 内) | 85% |
| Context pruning (hard clear) | 有 | 有 | 90% |
| Context budget check | 有 | 有 | 90% |
| Aggressive trim (最后兜底) | 有 | 有 | 85% |
| Context window resolution | 有 (多源) | 有 (config) | 80% |
| **Context window warn/block** | 有 | 有 (warn only) | **60%** |

---

## 六、History 清洗对齐度：95%

| 功能 | OpenClaw | AndroidForClaw | 对齐度 |
|------|---------|----------------|--------|
| dropThinkingBlocks | 有 | 有 | 100% |
| repairToolUseResultPairing | 有 | 有 | 95% |
| Displaced result repair | 有 | 有 | 95% |
| Duplicate result dedup | 有 | 有 | 95% |
| Orphan result drop | 有 | 有 | 95% |
| Synthetic error result | 有 | 有 | 95% |
| Tool name normalization | 有 | 有 | 90% |
| validateAnthropicTurns | 有 | 有 | 90% |
| validateGeminiTurns | 有 | 缺失 | **0%** |
| limitHistoryTurns | 有 | 有 | 95% |
| Merge consecutive user msgs | 有 | 有 | 95% |

---

## 七、Skills 体系对齐度：90%

| 功能 | OpenClaw | AndroidForClaw | 对齐度 |
|------|---------|----------------|--------|
| SKILL.md 解析 (frontmatter) | 有 | 有 | 95% |
| available_skills XML catalog | 有 | 有 | 95% |
| Always skills (全文注入) | 有 | 有 | 90% |
| Skill requirements check | 有 (bins/anyBins) | 有 | 85% |
| skill-creator | 有 | 有 (从 OpenClaw 移植) | 95% |
| ClawHub search/install | CLI | API (tools) | 80% |
| Skill lock manager | 有 | 有 | 85% |
| Progressive disclosure | 有 (metadata→SKILL.md→refs) | 有 | 90% |
| 5 层 Skill 加载 | 有 | 有 | 85% |

---

## 八、Channel 架构对齐度：60%

| Channel | OpenClaw | AndroidForClaw | 对齐度 |
|---------|---------|----------------|--------|
| Feishu | 完整 | 完整 (29 files) | 90% |
| Discord | 完整 | 完整 (15 files) | 80% |
| Telegram | 完整 | 框架就绪 (16 files stub) | 20% |
| Slack | 完整 | 框架就绪 (16 files stub) | 20% |
| Signal | 完整 | 框架就绪 (16 files stub) | 20% |
| WhatsApp | 完整 | 框架就绪 (16 files stub) | 20% |
| iMessage | 完整 | 缺失 | 0% |
| LINE | 完整 | 缺失 | 0% |
| IRC | 有 | 缺失 | 0% |
| Google Chat | 有 | 缺失 | 0% |

---

## 九、Provider 对齐度：80%

| 功能 | OpenClaw | AndroidForClaw | 对齐度 |
|------|---------|----------------|--------|
| OpenAI completions API | 有 | 有 | 90% |
| Anthropic API | 有 | 有 (legacy) | 70% |
| max_tokens normalization | 有 | 有 | 90% |
| Streaming (SSE) | 有 | 缺失 | **0%** |
| Auth profile rotation | 有 | 缺失 | **0%** |
| API key for model resolution | 有 | 简化版 | 60% |
| Provider failover | 有 | 缺失 | **0%** |
| Cache/prompt caching | 有 | 缺失 | **0%** |
| Usage tracking / cost | 有 | 缺失 | **0%** |

---

## 优先修复路线

### Phase 1: 常量修正（1小时）→ 对齐度 72% → 75%
- 修 4 个常量偏差

### Phase 2: System Prompt 补齐（2小时）→ 75% → 82%
- 添加 8 个缺失段落

### Phase 3: Agent 能力补齐（1天）→ 82% → 88%
- validateGeminiTurns
- Tool param normalization
- Adaptive read paging
- Process tool
- Transient error retry

### Phase 4: 高级能力（3天）→ 88% → 95%
- 自动 Compaction
- 模型 Failover
- Streaming
- message tool
