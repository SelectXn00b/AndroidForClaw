package com.xiaomo.hermes.hermes.gateway.platforms

/**
 * R-OBS-001: cross-module diagnostic sink for platform adapters.
 *
 * `hermes-android/` cannot reverse-depend on `app/`'s `WeixinFileLogger`
 * (single-direction module layering, see CLAUDE.md §1).  This interface
 * is declared in `hermes-android/` so adapters can call into a diag sink
 * by interface; the `app/` side instantiates an implementation that
 * forwards to `WeixinFileLogger` and injects it via the
 * `HermesGatewayController` when the adapter is constructed.
 *
 * Methods are abstract — adapters always call through nullable sink
 * references (`_diagSink?.i(...)`), so callers never need a default
 * no-op.  Bridge implementations in `app/` (e.g.
 * `WeixinFileLoggerDiagSink`) provide the real forwarding.
 */
interface PlatformDiagSink {
    fun i(tag: String, msg: String)
    fun w(tag: String, msg: String)
    fun e(tag: String, msg: String)
    fun d(tag: String, msg: String)
}
