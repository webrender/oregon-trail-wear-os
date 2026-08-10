package com.oregontrail.wear.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HuntingTest {

    @Test
    fun `hunting needs ammunition and a party actually travelling`() {
        assertTrue(Hunting.canHunt(TestStates.outfitted(bullets = 20)))
        assertFalse(
            "no bullets, nothing to hunt with",
            Hunting.canHunt(TestStates.outfitted(bullets = 0)),
        )
        assertFalse(
            "awaiting a decision, not free to wander off",
            Hunting.canHunt(TestStates.outfitted().copy(heading = null)),
        )
        assertFalse(
            "the run is over",
            Hunting.canHunt(TestStates.outfitted().copy(outcome = Outcome.Arrived)),
        )
    }

    @Test
    fun `ground follows the region the party is heading into`() {
        val prairie = TestStates.outfitted().copy(heading = LandmarkId.KANSAS_RIVER)
        val rockies = TestStates.outfitted().copy(heading = LandmarkId.SOUTH_PASS)
        assertEquals(HuntingGround.PRAIRIE, Hunting.groundAt(prairie))
        assertEquals(HuntingGround.ROCKIES, Hunting.groundAt(rockies))
    }

    @Test
    fun `meat yield is drawn from the session rng and stays in range`() {
        val rng = Rng.seeded(99L)
        repeat(50) {
            val meat = Hunting.meatFor(rng, Animal.DEER)
            assertTrue("$meat out of range", meat in Animal.DEER.meatPounds)
        }
    }

    @Test
    fun `starting a hunt does not disturb the caller's state`() {
        val state = TestStates.outfitted()
        val before = state.rng.snapshot()

        val rng = Hunting.start(state)
        rng.nextInt(1000)

        assertEquals(before, state.rng.snapshot())
    }

    @Test
    fun `a finished hunt spends bullets, carries meat, and advances a day`() {
        val state = TestStates.outfitted(bullets = 30, foodPounds = 100)
        val rng = Hunting.start(state)

        val outcome = Hunting.finish(state, rng, bulletsUsed = 12, meatPoundsShot = 40)

        assertEquals(18, outcome.day.state.inventory.bullets)
        // 100 on hand, +40 carried back, -15 eaten that day (5 survivors at Filling).
        assertEquals(125, outcome.day.state.inventory.foodPounds)
        assertEquals(40, outcome.meatPoundsCarried)
        assertEquals(0, outcome.meatPoundsWasted)
        assertEquals(state.dayOfJourney + 1, outcome.day.state.dayOfJourney)
    }

    @Test
    fun `meat over the carry limit is wasted, not banked`() {
        val state = TestStates.outfitted(foodPounds = 0)
        val rng = Hunting.start(state)

        val outcome = Hunting.finish(state, rng, bulletsUsed = 1, meatPoundsShot = 350)

        assertEquals(Hunting.CARRY_LIMIT_POUNDS, outcome.meatPoundsCarried)
        assertEquals(250, outcome.meatPoundsWasted)
    }

    @Test
    fun `bullets used never drives ammunition negative`() {
        val state = TestStates.outfitted(bullets = 5)
        val rng = Hunting.start(state)

        val outcome = Hunting.finish(state, rng, bulletsUsed = 5000, meatPoundsShot = 0)

        assertEquals(0, outcome.day.state.inventory.bullets)
    }

    @Test
    fun `an escaped bear costs the party health`() {
        val state = TestStates.outfitted()

        // Independent forks of the same starting state, so both sessions see identical
        // weather and illness rolls and the only difference is the bear.
        val mauled = Hunting.finish(
            state, Hunting.start(state), bulletsUsed = 1, meatPoundsShot = 0, bearsEscaped = 1,
        )
        val clean = Hunting.finish(
            state, Hunting.start(state), bulletsUsed = 1, meatPoundsShot = 0, bearsEscaped = 0,
        )

        assertTrue(
            "a bear reaching the hunter should cost health points",
            mauled.day.state.party.healthPoints > clean.day.state.party.healthPoints,
        )
    }

    @Test
    fun `a hunting day still eats food and can starve a party out of ammunition`() {
        val state = TestStates.outfitted(foodPounds = 0)
        val rng = Hunting.start(state)

        val outcome = Hunting.finish(state, rng, bulletsUsed = 1, meatPoundsShot = 0)

        assertTrue(
            "a fed-then-starved day should still register as out of food",
            outcome.day.events.contains(GameEvent.OutOfFood),
        )
    }
}
