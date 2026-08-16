package com.oregontrail.wear.platform

import kotlinx.coroutines.CoroutineDispatcher

/**
 * The handful of things the game needs that Kotlin's common standard library does not
 * have — and nothing else.
 *
 * This file is deliberately short and deliberately dull. The rule the port is built on is
 * that `core/` never learns there is a platform at all, and everything above it learns as
 * little as possible: four declarations, each one a thing a watch and a browser genuinely
 * do differently. Anything longer than this list is a sign that a screen has grown a
 * platform assumption that should have been designed out instead.
 */

/**
 * A monotonic-ish clock reading, used once: to seed a run's RNG.
 *
 * Nothing depends on the unit or the epoch, only on successive runs getting different
 * values, so the browser's millisecond-resolution timers are as good here as the JVM's
 * nanosecond ones. It is *not* a wall clock — [com.oregontrail.wear.core.TrailDate] keeps
 * the game's own calendar and never consults this.
 */
expect fun seedFromClock(): Long

/**
 * Where the save file is written.
 *
 * A background dispatcher on the watch, because serialising the run and writing it costs
 * four filesystem round-trips that must not land in the frame drawing their result. The
 * browser has no worker to hand this to and `localStorage` is synchronous anyway, so
 * there the "background" is the main thread and the write is simply cheap enough.
 */
expect val storageDispatcher: CoroutineDispatcher

/**
 * Mutual exclusion around the save file.
 *
 * Real on the watch, where `flushSave()` runs on the main thread while the save worker
 * may be mid-write on another. A no-op in the browser, which has one thread and cannot
 * have two writers — the class exists there only so the call site can stay identical.
 */
expect class StorageLock() {
    fun <T> withLock(block: () -> T): T
}

/**
 * Whether this is a development build, which is the one thing gating the debug menu.
 *
 * A plain flag set by the platform's entry point rather than an `expect val`, because on
 * Android the honest answer is `BuildConfig.DEBUG` and a library module's `BuildConfig` is
 * not it — AGP hardcodes a library's to `false` regardless of the variant being built. So
 * `:app` passes down what it knows, and the browser build decides for itself.
 */
object BuildInfo {
    var isDebug: Boolean = false
}
