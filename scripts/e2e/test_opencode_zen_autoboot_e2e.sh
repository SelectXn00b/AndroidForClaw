#!/usr/bin/env bash
# End-to-end 验证：全自动新用户首启 → OpenCode Zen public-key 兜底（R-AGENT-002, TC-AGENT-200-h）
#
# 与 test_builtin_key_e2e.sh 的关键区别：
#   - test_builtin_key_e2e.sh：清 DataStore + 显式广播 SET_API_KEY 写默认值（模拟点按钮）
#   - test_opencode_zen_autoboot_e2e.sh：清 DataStore + **不**广播任何 API 配置，
#     直接发 EXTERNAL_CHAT，验证 ModelConfigManager.initializeIfNeeded() 在
#     冷启动时自动 seed apiProviderType=OPENCODE_ZEN, apiKey="public",
#     model=OpenCodeZenDefaults.selectDefaultFreeModel(context)
#
# 退出码：0=PASS，非 0=FAIL

set -euo pipefail

PKG="com.xiaomo.androidforclaw"
MAIN_ACTIVITY="${PKG}/com.ai.assistance.operit.ui.main.MainActivity"
CHAT_RECEIVER="${PKG}/com.ai.assistance.operit.integrations.intent.ExternalChatReceiver"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

WAIT_AFTER_LAUNCH_S="${WAIT_AFTER_LAUNCH_S:-8}"
MAX_WAIT_S="${MAX_WAIT_S:-180}"

cd "$(dirname "$0")/../.."

log() { printf '\033[1;36m[e2e-zen-autoboot]\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m[FAIL]\033[0m %s\n' "$*" >&2; exit 1; }
pass() { printf '\033[1;32m[PASS]\033[0m %s\n' "$*"; }

### 1. 设备
DEVICE="${ADB_DEVICE:-}"
if [[ -z "$DEVICE" ]]; then
  DEVICE="$(adb devices | awk 'NR>1 && $2=="device" && $1 ~ /^emulator-/{print $1; exit}')"
  [[ -z "$DEVICE" ]] && DEVICE="$(adb devices | awk 'NR>1 && $2=="device"{print $1; exit}')"
fi
[[ -n "$DEVICE" ]] || fail "no adb device"
log "device=$DEVICE"
ADB="adb -s $DEVICE"

### 2. 安装 + 清 API DataStore（保留 Keystore + gateway prefs）
[[ -f "$APK_PATH" ]] || fail "$APK_PATH not found, build first: ./gradlew :app:assembleDebug"
log "force-stop any running $PKG"
$ADB shell am force-stop "$PKG" >/dev/null || true
log "installing $APK_PATH"
$ADB install -r -t "$APK_PATH" >/dev/null

log "wiping API-specific DataStore (preserves Keystore + gateway creds)"
$ADB shell "run-as $PKG sh -c 'rm -f files/datastore/api_settings.preferences_pb files/datastore/model_configs.preferences_pb 2>/dev/null; true'" >/dev/null || true
sleep 1

### 3. 启动 app（冷启动；让 ModelConfigManager.initializeIfNeeded 自动 seed）
log "launching app (cold start, autoboot path — NO SET_API_KEY broadcast)"
$ADB logcat -c
$ADB shell am start -n "$MAIN_ACTIVITY" >/dev/null
sleep "$WAIT_AFTER_LAUNCH_S"

### 4. 直接发 EXTERNAL_CHAT（无任何 API 配置广播；agent 必须能从 autoboot 默认值连出去）
REQ_ID="e2e-zen-autoboot-$(date +%s)"
TOKEN="HERMES_E2E_ZEN_$((RANDOM))"
MSG="请严格只用一行回复，回复内容必须以 $TOKEN 开头，不要加任何其他前缀或 XML。"
log "sending chat message requestId=$REQ_ID token=$TOKEN (autoboot — no API config broadcast)"
$ADB logcat -c
$ADB shell "am broadcast \
  -n '$CHAT_RECEIVER' \
  -a com.ai.assistance.operit.EXTERNAL_CHAT \
  --es request_id '$REQ_ID' \
  --es message '$MSG' \
  --ez return_tool_status false \
  --ez create_new_chat true \
  --ez show_floating true" >/dev/null

### 5. 监听 logcat（agent-level：aiResponsePreview 含 TOKEN）
START_TS=$(date +%s)
RESULT_BCAST_PAT="ExternalChatReceiver.*Result broadcast: requestId=$REQ_ID success=true"
FAIL_PAT='User not found|status code: 40[0-9]|NonRetriableException|error.*code.*40[0-9]'

while :; do
  NOW=$(date +%s)
  ELAPSED=$((NOW - START_TS))
  if (( ELAPSED > MAX_WAIT_S )); then
    log "--- last 40 log lines ---"
    $ADB logcat -d -v time 2>/dev/null | grep -E "AIService|Hermes|OpenRouter|ExternalChat|MessageProcessing|ApiConfig|ModelConfigManager" | tail -40 || true
    fail "timeout ${MAX_WAIT_S}s waiting for agent-level completion"
  fi

  LOG="$($ADB logcat -d -v time 2>/dev/null || true)"
  if echo "$LOG" | grep -Eq "$FAIL_PAT"; then
    log "--- failing log ---"
    echo "$LOG" | grep -E "$FAIL_PAT|AIService|HermesAgentLoop|ModelConfigManager" | tail -20
    fail "saw auth/4xx error after ${ELAPSED}s — autoboot did not seed default config correctly"
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
    pass "OpenCode Zen autoboot agent-level chat turn completed after ${ELAPSED}s (tokenOK=yes)"
    printf '  TOKEN=%s found in aiResponsePreview\n' "$TOKEN"
    echo "$LOG" | grep -E "$RESULT_BCAST_PAT" | tail -1
    HEAD=$(git rev-parse HEAD 2>/dev/null || echo unknown)
    MARKER_DIR="scripts/e2e"
    : > "$MARKER_DIR/.green-zen-autoboot"
    echo -n "$HEAD" > "$MARKER_DIR/.green-zen-autoboot"
    exit 0
  fi
  sleep 2
done
