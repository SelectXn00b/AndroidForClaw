package com.xiaomo.androidforclaw.ui.skills

import kotlinx.serialization.Serializable

/**
 * Skills 市场数据模型
 */
data class SkillItem(
    val slug: String,
    val name: String,
    val author: String,
    val description: String,
    val downloads: String = "",
    val category: String = "",
    val clawhubUrl: String = "",
)

data class SkillCollection(
    val name: String,
    val description: String,
    val url: String,
    val source: String = "", // "github" / "website"
    val coverEmoji: String = "📦",
    val stats: String = "",
)

data class SkillCategory(
    val label: String,
    val emoji: String,
)

/**
 * Skills 市场静态数据源
 */
object SkillsMarketData {

    val categories = listOf(
        SkillCategory("全部", "🌐"),
        SkillCategory("自动化", "🔄"),
        SkillCategory("效率", "📊"),
        SkillCategory("开发工具", "💻"),
        SkillCategory("搜索研究", "🔍"),
        SkillCategory("通讯", "💬"),
        SkillCategory("智能家居", "🏠"),
        SkillCategory("安全", "🔒"),
        SkillCategory("自我进化", "🧠"),
    )

    /**
     * 来自 awesome-openclaw-skills 的热门 Skills
     */
    val featuredSkills = listOf(
        SkillItem("capability-evolver", "Capability Evolver", "autogame-17", "Agent 自我进化能力", "35K+", "自我进化", "https://clawhub.ai/autogame-17/capability-evolver"),
        SkillItem("self-improving-agent", "Self-Improving Agent", "pskoett", "从错误中学习，越用越聪明", "62.5K+", "自我进化", "https://clawhub.ai/pskoett/self-improving-agent"),
        SkillItem("gog", "GOG", "steipete", "Google Workspace 全家桶（Gmail/日历/Drive）", "14K+", "效率", "https://clawhub.ai/steipete/gog"),
        SkillItem("agent-browser", "Agent Browser", "TheSethRose", "自主浏览器自动化", "11K+", "自动化", "https://clawhub.ai/TheSethRose/agent-browser"),
        SkillItem("summarize", "Summarize", "steipete", "内容智能摘要（URL/YouTube/播客）", "10K+", "搜索研究", "https://clawhub.ai/steipete/summarize"),
        SkillItem("github", "GitHub", "openclaw", "PR/Issue 监控管理", "10K+", "开发工具", "https://clawhub.ai/openclaw/github"),
        SkillItem("mission-control", "Mission Control", "openclaw", "晨间任务简报聚合", "8K+", "效率", "https://clawhub.ai/openclaw/mission-control"),
        SkillItem("frontend-design", "Frontend Design", "openclaw", "生产级 UI 生成", "7K+", "开发工具", "https://clawhub.ai/openclaw/frontend-design"),
        SkillItem("slack", "Slack", "openclaw", "团队消息自动化", "6K+", "通讯", "https://clawhub.ai/openclaw/slack"),
        SkillItem("tavily", "Tavily", "openclaw", "AI 优化的网页搜索", "5K+", "搜索研究", "https://clawhub.ai/openclaw/tavily"),
        SkillItem("n8n-workflow", "N8N Workflow", "openclaw", "工作流编排引擎", "5K+", "自动化", "https://clawhub.ai/openclaw/n8n-workflow"),
        SkillItem("vercel", "Vercel", "openclaw", "自然语言部署", "4K+", "开发工具", "https://clawhub.ai/openclaw/vercel"),
        SkillItem("elevenlabs-agent", "ElevenLabs Agent", "openclaw", "语音合成与通话", "4K+", "通讯", "https://clawhub.ai/openclaw/elevenlabs-agent"),
        SkillItem("obsidian", "Obsidian", "openclaw", "知识库管理", "4K+", "效率", "https://clawhub.ai/openclaw/obsidian"),
        SkillItem("composio", "Composio", "openclaw", "860+ 工具集成平台", "3K+", "自动化", "https://clawhub.ai/openclaw/composio"),
        SkillItem("agent-memory", "Agent Memory", "dennis-da-menace", "持久记忆系统", "3K+", "自我进化", "https://clawhub.ai/Dennis-Da-Menace/agent-memory"),
        SkillItem("home-assistant", "Home Assistant", "openclaw", "本地智能家居控制", "3K+", "智能家居", "https://clawhub.ai/openclaw/home-assistant"),
        SkillItem("agent-autopilot", "Agent Autopilot", "edoserbia", "心跳驱动的自主任务执行", "2K+", "效率", "https://clawhub.ai/edoserbia/agent-autopilot"),
        SkillItem("security-auditor", "Security Auditor", "openclaw", "Skill 审计与监控", "2K+", "安全", "https://clawhub.ai/openclaw/security-auditor"),
        SkillItem("linear", "Linear", "openclaw", "Issue & Sprint 管理", "2K+", "开发工具", "https://clawhub.ai/openclaw/linear"),
        SkillItem("discord", "Discord", "openclaw", "社区管理", "2K+", "通讯", "https://clawhub.ai/openclaw/discord"),
        SkillItem("exa-search", "Exa Search", "openclaw", "开发者专用搜索", "2K+", "搜索研究", "https://clawhub.ai/openclaw/exa-search"),
    )

