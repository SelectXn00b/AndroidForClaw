package com.ai.assistance.operit.hermes.gateway

import com.xiaomo.hermes.hermes.gateway.platforms.PlatformDiagSink

/**
 * R-OBS-001: bridge from the hermes-android-side `PlatformDiagSink`
 * interface to the app-side `WeixinFileLogger` singleton.
 *
 * Installed onto `WeixinAdapter._diagSink` by `HermesGatewayController`
 * on first `dispatchOutgoing(platform = "weixin", ...)` call.  This
 * keeps the layering rule intact: `hermes-android` only sees the
 * interface; the implementation that writes to
 * `/sdcard/Download/Hermes/cron_logs/weixin.log` lives in `app/`.
 */
object WeixinFileLoggerDiagSink : PlatformDiagSink {
    override fun i(tag: String, msg: String) = WeixinFileLogger.i(tag, msg)
    override fun w(tag: String, msg: String) = WeixinFileLogger.w(tag, msg)
    override fun e(tag: String, msg: String) = WeixinFileLogger.e(tag, msg)
    override fun d(tag: String, msg: String) = WeixinFileLogger.d(tag, msg)
}
