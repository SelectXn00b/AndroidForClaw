# Hermes for Android

[![Release](https://img.shields.io/badge/Release-v1.2.0-blue.svg)](https://github.com/SelectXn00b/HermesApp/releases/latest)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://www.android.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> **让 AI 真正掌控你的 Android 手机。**

1:1 复刻 [Hermes](https://github.com/nicepkg/hermes) 框架的 Kotlin 版 Agent Loop，嵌入 Android 平台，实现完整 AI Agent 能力：看屏幕、点 App、跑代码、连平台。

**[详细文档](https://vcn23e479dhx.feishu.cn/wiki/UZtFwM6t9iArPVkMvRccwSran6d)** | **[快速开始](#快速开始)** | **[社区](#社区)**

---

## AI 能帮你做什么

**操控任何 App** — 微信、支付宝、抖音、淘宝、高德……凡是你能手动操作的，AI 都能操作。

```
你：帮我打开微信发消息给张三说"明天见"
AI：→ 打开微信 → 搜索张三 → 输入消息 → 发送 ✅
```

**跨应用联动** — 微信收到地址 → 复制 → 打开高德 → 搜索 → 开始导航

**执行代码** — 通过 Termux SSH 或内置 Shell 执行命令（Python/Node.js/Shell）

**搜索 & 抓取网页** — Brave 搜索 + 网页全文抓取

**多平台消息** — 飞书、Discord 远程控制手机 AI

**MCP Server** — 将手机无障碍/截屏能力暴露给 Claude Desktop、Cursor 等外部 Agent

**技能扩展** — 从 [ClawHub](https://clawhub.com) 安装新能力，或自己创建 Skill

---

## 快速开始

### 下载安装

从 [Release 页面](https://github.com/SelectXn00b/HermesApp/releases/latest) 下载：

| APK | 说明 | 必装？ |
|-----|------|--------|
| **Hermes** | 主应用（含无障碍服务、Agent、Gateway） | 必装 |
| **BrowserForClaw** | AI 浏览器（网页自动化） | 可选 |

> Termux 需从 [F-Droid](https://f-droid.org/packages/com.termux/) 单独安装（不要用 Play Store 版本）。不装也能用基础 shell 命令，装了有完整 Linux 环境。

### 3 步上手

1. **安装** — 下载安装 Hermes
2. **配置** — 打开 App，输入 API Key（或跳过使用内置 Key），开启无障碍 + 录屏权限
3. **开聊** — 直接对话，或通过飞书/Discord 发消息

> 首次打开自动弹出引导页，默认 OpenRouter + MiMo V2 Pro，支持一键跳过

---

## 技术架构

```
┌──────────────────────────────────────────┐
│  Channels                                 │
│  飞书 · Discord · 设备内对话               │
├──────────────────────────────────────────┤
│  Agent Runtime                            │
│  AgentLoop · 65+ Tools · 27 Skills ·      │
│  Context 管理 · Memory · Skill Recorder   │
├──────────────────────────────────────────┤
│  Providers                                │
│  OpenRouter · MiMo · Gemini · Anthropic · │
│  OpenAI · 自定义 OpenAI 兼容              │
├──────────────────────────────────────────┤
│  Android Platform                         │
│  Accessibility · Termux SSH · device tool │
│  MediaProjection · BrowserForClaw         │
└──────────────────────────────────────────┘
```

### 核心特性

| 特性 | 说明 |
|------|------|
| **Playwright 模式** | 屏幕操作对齐 Playwright —— `snapshot` 获取 UI 树 + ref → `act` 操作元素 |
| **统一 exec** | 自动路由 Termux SSH（连接池 + 断线重连）或内置 Shell |
| **Context 管理** | 4 层防护：limitHistoryTurns + 工具结果裁剪 + budget guard + auto-summarization |
| **Model 智能路由** | Model ID 标准化 + Fallback Chain + API Key 轮换 + Allowlist/Blocklist |
| **Gateway SLOT 隔离** | 每个渠道消息通过独立 ChatRuntimeSlot.GATEWAY 处理，与主 UI 互不干扰 |
| **Skill 体系** | 27 个内置 Skill，设备上可自由编辑，支持 ClawHub 在线安装 |
| **Skill Recorder** | 录制用户操作步骤，AI 自动生成可复用的 Skill |
| **多模型** | MiMo V2 Pro · DeepSeek R1 · Claude Sonnet 4 · Gemini 2.5 · GPT-4.1 |
| **MCP Server** | 将无障碍/截屏能力暴露给外部 Agent（端口 8399，Streamable HTTP） |
| **渠道模型覆盖** | 每个消息渠道可独立选择模型 |

---

## 完整能力表

### 通用 Tools

| Tool | 功能 |
|------|------|
| `device` | 屏幕操作：snapshot / tap / type / scroll / press / open（Playwright 模式） |
| `read_file` | 读取文件内容 |
| `write_file` | 创建或覆盖文件 |
| `edit_file` | 精确编辑文件（diff 模式） |
| `list_dir` | 列出目录内容 |
| `exec` | 执行命令（Termux SSH / 内置 Shell） |
| `web_search` | Brave 搜索引擎 |
| `web_fetch` | 抓取网页内容 |
| `javascript` | 执行 JavaScript（QuickJS） |
| `tts` | 文本转语音 |
| `skills_search` | 搜索 ClawHub 技能 |
| `skills_install` | 从 ClawHub 安装技能 |
| `config_get` / `config_set` | 读写配置项 |
| `memory_search` / `memory_get` | 语义搜索/读取记忆 |

### Android 专属 Tools

| Tool | 功能 |
|------|------|
| `list_installed_apps` | 列出已安装应用 |
| `install_app` | 安装 APK |
| `start_activity` | 启动 Activity |
| `feishu_send_image` | 通过飞书发送图片 |
| `eye` | 摄像头拍照 |
| `log` | 查看系统日志 |
| `stop` | 停止 Agent |

### 飞书 Tools（39 个）

| 类别 | 说明 |
|------|------|
| 文档 | 获取 / 创建 / 更新 / 媒体 / 评论 |
| Wiki | 空间 / 节点 |
| 云盘 | 文件操作 |
| 多维表格 | 应用 / 表 / 字段 / 记录 / 视图 |
| 任务 | 任务 / 子任务 / 评论 |
| 群聊 | 群管理 / 成员管理 |
| 日历 | 日历 / 事件 / 忙闲查询 |
| 消息 | 发消息 / 搜索消息 / 资源 |
| 搜索 | 搜索文档 / Wiki |

### 27 个 Skills

| 类别 | Skills |
|------|--------|
| 飞书全家桶 | `feishu` · `feishu-doc` · `feishu-wiki` · `feishu-drive` · `feishu-bitable` · `feishu-chat` · `feishu-task` · `feishu-perm` · `feishu-urgent` · `feishu-calendar` · `feishu-common` · `feishu-im` · `feishu-search` · `feishu-sheets` |
| 搜索 & 网页 | `browser` · `weather` · `lark-cli` |
| 技能管理 | `clawhub` · `skill-creator` |
| 开发调试 | `debugging` · `data-processing` · `session-logs` · `context-security` |
| 配置管理 | `model-config` · `channel-config` · `install-app` · `model-usage` |

> Skills 存储在 `/sdcard/.hermes/skills/`，可自由编辑、添加、删除。

### 消息渠道

| 渠道 | 状态 | 功能 |
|------|------|------|
| **飞书** | 可用 | WebSocket 实时连接，群聊/私聊，39 个飞书工具，流式卡片回复 |
| **Discord** | 可用 | Gateway v10，群聊/私聊，DM 策略管理 |
| **设备内对话** | 可用 | 内置聊天界面，多 session 管理 |
| **Telegram** | 开发中 | — |
| **Slack** | 开发中 | — |

### 支持的模型

| Provider | 模型 | 说明 |
|----------|------|------|
| **OpenRouter** | MiMo V2 Pro, Hunter Alpha, DeepSeek R1, Claude Sonnet 4, GPT-4.1 | 推荐，内置 Key |
| **小米 MiMo** | MiMo V2 Pro, MiMo V2 Flash, MiMo V2 Omni | 直连小米 API |
| **Google** | Gemini 2.5 Pro, Gemini 2.5 Flash | 直连 |
| **Anthropic** | Claude Sonnet 4, Claude Opus 4 | 直连 |
| **OpenAI** | GPT-4.1, GPT-4.1 Mini, o3 | 直连 |
| **自定义** | 任何 OpenAI 兼容 API | Ollama, vLLM 等 |

> **默认配置**：OpenRouter + MiMo V2 Pro（1M 上下文 + 推理），跳过引导页自动使用内置 Key。

---

## 配置参考

`/sdcard/.hermes/config/openclaw.json`

```json
{
  "models": {
    "providers": {
      "openrouter": {
        "baseUrl": "https://openrouter.ai/api/v1",
        "apiKey": "sk-or-v1-你的key",
        "models": [{"id": "xiaomi/mimo-v2-pro", "reasoning": true, "contextWindow": 1048576}]
      }
    }
  },
  "agents": {
    "defaults": {
      "model": { "primary": "openrouter/xiaomi/mimo-v2-pro" }
    }
  },
  "channels": {
    "feishu": { "enabled": true, "appId": "cli_xxx", "appSecret": "xxx" },
    "discord": {
      "enabled": true,
      "botToken": "your-discord-bot-token",
      "model": "openrouter/xiaomi/mimo-v2-pro"
    }
  }
}
```

详细配置参考 **[飞书文档](https://vcn23e479dhx.feishu.cn/wiki/UZtFwM6t9iArPVkMvRccwSran6d)**

---

## 从源码构建

```bash
git clone https://github.com/SelectXn00b/HermesApp.git
cd HermesApp
export JAVA_HOME=/path/to/jdk21
./gradlew assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
```

---

## Related Projects

| 项目 | 说明 |
|------|------|
| [Hermes](https://github.com/nicepkg/hermes) | AI Agent 框架（桌面端） |
| [Hermes for Android](https://github.com/SelectXn00b/HermesApp) | Hermes Android 客户端（本项目） |

---

## 社区

<div align="center">

#### 飞书群

[![加入飞书群](docs/images/feishu-qrcode.jpg)](https://applink.feishu.cn/client/chat/chatter/add_by_link?link_token=566r8836-6547-43e0-b6be-d6c4a5b12b74)

**[点击加入飞书群](https://applink.feishu.cn/client/chat/chatter/add_by_link?link_token=566r8836-6547-43e0-b6be-d6c4a5b12b74)**

---

#### Discord

[![Discord](https://img.shields.io/badge/Discord-加入服务器-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/rDpaFym2b8)

**[加入 Discord](https://discord.gg/rDpaFym2b8)**

---

#### 微信群

<img src="docs/images/wechat-qrcode.png" width="300" alt="微信群二维码">

**扫码加入微信群** - 7天内有效

</div>

---

## 相关链接

- [Hermes](https://github.com/nicepkg/hermes) — 架构参照
- [ClawHub](https://clawhub.com) — 技能市场
- [架构文档](ARCHITECTURE.md) — 详细设计

---

## License

MIT — [LICENSE](LICENSE)

---

<div align="center">

**如果这个项目对你有帮助，请给个 Star 支持开源！**

</div>
