package com.oregontrail.wear.core

import kotlinx.serialization.Serializable

/** How a run ended. Null while it is still in progress. */
@Serializable
sealed interface Outcome {
    /** Reached Oregon City with at least one survivor. */
    @Serializable
    data object Arrived : Outcome

    /** Everyone died on the trail. */
    @Serializable
    data object PartyLost : Outcome
}

/**
 * The complete state of a run.
 *
 * Immutable and serialisable in full, including the RNG's internal state, so a saved
 * game resumes on exactly the sequence of rolls it would have seen. That matters more
 * on Wear OS than it would elsewhere: the system kills backgrounded apps aggressively,
 * so a 20-30 minute run *will* be interrupted, and resume has to be indistinguishable
 * from never having stopped.
 */
@Serializable
data class GameState(
    val profession: Profession,
    val party: Party,
    val inventory: Inventory,

    /** The last landmark reached. */
    val at: LandmarkId,
    /** The landmark being travelled toward, or null once the trail is finished. */
    val heading: LandmarkId?,
    /** Miles covered so far on the current segment. */
    val milesIntoSegment: Int,
    val totalMiles: Int,

    val startMonth: Int,
    val dayOfJourney: Int,

    val pace: Pace,
    val rations: Rations,
    val weather: Weather,

    val rng: Rng,
    val outcome: Outcome? = null,
) {
    val date: TrailDate get() = TrailDate.of(startMonth, dayOfJourney)

    val currentLandmark: Landmark get() = Trail[at]

    val headingLandmark: Landmark? get() = heading?.let { Trail[it] }

    val isOver: Boolean get() = outcome != null

    /**
     * True when the party cannot travel until the player decides something: which way
     * to go at a branch, or how to get across a river.
     *
     * [TurnEngine.advanceDay] refuses to advance in this state rather than burning days
     * in place. Without that guard a caller that forgot to resolve a crossing would
     * quietly starve the party to death while appearing to make progress.
     */
    val awaitingDecision: Boolean get() = !isOver && heading == null

    /** The route currently being travelled, or null if the trail is finished. */
    val currentRoute: Route?
        get() = heading?.let { target -> currentLandmark.routes.firstOrNull { it.to == target } }

    /** Miles still to cover before the next landmark. */
    val milesToNextLandmark: Int
        get() = ((currentRoute?.miles ?: 0) - milesIntoSegment).coerceAtLeast(0)

    /** The store here, if this landmark has one. */
    val store: Store? get() = Store.at(currentLandmark)

    /** Daily food requirement at the current ration setting. */
    val dailyFoodPounds: Int get() = rations.poundsPerPersonPerDay * party.survivorCount

    companion object {
        /** Oxen the wagon needs before it can move at all. */
        const val MINIMUM_OXEN = 2

        /**
         * A run that has been outfitted but has not yet left Independence. The player
         * still has to shop, so the inventory is empty apart from the starting cash.
         */
        fun newGame(
            profession: Profession,
            memberNames: List<String>,
            startMonth: Int,
            seed: Long,
        ): GameState {
            require(memberNames.isNotEmpty()) { "a party needs at least one traveller" }
            require(startMonth in TrailDate.DEPARTURE_MONTHS) {
                "departure month $startMonth is outside the viable window " +
                    "${TrailDate.DEPARTURE_MONTHS}"
            }
            val members = memberNames.mapIndexed { index, name ->
                Traveler(name = name, isLeader = index == 0)
            }
            return GameState(
                profession = profession,
                party = Party(members),
                inventory = Inventory(cashCents = profession.startingCents),
                at = LandmarkId.INDEPENDENCE,
                heading = Trail.start.routes.first().to,
                milesIntoSegment = 0,
                totalMiles = 0,
                startMonth = startMonth,
                dayOfJourney = 0,
                pace = Pace.STEADY,
                rations = Rations.FILLING,
                weather = Weather.WARM,
                rng = Rng.seeded(seed),
            )
        }
    }
}
