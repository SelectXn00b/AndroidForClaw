# Hermes for Android

[![Release](https://img.shields.io/badge/Release-v1.2.0-blue.svg)](https://github.com/SelectXn00b/HermesApp/releases/latest)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://www.android.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> **Let AI truly control your Android phone.**

A 1:1 Kotlin port of the [Hermes](https://github.com/nicepkg/hermes) agent loop embedded into Android, delivering full AI Agent capabilities on your phone: see the screen, tap apps, run code, connect platforms.

**[Docs (Chinese)](https://vcn23e479dhx.feishu.cn/wiki/UZtFwM6t9iArPVkMvRccwSran6d)** | **[Quick Start](#quick-start)** | **[Community](#community)** | **[中文文档](README.md)**

---

## What Can AI Do for You

**Control Any App** — WeChat, Alipay, TikTok, Taobao, Maps… Anything you can do manually, AI can do too.

```
You: Open WeChat and send "See you tomorrow" to John
AI:  → Open WeChat → Search John → Type message → Send ✅
```

**Cross-App Workflows** — Copy address from WeChat → Open Maps → Search → Start navigation

**Run Code** — Execute Python/Node.js/Shell via Termux SSH or built-in Shell

**Web Search & Fetch** — Brave search + full page content fetch

**Multi-Platform Messaging** — Control your phone AI remotely via Feishu or Discord

**MCP Server** — Expose phone accessibility/screenshot to external agents like Claude Desktop, Cursor

**Skill Extensions** — Install new capabilities from [ClawHub](https://clawhub.com) or create your own

---

## Quick Start

### Download & Install

Download from the [Release page](https://github.com/SelectXn00b/HermesApp/releases/latest):

| APK | Description | Required? |
|-----|-------------|-----------|
| **Hermes** | Main app (Accessibility Service, Agent, Gateway) | Required |
| **BrowserForClaw** | AI Browser (web automation) | Optional |

> Termux should be installed from [F-Droid](https://f-droid.org/packages/com.termux/) (not Play Store). Without it, basic shell commands still work. With it, you get a full Linux environment.

### 3 Steps to Get Started

1. **Install** — Download and install Hermes
2. **Configure** — Open the app, enter an API Key (or skip to use built-in Key), enable Accessibility + Screen Capture permissions
3. **Chat** — Talk directly in the app, or send messages via Feishu/Discord

> First launch opens a setup wizard automatically. Default: OpenRouter + MiMo V2 Pro. One-click skip supported.

---

## Architecture

```
┌──────────────────────────────────────────┐
│  Channels                                 │
│  Feishu · Discord · In-app chat           │
├──────────────────────────────────────────┤
│  Agent Runtime                            │
│  AgentLoop · 65+ Tools · 27 Skills ·      │
│  Context Management · Memory ·            │
│  Skill Recorder                           │
├──────────────────────────────────────────┤
│  Providers                                │
│  OpenRouter · MiMo · Gemini · Anthropic · │
│  OpenAI · Custom OpenAI-compatible        │
├──────────────────────────────────────────┤
│  Android Platform                         │
│  Accessibility · Termux SSH · device tool │
│  MediaProjection · BrowserForClaw         │
└──────────────────────────────────────────┘
```

### Core Features

| Feature | Description |
|---------|-------------|
| **Playwright Mode** | Screen ops aligned with Playwright — `snapshot` gets UI tree + ref → `act` operates elements |
| **Unified exec** | Auto-routes to Termux SSH (connection pool + auto-reconnect) or built-in Shell |
| **Context Management** | 4-layer protection: limitHistoryTurns + tool result trimming + budget guard + auto-summarization |
| **Model Smart Routing** | Model ID normalization + Fallback Chain + API Key rotation + Allowlist/Blocklist |
| **Gateway SLOT Isolation** | Each channel message processed via independent ChatRuntimeSlot.GATEWAY, isolated from main UI |
| **Skill System** | 27 built-in Skills editable on device, ClawHub online installation |
| **Skill Recorder** | Record user operations step-by-step, AI auto-generates reusable Skills |
| **Multi-model** | MiMo V2 Pro · DeepSeek R1 · Claude Sonnet 4 · Gemini 2.5 · GPT-4.1 |
| **MCP Server** | Expose accessibility/screenshot to external agents (port 8399, Streamable HTTP) |
| **Per-channel Model** | Each messaging channel can independently select a model |

---

## Full Capability Table

### General Tools

| Tool | Function |
|------|----------|
| `device` | Screen ops: snapshot / tap / type / scroll / press / open (Playwright mode) |
| `read_file` | Read file contents |
| `write_file` | Create or overwrite files |
| `edit_file` | Precise file editing (diff mode) |
| `list_dir` | List directory contents |
| `exec` | Execute commands (Termux SSH / built-in Shell) |
| `web_search` | Brave search engine |
| `web_fetch` | Fetch web page content |
| `javascript` | Execute JavaScript (QuickJS) |
| `tts` | Text-to-speech |
| `skills_search` | Search ClawHub skills |
| `skills_install` | Install skills from ClawHub |
| `config_get` / `config_set` | Read/write config entries |
| `memory_search` / `memory_get` | Semantic search/read memory |

### Android-Specific Tools

| Tool | Function |
|------|----------|
| `list_installed_apps` | List installed apps |
| `install_app` | Install APK |
| `start_activity` | Launch Activity |
| `feishu_send_image` | Send image via Feishu |
| `eye` | Camera capture |
| `log` | View system logs |
| `stop` | Stop the Agent |

### Feishu Tools (39)

| Category | Description |
|----------|-------------|
| Docs | Get / Create / Update / Media / Comments |
| Wiki | Spaces / Nodes |
| Drive | File operations |
| Bitable | Apps / Tables / Fields / Records / Views |
| Tasks | Tasks / Subtasks / Comments |
| Chat | Group management / Member management |
| Calendar | Calendars / Events / Free-busy query |
| Messages | Send / Search / Resources |
| Search | Search docs / Wiki |

### 27 Skills

| Category | Skills |
|----------|--------|
| Feishu Suite | `feishu` · `feishu-doc` · `feishu-wiki` · `feishu-drive` · `feishu-bitable` · `feishu-chat` · `feishu-task` · `feishu-perm` · `feishu-urgent` · `feishu-calendar` · `feishu-common` · `feishu-im` · `feishu-search` · `feishu-sheets` |
| Search & Web | `browser` · `weather` · `lark-cli` |
| Skill Management | `clawhub` · `skill-creator` |
| Dev & Debug | `debugging` · `data-processing` · `session-logs` · `context-security` |
| Config Management | `model-config` · `channel-config` · `install-app` · `model-usage` |

> Skills are stored at `/sdcard/.hermes/skills/` — freely editable, addable, and removable.

### Messaging Channels

| Channel | Status | Features |
|---------|--------|----------|
| **Feishu** | Available | WebSocket real-time, group/DM, 39 Feishu tools, streaming card replies |
| **Discord** | Available | Gateway v10, group/DM, DM policy management |
| **In-app Chat** | Available | Built-in chat UI, multi-session management |
| **Telegram** | In development | — |
| **Slack** | In development | — |

### Supported Models

| Provider | Models | Notes |
|----------|--------|-------|
| **OpenRouter** | MiMo V2 Pro, Hunter Alpha, DeepSeek R1, Claude Sonnet 4, GPT-4.1 | Recommended, built-in Key |
| **Xiaomi MiMo** | MiMo V2 Pro, MiMo V2 Flash, MiMo V2 Omni | Direct Xiaomi API |
| **Google** | Gemini 2.5 Pro, Gemini 2.5 Flash | Direct |
| **Anthropic** | Claude Sonnet 4, Claude Opus 4 | Direct |
| **OpenAI** | GPT-4.1, GPT-4.1 Mini, o3 | Direct |
| **Custom** | Any OpenAI-compatible API | Ollama, vLLM, etc. |

> **Default**: OpenRouter + MiMo V2 Pro (1M context + reasoning). Skip the wizard to auto-use built-in Key.

---

## Configuration

`/sdcard/.hermes/config/openclaw.json`

```json
{
  "models": {
    "providers": {
      "openrouter": {
        "baseUrl": "https://openrouter.ai/api/v1",
        "apiKey": "sk-or-v1-your-key",
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

See **[Feishu Docs](https://vcn23e479dhx.feishu.cn/wiki/UZtFwM6t9iArPVkMvRccwSran6d)** for detailed configuration reference.

---

## Build from Source

```bash
git clone https://github.com/SelectXn00b/HermesApp.git
cd HermesApp
export JAVA_HOME=/path/to/jdk21
./gradlew assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
```

---

## Related Projects

| Project | Description |
|---------|-------------|
| [Hermes](https://github.com/nicepkg/hermes) | AI Agent framework (Desktop) |
| [Hermes for Android](https://github.com/SelectXn00b/HermesApp) | Hermes Android client (this project) |

---

## Community

<div align="center">

#### Feishu Group

[![Join Feishu Group](docs/images/feishu-qrcode.jpg)](https://applink.feishu.cn/client/chat/chatter/add_by_link?link_token=566r8836-6547-43e0-b6be-d6c4a5b12b74)

**[Click to join Feishu Group](https://applink.feishu.cn/client/chat/chatter/add_by_link?link_token=566r8836-6547-43e0-b6be-d6c4a5b12b74)**

---

#### Discord

[![Discord](https://img.shields.io/badge/Discord-Join%20Server-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/rDpaFym2b8)

**[Join Discord](https://discord.gg/rDpaFym2b8)**

---

#### WeChat Group

<img src="docs/images/wechat-qrcode.png" width="300" alt="WeChat Group QR Code">

**Scan to join WeChat group** — Valid for 7 days

</div>

---

## Links

- [Hermes](https://github.com/nicepkg/hermes) — Architecture reference
- [ClawHub](https://clawhub.com) — Skill marketplace
- [Architecture Doc](ARCHITECTURE.md) — Detailed design

---

## License

MIT — [LICENSE](LICENSE)

---

<div align="center">

**If this project helps you, please give it a Star!**

</div>
