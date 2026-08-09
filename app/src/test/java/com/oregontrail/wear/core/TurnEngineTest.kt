package com.oregontrail.wear.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnEngineTest {

    // ---- Determinism and immutability ----

    @Test
    fun `the same seed replays identically`() {
        val a = TestStates.run(TestStates.outfitted(seed = 777), days = 60).last().state
        val b = TestStates.run(TestStates.outfitted(seed = 777), days = 60).last().state

        assertEquals(a.dayOfJourney, b.dayOfJourney)
        assertEquals(a.totalMiles, b.totalMiles)
        assertEquals(a.at, b.at)
        assertEquals(a.inventory, b.inventory)
        assertEquals(a.party, b.party)
    }

    @Test
    fun `advancing a day leaves the state passed in untouched`() {
        val start = TestStates.outfitted()
        val before = start.rng.snapshot()

        TurnEngine.advanceDay(start)
        TurnEngine.advanceDay(start)

        assertEquals("advanceDay must not consume the caller's RNG", before, start.rng.snapshot())
        assertEquals(0, start.dayOfJourney)
        assertEquals(0, start.totalMiles)
    }

    @Test
    fun `advancing the same state twice gives the same result`() {
        val start = TestStates.outfitted()
        val first = TurnEngine.advanceDay(start).state
        val second = TurnEngine.advanceDay(start).state

        assertEquals(first.totalMiles, second.totalMiles)
        assertEquals(first.inventory, second.inventory)
        assertEquals(first.weather, second.weather)
    }

    // ---- Travel ----

    @Test
    fun `a day of travel advances the date and covers ground`() {
        val result = TurnEngine.advanceDay(TestStates.outfitted())
        assertEquals(1, result.state.dayOfJourney)
        assertTrue("should have moved", result.state.totalMiles > 0)
    }

    @Test
    fun `pace changes how far the party gets`() {
        // Measured on the long Fort Kearney -> Chimney Rock leg (250 miles) so neither
        // pace runs into a landmark and has its distance capped.
        fun milesIn(pace: Pace): Int {
            val start = TestStates.outfitted(seed = 4242).copy(
                pace = pace,
                at = LandmarkId.FORT_KEARNEY,
                heading = LandmarkId.CHIMNEY_ROCK,
            )
            return TestStates.run(start, days = 10).last().state.totalMiles
        }

        val steady = milesIn(Pace.STEADY)
        val grueling = milesIn(Pace.GRUELING)
        assertTrue("grueling ($grueling) should outpace steady ($steady)", grueling > steady)
    }

    @Test
    fun `travel is refused while a decision is pending`() {
        val atRiver = TestStates.outfitted().copy(at = LandmarkId.KANSAS_RIVER, heading = null)
        assertTrue(atRiver.awaitingDecision)

        val result = TurnEngine.advanceDay(atRiver)
        assertEquals("no day should pass", atRiver.dayOfJourney, result.state.dayOfJourney)
        assertEquals("no food should be eaten", atRiver.inventory, result.state.inventory)
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `a wagon without enough oxen goes nowhere`() {
        val stranded = TestStates.outfitted(oxen = 1)
        val result = TestStates.run(stranded, days = 10).last()
        assertEquals(0, result.state.totalMiles)
    }

    @Test
    fun `the party arrives at the Kansas River and stops there`() {
        val results = TestStates.run(TestStates.outfitted(), days = 40)
        val arrival = results.firstOrNull { r ->
            r.events.any { it is GameEvent.ArrivedAt && it.landmark == LandmarkId.KANSAS_RIVER }
        }
        assertNotNull("should reach the Kansas River within 40 days", arrival)
        assertEquals(LandmarkId.KANSAS_RIVER, arrival!!.state.at)
        assertEquals(0, arrival.state.milesIntoSegment)
    }

    @Test
    fun `mileage never overshoots a landmark`() {
        var s = TestStates.outfitted()
        repeat(40) {
            val route = s.currentRoute
            s = TurnEngine.advanceDay(s).state
            if (route != null) {
                assertTrue(
                    "milesIntoSegment ${s.milesIntoSegment} exceeded segment ${route.miles}",
                    s.milesIntoSegment <= route.miles,
                )
            }
        }
    }

    // ---- Branch points ----

    @Test
    fun `arriving at a branch leaves the party without a heading until they choose`() {
        val atBranch = TestStates.outfitted().copy(
            at = LandmarkId.INDEPENDENCE_ROCK,
            heading = LandmarkId.SOUTH_PASS,
            milesIntoSegment = 100,
        )
        val results = TestStates.run(atBranch, days = 5)
        val arrival = results.first { r ->
            r.events.any { it is GameEvent.ArrivedAt && it.landmark == LandmarkId.SOUTH_PASS }
        }
        assertNull("a branch must wait for the player", arrival.state.heading)
    }

    @Test
    fun `choosing a route sets the heading`() {
        val atSouthPass = TestStates.outfitted().copy(at = LandmarkId.SOUTH_PASS, heading = null)
        val chosen = TurnEngine.chooseRoute(atSouthPass, LandmarkId.FORT_BRIDGER)
        assertEquals(LandmarkId.FORT_BRIDGER, chosen.heading)
    }

    @Test
    fun `an unreachable route is refused`() {
        val atSouthPass = TestStates.outfitted().copy(at = LandmarkId.SOUTH_PASS, heading = null)
        val unchanged = TurnEngine.chooseRoute(atSouthPass, LandmarkId.OREGON_CITY)
        assertNull(unchanged.heading)
    }

    // ---- Food and health ----

    @Test
    fun `rations determine how fast food is eaten`() {
        val filling = TurnEngine.advanceDay(TestStates.outfitted().copy(rations = Rations.FILLING))
        val bare = TurnEngine.advanceDay(TestStates.outfitted().copy(rations = Rations.BARE_BONES))

        assertEquals(1000 - 15, filling.state.inventory.foodPounds) // 3 lb x 5 people
        assertEquals(1000 - 5, bare.state.inventory.foodPounds) // 1 lb x 5 people
    }

    @Test
    fun `an empty larder is reported and hurts`() {
        val starving = TestStates.outfitted(foodPounds = 0)
        val result = TurnEngine.advanceDay(starving)

        assertTrue(result.events.any { it is GameEvent.OutOfFood })
        assertTrue(result.state.party.healthPoints > starving.party.healthPoints)
    }

    @Test
    fun `a grueling pace on bare rations wrecks the party's health`() {
        val hard = TestStates.outfitted(seed = 9).copy(
            pace = Pace.GRUELING,
            rations = Rations.BARE_BONES,
        )
        val easy = TestStates.outfitted(seed = 9).copy(
            pace = Pace.STEADY,
            rations = Rations.FILLING,
        )

        val hardHealth = TestStates.run(hard, days = 30).last().state.party.healthPoints
        val easyHealth = TestStates.run(easy, days = 30).last().state.party.healthPoints
        assertTrue("$hardHealth should be worse than $easyHealth", hardHealth > easyHealth)
    }

    @Test
    fun `resting recovers health and costs a day but no distance`() {
        val worn = TestStates.outfitted().let { it.copy(party = it.party.adjustHealth(60)) }
        val rested = TurnEngine.rest(worn).state

        assertEquals(worn.dayOfJourney + 1, rested.dayOfJourney)
        assertEquals(worn.totalMiles, rested.totalMiles)
        assertTrue(
            "resting should improve health: ${worn.party.healthPoints} -> ${rested.party.healthPoints}",
            rested.party.healthPoints < worn.party.healthPoints,
        )
    }

    @Test
    fun `health cannot improve past perfect`() {
        var s = TestStates.outfitted().copy(rations = Rations.FILLING)
        repeat(30) { s = TurnEngine.rest(s).state }
        assertEquals(0, s.party.healthPoints)
    }

    // ---- Illness and death ----

    @Test
    fun `a party in dire health eventually loses someone`() {
        // Deliberately hostile: no food, no clothing, grueling pace, ill from the start.
        var s = TestStates.outfitted(foodPounds = 0, clothingSets = 0, seed = 31337).copy(
            pace = Pace.GRUELING,
            rations = Rations.BARE_BONES,
        )
        s = s.copy(party = s.party.adjustHealth(150))

        val results = TestStates.run(s, days = 200)
        assertTrue(
            "someone should have died",
            results.any { r -> r.events.any { it is GameEvent.Died } },
        )
    }

    @Test
    fun `losing everyone ends the run`() {
        var s = TestStates.outfitted(foodPounds = 0, clothingSets = 0, seed = 5150).copy(
            pace = Pace.GRUELING,
            rations = Rations.BARE_BONES,
        )
        s = s.copy(party = s.party.adjustHealth(200))

        val results = TestStates.run(s, days = 400)
        val end = results.last().state
        if (end.party.isWipedOut) {
            assertEquals(Outcome.PartyLost, end.outcome)
            assertTrue(end.isOver)
        }
    }

    @Test
    fun `a finished run stops advancing`() {
        val done = TestStates.outfitted().copy(outcome = Outcome.Arrived)
        val result = TurnEngine.advanceDay(done)
        assertEquals(done.dayOfJourney, result.state.dayOfJourney)
        assertTrue(result.events.isEmpty())
    }

    // ---- Reaching Oregon ----

    /**
     * End-to-end integration: a competently played run should reach Oregon.
     *
     * "Competently" matters. An earlier version of this test drove a grueling pace the
     * whole way and the party died every time — which turned out to be correct
     * behaviour, not a bug. Four months of forced march with no rest days is fatal, and
     * the engine is right to say so. The policy below is what a sensible player does:
     * a sustainable pace, full rations, and stopping when the party is worn down.
     */
    @Test
    fun `a competently played run reaches Oregon`() {
        var s = TestStates.outfitted(oxen = 8, foodPounds = 1000, cashCents = 600_00, seed = 2468)
            .copy(pace = Pace.STRENUOUS, rations = Rations.FILLING)
        var lastRestockedAt: LandmarkId? = null

        // Rivers, branches and shopping are all the caller's business, not
        // advanceDay's, so drive them here the way the UI eventually will.
        repeat(500) {
            if (s.isOver) return@repeat
            val landmark = s.currentLandmark
            val conditions = RiverCrossing.conditionsAt(s)
            val store = s.store
            s = when {
                s.awaitingDecision && conditions != null ->
                    RiverCrossing.cross(s, conditions, CrossingMethod.CAULK).state
                s.awaitingDecision && landmark.isBranch ->
                    TurnEngine.chooseRoute(s, landmark.routes.first().to)
                store != null && landmark.id != lastRestockedAt -> {
                    lastRestockedAt = landmark.id
                    s.copy(inventory = restock(store, s.inventory))
                }
                s.party.healthPoints > 60 -> TurnEngine.rest(s).state
                else -> TurnEngine.advanceDay(s).state
            }
        }

        val diagnostic = "stalled at ${s.at} heading ${s.heading} on day ${s.dayOfJourney}, " +
            "${s.totalMiles} miles, ${s.inventory.foodPounds} lb food, ${s.inventory.oxen} oxen, " +
            "health ${s.party.healthPoints}, ${s.party.survivorCount} alive"
        assertEquals(diagnostic, Outcome.Arrived, s.outcome)
        assertEquals(LandmarkId.OREGON_CITY, s.at)
        assertTrue("should have covered the trail, got ${s.totalMiles}", s.totalMiles > 1900)
        assertTrue("someone should have made it", s.party.survivorCount > 0)
    }

    /**
     * Tops the wagon back up at a fort: enough oxen to pull it, and food for the road
     * ahead. Spends at most half the remaining cash on food so there is something left
     * for the next fort, where everything will be dearer.
     */
    private fun restock(store: Store, inventory: Inventory): Inventory {
        var inv = inventory
        val yokesWanted = ((8 - inv.oxen).coerceAtLeast(0) + 1) / 2
        val yokes = minOf(yokesWanted, store.affordable(Good.OXEN, inv.cashCents))
        if (yokes > 0) inv = store.buy(inv, Good.OXEN, yokes)

        val budget = inv.cashCents / 2
        val poundsWanted = (600 - inv.foodPounds).coerceAtLeast(0)
        val pounds = minOf(poundsWanted, store.affordable(Good.FOOD, budget))
        if (pounds > 0) inv = store.buy(inv, Good.FOOD, pounds)

        return inv
    }
}
