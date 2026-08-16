package com.oregontrail.wear.data

import com.oregontrail.wear.core.GameState
import kotlinx.serialization.json.Json

/**
 * Save and resume.
 *
 * This is load-bearing rather than polish. Wear OS kills backgrounded apps aggressively,
 * a browser tab can be closed mid-sentence, and a run is designed to take 20-30 minutes —
 * so a playthrough *will* be interrupted, and resume has to be indistinguishable from
 * never having stopped. That is why [GameState] serialises its RNG state too: a resumed
 * game continues on exactly the rolls it would have seen, rather than quietly forking
 * into a different run.
 */
object SaveGame {

    /**
     * `ignoreUnknownKeys` lets a save written by an older build load after a field is
     * removed. It does not help when a field is *added* without a default — do that and
     * every existing save becomes unreadable, so give new fields defaults.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(state: GameState): String = json.encodeToString(GameState.serializer(), state)

    /** Decodes a save, returning null if it is corrupt or from an incompatible build. */
    fun decode(text: String): GameState? = try {
        json.decodeFromString(GameState.serializer(), text)
    } catch (e: Exception) {
        // A corrupt save must never crash the app on launch — losing a run is bad, but
        // an app that cannot start is worse.
        null
    }
}

/**
 * Storage for the current run.
 *
 * Takes a [Storage] rather than a file or a `Context`, so the same class serves the
 * watch's app-private directory and the browser's `localStorage`, and so it can be tested
 * on the JVM alongside the game core with no Android framework or Robolectric involved.
 */
class SaveRepository(private val storage: Storage) {

    fun save(state: GameState): Boolean = storage.write(SaveGame.encode(state))

    fun load(): GameState? = storage.read()?.let { SaveGame.decode(it) }

    fun hasSave(): Boolean = storage.exists()

    fun clear() = storage.clear()
}
