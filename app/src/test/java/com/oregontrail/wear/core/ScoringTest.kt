package com.oregontrail.wear.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoringTest {

    /**
     * The documented practical maximum is 2,690 points before the profession
     * multiplier. Reproducing it exactly is the check that the scoring table has been
     * transcribed correctly — every line item has to be right for the total to land.
     *
     * The scenario: five survivors in good health, 1,950 lb of food, a wagon, six oxen,
     * ten sets of clothing, six spare parts, and $30 in hand.
     */
    @Test
    fun `documented practical maximum is 2690 before the multiplier`() {
        val state = TestStates.outfitted().copy(
            inventory = Inventory(
                oxen = 6,
                foodPounds = 1950,
                clothingSets = 10,
                bullets = 0,
                spareWheels = 2,
                spareAxles = 2,
                spareTongues = 2,
                cashCents = 30_00,
            ),
        )
        val score = Scoring.score(state)

        assertEquals(2500, score.lines.single { it.label.startsWith("Surviving") }.points)
        assertEquals(50, score.lines.single { it.label == "Wagon" }.points)
        assertEquals(24, score.lines.single { it.label == "Oxen" }.points)
        assertEquals(20, score.lines.single { it.label == "Sets of clothing" }.points)
        assertEquals(12, score.lines.single { it.label == "Spare parts" }.points)
        assertEquals(78, score.lines.single { it.label == "Food" }.points)
        assertEquals(6, score.lines.single { it.label == "Cash" }.points)

        assertEquals(2690, score.subtotal)
    }

    @Test
    fun `profession multiplier is applied to the subtotal`() {
        val inventory = Inventory(oxen = 6, foodPounds = 1950, clothingSets = 10, cashCents = 30_00)
            .copy(spareWheels = 2, spareAxles = 2, spareTongues = 2)

        val banker = TestStates.outfitted(Profession.BANKER).copy(inventory = inventory)
        val farmer = TestStates.outfitted(Profession.FARMER).copy(inventory = inventory)

        assertEquals(banker.let(Scoring::score).subtotal, farmer.let(Scoring::score).subtotal)
        assertEquals(2690, Scoring.score(banker).total)
        assertEquals(2690 * 3, Scoring.score(farmer).total)
    }

    @Test
    fun `survivors are worth less when the party arrives in poor health`() {
        val base = TestStates.outfitted()
        val healthy = base.copy(party = base.party.copy(healthPoints = 0))
        val ailing = base.copy(party = base.party.copy(healthPoints = 120))

        assertEquals(HealthLevel.GOOD, healthy.party.health)
        assertEquals(HealthLevel.VERY_POOR, ailing.party.health)
        assertEquals(5 * 500, survivorPoints(healthy))
        assertEquals(5 * 200, survivorPoints(ailing))
    }

    @Test
    fun `the dead score nothing`() {
        val base = TestStates.outfitted()
        val bereaved = base.copy(
            party = base.party.withMember("Thomas") { it.copy(alive = false, diedOnDay = 20) },
        )
        assertEquals(4 * 500, survivorPoints(bereaved))
    }

    /**
     * Ammunition scores per 50 bullets but is bought in boxes of 20 — a deliberate unit
     * mismatch in the original. This pins the behaviour that falls out of it: two boxes
     * (40 bullets) score nothing at all.
     */
    @Test
    fun `ammunition scores per fifty bullets regardless of box size`() {
        fun pointsFor(bullets: Int): Int {
            val state = TestStates.outfitted().copy(inventory = Inventory(bullets = bullets))
            return Scoring.score(state).lines.firstOrNull { it.label == "Ammunition" }?.points ?: 0
        }
        assertEquals(0, pointsFor(40))
        assertEquals(1, pointsFor(50))
        assertEquals(2, pointsFor(100))
    }

    @Test
    fun `food scores per twenty five pounds`() {
        val state = TestStates.outfitted().copy(inventory = Inventory(foodPounds = 249))
        assertEquals(9, Scoring.score(state).lines.single { it.label == "Food" }.points)
    }

    @Test
    fun `empty categories are left out of the breakdown`() {
        val state = TestStates.outfitted().copy(inventory = Inventory())
        val labels = Scoring.score(state).lines.map { it.label }
        assertEquals(false, labels.contains("Oxen"))
        assertEquals(true, labels.contains("Wagon"))
    }

    private fun survivorPoints(state: GameState): Int =
        Scoring.score(state).lines.single { it.label.startsWith("Surviving") }.points
}
