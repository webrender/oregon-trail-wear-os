package com.oregontrail.wear.data

import com.oregontrail.wear.core.HighScoreTable
import kotlinx.serialization.json.Json

/**
 * Storage for the high score table.
 *
 * A separate [Storage] slot from the run, on purpose. The table outlives every individual
 * journey: abandoning a run, or finishing one and starting another, calls
 * [SaveRepository.clear] — and a scoreboard kept in the same slot would be deleted along
 * with it, which is precisely backwards for the one piece of state the player is meant to
 * accumulate.
 */
class HighScoreRepository(private val storage: Storage) {

    /**
     * `ignoreUnknownKeys` lets a table written by a later build load in an earlier one.
     * As with the save file, a new field must carry a default or every existing table
     * becomes unreadable.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * The stored table, or an empty one if there is nothing to read or it cannot be
     * parsed.
     *
     * A corrupt scoreboard costs the player their records, which is bad, but refusing to
     * start would be worse — and unlike the run, there is nothing here worth trying to
     * salvage a fragment of.
     */
    fun load(): HighScoreTable = try {
        val text = storage.read()
        if (text == null) {
            HighScoreTable()
        } else {
            json.decodeFromString(HighScoreTable.serializer(), text)
                .let { HighScoreTable(it.entries.sortedByDescending { e -> e.points }) }
        }
    } catch (e: Exception) {
        HighScoreTable()
    }

    fun save(table: HighScoreTable): Boolean =
        storage.write(json.encodeToString(HighScoreTable.serializer(), table))
}
