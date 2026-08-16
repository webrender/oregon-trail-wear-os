package com.oregontrail.wear.core

import kotlinx.serialization.Serializable

/**
 * One finished journey, as the table remembers it.
 *
 * Deliberately not named after anyone. ADR 0004 forbids free text, so a name could only
 * come from a picker, and with the leader fixed at `PartyNames.defaultParty.first()` every
 * row would read the same anyway. [profession] is the fact worth keeping instead: it is
 * both the difficulty setting and the score multiplier, so it is what makes two totals
 * comparable — 3,000 as a farmer is a very different run from 3,000 as a banker.
 *
 * Stored as the enum rather than its display name because this is persisted data and the
 * enum is the honest type. The cost is that renaming a constant orphans every existing
 * table — [com.oregontrail.wear.data.HighScoreRepository] treats a file it cannot parse as
 * an empty one, so that failure loses the scores rather than the app.
 */
@Serializable
data class HighScoreEntry(
    val points: Int,
    val profession: Profession,
)

/** Where a finished run landed, and the table it landed in. */
data class Placement(val table: HighScoreTable, val rank: Int?) {
    val madeTheTable: Boolean get() = rank != null
}

/**
 * The ten best journeys, best first.
 *
 * Ten because that is what the 1985 game kept, and because ten rows is about as much as
 * is worth scrolling on a 1.2" display.
 */
@Serializable
data class HighScoreTable(val entries: List<HighScoreEntry> = emptyList()) {

    val isEmpty: Boolean get() = entries.isEmpty()

    /**
     * Files the run at [entry] and returns the new table.
     *
     * Ties go to whoever got there first: a run that only equals the score above it slots
     * in *below* it, so a standing record is never displaced by a repeat of itself.
     *
     * The existing rows are re-sorted rather than assumed to be in order. The table is
     * held in a file the player's device owns, and a table that has somehow come back
     * unordered should be corrected here rather than quietly ranking a new run against
     * the wrong neighbour.
     */
    fun with(entry: HighScoreEntry): Placement {
        val ranked = entries.sortedByDescending { it.points }
        val insertAt = ranked
            .indexOfFirst { entry.points > it.points }
            .takeIf { it >= 0 }
            ?: ranked.size

        if (insertAt >= CAPACITY) {
            // Not good enough to make the cut. The table still comes back normalised, so
            // a caller that saves it unconditionally is not writing back a bad ordering.
            return Placement(HighScoreTable(ranked.take(CAPACITY)), rank = null)
        }

        val next = ranked.toMutableList().apply { add(insertAt, entry) }.take(CAPACITY)
        return Placement(HighScoreTable(next), rank = insertAt + 1)
    }

    companion object {
        const val CAPACITY = 10
    }
}
