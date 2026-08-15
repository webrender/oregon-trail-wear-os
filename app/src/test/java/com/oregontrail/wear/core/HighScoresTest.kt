package com.oregontrail.wear.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ranking finished runs.
 *
 * The insertion rules are the whole of this feature's logic, and two of them are easy to
 * get subtly wrong in ways no one would notice for a long time: a tie must not displace
 * the run that got there first, and a run that misses the cut must still leave the table
 * exactly as it found it.
 */
class HighScoresTest {

    private fun entry(points: Int, profession: Profession = Profession.BANKER) =
        HighScoreEntry(points = points, profession = profession)

    private fun tableOf(vararg points: Int) =
        HighScoreTable(points.map { entry(it) })

    @Test
    fun `a new table is empty`() {
        assertTrue(HighScoreTable().isEmpty)
        assertEquals(emptyList<HighScoreEntry>(), HighScoreTable().entries)
    }

    @Test
    fun `the first finished run takes first place`() {
        val placement = HighScoreTable().with(entry(1200))

        assertEquals(1, placement.rank)
        assertTrue(placement.madeTheTable)
        assertEquals(listOf(entry(1200)), placement.table.entries)
    }

    @Test
    fun `a better run goes above a worse one`() {
        val placement = tableOf(1000).with(entry(2000))

        assertEquals(1, placement.rank)
        assertEquals(listOf(2000, 1000), placement.table.entries.map { it.points })
    }

    @Test
    fun `a worse run goes below a better one`() {
        val placement = tableOf(2000).with(entry(1000))

        assertEquals(2, placement.rank)
        assertEquals(listOf(2000, 1000), placement.table.entries.map { it.points })
    }

    @Test
    fun `a run slots into the middle`() {
        val placement = tableOf(3000, 2000, 1000).with(entry(2500))

        assertEquals(2, placement.rank)
        assertEquals(listOf(3000, 2500, 2000, 1000), placement.table.entries.map { it.points })
    }

    /**
     * A standing record is never displaced by a repeat of itself. Equalling the score
     * above you is not beating it.
     */
    @Test
    fun `a tie goes to the run that got there first`() {
        val standing = entry(2000, Profession.FARMER)
        val equalled = entry(2000, Profession.BANKER)

        val placement = HighScoreTable(listOf(standing)).with(equalled)

        assertEquals(2, placement.rank)
        assertEquals(listOf(standing, equalled), placement.table.entries)
    }

    @Test
    fun `the table holds ten`() {
        var table = HighScoreTable()
        repeat(15) { table = table.with(entry(it * 100)).table }

        assertEquals(HighScoreTable.CAPACITY, table.entries.size)
        assertEquals(1400, table.entries.first().points)
        assertEquals(500, table.entries.last().points)
    }

    @Test
    fun `a run that beats the tenth place evicts it`() {
        val full = HighScoreTable((1..10).map { entry(it * 100) }.sortedByDescending { it.points })
        val placement = full.with(entry(150))

        assertEquals(10, placement.rank)
        assertEquals(HighScoreTable.CAPACITY, placement.table.entries.size)
        assertTrue(placement.table.entries.none { it.points == 100 })
        assertEquals(150, placement.table.entries.last().points)
    }

    @Test
    fun `a run that misses the cut does not place and changes nothing`() {
        val full = HighScoreTable((1..10).map { entry(it * 100) }.sortedByDescending { it.points })
        val placement = full.with(entry(50))

        assertNull(placement.rank)
        assertFalse(placement.madeTheTable)
        assertEquals(full.entries, placement.table.entries)
    }

    /**
     * Equalling the last place on a full table is the boundary between the two previous
     * cases, and the tie rule decides it: the run already there keeps the spot.
     */
    @Test
    fun `equalling the tenth place on a full table does not place`() {
        val full = HighScoreTable((1..10).map { entry(it * 100) }.sortedByDescending { it.points })
        val placement = full.with(entry(100))

        assertNull(placement.rank)
        assertEquals(full.entries, placement.table.entries)
    }

    /**
     * The table lives in a file on the player's device. If one somehow comes back out of
     * order, a new run must not be ranked against the wrong neighbour.
     */
    @Test
    fun `an unordered table is corrected as a run is filed`() {
        val placement = tableOf(1000, 3000, 2000).with(entry(2500))

        assertEquals(2, placement.rank)
        assertEquals(listOf(3000, 2500, 2000, 1000), placement.table.entries.map { it.points })
    }

    @Test
    fun `the profession is kept with the score`() {
        val placement = HighScoreTable().with(entry(900, Profession.CARPENTER))
        assertEquals(Profession.CARPENTER, placement.table.entries.single().profession)
    }
}
