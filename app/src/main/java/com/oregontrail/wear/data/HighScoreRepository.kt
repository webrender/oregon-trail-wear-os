package com.oregontrail.wear.data

import com.oregontrail.wear.core.HighScoreTable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * File-backed storage for the high score table.
 *
 * A separate file from the run, on purpose. The table outlives every individual journey:
 * abandoning a run, or finishing one and starting another, calls
 * [SaveRepository.clear] — and a scoreboard kept inside `run.json` would be deleted along
 * with it, which is precisely backwards for the one piece of state the player is meant to
 * accumulate.
 *
 * Takes a [File] rather than a Context for the same reason [SaveRepository] does: it can
 * then be tested on the JVM with no Android framework involved.
 */
class HighScoreRepository(private val file: File) {

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
        if (!file.exists()) {
            HighScoreTable()
        } else {
            json.decodeFromString(HighScoreTable.serializer(), file.readText())
                .let { HighScoreTable(it.entries.sortedByDescending { e -> e.points }) }
        }
    } catch (e: Exception) {
        HighScoreTable()
    }

    /**
     * Writes the table atomically — full write to a temporary file, then rename — for the
     * same reason the run is written that way: a watch can be killed mid-write, and a
     * half-written file is a lost scoreboard.
     */
    fun save(table: HighScoreTable): Boolean = try {
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(json.encodeToString(HighScoreTable.serializer(), table))
        if (file.exists()) file.delete()
        temp.renameTo(file)
    } catch (e: IOException) {
        false
    }
}
