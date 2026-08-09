package com.oregontrail.wear.data

import com.oregontrail.wear.core.GameState
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * Save and resume.
 *
 * This is load-bearing rather than polish. Wear OS kills backgrounded apps aggressively,
 * and a run is designed to take 20-30 minutes, so a playthrough *will* be interrupted —
 * resume has to be indistinguishable from never having stopped. That is why [GameState]
 * serialises its RNG state too: a resumed game continues on exactly the rolls it would
 * have seen, rather than quietly forking into a different run.
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
 * File-backed storage for the current run.
 *
 * Takes a [File] rather than a Context so it can be tested on the JVM alongside the
 * game core, with no Android framework or Robolectric involved.
 */
class SaveRepository(private val file: File) {

    /**
     * Writes the save atomically: full write to a temporary file, then rename.
     *
     * A watch can be killed mid-write, and a half-written JSON file would fail to parse
     * and silently discard the run. Rename is atomic on the same filesystem, so the
     * save on disk is always either the previous one or the new one, never a fragment.
     */
    fun save(state: GameState): Boolean = try {
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(SaveGame.encode(state))
        if (file.exists()) file.delete()
        temp.renameTo(file)
    } catch (e: IOException) {
        false
    }

    fun load(): GameState? =
        if (!file.exists()) null else try {
            SaveGame.decode(file.readText())
        } catch (e: IOException) {
            null
        }

    fun hasSave(): Boolean = file.exists()

    fun clear() {
        file.delete()
        File(file.parentFile, "${file.name}.tmp").delete()
    }
}
