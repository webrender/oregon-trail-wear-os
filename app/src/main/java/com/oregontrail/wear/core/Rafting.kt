package com.oregontrail.wear.core

/**
 * How hard the raft hit something.
 *
 * The 1985 game did not grade its impacts — a scrape and a broadside cost the same, and
 * the designer names that as the thing he wishes had been built (see
 * docs/reference/game-mechanics-1985.md). Grading them is the whole reason this enum
 * exists: [RaftScreen][com.oregontrail.wear.ui.screens.RaftScreen] decides which one a
 * collision was from how deep the overlap went, and the loss table below does the rest.
 */
enum class RaftImpact(val severity: Int) {
    /** Clipped a rock in passing. */
    GRAZE(1),

    /** Hit a rock square on. */
    SOLID(2),

    /** Put the raft into the bank, which is worse than any rock. */
    BANK(3),
}

/** The bill for one run down the Columbia. */
data class RaftDamage(
    val foodPounds: Int = 0,
    val oxenLost: Int = 0,
    val clothingSets: Int = 0,
    val drowned: List<String> = emptyList(),
) {
    val isAnything: Boolean
        get() = foodPounds > 0 || oxenLost > 0 || clothingSets > 0 || drowned.isNotEmpty()

    /**
     * Whether the river took something the party cannot simply buy more of.
     *
     * Food and clothing are supplies: a graze that costs ten pounds of flour is a scrape,
     * and calling it anything more devalues the word for the descent that actually went
     * badly. An ox or a life is a different order of thing — an ox lost here cannot be
     * replaced at all, because there is no fort left between the Columbia and Oregon City.
     */
    val costLifeOrLivestock: Boolean
        get() = oxenLost > 0 || drowned.isNotEmpty()

    operator fun plus(other: RaftDamage): RaftDamage = RaftDamage(
        foodPounds = foodPounds + other.foodPounds,
        oxenLost = oxenLost + other.oxenLost,
        clothingSets = clothingSets + other.clothingSets,
        drowned = drowned + other.drowned,
    )
}

/** What the descent did to the run. */
data class RaftOutcome(
    val day: DayResult,
    val damage: RaftDamage,
    /** True if the raft broke up before reaching the landing. */
    val wrecked: Boolean,
    /** True if the raft was put ashore at the landing rather than swept past it. */
    val landed: Boolean,
    /** Days spent walking back to the landing, when it was missed. */
    val extraDays: Int,
)

/**
 * Rafting the Columbia: the last leg, and the run's only unavoidable minigame.
 *
 * The split mirrors [Hunting]. Steering, rocks, and collision detection are a UI
 * concern and live in the Compose layer; this object owns only what has to survive a
 * save and be replayable — what the impacts cost, and the days the descent takes. The
 * screen reports a list of [RaftImpact]s and whether the landing was made, once, at the
 * end.
 *
 * Everything here is a tuning knob. The one *sourced* fact about the original's numbers
 * is the designer's own verdict that they were too harsh — "the losses from hitting a
 * rock often seem excessive, and the losses from hitting the river bank seem even more
 * out of whack" — so these are pitched to sting rather than to ruin, with the bank kept
 * distinctly worse than a rock because that much of the original's shape is worth
 * keeping.
 */
object Rafting {

    /** Severity points that break the raft up rather than merely damaging it. */
    const val WRECK_THRESHOLD = 6

    /** Health cost of putting the raft into the bank, per hit. */
    private const val BANK_HEALTH_PENALTY = 3

    /** Health cost of breaking the raft up. */
    private const val WRECK_HEALTH_PENALTY = 8

    /** Days spent recovering the wagon and walking back downriver to the landing. */
    const val MISSED_LANDING_DAYS = 2

    /**
     * Forks the run's RNG for a descent to roll its losses from — see [Rng.fork] and
     * [Hunting.start], which does the same thing for the same reason.
     */
    fun start(state: GameState): Rng = state.rng.fork()

    /** True where the party is standing on a bank with no choice but to run the water. */
    fun isAtRapids(state: GameState): Boolean =
        !state.isOver && state.currentLandmark.kind is LandmarkKind.Rapids

