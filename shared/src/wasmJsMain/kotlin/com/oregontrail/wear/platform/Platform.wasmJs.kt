package com.oregontrail.wear.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * `performance.now()` is fractional milliseconds since the page loaded, so on its own it
 * would hand every fresh tab the same short sequence of seeds — two runs started a second
 * into two visits would draw the same weather, the same illnesses, and the same river.
 * Mixed with the wall clock it is unique per run again, and the fractional part keeps two
 * runs started in the same millisecond apart.
 */
actual fun seedFromClock(): Long =
    wallClockMillis().toLong() xor (performanceNow() * 1_000_000).toLong()

private fun wallClockMillis(): Double = js("Date.now()")

private fun performanceNow(): Double = js("performance.now()")

/**
 * The main thread, because there is no other one. Wasm/JS has no worker the Kotlin
 * coroutines runtime can dispatch to here, and it does not matter: this dispatcher exists
 * to keep a four-round-trip filesystem write out of a frame, and the browser's equivalent
 * is a single synchronous `localStorage.setItem` of a few kilobytes.
 */
actual val storageDispatcher: CoroutineDispatcher get() = Dispatchers.Main

/** Nothing to lock. One thread, so `withLock` is the block. */
actual class StorageLock {
    actual fun <T> withLock(block: () -> T): T = block()
}
