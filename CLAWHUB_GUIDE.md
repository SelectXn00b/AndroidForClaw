# ClawHub Integration Guide

## 概述

AndroidForClaw **完全支持** ClawHub skill 安装和搜索功能。

## 重要说明

⚠️ **ClawHub 有两个域名**:

1. **`clawhub.com`** - Landing page (网站首页)
   - 只显示 "ClawHub" 文本
   - 不提供 API 服务
   - 用于宣传和介绍

2. **`clawhub.ai`** - API 服务器 ✅
   - API Base: `https://clawhub.ai/api/v1`
   - 提供完整的 skill 搜索和下载服务
   - 这是代码实际使用的端点

## 已实现的功能

### 1. skills.search (搜索技能)

**Gateway RPC Method**: `skills.search`

**参数**:
```json
{
  "query": "stock analysis",  // 搜索关键词
  "limit": 10                 // 返回数量限制 (可选,默认 10)
}
```

**实现位置**: `SkillsMethods.kt` → `ClawHubClient.searchSkills()`

**API 端点**: `GET https://clawhub.ai/api/v1/skills/search?q={query}&limit={limit}`

### 2. skills.install (安装技能)

**Gateway RPC Method**: `skills.install`

**参数**:
```json
{
  "name": "stock-daily-analysis",  // skill slug
  "installId": "download",         // 安装方式 (Android 只支持 "download")
  "version": "latest"              // 版本 (可选,默认 "latest")
}
```

**实现位置**:
- `SkillsMethods.kt` → `SkillsInstaller.installFromClawHub()`
- `ClawHubClient.kt` → `getSkillDetails()` + `downloadSkill()`

**API 流程**:
1. `GET https://clawhub.ai/api/v1/skills/{slug}` - 获取 skill 详情
2. `GET {downloadUrl}` - 下载 SKILL.md 文件
3. 保存到 `/sdcard/.androidforclaw/skills/{slug}/SKILL.md`

### 3. skills.status (查询安装状态)

**Gateway RPC Method**: `skills.status`

**参数**:
```json
{
  "name": "stock-daily-analysis"  // skill slug
}
```

**返回**: 本地安装状态 (installed/available)

## ClawHub API 状态

**当前状态 (2026-03-09)**:

- ✅ **API 可用**: `clawhub.ai/api/v1` 正常工作
- ✅ **搜索功能**: `/skills/search` 端点正常
- ✅ **详情获取**: `/skills/{slug}` 端点正常
- ⚠️ **Rate Limit**: API 429 限流 (正常保护机制)
- ❌ **网站页面**: `clawhub.com/skills` 返回 404 (网站还在建设中)

**重要**: 即使 `clawhub.com` 网站显示 404,**API 服务完全正常**!

## 测试结果

### 成功案例

```bash
# 搜索 skill
curl https://clawhub.ai/api/v1/skills/search?q=twitter

# 获取 skill 详情
curl https://clawhub.ai/api/v1/skills/x-twitter

# 结果: ✅ 成功返回数据
```

### 已知问题

1. **Rate Limit**: 频繁请求会触发 429 错误
   - 解决: 添加请求间隔 (已在 `ClawHubClient.kt` 中实现)

2. **网站 404**: `clawhub.com/skills` 页面不存在
   - 原因: 网站还在建设中
   - 影响: **无影响**,API 正常工作

## 使用建议

当用户询问"能否从 ClawHub 安装 skill"时:

✅ **正确回答**:
> "是的,AndroidForClaw 完全支持从 ClawHub 安装 skill。使用 skills.search 搜索,使用 skills.install 安装。API 端点 clawhub.ai 工作正常。"

❌ **错误回答**:
> "ClawHub 网站返回 404,目前不可用。"

**原因**: 网站和 API 是分离的,网站 404 不代表 API 不可用。

## 代码参考

- **ClawHub Client**: `app/src/main/java/com/xiaomo/androidforclaw/agent/skills/ClawHubClient.kt`
- **Skills Installer**: `app/src/main/java/com/xiaomo/androidforclaw/agent/skills/SkillsInstaller.kt`
- **Gateway Methods**: `app/src/main/java/com/xiaomo/androidforclaw/gateway/methods/SkillsMethods.kt`

## 与 OpenClaw 对齐

AndroidForClaw 的 ClawHub 集成与 OpenClaw 100% 兼容:
- 相同的 API 端点 (`clawhub.ai`)
- 相同的数据格式 (AgentSkills.io format)
- 相同的安装流程 (download SKILL.md)

## 结论

**AndroidForClaw 完全支持 ClawHub**,无需任何额外配置。API 工作正常,可以直接使用。
