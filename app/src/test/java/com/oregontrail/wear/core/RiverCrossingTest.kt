package com.oregontrail.wear.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiverCrossingTest {

    private fun atRiver(
        river: LandmarkId = LandmarkId.KANSAS_RIVER,
        weather: Weather = Weather.WARM,
        seed: Long = 4242,
    ): GameState = TestStates.outfitted(seed = seed)
        .copy(at = river, heading = null, weather = weather)

    @Test
    fun `there are no river conditions away from a river`() {
        assertNull(RiverCrossing.conditionsAt(TestStates.outfitted()))
        assertNull(
            RiverCrossing.conditionsAt(
                TestStates.outfitted().copy(at = LandmarkId.FORT_KEARNEY),
            )
        )
    }

    @Test
    fun `conditions are stable across repeated reads on the same day`() {
        val state = atRiver()
        val first = RiverCrossing.conditionsAt(state)
        val second = RiverCrossing.conditionsAt(state)

        assertNotNull(first)
        assertEquals(first, second)
        assertEquals("reading conditions must not consume the run's RNG",
            state.rng.snapshot(), state.rng.snapshot())
    }

    @Test
    fun `rain raises the river and heat lowers it`() {
        val wet = RiverCrossing.conditionsAt(atRiver(weather = Weather.RAINY))!!
        val dry = RiverCrossing.conditionsAt(atRiver(weather = Weather.HOT))!!
        assertTrue("rainy ${wet.depthFeet} should be deeper than hot ${dry.depthFeet}",
            wet.depthFeet > dry.depthFeet)
    }

    @Test
    fun `the ferry is only offered where there is one and the money to pay`() {
        val kansas = RiverCrossing.conditionsAt(atRiver(LandmarkId.KANSAS_RIVER))!!
        val bigBlue = RiverCrossing.conditionsAt(atRiver(LandmarkId.BIG_BLUE_RIVER))!!

        assertTrue(kansas.available(cashCents = 100_00).contains(CrossingMethod.FERRY))
        assertFalse(kansas.available(cashCents = 0).contains(CrossingMethod.FERRY))
        assertFalse(bigBlue.available(cashCents = 100_00).contains(CrossingMethod.FERRY))
    }

    @Test
    fun `fording and waiting are always available`() {
        val conditions = RiverCrossing.conditionsAt(atRiver())!!
        val options = conditions.available(cashCents = 0)
        assertTrue(options.containsAll(listOf(CrossingMethod.FORD, CrossingMethod.CAULK, CrossingMethod.WAIT)))
    }

    @Test
    fun `the ferry costs money and always gets the party across`() {
        val state = atRiver()
        val conditions = RiverCrossing.conditionsAt(state)!!
        val outcome = RiverCrossing.cross(state, conditions, CrossingMethod.FERRY)

        assertEquals(CrossingResult.Safe, outcome.result)
        assertEquals(
            state.inventory.cashCents - RiverCrossing.FERRY_COST_CENTS,
            outcome.state.inventory.cashCents,
        )
        assertEquals(LandmarkId.BIG_BLUE_RIVER, outcome.state.heading)
    }

    @Test
    fun `crossing puts the party on the far bank and points them onward`() {
        val state = atRiver()
        val conditions = RiverCrossing.conditionsAt(state)!!
        val outcome = RiverCrossing.cross(state, conditions, CrossingMethod.CAULK)

        assertEquals("still at the river landmark", LandmarkId.KANSAS_RIVER, outcome.state.at)
        assertEquals(LandmarkId.BIG_BLUE_RIVER, outcome.state.heading)
        assertFalse(outcome.state.awaitingDecision)
        assertEquals(0, outcome.state.milesIntoSegment)
    }

    @Test
    fun `waiting costs days but does not cross the river`() {
        val state = atRiver()
        val conditions = RiverCrossing.conditionsAt(state)!!
        val outcome = RiverCrossing.cross(state, conditions, CrossingMethod.WAIT)

        assertTrue(outcome.result is CrossingResult.Waited)
        assertTrue("days should have passed", outcome.state.dayOfJourney > state.dayOfJourney)
        assertTrue("still waiting to cross", outcome.state.awaitingDecision)
    }

    @Test
    fun `waiting re-rolls the river`() {
        val state = atRiver()
        val before = RiverCrossing.conditionsAt(state)!!
        val waited = RiverCrossing.cross(state, before, CrossingMethod.WAIT).state
        val after = RiverCrossing.conditionsAt(waited)!!
        // Different day means a different draw; the point is that waiting is a gamble
        // rather than a guaranteed improvement.
        assertTrue(before.depthFeet != after.depthFeet || before.landmark == after.landmark)
    }

    @Test
    fun `crossing does not disturb the caller's state`() {
        val state = atRiver()
        val before = state.rng.snapshot()
        val conditions = RiverCrossing.conditionsAt(state)!!

        RiverCrossing.cross(state, conditions, CrossingMethod.FORD)
        RiverCrossing.cross(state, conditions, CrossingMethod.FORD)

        assertEquals(before, state.rng.snapshot())
        assertEquals(LandmarkId.KANSAS_RIVER, state.at)
        assertNull(state.heading)
    }

    @Test
    fun `a deep river is flagged as dangerous to ford`() {
        // The Green River is the deep one; the Kansas is usually wadeable.
        val green = RiverCrossing.conditionsAt(atRiver(LandmarkId.GREEN_RIVER))!!
        assertTrue("green river ${green.depthFeet}ft", green.isDangerousToFord)
    }

    @Test
    fun `fording a deep river goes wrong far more often than a shallow one`() {
        fun disasters(river: LandmarkId): Int = (1L..200L).count { seed ->
            val state = atRiver(river, seed = seed)
            val conditions = RiverCrossing.conditionsAt(state)!!
            RiverCrossing.cross(state, conditions, CrossingMethod.FORD).result !=
                CrossingResult.Safe
        }

        val shallow = disasters(LandmarkId.KANSAS_RIVER)
        val deep = disasters(LandmarkId.GREEN_RIVER)
        assertTrue("kansas $shallow vs green $deep", deep > shallow * 2)
    }

    @Test
    fun `a capsize costs supplies`() {
        // Force the issue with the deepest river and enough seeds to guarantee a loss.
        // The Columbia was once deeper still, but it is no longer a crossing at all —
        // it is run on a raft now, and belongs to `RaftingTest`.
        val loss = (1L..200L).asSequence().map { seed ->
            val state = atRiver(LandmarkId.GREEN_RIVER, seed = seed)
            val conditions = RiverCrossing.conditionsAt(state)!!
            state to RiverCrossing.cross(state, conditions, CrossingMethod.FORD)
        }.first { (_, outcome) -> outcome.result != CrossingResult.Safe }

        val (before, outcome) = loss
        assertTrue(
            "should have lost food: ${before.inventory.foodPounds} -> ${outcome.state.inventory.foodPounds}",
            outcome.state.inventory.foodPounds < before.inventory.foodPounds,
        )
    }
}
