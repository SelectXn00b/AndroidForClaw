# 📱 AndroidForClaw

[![Release](https://img.shields.io/badge/Release-v1.0.2-blue.svg)](https://github.com/xiaomochn/AndroidForClaw/releases/latest)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://www.android.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> **让 AI 真正掌控你的 Android 手机。**

底层架构对齐 [OpenClaw](https://github.com/openclaw/openclaw)（280k+ Star），在手机上实现完整的 AI Agent 能力——看屏幕、点 App、跑代码、连平台。

**[📖 详细文档](https://vcn23e479dhx.feishu.cn/wiki/UZtFwM6t9iArPVkMvRccwSran6d)** · **[🚀 快速开始](#-快速开始)** · **[💬 加入社区](#-社区)**

---

## 🔥 AI 能帮你做什么

### 📱 操控任何 App

微信、支付宝、抖音、淘宝、高德……**凡是你能手动操作的，AI 都能操作。**

```
你：帮我打开微信发消息给张三说"明天见"
AI：→ 打开微信 → 搜索张三 → 输入消息 → 发送 ✅
```

### 🔗 跨应用联动

```
你：微信收到一个地址，帮我导航过去
AI：→ 微信复制地址 → 打开高德 → 搜索 → 开始导航
```

### 🐧 执行代码

Python、Node.js、Shell——直接在手机上跑：

```
你：用 Python 帮我分析一下 Downloads 文件夹里的 CSV
AI：→ exec("python3 analyze.py") → 返回分析结果
```

### 🌐 搜索 & 抓取网页

```
你：搜一下今天的科技新闻
AI：→ web_search("科技新闻") → 返回标题+链接+摘要
```

### 💬 多平台消息

通过飞书、Discord 远程控制你的手机 AI：

| 渠道 | 状态 |
|------|------|
| 飞书 | ✅ 可用 |
| Discord | ✅ 可用 |
| Telegram · Slack · Signal · WhatsApp | 🔧 开发中 |

### 🧩 技能扩展

从 [ClawHub](https://clawhub.com) 搜索安装新能力，或自己创建 Skill：

```
你：看看 ClawHub 上有什么技能
AI：→ skills_search("") → 展示可用技能列表
```

---

## ⚡ 快速开始

### 下载安装

从 [Release 页面](https://github.com/xiaomochn/AndroidForClaw/releases/latest) 下载：

| APK | 说明 | 必装？ |
|-----|------|--------|
| **AndroidForClaw** | 主应用 (含无障碍服务、Agent、Gateway) | ✅ 必装 |
| **BrowserForClaw** | AI 浏览器 (网页自动化) | 可选 |
| **[Termux](https://f-droid.org/packages/com.termux/)** | 终端 (执行 Python/Node.js) | 可选 |

### 3 步上手

1. **安装** — 下载安装 AndroidForClaw
2. **配置** — 打开 App，输入 API Key（或跳过用内置免费 Key），开启无障碍 + 录屏权限
3. **开聊** — 直接对话，或通过飞书/Discord 发消息

> 💡 推荐注册 [OpenRouter](https://openrouter.ai/keys) 获取免费 API Key

### Termux 配置（可选）

装了 Termux，AI 就能跑 Python/Node.js/Shell。App 内置一键配置向导：

**设置 → Termux 配置 → 复制命令 → 粘贴到 Termux → 完成**

---

## 🏗️ 技术架构

```
324 源文件 · 62,000+ 行代码 · 10 个模块
```

```
┌──────────────────────────────────────────┐
│  Channels                                 │
│  飞书 · Discord · Telegram · Slack ·      │
│  Signal · WhatsApp · 设备内对话            │
├──────────────────────────────────────────┤
│  Agent Runtime                            │
│  AgentLoop · 19 Tools · 20 Skills ·       │
│  Context 管理 (4层防护) · Memory           │
├──────────────────────────────────────────┤
│  Providers                                │
│  OpenRouter · Azure · Anthropic · OpenAI  │
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
| **统一 exec** | 自动路由 Termux（SSH）或内置 Shell，对模型透明 |
| **Context 管理** | 4 层防护对齐 OpenClaw：limitHistoryTurns + 工具结果裁剪 + budget guard |
| **Skill 体系** | 20 个内置 Skill 可在设备上自由编辑，支持 ClawHub 在线安装 |
| **多模型** | GPT-5 / Claude / Gemini / DeepSeek / 任何 OpenAI 兼容 API |

---

## 🛠️ 配置

`/sdcard/.androidforclaw/openclaw.json`

```json
{
  "models": {
    "providers": {
      "openrouter": {
        "baseUrl": "https://openrouter.ai/api/v1",
        "apiKey": "sk-or-v1-你的key",
        "models": [{"id": "openrouter/hunter-alpha", "reasoning": true}]
      }
    }
  },
  "channels": {
    "feishu": { "enabled": true, "appId": "cli_xxx", "appSecret": "xxx" }
  }
}
```

详细配置参考 **[📖 飞书文档](https://vcn23e479dhx.feishu.cn/wiki/UZtFwM6t9iArPVkMvRccwSran6d)**

---

## 🔨 从源码构建

```bash
git clone https://github.com/xiaomochn/AndroidForClaw.git
cd AndroidForClaw
export JAVA_HOME=/path/to/jdk17
./gradlew assembleRelease
adb install releases/AndroidForClaw-v1.0.2-release.apk
```

---

## 📞 社区

<div align="center">

#### 飞书群

[![加入飞书群](docs/images/feishu-qrcode.jpg)](https://applink.feishu.cn/client/chat/chatter/add_by_link?link_token=566r8836-6547-43e0-b6be-d6c4a5b12b74)

**[点击加入飞书群](https://applink.feishu.cn/client/chat/chatter/add_by_link?link_token=566r8836-6547-43e0-b6be-d6c4a5b12b74)**

---

#### Discord

[![Discord](https://img.shields.io/badge/Discord-加入服务器-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/k9NKrXUN)

**[加入 Discord](https://discord.gg/k9NKrXUN)**

---

#### 微信群

<img src="docs/images/wechat-qrcode.png" width="300" alt="微信群二维码">

**扫码加入微信群** - 7天内有效

</div>

---

## 🔗 相关链接

- [OpenClaw](https://github.com/openclaw/openclaw) — 架构参照
- [ClawHub](https://clawhub.com) — 技能市场
- [源码映射](MAPPING.md) — OpenClaw ↔ AndroidForClaw 对照
- [架构文档](ARCHITECTURE.md) — 详细设计

---

## 📄 License

MIT — [LICENSE](LICENSE)

## 🙏 致谢

- **[OpenClaw](https://github.com/openclaw/openclaw)** — 架构灵感
- **[Claude](https://www.anthropic.com/claude)** — AI 推理能力

---

<div align="center">

⭐ **如果这个项目对你有帮助，请给个 Star 支持开源！** ⭐

</div>
