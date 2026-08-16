package com.oregontrail.wear.data

import com.oregontrail.wear.core.CrossingMethod
import com.oregontrail.wear.core.GameState
import com.oregontrail.wear.core.LandmarkId
import com.oregontrail.wear.core.Outcome
import com.oregontrail.wear.core.Pace
import com.oregontrail.wear.core.Profession
import com.oregontrail.wear.core.Rations
import com.oregontrail.wear.core.RiverCrossing
import com.oregontrail.wear.core.TestStates
import com.oregontrail.wear.core.TurnEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SaveGameTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun repo(): SaveRepository = SaveRepository(FileStorage(File(folder.root, "run.json")))

    @Test
    fun `a run survives a round trip`() {
        val original = TestStates.run(TestStates.outfitted(), days = 40).last().state
        val restored = SaveGame.decode(SaveGame.encode(original))

        assertNotNull(restored)
        assertEquals(original, restored)
    }

    @Test
    fun `every field of a mid-run state is preserved`() {
        val state = TestStates.run(
            TestStates.outfitted().copy(pace = Pace.STRENUOUS, rations = Rations.MEAGER),
            days = 25,
        ).last().state

        val restored = SaveGame.decode(SaveGame.encode(state))!!

        assertEquals(state.dayOfJourney, restored.dayOfJourney)
        assertEquals(state.at, restored.at)
        assertEquals(state.heading, restored.heading)
        assertEquals(state.milesIntoSegment, restored.milesIntoSegment)
        assertEquals(state.totalMiles, restored.totalMiles)
        assertEquals(state.inventory, restored.inventory)
        assertEquals(state.party, restored.party)
        assertEquals(state.pace, restored.pace)
        assertEquals(state.rations, restored.rations)
        assertEquals(state.weather, restored.weather)
        assertEquals(state.profession, restored.profession)
    }

    /**
     * The point of serialising the RNG: a resumed run must continue on the same rolls it
     * would have seen, not fork into a different game. Without this, saving and resuming
     * would be a way to reroll a bad river crossing.
     */
    @Test
    fun `resuming continues the same sequence of rolls`() {
        val mid = TestStates.run(TestStates.outfitted(), days = 30).last().state
        val resumed = SaveGame.decode(SaveGame.encode(mid))!!

        assertEquals(mid.rng.snapshot(), resumed.rng.snapshot())

        val continuedDirectly = TestStates.run(mid, days = 20).last().state
        val continuedAfterResume = TestStates.run(resumed, days = 20).last().state

        assertEquals(continuedDirectly.totalMiles, continuedAfterResume.totalMiles)
        assertEquals(continuedDirectly.inventory, continuedAfterResume.inventory)
        assertEquals(continuedDirectly.party, continuedAfterResume.party)
        assertEquals(continuedDirectly.at, continuedAfterResume.at)
    }

    @Test
    fun `a run paused at a river resumes still waiting to cross`() {
        var s = TestStates.outfitted()
        while (!s.awaitingDecision && !s.isOver) s = TurnEngine.advanceDay(s).state

        val resumed = SaveGame.decode(SaveGame.encode(s))!!
        assertTrue(resumed.awaitingDecision)
        assertEquals(LandmarkId.KANSAS_RIVER, resumed.at)

        val conditions = RiverCrossing.conditionsAt(resumed)
        assertNotNull("the river should still be there", conditions)
        assertEquals(RiverCrossing.conditionsAt(s), conditions)
    }

    @Test
    fun `a finished run round trips with its outcome`() {
        val finished = TestStates.outfitted().copy(outcome = Outcome.Arrived)
        assertEquals(Outcome.Arrived, SaveGame.decode(SaveGame.encode(finished))!!.outcome)

        val lost = TestStates.outfitted().copy(outcome = Outcome.PartyLost)
        assertEquals(Outcome.PartyLost, SaveGame.decode(SaveGame.encode(lost))!!.outcome)
    }

    // ---- Repository ----

    @Test
    fun `there is nothing to load before anything is saved`() {
        val repo = repo()
        assertFalse(repo.hasSave())
        assertNull(repo.load())
    }

    @Test
    fun `saving then loading returns the same run`() {
        val repo = repo()
        val state = TestStates.run(TestStates.outfitted(), days = 15).last().state

        assertTrue(repo.save(state))
        assertTrue(repo.hasSave())
        assertEquals(state, repo.load())
    }

    @Test
    fun `saving again replaces the previous run`() {
        val repo = repo()
        val early = TestStates.run(TestStates.outfitted(), days = 5).last().state
        val later = TestStates.run(early, days = 5).last().state

        repo.save(early)
        repo.save(later)

        assertEquals(later.dayOfJourney, repo.load()!!.dayOfJourney)
    }

    @Test
    fun `clearing removes the run`() {
        val repo = repo()
        repo.save(TestStates.outfitted())
        repo.clear()

        assertFalse(repo.hasSave())
        assertNull(repo.load())
    }

    /**
     * A corrupt save must never stop the app starting. Losing a run is bad; an app that
     * crashes on launch and can never be recovered without a reinstall is worse.
     */
    @Test
    fun `a corrupt save is treated as no save rather than crashing`() {
        val file = File(folder.root, "run.json")
        file.writeText("{ this is not json")

        assertNull(SaveRepository(FileStorage(file)).load())
    }

    @Test
    fun `a truncated save is treated as no save`() {
        val state = TestStates.outfitted()
        val full = SaveGame.encode(state)
        val file = File(folder.root, "run.json")
        file.writeText(full.substring(0, full.length / 2))

        assertNull(SaveRepository(FileStorage(file)).load())
    }

    @Test
    fun `an empty save file is treated as no save`() {
        val file = File(folder.root, "run.json")
        file.writeText("")
        assertNull(SaveRepository(FileStorage(file)).load())
    }

    /**
     * The write is atomic, so a kill mid-save leaves the *previous* run intact rather
     * than a half-written file. Simulated here by leaving a stale temp file behind.
     */
    @Test
    fun `a leftover temp file does not corrupt the saved run`() {
        val repo = repo()
        val state = TestStates.run(TestStates.outfitted(), days = 10).last().state
        repo.save(state)

        File(folder.root, "run.json.tmp").writeText("{ half-written garbage")

        assertEquals(state, repo.load())
    }

    @Test
    fun `clearing removes a stale temp file too`() {
        val repo = repo()
        repo.save(TestStates.outfitted())
        File(folder.root, "run.json.tmp").writeText("garbage")

        repo.clear()

        assertFalse(File(folder.root, "run.json.tmp").exists())
    }

    @Test
    fun `saves are small enough to write often`() {
        // Saved after every turn, so size matters more than it would elsewhere.
        val state = TestStates.run(TestStates.outfitted(), days = 60).last().state
        val bytes = SaveGame.encode(state).toByteArray().size
        assertTrue("save was $bytes bytes", bytes < 8_000)
    }

    @Test
    fun `a crossing decision survives being saved mid-river`() {
        var s = TestStates.outfitted()
        while (!s.awaitingDecision && !s.isOver) s = TurnEngine.advanceDay(s).state

        val resumed = SaveGame.decode(SaveGame.encode(s))!!
        val conditions = RiverCrossing.conditionsAt(resumed)!!

        val crossedDirectly = RiverCrossing.cross(s, conditions, CrossingMethod.FORD)
        val crossedAfterResume = RiverCrossing.cross(resumed, conditions, CrossingMethod.FORD)

        assertEquals(crossedDirectly.result, crossedAfterResume.result)
        assertEquals(crossedDirectly.state.inventory, crossedAfterResume.state.inventory)
    }

    @Test
    fun `a new game for each profession round trips`() {
        for (profession in Profession.entries) {
            val state = GameState.newGame(profession, TestStates.party, startMonth = 4, seed = 1)
            assertEquals(state, SaveGame.decode(SaveGame.encode(state)))
        }
    }
}
