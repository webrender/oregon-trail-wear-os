package com.oregontrail.wear.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RaftingTest {

    private fun atTheRapids(seed: Long = 4242): GameState =
        TestStates.outfitted(seed = seed)
            .copy(at = LandmarkId.COLUMBIA_RIVER, heading = null)

    private fun descend(
        state: GameState,
        impacts: List<RaftImpact> = emptyList(),
        landed: Boolean = true,
    ): RaftOutcome = Rafting.finish(state, Rafting.start(state), impacts, landed)

    @Test
    fun `the Columbia is rapids, not a crossing`() {
        val columbia = Trail[LandmarkId.COLUMBIA_RIVER]
        assertTrue(columbia.kind is LandmarkKind.Rapids)
        // The crossing menu must not be reachable here — there is nothing to decide.
        assertEquals(null, RiverCrossing.conditionsAt(atTheRapids()))
        assertTrue(Rafting.isAtRapids(atTheRapids()))
    }

    @Test
    fun `a clean descent costs a day and points the party at Oregon City`() {
        val before = atTheRapids()
        val outcome = descend(before)
        val after = outcome.day.state

        assertFalse(outcome.wrecked)
        assertTrue(outcome.landed)
        assertFalse("a clean run should cost nothing but the day", outcome.damage.isAnything)
        assertEquals(LandmarkId.OREGON_CITY, after.heading)
        assertEquals(0, after.milesIntoSegment)
        assertEquals(before.dayOfJourney + 1, after.dayOfJourney)
        assertEquals(before.inventory.oxen, after.inventory.oxen)
    }

    /**
     * What the result screen's heading turns on — see `RaftResultScreen`, which titles a
     * descent "Ashore" or "A rough run" by this and nothing else.
     *
     * The distinction is between what can be bought again and what cannot. Food and
     * clothing are supplies; an ox lost on the Columbia is gone for good, because there
     * is no fort left between here and Oregon City.
     */
    @Test
    fun `only an ox or a life makes a descent a rough one`() {
        assertFalse(RaftDamage().costLifeOrLivestock)
        assertFalse(RaftDamage(foodPounds = 90).costLifeOrLivestock)
        assertFalse(RaftDamage(clothingSets = 2).costLifeOrLivestock)
        assertTrue(RaftDamage(oxenLost = 1).costLifeOrLivestock)
        assertTrue(RaftDamage(drowned = listOf("Amanda")).costLifeOrLivestock)

        // And it stays a strictly narrower question than "did the river take anything",
        // which is what decides whether the losses are itemised at all.
        val supplies = RaftDamage(foodPounds = 40)
        assertTrue(supplies.isAnything)
        assertFalse(supplies.costLifeOrLivestock)
    }

    /**
     * The whole reason [RaftImpact] grades its hits — the 1985 game charged the same for
     * a scrape as for a broadside, and its designer names that as the fault he'd fix.
     */
    @Test
    fun `a graze costs less than a solid hit, which costs less than the bank`() {
        fun averageFoodLost(impact: RaftImpact): Double = (1L..200L)
            .map { seed -> descend(atTheRapids(seed), listOf(impact)).damage.foodPounds }
            .average()

        val graze = averageFoodLost(RaftImpact.GRAZE)
        val solid = averageFoodLost(RaftImpact.SOLID)
        val bank = averageFoodLost(RaftImpact.BANK)

        assertTrue("graze $graze vs solid $solid", solid > graze)
        assertTrue("solid $solid vs bank $bank", bank > solid)
    }

    @Test
    fun `only the bank and a wreck cost the party its health`() {
        val before = atTheRapids()
        val grazed = descend(before, List(3) { RaftImpact.GRAZE })
        assertEquals(
            "grazes are supplies, not injuries",
            before.party.healthPoints,
            grazed.day.state.party.healthPoints,
        )

        val banked = descend(before, listOf(RaftImpact.BANK))
        assertTrue(
            "the bank should hurt: ${banked.day.state.party.healthPoints}",
            banked.day.state.party.healthPoints > before.party.healthPoints,
        )
    }

    @Test
    fun `enough damage breaks the raft up`() {
        val light = descend(atTheRapids(), listOf(RaftImpact.GRAZE, RaftImpact.SOLID))
        assertFalse(light.wrecked)

        // Three bank hits is nine severity points against a threshold of six.
        val heavy = descend(atTheRapids(), List(3) { RaftImpact.BANK })
        assertTrue(heavy.wrecked)
        assertTrue(
            "a wreck should cost more than the hits that caused it",
            heavy.damage.foodPounds > light.damage.foodPounds,
        )
    }

    @Test
    fun `missing the landing is paid for in days, not supplies`() {
        val before = atTheRapids()
        val missed = descend(before, landed = false)

        assertFalse(missed.landed)
        assertEquals(Rafting.MISSED_LANDING_DAYS, missed.extraDays)
        assertEquals(
            before.dayOfJourney + 1 + Rafting.MISSED_LANDING_DAYS,
            missed.day.state.dayOfJourney,
        )
        assertFalse("walking back should cost no cargo", missed.damage.isAnything)
        // Still on the trail, still heading for Oregon City — a missed landing is a
        // delay, not a dead end.
        assertEquals(LandmarkId.OREGON_CITY, missed.day.state.heading)
    }

    /**
     * A near-empty wagon cannot lose more than it has.
     *
     * Worth pinning because the loss table rolls in absolute pounds and heads, without
     * reference to the inventory: a party that reached the Columbia on fumes would go
     * negative without the cap, and negative food scores points.
     */
    @Test
    fun `losses never take more than the wagon carries`() {
        val destitute = atTheRapids().copy(
            inventory = Inventory(oxen = 1, foodPounds = 12, clothingSets = 0, bullets = 0),
        )
        for (seed in 1L..100L) {
            val outcome = descend(
                destitute.copy(rng = Rng.seeded(seed)),
                List(3) { RaftImpact.BANK },
            )
            val inventory = outcome.day.state.inventory
            assertTrue("food went negative on seed $seed", inventory.foodPounds >= 0)
            assertTrue("oxen went negative on seed $seed", inventory.oxen >= 0)
            assertTrue("clothing went negative on seed $seed", inventory.clothingSets >= 0)
        }
    }

    /**
     * The river cannot drown the same traveller twice.
     *
     * [Rafting.roll] knows only *that* someone was taken; the names are drawn once,
     * against the survivors as they stand. Rolling them at the point of the impact —
     * the obvious way to write it — would let two hits in one descent kill one person
     * twice and leave the party count wrong.
     */
    @Test
    fun `the drowned are distinct people`() {
        val drownings = (1L..400L).mapNotNull { seed ->
            val outcome = descend(atTheRapids(seed), List(4) { RaftImpact.BANK })
            outcome.damage.drowned.takeIf { it.size > 1 }
        }
        assertTrue("no descent in 400 drowned more than one person", drownings.isNotEmpty())
        for (names in drownings) {
            assertEquals("the same person drowned twice: $names", names.distinct().size, names.size)
        }
    }

    @Test
    fun `a party drowned to the last ends the run`() {
        val alone = atTheRapids().copy(
            party = Party(listOf(Traveler(name = "Amanda", isLeader = true))),
        )
        val lost = (1L..400L).asSequence()
            .map { seed -> descend(alone.copy(rng = Rng.seeded(seed)), List(4) { RaftImpact.BANK }) }
            .firstOrNull { it.damage.drowned.isNotEmpty() }

        assertTrue("no seed in 400 drowned the last traveller", lost != null)
        assertEquals(Outcome.PartyLost, lost!!.day.state.outcome)
    }

    /** Reading the descent must not disturb the state the caller is still holding. */
    @Test
    fun `a descent leaves the run's own RNG untouched`() {
        val state = atTheRapids()
        val before = state.rng.snapshot()

        descend(state, List(2) { RaftImpact.SOLID })
        descend(state, List(2) { RaftImpact.SOLID })

        assertEquals(before, state.rng.snapshot())
        assertEquals(LandmarkId.COLUMBIA_RIVER, state.at)
        assertEquals(null, state.heading)
    }
}
