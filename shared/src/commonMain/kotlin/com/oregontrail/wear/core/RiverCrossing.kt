package com.oregontrail.wear.core

/** What the player can do when the wagon reaches a river. */
enum class CrossingMethod(val displayName: String) {
    FORD("Ford the river"),
    CAULK("Caulk the wagon and float"),
    FERRY("Take the ferry"),

    /**
     * Pay someone who knows the crossing to take the wagon over.
     *
     * Offered only where [LandmarkKind.River.guideAvailable] — in practice the Snake, as
     * in the 1985 version. Paid in clothing rather than cash, which is both what the
     * original charged and the reason this is a decision: the Snake is the last river
     * before the Blue Mountains, and clothing is what keeps the party alive in them.
     */
    HIRE_GUIDE("Hire a guide"),

    WAIT("Wait a few days"),
}

/** The river as the party finds it today. Depth is re-rolled while they wait. */
data class RiverConditions(
    val landmark: LandmarkId,
    val widthFeet: Int,
    val depthFeet: Double,
    val ferryAvailable: Boolean,
    val ferryCostCents: Int,
    val guideAvailable: Boolean,
    val guideCostSets: Int,
) {
    val isDangerousToFord: Boolean get() = depthFeet > Rivers.DANGEROUS_DEPTH_FEET

    fun available(inventory: Inventory): List<CrossingMethod> = buildList {
        add(CrossingMethod.FORD)
        add(CrossingMethod.CAULK)
        if (ferryAvailable && inventory.cashCents >= ferryCostCents) add(CrossingMethod.FERRY)
        if (guideAvailable && inventory.clothingSets >= guideCostSets) {
            add(CrossingMethod.HIRE_GUIDE)
        }
        add(CrossingMethod.WAIT)
    }
}

sealed interface CrossingResult {
    /** Across without incident. */
    data object Safe : CrossingResult

    /** Across, but the river took some of the load. */
    data class LostSupplies(val foodPounds: Int, val clothingSets: Int) : CrossingResult

    /** The wagon tipped: supplies gone and, sometimes, someone with them. */
    data class Capsized(
        val foodPounds: Int,
        val oxenLost: Int,
        val drowned: String?,
    ) : CrossingResult

    /** Waited out the weather; the river may or may not have dropped. */
    data class Waited(val days: Int) : CrossingResult
}

data class CrossingOutcome(val state: GameState, val result: CrossingResult)

/**
 * River crossings — the decision the 1985 version is most remembered for.
 *
 * The one sourced number here is the three-foot threshold: fording deeper than that is
 * documented as a way to lose the wagon. Everything else is a tuning knob chosen to
 * make the four options genuinely trade off against each other, rather than one being
 * strictly correct:
 *
 * - **Ford** is free and safe in shallow water, ruinous in deep.
 * - **Caulk** ignores depth but risks capsizing in a wide, fast river.
 * - **Ferry** is safe but costs money that scores points at the end.
 * - **Hire a guide** is nearly as safe as a ferry, where offered, and costs clothing.
 * - **Wait** costs days and food, and only sometimes helps.
 */
object RiverCrossing {

    /** Tuning knob: what the ferryman charges. */
    const val FERRY_COST_CENTS = 5_00

    /**
     * What a guide charges, in sets of clothing.
     *
     * Clothing rather than cash because that is what the 1985 version charged, and because
     * it makes the price bite in a way money would not: cash at this point in the run is
     * nearly spent anyway, whereas [TurnEngine]'s cold-weather penalty checks clothing
     * against the number of survivors, and the Snake is the last river before the Blue
     * Mountains. Three sets is a party of five going into the mountains underclothed.
     */
    const val GUIDE_COST_SETS = 3

    /**
     * What a guide is worth: Bouchard states the 1985 game's guide "reduces your risk by
     * 80%", which is the one number in this file taken from the original's designer rather
     * than tuned. Applied to the ford risk, since a guide walks the wagon over rather than
     * floating it.
     */
    private const val GUIDE_RISK_MULTIPLIER = 0.20

    /** Tuning knob: days lost to a single "wait" decision. */
    private const val WAIT_DAYS = 3

    /**
     * Reads the river at the party's current landmark, or null if they're not at one.
     *
     * Uses a *derived* generator rather than consuming the run's RNG, so this is safe
     * to call repeatedly — the UI will ask on every recomposition, and a river that
     * changed depth each time it was looked at would make the decision meaningless.
     * The derivation is salted with the day, so waiting does re-roll it.
     */
    fun conditionsAt(state: GameState): RiverConditions? {
        val landmark = state.currentLandmark
        val river = landmark.kind as? LandmarkKind.River ?: return null

        // Depth varies with recent weather, which is what makes the decision live
        // rather than a lookup: the same river is a different problem each run.
        val weatherShift = when (state.weather) {
            Weather.RAINY, Weather.SNOWY -> 1.4
            Weather.COLD, Weather.VERY_COLD -> 0.9
            Weather.HOT -> 0.7
            else -> 1.0
        }
        val local = state.rng.derive(state.dayOfJourney * 31L + landmark.id.ordinal)
        val jitter = 0.75 + local.nextDouble() * 0.6
        val depth = river.typicalDepthFeet * weatherShift * jitter

        return RiverConditions(
            landmark = landmark.id,
            widthFeet = river.widthFeet,
            depthFeet = (depth * 10).toInt() / 10.0,
            ferryAvailable = river.ferryAvailable,
            ferryCostCents = FERRY_COST_CENTS,
            guideAvailable = river.guideAvailable,
            guideCostSets = GUIDE_COST_SETS,
        )
    }

