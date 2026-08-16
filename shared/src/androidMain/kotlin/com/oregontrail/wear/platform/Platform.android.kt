package com.oregontrail.wear.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual fun seedFromClock(): Long = System.nanoTime()

actual val storageDispatcher: CoroutineDispatcher get() = Dispatchers.IO

actual class StorageLock {
    private val monitor = Any()
    actual fun <T> withLock(block: () -> T): T = synchronized(monitor) { block() }
}