    /**
     * 热门 Skill 集合（精选合集）
     */
    val collections = listOf(
        SkillCollection("VoltAgent 精选合集", "从 13,700+ 技能中筛选 5,400+ 优质技能", "https://github.com/VoltAgent/awesome-openclaw-skills", "github", "🦞", "5,400+ skills · 21.8K ⭐"),
        SkillCollection("Sundial Top Skills", "最热门、最实用的技能精选集合", "https://github.com/sundial-org/awesome-openclaw-skills", "github", "⭐", "定期更新"),
        SkillCollection("中文必装清单", "从 13,000+ 技能中精选 10 大必装，国内友好", "https://www.cnblogs.com/informatics/p/19679935", "website", "🇨🇳", "10 大必装"),
        SkillCollection("5000+ 工作流指南", "10 分钟搭出能落地的工作流", "https://www.zymn.cc/2026/03/22/openclaw-skills-guide/", "website", "⚡", "实战教程"),
        SkillCollection("阿里云精选榜", "10,000+ 技能精选 + 部署教程", "https://developer.aliyun.com/article/1714848", "website", "☁️", "含部署教程"),
        SkillCollection("awesome-openclaw 资源库", "skills + plugins + Dashboard + Memory 大全", "https://github.com/alvinreal/awesome-openclaw", "github", "📚", "全方位资源"),
    )

    /**
     * 底部聚合内容列表
     */
    val aggregatedResources = listOf(
        SkillItem("clawhub-ai", "ClawHub 官方市场", "OpenClaw", "官方技能注册表，13,700+ 社区技能", "13,700+", "", "https://clawhub.ai"),
        SkillItem("aiagentstore", "AI Agent Store 精选", "aiagentstore.ai", "2,999+ 精选分类集合", "2,999+", "", "https://aiagentstore.ai/ai-agent/awesome-openclaw-skills"),
        SkillItem("openclaw-hub", "OpenClaw Hub 排行", "openclaw-hub.org", "按下载/星标排名的技能排行榜", "", "", "https://openclaw-hub.org/openclaw-hub-top-skills.html"),
        SkillItem("vincentkoc", "Vincent Koc 资源集", "vincentkoc", "skills + plugins + MCP tools + 部署栈", "", "", "https://github.com/vincentkoc/awesome-openclaw"),
        SkillItem("kdnuggets", "KDnuggets 10 大仓库", "kdnuggets", "10 个必学的 OpenClaw GitHub 仓库", "", "", "https://www.kdnuggets.com/10-github-repositories-to-master-openclaw"),
        SkillItem("thunderbit", "Thunderbit Top 10", "thunderbit", "2026 年最佳 OpenClaw 技能指南", "", "", "https://thunderbit.com/blog/best-skills-for-openclaw"),
    )
}