    fun cross(
        state: GameState,
        conditions: RiverConditions,
        method: CrossingMethod,
    ): CrossingOutcome {
        // Fork first, so the state handed in is left untouched — see Rng.fork.
        val forked = state.copy(rng = state.rng.fork())
        return when (method) {
            CrossingMethod.FORD -> ford(forked, conditions)
            CrossingMethod.CAULK -> caulk(forked, conditions)
            CrossingMethod.FERRY -> ferry(forked, conditions)
            CrossingMethod.HIRE_GUIDE -> guided(forked, conditions)
            CrossingMethod.WAIT -> wait(forked)
        }
    }

    private fun ford(state: GameState, conditions: RiverConditions): CrossingOutcome =
        attemptFord(state, fordRisk(conditions))

    /**
     * A guide takes the wagon over, for [GUIDE_COST_SETS] sets of clothing.
     *
     * The clothing is handed over whatever happens next — a guide is paid for the crossing,
     * not for the outcome — so a party that hires one and capsizes anyway is out both.
     */
    private fun guided(state: GameState, conditions: RiverConditions): CrossingOutcome {
        val paid = state.copy(
            inventory = state.inventory.adjust(Good.CLOTHING, -conditions.guideCostSets),
        )
        return attemptFord(paid, fordRisk(conditions) * GUIDE_RISK_MULTIPLIER)
    }

    /** Safe up to the three-foot mark, then rapidly not. */
    private fun fordRisk(conditions: RiverConditions): Double = when {
        conditions.depthFeet <= 2.0 -> 0.02
        conditions.depthFeet <= Rivers.DANGEROUS_DEPTH_FEET -> 0.15
        else -> 0.45 + (conditions.depthFeet - Rivers.DANGEROUS_DEPTH_FEET) * 0.15
    }

    private fun attemptFord(state: GameState, risk: Double): CrossingOutcome =
        if (state.rng.chance(risk.coerceAtMost(0.95))) {
            capsize(state)
        } else {
            CrossingOutcome(arriveAcross(state), CrossingResult.Safe)
        }

    private fun caulk(state: GameState, conditions: RiverConditions): CrossingOutcome {
        val rng = state.rng
        // Floating doesn't care how deep it is — it cares how far there is to drift.
        val risk = 0.08 + (conditions.widthFeet / 1000.0) * 0.18
        return if (rng.chance(risk.coerceAtMost(0.6))) {
            capsize(state)
        } else {
            CrossingOutcome(arriveAcross(state), CrossingResult.Safe)
        }
    }

    private fun ferry(state: GameState, conditions: RiverConditions): CrossingOutcome {
        val paid = state.inventory.spend(conditions.ferryCostCents)
        return CrossingOutcome(
            arriveAcross(state.copy(inventory = paid)),
            CrossingResult.Safe,
        )
    }

    private fun wait(state: GameState): CrossingOutcome {
        var s = state
        repeat(WAIT_DAYS) { s = TurnEngine.rest(s).state }
        return CrossingOutcome(s, CrossingResult.Waited(WAIT_DAYS))
    }

    private fun capsize(state: GameState): CrossingOutcome {
        val rng = state.rng
        val foodLost = rng.nextInt(20, 80).coerceAtMost(state.inventory.foodPounds)
        val oxenLost = if (rng.chance(0.4)) 1 else 0
        val clothingLost = rng.nextInt(0, 2).coerceAtMost(state.inventory.clothingSets)

        var party = state.party
        // Losing someone in the water is rare but is the reason the decision has weight.
        val drowned = if (rng.chance(0.12)) party.survivors.let { rng.pick(it) } else null
        if (drowned != null) {
            party = party.withMember(drowned.name) {
                it.copy(alive = false, diedOnDay = state.dayOfJourney)
            }
        }

        val inventory = state.inventory.copy(
            foodPounds = state.inventory.foodPounds - foodLost,
            oxen = (state.inventory.oxen - oxenLost).coerceAtLeast(0),
            clothingSets = state.inventory.clothingSets - clothingLost,
        )

        var s = arriveAcross(state.copy(inventory = inventory, party = party))
        if (s.party.isWipedOut) s = s.copy(outcome = Outcome.PartyLost)

        return CrossingOutcome(
            s,
            if (drowned != null || oxenLost > 0) {
                CrossingResult.Capsized(foodLost, oxenLost, drowned?.name)
            } else {
                CrossingResult.LostSupplies(foodLost, clothingLost)
            },
        )
    }

    /**
     * Puts the party on the far bank and points them at the next landmark. Crossing is
     * the act of leaving a river landmark, so it resolves the onward heading the same
     * way arriving at any single-route landmark would.
     */
    private fun arriveAcross(state: GameState): GameState {
        val landmark = state.currentLandmark
        val onward = when {
            landmark.isEnd -> null
            landmark.isBranch -> null
            else -> landmark.routes.first().to
        }
        return state.copy(heading = onward, milesIntoSegment = 0)
    }
}
