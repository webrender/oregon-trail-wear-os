package com.oregontrail.wear.ui

import com.oregontrail.wear.core.GameState
import com.oregontrail.wear.core.LandmarkId
import com.oregontrail.wear.core.Outcome
import com.oregontrail.wear.core.TestStates
import com.oregontrail.wear.core.TurnEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Routing from game state to screen.
 *
 * The stakes here are specific: [TurnEngine.advanceDay] refuses to advance while
 * [GameState.awaitingDecision] is true, so if the UI fails to route to the screen that
 * resolves the decision, the player is left on a travel screen where the days visibly
 * stop passing and nothing explains why. These tests reach that state through the engine
 * rather than by hand-building it, so they stay honest if the engine changes when it
 * leaves `heading` null.
 */
class ScreenFlowTest {

    @Test
    fun `a finished run goes to the ending`() {
        val arrived = TestStates.outfitted().copy(outcome = Outcome.Arrived)
        assertEquals(Screen.GameOver, demandedScreen(arrived))

        val lost = TestStates.outfitted().copy(outcome = Outcome.PartyLost)
        assertEquals(Screen.GameOver, demandedScreen(lost))
    }

    @Test
    fun `a run under way demands nothing in particular`() {
        assertNull(demandedScreen(TestStates.outfitted()))
        assertEquals(Screen.Trail, resumeScreen(TestStates.outfitted()))
    }

    @Test
    fun `reaching a river demands the crossing screen`() {
        val atRiver = travelUntilAwaitingDecision(TestStates.outfitted())
        assertEquals(
            "the first decision on the trail is the Kansas River",
            LandmarkId.KANSAS_RIVER,
            atRiver.at,
        )
        assertEquals(Screen.River, demandedScreen(atRiver))
        assertEquals(Screen.River, resumeScreen(atRiver))
    }

    /**
     * The rapids demand the raft, not the crossing menu.
     *
     * These are the same state — at a landmark, `heading` null — and the only thing that
     * tells them apart is the landmark's kind. Getting this wrong would strand the
     * player on a ford/caulk/ferry menu for a river that has none of those.
     */
    @Test
    fun `reaching the rapids demands the raft screen`() {
        val atTheColumbia = TestStates.outfitted()
            .copy(at = LandmarkId.COLUMBIA_RIVER, heading = null)
        assertEquals(Screen.Raft, demandedScreen(atTheColumbia))
        assertEquals(Screen.Raft, resumeScreen(atTheColumbia))
    }

    @Test
    fun `reaching a branch demands the fork screen`() {
        val atBranch = TestStates.outfitted().copy(at = LandmarkId.SOUTH_PASS, heading = null)
        assertEquals(Screen.Fork, demandedScreen(atBranch))
    }

    /**
     * The ending wins over an outstanding decision.
     *
     * A party can be wiped out by a capsized wagon at a river, which leaves the state
     * both over *and* at a river. Showing the crossing options to a dead party would be
     * absurd, and worse, unwinnable — there is no way off that screen.
     */
    @Test
    fun `an ending outranks an outstanding decision`() {
        val drownedAtTheRiver = TestStates.outfitted().copy(
            at = LandmarkId.KANSAS_RIVER,
            heading = null,
            outcome = Outcome.PartyLost,
        )
        assertEquals(Screen.GameOver, demandedScreen(drownedAtTheRiver))
    }

    /**
     * The travel screen cannot be reached while a decision is outstanding.
     *
     * This is the freeze the player actually hit: at a branch, "Check supplies" then
     * "Back" lands on the trail menu, whose "Continue on" asks for [Screen.Trail] by
     * name. Granting it strands the wagon on a screen where every day is a no-op — see
     * [allowedScreen].
     */
    @Test
    fun `asking for the trail while a decision is outstanding routes to the decision`() {
        val atBranch = TestStates.outfitted().copy(at = LandmarkId.SOUTH_PASS, heading = null)
        assertEquals(Screen.Fork, allowedScreen(atBranch, Screen.Trail))
        assertEquals(Screen.Fork, allowedScreen(atBranch, Screen.TrailMenu))

        val atRiver = travelUntilAwaitingDecision(TestStates.outfitted())
        assertEquals(Screen.River, allowedScreen(atRiver, Screen.Trail))
        assertEquals(Screen.River, allowedScreen(atRiver, Screen.TrailMenu))
    }

    /** The same guard must not interfere with a run that is simply under way. */
    @Test
    fun `the trail and its menu stay reachable while the wagon can roll`() {
        val rolling = TestStates.outfitted()
        assertEquals(Screen.Trail, allowedScreen(rolling, Screen.Trail))
        assertEquals(Screen.TrailMenu, allowedScreen(rolling, Screen.TrailMenu))
    }

    /**
     * Only the two screens that assume a rolling wagon are guarded. Checking supplies or
     * buying from a fort's store at a branch is legitimate, and must still work.
     */
    @Test
    fun `other screens are reachable with a decision outstanding`() {
        val atBranch = TestStates.outfitted().copy(at = LandmarkId.SOUTH_PASS, heading = null)
        assertEquals(Screen.Supplies, allowedScreen(atBranch, Screen.Supplies))
        assertEquals(Screen.Store, allowedScreen(atBranch, Screen.Store))
        assertEquals(Screen.Arrival, allowedScreen(atBranch, Screen.Arrival))
    }

    /**
     * The map is somewhere the player chooses to go, never somewhere they are sent.
     *
     * It is only linked from the trail menu, which is itself unreachable while a decision
     * is outstanding — so the map inherits that guard rather than needing its own, and the
     * way back out of it goes through [allowedScreen] like every other tap.
     */
    @Test
    fun `the map is reachable but never demanded`() {
        val rolling = TestStates.outfitted()
        assertEquals(Screen.Map, allowedScreen(rolling, Screen.Map))
        assertNull(demandedScreen(rolling))

        val atBranch = rolling.copy(at = LandmarkId.SOUTH_PASS, heading = null)
        assertEquals(Screen.Fork, demandedScreen(atBranch))
        assertEquals(Screen.Fork, allowedScreen(atBranch, Screen.TrailMenu))
    }

    /** Before a run exists there is no state to consult, and the title screen navigates. */
    @Test
    fun `navigation without a run is left alone`() {
        assertEquals(Screen.Trail, allowedScreen(null, Screen.Trail))
    }

    /** Advances real days until the engine stops and asks for a decision. */
    private fun travelUntilAwaitingDecision(start: GameState): GameState {
        var state = start
        repeat(200) {
            if (state.awaitingDecision || state.isOver) return state
            state = TurnEngine.advanceDay(state).state
        }
        error("never reached a decision point in 200 days")
    }
}
