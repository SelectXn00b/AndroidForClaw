package com.xiaomo.hermes.shared

/**
 * OpenClaw module: shared
 * Source: OpenClaw/src/shared/listeners.ts
 *
 * Generic listener notification and registration helpers.
 */

/** Notify all listeners with the given event. Errors are caught and optionally reported. */
fun <T> notifyListeners(
    listeners: Iterable<(T) -> Unit>,
    event: T,
    onError: ((Throwable) -> Unit)? = null
) {
    for (listener in listeners) {
        try {
            listener(event)
        } catch (e: Exception) {
            onError?.invoke(e)
        }
    }
}

/** Register a listener in a mutable set. Returns an unsubscribe function. */
fun <T> registerListener(
    listeners: MutableSet<(T) -> Unit>,
    listener: (T) -> Unit
): () -> Unit {
    listeners.add(listener)
    return { listeners.remove(listener) }
}
