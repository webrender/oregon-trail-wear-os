package com.oregontrail.wear.data

import com.oregontrail.wear.core.HighScoreEntry
import com.oregontrail.wear.core.HighScoreTable
import com.oregontrail.wear.core.Profession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HighScoreRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun repo(): HighScoreRepository =
        HighScoreRepository(File(folder.root, "scores.json"))

    private fun entry(points: Int, profession: Profession = Profession.BANKER) =
        HighScoreEntry(points = points, profession = profession)

    @Test
    fun `there is no table before anything is saved`() {
        assertTrue(repo().load().isEmpty)
    }

    @Test
    fun `saving then loading returns the same table`() {
        val repo = repo()
        val table = HighScoreTable(
            listOf(
                entry(4200, Profession.FARMER),
                entry(3100, Profession.CARPENTER),
                entry(900, Profession.BANKER),
            )
        )

        assertTrue(repo.save(table))
        assertEquals(table, repo.load())
    }

    @Test
    fun `saving again replaces the previous table`() {
        val repo = repo()
        repo.save(HighScoreTable(listOf(entry(100))))
        repo.save(HighScoreTable(listOf(entry(500), entry(100))))

        assertEquals(listOf(500, 100), repo.load().entries.map { it.points })
    }

    /**
     * The scoreboard is not worth crashing over. Losing it is a real cost — it is the one
     * thing in the app that accumulates — but an app that will not start is worse, and
     * unlike a run there is no fragment here worth trying to salvage.
     */
    @Test
    fun `a corrupt table is treated as an empty one`() {
        val file = File(folder.root, "scores.json")
        file.writeText("{ this is not json")

        assertTrue(HighScoreRepository(file).load().isEmpty)
    }

    @Test
    fun `an empty file is treated as an empty table`() {
        val file = File(folder.root, "scores.json")
        file.writeText("")

        assertTrue(HighScoreRepository(file).load().isEmpty)
    }

    /**
     * A profession that no longer exists cannot be decoded, so the whole table goes. That
     * is the documented cost of storing the enum rather than a display string — pinned
     * here so it is a known trade rather than a surprise.
     */
    @Test
    fun `a table naming an unknown profession is treated as empty`() {
        val file = File(folder.root, "scores.json")
        file.writeText("""{"entries":[{"points":100,"profession":"BLACKSMITH"}]}""")

        assertTrue(HighScoreRepository(file).load().isEmpty)
    }

    /** However the file came to be out of order, the table comes back ranked. */
    @Test
    fun `a table stored out of order is sorted on the way in`() {
        val file = File(folder.root, "scores.json")
        file.writeText(
            """{"entries":[{"points":100,"profession":"BANKER"},""" +
                """{"points":900,"profession":"FARMER"},""" +
                """{"points":400,"profession":"CARPENTER"}]}"""
        )

        assertEquals(listOf(900, 400, 100), HighScoreRepository(file).load().entries.map { it.points })
    }

    /**
     * Written the same way the run is: full write to a temp file, then rename, so a watch
     * killed mid-write leaves the previous table intact rather than a fragment.
     */
    @Test
    fun `a leftover temp file does not corrupt the stored table`() {
        val repo = repo()
        val table = HighScoreTable(listOf(entry(700)))
        repo.save(table)

        File(folder.root, "scores.json.tmp").writeText("{ half-written garbage")

        assertEquals(table, repo.load())
    }
}
