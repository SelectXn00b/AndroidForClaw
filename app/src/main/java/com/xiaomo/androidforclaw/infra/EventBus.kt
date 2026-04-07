package com.xiaomo.androidforclaw.infra

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

/**
 * OpenClaw module: infra
 * Source: OpenClaw/src/infra/agent-events.ts
 *
 * Typed event bus using global-singleton pattern.
 * Aligned with TS registerListener / notifyListeners pattern.
 */

/** Unsubscribe handle returned by registerListener. */
fun interface Unsubscribe {
    fun invoke()
}

/**
 * Typed event bus. Each event type T gets its own bus instance.
 *
 * Usage:
 *   val bus = EventBus<MyEvent>()
 *   val unsub = bus.registerListener { event -> ... }
 *   bus.emit(MyEvent(...))
 *   unsub()
 */
class EventBus<T : Any> {
    private val listeners = CopyOnWriteArraySet<(T) -> Unit>()
    private val sequenceCounter = AtomicLong(0)

    /** Register a listener. Returns an Unsubscribe handle. */
    fun registerListener(listener: (T) -> Unit): Unsubscribe {
        listeners.add(listener)
        return Unsubscribe { listeners.remove(listener) }
    }

    /** Notify all registered listeners with the given event. */
    fun emit(event: T) {
        sequenceCounter.incrementAndGet()
        for (listener in listeners) {
            try {
                listener(event)
            } catch (_: Exception) {
                // best-effort: don't let one listener crash others
            }
        }
    }

    /** Current listener count. */
    val listenerCount: Int get() = listeners.size

    /** Events emitted since creation (monotonic counter). */
    val sequence: Long get() = sequenceCounter.get()

    /** Remove all listeners. */
    fun clear() = listeners.clear()
}

/**
 * Global singleton event bus registry.
 * Aligned with TS resolveGlobalSingleton pattern for named event buses.
 */
object GlobalEventBus {
    private val buses = ConcurrentHashMap<String, EventBus<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> resolve(name: String): EventBus<T> {
        return buses.getOrPut(name) { EventBus<T>() } as EventBus<T>
    }

    fun remove(name: String) {
        buses.remove(name)
    }

    fun clearAll() {
        buses.values.forEach { it.clear() }
        buses.clear()
    }
}
