package com.xiaomo.hermes

import androidx.multidex.MultiDexApplication

/**
 * Custom Application for instrumentation tests
 * Enables MultiDex to ensure all test dependencies are loaded
 */
class TestApplication : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
    }
}
