package com.oregontrail.wear.data

/**
 * One named slot of text that survives the app being killed.
 *
 * The two repositories in this package used to take a [java.io.File] each, which was the
 * right shape when a watch was the only thing that ran them: it kept Android's `Context`
 * out of the engine and let both be tested on the JVM. A browser has no files, so the
 * seam moves down one level — the repositories keep their logic and their format, and
 * only the four operations underneath change.
 *
 * Deliberately not a key-value *map*. Each slot is a separate implementation instance
 * because the two slots have to be independently destroyable: clearing a run must not be
 * able to take the high score table with it, and a single store keyed by name makes that
 * a convention rather than a fact. See [SaveRepository] and [HighScoreRepository].
 *
 * [write] returns whether it succeeded. Both callers already treat a failed save as
 * survivable — a lost run is bad, a crash is worse — so the failure is reported rather
 * than thrown.
 */
interface Storage {
    fun read(): String?
    fun write(text: String): Boolean
    fun exists(): Boolean
    fun clear()
}
