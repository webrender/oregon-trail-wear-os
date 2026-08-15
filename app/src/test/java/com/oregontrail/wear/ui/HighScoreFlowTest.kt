package com.oregontrail.wear.ui

import com.oregontrail.wear.core.GameState
import com.oregontrail.wear.core.Outcome
import com.oregontrail.wear.core.Profession
import com.oregontrail.wear.core.Scoring
import com.oregontrail.wear.core.TestStates
import com.oregontrail.wear.data.HighScoreRepository
import com.oregontrail.wear.data.SaveGame
import com.oregontrail.wear.data.SaveRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Filing a finished run in the high score table.
 *
 * The property worth defending here is "exactly once per journey". A run can end in four
 * different places — the last event of a day, a river, the rapids, and the day that
 * quietly kills the last traveller — and re-opening a finished save lands on the same
 * ending screen again, so both double-scoring and not scoring at all are easy mistakes
 * that would only show up as a scoreboard nobody trusts.
 */
class HighScoreFlowTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val runFile: File get() = File(folder.root, "run.json")
    private val scoresFile: File get() = File(folder.root, "scores.json")

    /**
     * An unconfined save scope so the run's background writes happen inline. The score
     * itself is written synchronously and does not need it, but the controller launches
     * that worker in its constructor either way.
     */
    private fun controller() = GameController(
        repository = SaveRepository(runFile),
        scoreboard = HighScoreRepository(scoresFile),
        saveScope = CoroutineScope(Dispatchers.Unconfined),
    )

    private fun arrived(profession: Profession = Profession.FARMER): GameState =
        TestStates.outfitted(profession = profession).copy(outcome = Outcome.Arrived)

    @Test
    fun `there is nothing in the table before anyone finishes`() {
        assertTrue(controller().scores.isEmpty)
        assertNull(controller().lastPlacement)
    }

    @Test
    fun `arriving files the run with its score and profession`() {
        val controller = controller()
        val finished = arrived()

        controller.debugJumpTo(finished, Screen.GameOver)

        val entry = controller.scores.entries.single()
        assertEquals(Scoring.score(finished).total, entry.points)
        assertEquals(Profession.FARMER, entry.profession)
        assertEquals(1, controller.lastPlacement)
    }

    /**
     * The guard in `commit`: a run that was already over cannot be scored again by a
     * further state change.
     */
    @Test
    fun `a run already over is not scored a second time`() {
        val controller = controller()
        val finished = arrived()

        controller.debugJumpTo(finished, Screen.GameOver)
        controller.debugJumpTo(finished, Screen.GameOver)

        assertEquals(1, controller.scores.entries.size)
    }

    /** Dying scores nothing — points are awarded on arrival and only on arrival. */
    @Test
    fun `a lost party is not filed`() {
        val controller = controller()

        controller.debugJumpTo(
            TestStates.outfitted().copy(outcome = Outcome.PartyLost),
            Screen.GameOver,
        )

        assertTrue(controller.scores.isEmpty)
        assertNull(controller.lastPlacement)
    }

    /**
     * Wear OS kills backgrounded apps, so a player can perfectly well be shown the ending,
     * lose the process, and come back to the same ending. That must not score the run
     * twice — which is why resuming assigns the state rather than committing it.
     */
    @Test
    fun `resuming a finished run does not score it again`() {
        val first = controller()
        val finished = arrived()
        first.debugJumpTo(finished, Screen.GameOver)
        first.flushSave()

        val resumed = controller()
        assertEquals(1, resumed.scores.entries.size)

        resumed.resumeSavedGame()

        assertEquals(Screen.GameOver, resumed.screen)
        assertEquals(1, resumed.scores.entries.size)
        assertNull("a resumed ending is not a placement earned this session", resumed.lastPlacement)
    }

    /** A save written by a build without the scoreboard still scores on arrival, once. */
    @Test
    fun `a run resumed mid-journey is scored when it arrives`() {
        runFile.writeText(SaveGame.encode(TestStates.outfitted()))

        val controller = controller()
        controller.resumeSavedGame()
        assertTrue(controller.scores.isEmpty)

        controller.debugJumpTo(arrived(), Screen.GameOver)

        assertEquals(1, controller.scores.entries.size)
    }

    @Test
    fun `the table outlives the run it was scored from`() {
        val controller = controller()
        controller.debugJumpTo(arrived(), Screen.GameOver)

        controller.abandonRun()

        assertEquals("abandoning clears the run, never the scoreboard", 1, controller.scores.entries.size)
        assertNull(controller.lastPlacement)
        assertEquals(1, HighScoreRepository(scoresFile).load().entries.size)
    }

    @Test
    fun `the table survives the app being restarted`() {
        val first = controller()
        first.debugJumpTo(arrived(Profession.CARPENTER), Screen.GameOver)
        val expected = first.scores.entries.single()

        assertEquals(expected, controller().scores.entries.single())
    }

    @Test
    fun `journeys accumulate in ranked order across runs`() {
        val controller = controller()
        // Abandoning between arrivals is what the ending's "New Journey" chip does, and
        // it is what clears the finished run the once-per-run guard keys off. Jumping
        // straight from one ending to the next would be scored once, correctly.
        for (profession in listOf(Profession.BANKER, Profession.FARMER, Profession.CARPENTER)) {
            controller.debugJumpTo(arrived(profession), Screen.GameOver)
            controller.abandonRun()
        }

        val points = controller.scores.entries.map { it.points }
        assertEquals(3, points.size)
        assertEquals(points.sortedDescending(), points)
        // The multiplier is the whole reason the profession is worth keeping on a row.
        assertEquals(Profession.FARMER, controller.scores.entries.first().profession)
    }

    // ---- Navigation ----

    @Test
    fun `the table opens from the title screen and returns to it`() {
        val controller = controller()

        controller.showHighScores()
        assertEquals(Screen.HighScores, controller.screen)

        controller.closeHighScores()
        assertEquals(Screen.Title, controller.screen)
    }

    @Test
    fun `the table opens from the ending and returns to it`() {
        val controller = controller()
        controller.debugJumpTo(arrived(), Screen.GameOver)

        controller.showHighScores()
        assertEquals(Screen.HighScores, controller.screen)

        controller.closeHighScores()
        assertEquals(Screen.GameOver, controller.screen)
    }
}
