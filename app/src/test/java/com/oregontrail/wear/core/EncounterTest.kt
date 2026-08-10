package com.oregontrail.wear.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EncounterTest {

    @Test
    fun `a trade never asks for more than the party holds`() {
        val state = TestStates.outfitted(bullets = 3, clothingSets = 0)
        for (seed in 1L..300L) {
            val encounter = Encounters.roll(state, Rng.seeded(seed))
            if (encounter is Encounter.Trade) {
                assertTrue(
                    "wants ${encounter.wantsQuantity} of ${encounter.wants}, only holds " +
                        "${state.inventory.amountOf(encounter.wants)}",
                    encounter.wantsQuantity <= state.inventory.amountOf(encounter.wants),
                )
            }
        }
    }

    @Test
    fun `a trade is never offered with nothing to give away`() {
        // Only cash on hand, no goods at all.
        val bareHanded = TestStates.fresh()
        for (seed in 1L..300L) {
            val encounter = Encounters.roll(bareHanded, Rng.seeded(seed))
            assertTrue("got a trade with an empty wagon: $encounter", encounter !is Encounter.Trade)
        }
    }

    @Test
    fun `accepting a trade moves goods both ways`() {
        val state = TestStates.outfitted(bullets = 40, clothingSets = 1)
        val trade = Encounter.Trade(
            wants = Good.BULLETS, wantsQuantity = 10,
            gives = Good.CLOTHING, givesQuantity = 1,
        )
        val after = Encounters.accept(state, trade)

        assertEquals(30, after.inventory.bullets)
        assertEquals(2, after.inventory.clothingSets)
    }

    @Test
    fun `a trade the party can no longer afford is refused`() {
        val state = TestStates.outfitted(bullets = 5)
        val trade = Encounter.Trade(
            wants = Good.BULLETS, wantsQuantity = 10,
            gives = Good.CLOTHING, givesQuantity = 1,
        )
        val after = Encounters.accept(state, trade)
        assertEquals("nothing should change", state.inventory, after.inventory)
    }

    @Test
    fun `hiring a guide spends cash and improves health`() {
        val state = TestStates.outfitted(cashCents = 10_00)
            .let { it.copy(party = it.party.adjustHealth(20)) }
        val guide = Encounter.Guide(costCents = 5_00, healthBenefit = 6)

        val after = Encounters.hire(state, guide)

        assertEquals(state.inventory.cashCents - 5_00, after.inventory.cashCents)
        assertTrue(
            "health should improve: ${state.party.healthPoints} -> ${after.party.healthPoints}",
            after.party.healthPoints < state.party.healthPoints,
        )
    }

    @Test
    fun `hiring a guide the party can't afford is refused`() {
        val state = TestStates.outfitted(cashCents = 1_00)
        val guide = Encounter.Guide(costCents = 5_00, healthBenefit = 6)

        val after = Encounters.hire(state, guide)
        assertEquals(state, after)
    }

    @Test
    fun `advanceDay occasionally produces an encounter`() {
        val found = (1L..500L).any { seed ->
            val state = TestStates.outfitted(seed = seed)
            TurnEngine.advanceDay(state).encounter != null
        }
        assertTrue("no encounter turned up in 500 seeded days", found)
    }
}
