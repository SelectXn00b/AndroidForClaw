#!/usr/bin/env bash
# End-to-end 验证：新用户 → OpenCode Zen public-key 兜底路径（R-AGENT-002, TC-AGENT-200-c）
#
# 模拟"全新安装 + 点击 使用 OpenCode Zen 免费模型 按钮"的场景：
#   1. run-as rm 清掉 api_settings + model_configs DataStore（保留 Keystore + gateway 凭证）
#   2. 安装 APK
#   3. 广播 SET_API_KEY，写入 default 配置（provider=OPENCODE_ZEN, key="public",
#      endpoint=opencode.ai/zen/v1/chat/completions, model=nemotron-3-ultra-free）
#      —— 与 ConfigurationScreen 里点 "使用 OpenCode Zen 免费模型" 按钮等价
#   4. 启动 app
#   5. 发 external chat（带 TOKEN，要求 agent 一行回显 TOKEN）
#   6. 从 logcat 解析 aiResponsePreview，断言含 TOKEN
#
# 退出码：0=PASS，非 0=FAIL
#
# 文件名保留 test_builtin_key_e2e.sh 维持脚本调用入口稳定（marker .green-builtin-key
# 与 Stop hook 协作）；脚本语义已改为 OpenCode Zen public-key 路径。

set -euo pipefail

PKG="com.xiaomo.androidforclaw"
MAIN_ACTIVITY="${PKG}/com.ai.assistance.operit.ui.main.MainActivity"
API_RECEIVER="${PKG}/com.ai.assistance.operit.integrations.intent.ApiConfigReceiver"
CHAT_RECEIVER="${PKG}/com.ai.assistance.operit.integrations.intent.ExternalChatReceiver"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

WAIT_AFTER_LAUNCH_S="${WAIT_AFTER_LAUNCH_S:-6}"
MAX_WAIT_S="${MAX_WAIT_S:-180}"

cd "$(dirname "$0")/../.."

log() { printf '\033[1;36m[e2e-builtin]\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m[FAIL]\033[0m %s\n' "$*" >&2; exit 1; }
pass() { printf '\033[1;32m[PASS]\033[0m %s\n' "$*"; }

### 0. OpenCode Zen public-key constants
# 与 hermes-android/.../OpenCodeZenCatalog.kt 锁住的 4 个常量同步：
#   PROVIDER_ID = "opencode-zen"          (registry name only)
#   PUBLIC_API_KEY = "public"             (literal — not a secret)
#   DEFAULT_ENDPOINT = "https://opencode.ai/zen/v1/chat/completions"
#   BASELINE_FREE_MODEL = "nemotron-3-ultra-free"
#     (verified against live https://opencode.ai/zen/v1/models with
#      Authorization: Bearer public — earlier candidates `qwen/qwen3-coder`
#      and `grok-code` are present in models.dev's `opencode` provider but
#      the live endpoint returns 401 ModelError for them.
#      2026-06-18: switched from `nemotron-3-super-free` (now removed from
#      the live catalog — request returns
#      `401 ModelError: Model nemotron-3-super-free is not supported`) to
#      `nemotron-3-ultra-free`, the in-list `-free` successor verified to
#      respond on `Bearer public`.)
KEY="public"
PROVIDER="OPENCODE_ZEN"
ENDPOINT="https://opencode.ai/zen/v1/chat/completions"
MODEL="nemotron-3-ultra-free"
log "OpenCode Zen public-key path: provider=$PROVIDER endpoint=$ENDPOINT model=$MODEL"

### 1. 设备
DEVICE="${ADB_DEVICE:-}"
if [[ -z "$DEVICE" ]]; then
  DEVICE="$(adb devices | awk 'NR>1 && $2=="device" && $1 ~ /^emulator-/{print $1; exit}')"
  [[ -z "$DEVICE" ]] && DEVICE="$(adb devices | awk 'NR>1 && $2=="device"{print $1; exit}')"
fi
[[ -n "$DEVICE" ]] || fail "no adb device"
log "device=$DEVICE"
ADB="adb -s $DEVICE"

### 2. 安装 + 目标性清 API 相关的 DataStore
### 原先用 pm clear 会把 Android Keystore 里的 EncryptedSharedPreferences master key 一起抹掉
### (E/keystore2: Error::Km(VERIFICATION_FAILED)) 导致 gateway 加密凭证失活；改成 run-as rm
### 只删 api_settings.preferences_pb，Keystore + gateway prefs + 其他一切都保留
[[ -f "$APK_PATH" ]] || fail "$APK_PATH not found, build first: ./gradlew :app:assembleDebug"
log "force-stop any running $PKG"
$ADB shell am force-stop "$PKG" >/dev/null || true
log "installing $APK_PATH"
$ADB install -r -t "$APK_PATH" >/dev/null

log "wiping API-specific DataStore (preserves Keystore + gateway creds)"
# 只删 API key 相关的 DataStore 文件，确保广播 SET_API_KEY 走到 new-user 默认路径
# 其他文件（character_cards / custom_emoji / user_preferences / hermes_gateway_*）全部保留
$ADB shell "run-as $PKG sh -c 'rm -f files/datastore/api_settings.preferences_pb files/datastore/model_configs.preferences_pb 2>/dev/null; true'" >/dev/null || true
sleep 1