    /**
     * Books a finished descent against [state].
     *
     * Order matters: losses are applied first, then the party is put back on the trail
     * heading for Oregon City, and only then does the day pass — so a party that lost
     * its last food to the river still starves on the day it happens rather than a day
     * late.
     */
    fun finish(
        state: GameState,
        rng: Rng,
        impacts: List<RaftImpact>,
        landed: Boolean,
    ): RaftOutcome {
        val wrecked = impacts.sumOf { it.severity } >= WRECK_THRESHOLD

        var damage = RaftDamage()
        for (impact in impacts) damage += roll(rng, impact)
        if (wrecked) damage += rollWreck(rng)

        var party = state.party
        // Drowning is rolled per impact above, but the names are drawn here so a hit
        // cannot take someone the river has already taken.
        val drowned = mutableListOf<String>()
        repeat(damage.drowned.size) {
            val survivors = party.survivors
            if (survivors.isEmpty()) return@repeat
            val victim = rng.pick(survivors)
            drowned += victim.name
            party = party.withMember(victim.name) {
                it.copy(alive = false, diedOnDay = state.dayOfJourney)
            }
        }

        val healthCost = impacts.count { it == RaftImpact.BANK } * BANK_HEALTH_PENALTY +
            if (wrecked) WRECK_HEALTH_PENALTY else 0

        val settled = damage.copy(
            foodPounds = damage.foodPounds.coerceAtMost(state.inventory.foodPounds),
            oxenLost = damage.oxenLost.coerceAtMost(state.inventory.oxen),
            clothingSets = damage.clothingSets.coerceAtMost(state.inventory.clothingSets),
            drowned = drowned,
        )

        var s = state.copy(
            rng = rng,
            party = party.adjustHealth(healthCost),
            inventory = state.inventory.copy(
                foodPounds = state.inventory.foodPounds - settled.foodPounds,
                oxen = state.inventory.oxen - settled.oxenLost,
                clothingSets = state.inventory.clothingSets - settled.clothingSets,
            ),
            // The descent is the act of leaving the rapids, exactly as a crossing is the
            // act of leaving a river — so it resolves the onward heading the same way.
            heading = state.currentLandmark.routes.firstOrNull()?.to,
            milesIntoSegment = 0,
        )
        if (s.party.isWipedOut) s = s.copy(outcome = Outcome.PartyLost)

        // A missed landing is paid for in days, not supplies: the raft is recovered
        // downriver and walked back up. Cheaper than a wreck, and it should be — it is
        // the mistake a player makes while doing everything else right.
        val extraDays = if (landed) 0 else MISSED_LANDING_DAYS
        val day = spendDays(s, 1 + extraDays)

        return RaftOutcome(
            day = day,
            damage = settled,
            wrecked = wrecked,
            landed = landed,
            extraDays = extraDays,
        )
    }

    /** What one [impact] costs, before it is capped against what the wagon carries. */
    private fun roll(rng: Rng, impact: RaftImpact): RaftDamage = when (impact) {
        // A graze is meant to be survivable and frequent: the price of a near miss,
        // not of a mistake.
        RaftImpact.GRAZE -> RaftDamage(foodPounds = rng.nextInt(5, 20))

        RaftImpact.SOLID -> RaftDamage(
            foodPounds = rng.nextInt(25, 60),
            oxenLost = if (rng.chance(0.30)) 1 else 0,
            clothingSets = if (rng.chance(0.35)) 1 else 0,
            drowned = if (rng.chance(0.06)) listOf(PENDING) else emptyList(),
        )

        RaftImpact.BANK -> RaftDamage(
            foodPounds = rng.nextInt(40, 90),
            oxenLost = if (rng.chance(0.50)) 1 else 0,
            clothingSets = rng.nextInt(1, 2),
            drowned = if (rng.chance(0.12)) listOf(PENDING) else emptyList(),
        )
    }

    /** What breaking the raft up costs, on top of the impacts that caused it. */
    private fun rollWreck(rng: Rng): RaftDamage = RaftDamage(
        foodPounds = rng.nextInt(60, 120),
        oxenLost = rng.nextInt(1, 2),
        clothingSets = rng.nextInt(1, 2),
        drowned = if (rng.chance(0.25)) listOf(PENDING) else emptyList(),
    )

    /**
     * Advances [days] days of exertion, merging them into one [DayResult].
     *
     * The screen shows a single "this is what the river did" card, so the days behind it
     * collapse into one report; [TurnEngine.activeDay] short-circuits once a run is over,
     * so a party wiped out on the water does not go on eating.
     */
    private fun spendDays(state: GameState, days: Int): DayResult {
        var s = state
        val events = mutableListOf<GameEvent>()
        repeat(days) {
            val result = TurnEngine.activeDay(s)
            s = result.state
            events += result.events
        }
        return DayResult(s, events)
    }

    /**
     * Placeholder standing in for a drowning victim between the roll and the draw.
     *
     * [roll] knows *that* the river took someone but not *who* — picking a name there
     * would let two impacts in the same descent drown the same person twice. The names
     * are drawn once, in [finish], against the party as it stands.
     */
    private const val PENDING = ""
}
