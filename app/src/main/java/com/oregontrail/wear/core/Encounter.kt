package com.oregontrail.wear.core

/**
 * Something met on the trail between landmarks — distinct from the misfortunes and
 * windfalls [TurnEngine]'s own daily roll already covers, because these come with an
 * offer attached rather than just narrating an outcome, and so need a decision from the
 * player rather than a tap to dismiss.
 */
sealed interface Encounter {
    /** Another party heading the other way. Flavour only — nothing to decide. */
    data class Traveler(val tip: String) : Encounter

    /** A trader offers to swap goods. No cash changes hands either way. */
    data class Trade(
        val wants: Good,
        val wantsQuantity: Int,
        val gives: Good,
        val givesQuantity: Int,
    ) : Encounter

    /** A guide offers advice for a fee — cheaper than a doctor, and just as welcome. */
    data class Guide(val costCents: Int, val healthBenefit: Int) : Encounter
}

/**
 * Rolls and resolves trail encounters.
 *
 * Not sourced from the 1985 design — docs/reference/game-mechanics-1985.md is silent on
 * this mechanic — so every number here is a tuning knob, chosen to keep the offers
 * modest rather than to model anything historical.
 */
object Encounters {

    /** Chance per day of travel that the party meets someone. */
    const val CHANCE = 0.10

    private const val GUIDE_HEALTH_BENEFIT = 6
    private const val GUIDE_MIN_COST_CENTS = 3_00
    private const val GUIDE_MAX_COST_CENTS = 8_00

    private val TRAVELER_TIPS = listOf(
        "Heard tell the river crossings run high this time of year.",
        "A family two days back lost an ox to bad water. Careful what you drink.",
        "Game's been scarce further west — stock up on food while you can.",
        "There's good grazing a few days on, if your oxen need it.",
        "Someone at the last fort was selling spares cheap.",
        "Folks say the mountain passes close early most years.",
    )

    /** One encounter, weighted toward flavour over offers that cost something to weigh. */
    fun roll(state: GameState, rng: Rng): Encounter = when (rng.nextInt(100)) {
        in 0..39 -> Encounter.Traveler(rng.pick(TRAVELER_TIPS))
        in 40..74 -> rollTrade(state, rng) ?: Encounter.Traveler(rng.pick(TRAVELER_TIPS))
        else -> Encounter.Guide(
            costCents = rng.nextInt(GUIDE_MIN_COST_CENTS, GUIDE_MAX_COST_CENTS),
            healthBenefit = GUIDE_HEALTH_BENEFIT,
        )
    }

    /**
     * An offer the party can actually fulfil: [Encounter.Trade.wantsQuantity] never
     * exceeds what's currently held, so declining is a real choice rather than the only
     * option. Returns null if the wagon is carrying nothing to trade away yet — early in
     * a run, before the store visit, that's every good.
     */
    private fun rollTrade(state: GameState, rng: Rng): Encounter.Trade? {
        val held = Good.entries.filter { state.inventory.amountOf(it) > 0 }
        if (held.isEmpty()) return null

        val wants = rng.pick(held)
        val gives = rng.pick(Good.entries.filter { it != wants })
        val wantsMax = minOf(state.inventory.amountOf(wants), offerCeiling(wants))

        return Encounter.Trade(
            wants = wants,
            wantsQuantity = rng.nextInt(1, wantsMax),
            gives = gives,
            givesQuantity = rng.nextInt(1, offerCeiling(gives)),
        )
    }

    /** Keeps a single offer modest, independent of what the party can afford or holds. */
    private fun offerCeiling(good: Good): Int = when (good) {
        Good.OXEN -> 2
        Good.FOOD -> 60
        Good.CLOTHING -> 3
        Good.BULLETS -> 40
        Good.SPARE_WHEEL, Good.SPARE_AXLE, Good.SPARE_TONGUE -> 2
    }

    /** Books an accepted trade. No-ops if the party no longer holds enough to pay it. */
    fun accept(state: GameState, trade: Encounter.Trade): GameState {
        if (state.inventory.amountOf(trade.wants) < trade.wantsQuantity) return state
        return state.copy(
            inventory = state.inventory
                .adjust(trade.wants, -trade.wantsQuantity)
                .adjust(trade.gives, trade.givesQuantity),
        )
    }

    /** Books a hired guide. No-ops if the party can't cover the fee. */
    fun hire(state: GameState, guide: Encounter.Guide): GameState {
        if (state.inventory.cashCents < guide.costCents) return state
        return state.copy(
            inventory = state.inventory.spend(guide.costCents),
            party = state.party.adjustHealth(-guide.healthBenefit),
        )
    }
}