### 3. 启动 app（模拟新用户首次打开 app，等 DataStore 初始化）
log "launching app (cold start with wiped api_settings)"
$ADB logcat -c
$ADB shell am start -n "$MAIN_ACTIVITY" >/dev/null
sleep "$WAIT_AFTER_LAUNCH_S"

### 4. 广播 OpenCode Zen public-key 配置（等价于新用户点"使用 OpenCode Zen 免费模型"按钮）
log "broadcasting OpenCode Zen public-key config"
$ADB shell am broadcast \
  -n "$API_RECEIVER" \
  -a com.ai.assistance.operit.SET_API_KEY \
  --es key "$KEY" \
  --es provider "$PROVIDER" \
  --es endpoint "$ENDPOINT" \
  --es model "$MODEL" >/dev/null

# 延长到 40*0.5=20s，cold start 后 DataStore/ModelConfigManager 初始化可能耗时
for i in $(seq 1 40); do
  if $ADB logcat -d -v time -s ApiConfigReceiver:I 2>/dev/null | grep -q "Updated config"; then
    break
  fi
  sleep 0.5
done
$ADB logcat -d -v time -s ApiConfigReceiver:I | grep "Updated config" | tail -1 \
  >/dev/null || fail "config receiver did not log Updated config"
log "config applied"

### 5. 发送真实 chat 广播（带 TOKEN，agent-level 验收）
REQ_ID="e2e-builtin-$(date +%s)"
TOKEN="HERMES_E2E_BUILTIN_OK_$((RANDOM))"
MSG="请严格只用一行回复，回复内容必须以 $TOKEN 开头，不要加任何其他前缀或 XML。"
log "sending chat message requestId=$REQ_ID token=$TOKEN"
$ADB logcat -c
$ADB shell "am broadcast \
  -n '$CHAT_RECEIVER' \
  -a com.ai.assistance.operit.EXTERNAL_CHAT \
  --es request_id '$REQ_ID' \
  --es message '$MSG' \
  --ez return_tool_status false \
  --ez create_new_chat true \
  --ez show_floating true" >/dev/null

### 6. 监听 logcat（agent-level：断言 aiResponsePreview 里含 TOKEN）
START_TS=$(date +%s)
RESULT_BCAST_PAT="ExternalChatReceiver.*Result broadcast: requestId=$REQ_ID success=true"
FAIL_PAT='User not found|status code: 40[0-9]|NonRetriableException|error.*code.*40[0-9]'

while :; do
  NOW=$(date +%s)
  ELAPSED=$((NOW - START_TS))
  if (( ELAPSED > MAX_WAIT_S )); then
    log "--- last 40 log lines ---"
    $ADB logcat -d -v time 2>/dev/null | grep -E "AIService|Hermes|OpenRouter|ExternalChat|MessageProcessing|ApiConfig" | tail -40 || true
    fail "timeout ${MAX_WAIT_S}s waiting for agent-level completion"
  fi

  LOG="$($ADB logcat -d -v time 2>/dev/null || true)"
  if echo "$LOG" | grep -Eq "$FAIL_PAT"; then
    log "--- failing log ---"
    echo "$LOG" | grep -E "$FAIL_PAT|AIService|HermesAgentLoop" | tail -20
    fail "saw auth/4xx error after ${ELAPSED}s"
  fi

  RESULT_LINE="$(echo "$LOG" | grep -E "$RESULT_BCAST_PAT" | tail -1 || true)"
  if [[ -n "$RESULT_LINE" ]]; then
    PREVIEW="$(printf '%s' "$RESULT_LINE" | sed -n 's/.*aiResponsePreview=<<<\(.*\)>>>.*/\1/p')"
    if [[ -z "$PREVIEW" ]]; then
      log "--- empty aiResponsePreview; raw result line ---"
      printf '%s\n' "$RESULT_LINE"
      fail "aiResponsePreview empty — agent produced no final reply text"
    fi
    if ! printf '%s' "$PREVIEW" | grep -Fq "$TOKEN"; then
      log "--- TOKEN missing from aiResponsePreview ---"
      log "expected TOKEN=$TOKEN"
      log "preview (first 800 chars): ${PREVIEW:0:800}"
      fail "agent reply missing TOKEN — chat turn completed but text is wrong"
    fi
    pass "builtin-key agent-level chat turn completed after ${ELAPSED}s (tokenOK=yes)"
    printf '  TOKEN=%s found in aiResponsePreview\n' "$TOKEN"
    echo "$LOG" | grep -E "$RESULT_BCAST_PAT" | tail -1
    # write last-green marker so Stop hook accepts this SHA
    HEAD=$(git rev-parse HEAD 2>/dev/null || echo unknown)
    MARKER_DIR="scripts/e2e"
    : > "$MARKER_DIR/.green-builtin-key"
    echo -n "$HEAD" > "$MARKER_DIR/.green-builtin-key"
    exit 0
  fi
  sleep 2
done
