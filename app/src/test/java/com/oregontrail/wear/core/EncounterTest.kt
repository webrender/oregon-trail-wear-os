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
    fun `paying a doctor spends cash and improves health`() {
        val state = TestStates.outfitted(cashCents = 100_00)
            .let { it.copy(party = it.party.adjustHealth(40)) }
        val doctor = Encounter.Healer(costCents = 20_00, healthBenefit = 12, patient = null)

        val after = Encounters.treat(state, doctor)

        assertEquals(state.inventory.cashCents - 20_00, after.inventory.cashCents)
        assertEquals(28, after.party.healthPoints)
    }

    @Test
    fun `a doctor called to a sick traveller cures them`() {
        val ill = TestStates.outfitted(cashCents = 100_00).let { state ->
            state.copy(
                party = state.party.withMember("Eliza") { it.copy(ailment = Ailment.CHOLERA) },
            )
        }
        val doctor = Encounter.Healer(costCents = 40_00, healthBenefit = 20, patient = "Eliza")

        val after = Encounters.treat(ill, doctor)

        assertTrue("Eliza should be well again", after.party.ill.isEmpty())
    }

    @Test
    fun `a doctor the party can't afford is refused`() {
        val state = TestStates.outfitted(cashCents = 1_00)
        val doctor = Encounter.Healer(costCents = 20_00, healthBenefit = 12, patient = null)

        val after = Encounters.treat(state, doctor)
        assertEquals(state, after)
    }

    /**
     * The property that justifies the encounter existing at all: a scout reports on this
     * run, not from a list of sayings. Standing before the Snake, one of the things they
     * can say has to be about the Snake.
     */
    @Test
    fun `a scout ahead of the Snake knows there is no ferry there`() {
        val beforeSnake = TestStates.outfitted().copy(
            at = LandmarkId.FORT_HALL,
            heading = LandmarkId.SNAKE_RIVER,
            milesIntoSegment = 40,
        )
        val reports = Encounters.reports(beforeSnake)

        assertTrue(
            "no report mentioned the Snake: $reports",
            reports.any { it.contains("Snake River") && it.contains("uide") },
        )
    }

    @Test
    fun `a scout never reports past a branch the player has not chosen`() {
        // Standing one landmark short of South Pass, where the road forks to the Green
        // River or to Fort Bridger. Neither is certain, so neither may be described.
        val beforeFork = TestStates.outfitted().copy(
            at = LandmarkId.INDEPENDENCE_ROCK,
            heading = LandmarkId.SOUTH_PASS,
        )
        val reports = Encounters.reports(beforeFork)

        assertTrue(
            "a report guessed at the road past South Pass: $reports",
            reports.none { it.contains("Green River") || it.contains("Fort Bridger") },
        )
    }

    @Test
    fun `a scout always has something true to say`() {
        var state = TestStates.outfitted()
        for (day in 1..120) {
            assertTrue("nothing to report on day $day", Encounters.reports(state).isNotEmpty())
            val result = TestStates.run(state, 1).lastOrNull() ?: break
            state = result.state
            if (state.isOver) break
        }
    }

    /**
     * Scouts are retired. The three tests above still hold [Encounters.reports] to its
     * contract so the encounter can be brought back, but nothing may roll one — including
     * the empty-wagon path, where a trade cannot be offered and the fallback used to be a
     * scout. See [Encounter.Scout].
     */
    @Test
    fun `a scout is never rolled`() {
        val states = mapOf(
            "outfitted" to TestStates.outfitted(),
            "empty wagon" to TestStates.fresh(),
        )
        for ((name, state) in states) {
            for (seed in 1L..500L) {
                assertTrue(
                    "a scout turned up for $name on seed $seed",
                    Encounters.roll(state, Rng.seeded(seed)) !is Encounter.Scout,
                )
            }
        }
    }

    @Test
    fun `advanceDay occasionally produces an encounter`() {
        val found = (1L..500L).any { seed ->
            val state = TestStates.outfitted(seed = seed)
            TurnEngine.advanceDay(state).encounter != null
        }
        assertTrue("no encounter turned up in 500 seeded days", found)
    }

    // ---- Riders ----

    private fun onThePlains(seed: Long = 99L): GameState =
        TestStates.outfitted(seed = seed).copy(
            at = LandmarkId.CHIMNEY_ROCK,
            heading = LandmarkId.FORT_LARAMIE,
            milesIntoSegment = 50,
        )

    @Test
    fun `riders only ride in the middle country`() {
        val prairie = TestStates.outfitted().copy(
            at = LandmarkId.INDEPENDENCE,
            heading = LandmarkId.KANSAS_RIVER,
        )
        val metOnThePrairie = (1L..600L).any {
            Encounters.roll(prairie, Rng.seeded(it)) is Encounter.Riders
        }
        val metOnThePlains = (1L..600L).any {
            Encounters.roll(onThePlains(), Rng.seeded(it)) is Encounter.Riders
        }

        assertTrue("riders should not appear on the prairie", !metOnThePrairie)
        assertTrue("riders should appear on the plains", metOnThePlains)
    }

    /**
     * The screen prints the rider count beside `assets/art/portrait_riders.png`, in which
     * four riders are plainly countable. A rolled count made the sentence contradict the
     * picture. The literal 4 here is deliberate: it should fail if the constant moves
     * without the art moving too.
     */
    @Test
    fun `riders always number what the portrait shows`() {
        assertEquals("the portrait shows four riders", 4, Encounters.RIDERS_COUNT)

        val met = (1L..600L)
            .map { Encounters.roll(onThePlains(), Rng.seeded(it)) }
            .filterIsInstance<Encounter.Riders>()

        assertTrue("no riders turned up to check", met.isNotEmpty())
        assertTrue(
            "rider counts varied: ${met.map { it.count }.distinct()}",
            met.all { it.count == 4 },
        )
    }

    @Test
    fun `handing over supplies costs food and never a life`() {
        val state = onThePlains()
        val outcome = Encounters.face(state, Encounter.Riders(4), RidersResponse.PAY)

        assertTrue(
            "food should be taken",
            outcome.state.inventory.foodPounds < state.inventory.foodPounds,
        )
        assertEquals(
            "nobody dies handing over supplies",
            state.party.survivorCount,
            outcome.state.party.survivorCount,
        )
        assertTrue("the player should be told", outcome.events.isNotEmpty())
    }

    @Test
    fun `fighting always spends ammunition and sometimes costs a life`() {
        var deaths = 0
        for (seed in 1L..300L) {
            val state = onThePlains(seed)
            val outcome = Encounters.face(state, Encounter.Riders(4), RidersResponse.FIGHT)

            assertTrue(
                "seed $seed spent no ammunition",
                outcome.state.inventory.bullets < state.inventory.bullets,
            )
            if (outcome.state.party.survivorCount < state.party.survivorCount) deaths++
        }
        assertTrue("a fight should sometimes kill someone, never did in 300", deaths > 0)
        assertTrue("a fight should usually not kill anyone, killed $deaths of 300", deaths < 90)
    }

    @Test
    fun `riding hard gives up ground and costs strength`() {
        val state = onThePlains()
        val outcome = Encounters.face(state, Encounter.Riders(4), RidersResponse.FLEE)

        assertTrue(
            "ground should be given up",
            outcome.state.milesIntoSegment < state.milesIntoSegment,
        )
        assertTrue(
            "the run should cost the party",
            outcome.state.party.healthPoints > state.party.healthPoints,
        )
    }

    @Test
    fun `a fight is not offered to a party without the ammunition for one`() {
        assertTrue(Encounters.canFight(TestStates.outfitted(bullets = 200)))
        assertTrue(!Encounters.canFight(TestStates.outfitted(bullets = 4)))
    }

    /**
     * The frequency the encounter was asked for: once or twice a journey, not once a week.
     *
     * Simulated rather than reasoned about, because the answer depends on three numbers
     * that live in different files — [Encounters.CHANCE], the riders' share of the
     * encounter roll, and how many days the trail's middle takes at the default pace. If
     * any of those moves, this is the test that notices.
     *
     * Asserts on the *shape* of the distribution over many journeys rather than on a hard
     * maximum over a few. Riders are a per-day coin flip, so the count per journey has a
     * Poisson tail: at the target rate about one journey in two hundred meets five, which a
     * `max <= 4` over 40 seeds turns into a test that fails on the RNG stream shifting
     * rather than on the rate changing. It did exactly that when [Encounters.CHANCE] was
     * halved and the riders' share doubled to compensate — a change that leaves the per-day
     * rider rate identical by construction.
     */
    @Test
    fun `a whole journey meets riders once or twice`() {
        val counts = (1L..200L).map { seed ->
            TestStates.run(TestStates.outfitted(seed = seed), days = 200)
                .count { it.encounter is Encounter.Riders }
        }
        val average = counts.average()
        val metAny = counts.count { it >= 1 }.toDouble() / counts.size
        val crowds = counts.count { it > 4 }.toDouble() / counts.size

        assertTrue(
            "riders averaged $average per journey, wanted roughly one or two",
            average in 0.8..1.8,
        )
        // The floor the encounter exists for: most journeys must actually face them.
        assertTrue("only ${metAny * 100}% of journeys met riders at all", metAny >= 0.55)
        assertTrue("some journey should meet none", counts.any { it == 0 })
        assertTrue("crowds should be rare, saw ${crowds * 100}% over four", crowds <= 0.03)
    }
}
