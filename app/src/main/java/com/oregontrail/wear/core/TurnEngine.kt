package com.oregontrail.wear.core

import kotlin.math.roundToInt

/** Something that happened on a given day, for the player to be told about. */
sealed interface GameEvent {
    data class Traveled(val miles: Int) : GameEvent
    data class ArrivedAt(val landmark: LandmarkId) : GameEvent
    data class FellIll(val traveler: String, val ailment: Ailment) : GameEvent
    data class Recovered(val traveler: String) : GameEvent
    data class Died(val traveler: String, val cause: String) : GameEvent
    data class OxDied(val remaining: Int) : GameEvent
    data class WagonBroke(val part: String, val hadSpare: Boolean) : GameEvent
    data class SuppliesStolen(val what: String, val amount: Int) : GameEvent
    data class SuppliesFound(val what: String, val amount: Int) : GameEvent
    data class LostTime(val reason: String, val miles: Int) : GameEvent
    data object OutOfFood : GameEvent
    data object BadWater : GameEvent
    data object PartyLost : GameEvent
}

data class DayResult(
    val state: GameState,
    val events: List<GameEvent>,
    /** Someone met on the road today, if anyone, awaiting the player's decision. */
    val encounter: Encounter? = null,
)

/**
 * The turn engine: one function that advances the world by a day, plus the discrete
 * actions the player can take instead of travelling.
 *
 * Everything here is pure Kotlin with no Android dependencies, so it runs under plain
 * JUnit on the JVM. That is deliberate — balance work is the part of this project that
 * needs the fastest feedback loop, and putting an emulator or a watch in that loop
 * would make it unbearable.
 *
 * Rules and constants are documented in docs/reference/game-mechanics-1985.md.
 * Anything not sourced there is a tuning knob and is marked as such.
 */
object TurnEngine {

    // ---- Tuning knobs (not sourced; adjust freely against play testing) ----

    /** Chance per day that a random misfortune or windfall occurs. */
    private const val EVENT_CHANCE = 0.15

    /** Baseline chance per day that a healthy traveller falls ill. */
    private const val BASE_ILLNESS_CHANCE = 0.02

    /** Additional illness chance per point of accumulated party ill-health. */
    private const val ILLNESS_CHANCE_PER_HEALTH_POINT = 0.001

    /** Chance per day that an ill traveller recovers. */
    private const val RECOVERY_CHANCE = 0.25

    /** Health penalty for facing cold weather without a set of clothing each. */
    private const val UNDERCLOTHED_PENALTY = 3

    /** Health penalty per day with no food at all. */
    private const val STARVATION_PENALTY = 5

    /** Health recovered by a day's rest, provided the party can eat. */
    private const val REST_RECOVERY = -6

    // ---- Travel ----

    /**
     * Advances one day of travel.
     *
     * Order matters: weather is rolled first because it scales distance, random events
     * resolve before the day's mileage is banked so that a broken wagon can actually
     * stop the party, and arrival is checked last so a landmark reached mid-event still
     * registers.
     */
    fun advanceDay(state: GameState): DayResult {
        // Refuse rather than burn a day in place — see GameState.awaitingDecision.
        // Resting is still allowed while waiting; travelling is not.
        if (state.isOver || state.awaitingDecision) return DayResult(state, emptyList())

        val events = mutableListOf<GameEvent>()
        // Fork so the caller's state is left exactly as it was — see Rng.fork.
        val rng = state.rng.fork()
        var s = state.copy(
            rng = rng,
            dayOfJourney = state.dayOfJourney + 1,
            weather = WeatherModel.roll(state.startMonth, state.dayOfJourney + 1, rng),
        )

        var miles = plannedMiles(s, rng)

        // Random events can consume supplies and cost distance.
        if (rng.chance(EVENT_CHANCE)) {
            val outcome = rollRandomEvent(s, events)
            s = outcome.state
            miles = if (outcome.stranded) 0 else (miles - outcome.milesLost).coerceAtLeast(0)
        }

        // A wagon with too few oxen does not move, whatever the pace setting says.
        if (s.inventory.oxen < GameState.MINIMUM_OXEN) miles = 0

        s = consumeFood(s, events)
        s = applyDailyHealth(s)
        s = resolveIllness(s, events)
        s = advanceDistance(s, miles, events)

        if (s.party.isWipedOut) {
            events += GameEvent.PartyLost
            s = s.copy(outcome = Outcome.PartyLost)
        }

        // Rolled from a derived generator — see Rng.derive — rather than the day's own
        // `rng`, so adding or retuning encounters can never perturb the exact sequence
        // of weather/illness/event rolls every other day depends on, including in
        // fixed-seed tests far downstream of this one.
        var encounter: Encounter? = null
        if (!s.isOver) {
            val encounterRng = s.rng.derive(s.dayOfJourney * 97L + 13)
            if (encounterRng.chance(Encounters.CHANCE)) {
                encounter = Encounters.roll(s, encounterRng)
            }
        }

        return DayResult(s, events, encounter)
    }

    /** Rests in place for a day: no travel, food is still eaten, health recovers. */
    fun rest(state: GameState): DayResult {
        if (state.isOver) return DayResult(state, emptyList())

        val events = mutableListOf<GameEvent>()
        val rng = state.rng.fork()
        var s = state.copy(
            rng = rng,
            dayOfJourney = state.dayOfJourney + 1,
            weather = WeatherModel.roll(state.startMonth, state.dayOfJourney + 1, rng),
        )
        // Resting only helps if there is something to eat. A party with an empty larder
        // that rests must still deteriorate — otherwise resting becomes a way to survive
        // starvation indefinitely, and a stranded party never resolves at all.
        val ate = s.inventory.foodPounds > 0
        s = consumeFood(s, events)
        if (ate) {
            // Resting reverses the day's wear rather than merely pausing it — that is
            // what makes stopping for an ill traveller worth the days it costs.
            s = s.copy(party = s.party.adjustHealth(REST_RECOVERY + s.rations.healthCost))
        }
        s = resolveIllness(s, events)

        if (s.party.isWipedOut) {
            events += GameEvent.PartyLost
            s = s.copy(outcome = Outcome.PartyLost)
        }
        return DayResult(s, events)
    }

    /**
     * Spends a day hunting instead of travelling.
     *
     * Food is eaten and illness resolves exactly as it would on a rest day — see [rest]
     * — but health does not get [REST_RECOVERY]'s benefit: a hunt is a day spent on foot
     * with a rifle, not a day sitting still. [Hunting.finish] calls this once a session
     * is done to book the day itself; the meat and ammunition it cost are applied
     * separately, before this runs, so that a party that goes hungry mid-hunt still
     * starves on schedule.
     */
    fun huntingDay(state: GameState): DayResult {
        if (state.isOver) return DayResult(state, emptyList())

        val events = mutableListOf<GameEvent>()
        val rng = state.rng.fork()
        var s = state.copy(
            rng = rng,
            dayOfJourney = state.dayOfJourney + 1,
            weather = WeatherModel.roll(state.startMonth, state.dayOfJourney + 1, rng),
        )
        s = consumeFood(s, events)
        s = resolveIllness(s, events)

        if (s.party.isWipedOut) {
            events += GameEvent.PartyLost
            s = s.copy(outcome = Outcome.PartyLost)
        }
        return DayResult(s, events)
    }

    /**
     * Chooses which way to go at a branch point. No-op if [to] is not actually
     * reachable from where the party is standing.
     */
    fun chooseRoute(state: GameState, to: LandmarkId): GameState {
        val valid = state.currentLandmark.routes.any { it.to == to }
        return if (valid) state.copy(heading = to, milesIntoSegment = 0) else state
    }

    // ---- Internals ----

    private fun plannedMiles(state: GameState, rng: Rng): Int {
        val oxen = state.inventory.oxen
        if (oxen < GameState.MINIMUM_OXEN) return 0

        val base = when {
            oxen >= 8 -> 22
            oxen >= 6 -> 20
            oxen >= 4 -> 17
            else -> 12
        }
        val healthFactor = when (state.party.health) {
            HealthLevel.GOOD -> 1.00
            HealthLevel.FAIR -> 0.95
            HealthLevel.POOR -> 0.85
            HealthLevel.VERY_POOR -> 0.70
        }
        val planned = base * state.pace.multiplier * state.weather.travelFactor * healthFactor
        return (planned.roundToInt() + rng.nextInt(-2, 2)).coerceAtLeast(0)
    }

    private fun consumeFood(state: GameState, events: MutableList<GameEvent>): GameState {
        val needed = state.dailyFoodPounds
        val available = state.inventory.foodPounds
        return if (available <= 0) {
            events += GameEvent.OutOfFood
            state.copy(party = state.party.adjustHealth(STARVATION_PENALTY))
        } else {
            if (available < needed) events += GameEvent.OutOfFood
            state.copy(inventory = state.inventory.consumeFood(needed))
        }
    }

    private fun applyDailyHealth(state: GameState): GameState {
        var delta = state.pace.healthCost + state.rations.healthCost + state.weather.healthCost
        if (state.weather.needsClothing &&
            state.inventory.clothingSets < state.party.survivorCount
        ) {
            delta += UNDERCLOTHED_PENALTY
        }
        return state.copy(party = state.party.adjustHealth(delta))
    }

    /**
     * Resolves existing illnesses, then rolls for a new one.
     *
     * An ill traveller either recovers, dies, or stays ill; the worse the party's
     * overall health, the more the odds tilt toward dying. This is the mechanism that
     * makes ignoring an illness genuinely dangerous, which the original documented as
     * one of the main ways to lose people.
     */
    private fun resolveIllness(state: GameState, events: MutableList<GameEvent>): GameState {
        val rng = state.rng
        var party = state.party

        for (traveler in state.party.ill) {
            val healthPressure = when (party.health) {
                HealthLevel.GOOD -> 0.4
                HealthLevel.FAIR -> 0.7
                HealthLevel.POOR -> 1.2
                HealthLevel.VERY_POOR -> 2.0
            }
            val ailment = traveler.ailment ?: continue
            val deathChance = ailment.severity * 0.01 * healthPressure

            when {
                rng.chance(deathChance) -> {
                    events += GameEvent.Died(traveler.name, ailment.displayName)
                    party = party.withMember(traveler.name) {
                        it.copy(alive = false, diedOnDay = state.dayOfJourney)
                    }
                }
                rng.chance(RECOVERY_CHANCE) -> {
                    events += GameEvent.Recovered(traveler.name)
                    party = party.withMember(traveler.name) { it.copy(ailment = null) }
                }
            }
        }

        val healthy = party.survivors.filter { it.ailment == null }
        if (healthy.isNotEmpty()) {
            val chance = BASE_ILLNESS_CHANCE +
                party.healthPoints * ILLNESS_CHANCE_PER_HEALTH_POINT
            if (rng.chance(chance)) {
                val victim = rng.pick(healthy)
                val ailment = rng.pick(Ailment.entries.filter { it.isDisease })
                events += GameEvent.FellIll(victim.name, ailment)
                party = party.withMember(victim.name) { it.copy(ailment = ailment) }
            }
        }

        return state.copy(party = party)
    }

    /** Banks the day's mileage and handles arriving at the next landmark. */
    private fun advanceDistance(
        state: GameState,
        miles: Int,
        events: MutableList<GameEvent>,
    ): GameState {
        val route = state.currentRoute ?: return state
        if (miles <= 0) {
            events += GameEvent.Traveled(0)
            return state
        }

        val remaining = route.miles - state.milesIntoSegment
        val actual = miles.coerceAtMost(remaining)
        events += GameEvent.Traveled(actual)

        val arrived = actual >= remaining
        if (!arrived) {
            return state.copy(
                milesIntoSegment = state.milesIntoSegment + actual,
                totalMiles = state.totalMiles + actual,
            )
        }

        val destination = Trail[route.to]
        events += GameEvent.ArrivedAt(destination.id)

        // A single onward route is taken automatically. `heading` is left null wherever
        // the party cannot simply walk on: at a branch until they choose, at a river
        // until it is crossed (RiverCrossing sets the heading on the far bank), and at
        // the end of the trail for good.
        val nextHeading = when {
            destination.isEnd -> null
            destination.isBranch -> null
            destination.kind is LandmarkKind.River -> null
            else -> destination.routes.first().to
        }

        return state.copy(
            at = destination.id,
            heading = nextHeading,
            milesIntoSegment = 0,
            totalMiles = state.totalMiles + actual,
            outcome = if (destination.isEnd) Outcome.Arrived else state.outcome,
        )
    }

    /** What a random event did to the day, beyond its effect on [GameState]. */
    private data class EventOutcome(
        val state: GameState,
        val milesLost: Int = 0,
        /** The party cannot move at all today, regardless of planned mileage. */
        val stranded: Boolean = false,
    )

    /**
     * Rolls one random event.
     *
     * Weights are tuning knobs. Misfortunes outnumber windfalls deliberately — the
     * trail is supposed to grind you down.
     */
    private fun rollRandomEvent(
        state: GameState,
        events: MutableList<GameEvent>,
    ): EventOutcome {
        val rng = state.rng
        var s = state
        var milesLost = 0
        var stranded = false

        when (rng.nextInt(100)) {
            in 0..14 -> {
                // An ox dies. With too few left the wagon simply stops, which is the
                // slow-motion way runs are lost.
                val remaining = (s.inventory.oxen - 1).coerceAtLeast(0)
                s = s.copy(inventory = s.inventory.copy(oxen = remaining))
                events += GameEvent.OxDied(remaining)
            }
            in 15..29 -> {
                val part = rng.pick(listOf("wheel", "axle", "tongue"))
                val inv = s.inventory
                val hasSpare = when (part) {
                    "wheel" -> inv.spareWheels > 0
                    "axle" -> inv.spareAxles > 0
                    else -> inv.spareTongues > 0
                }
                s = if (hasSpare) {
                    s.copy(
                        inventory = when (part) {
                            "wheel" -> inv.copy(spareWheels = inv.spareWheels - 1)
                            "axle" -> inv.copy(spareAxles = inv.spareAxles - 1)
                            else -> inv.copy(spareTongues = inv.spareTongues - 1)
                        }
                    )
                } else {
                    // No spare means the day is spent making do at the roadside.
                    stranded = true
                    s.copy(party = s.party.adjustHealth(4))
                }
                events += GameEvent.WagonBroke(part, hasSpare)
            }
            in 30..39 -> {
                val stolen = rng.nextInt(10, 40).coerceAtMost(s.inventory.foodPounds)
                if (stolen > 0) {
                    s = s.copy(inventory = s.inventory.consumeFood(stolen))
                    events += GameEvent.SuppliesStolen("food", stolen)
                }
            }
            in 40..49 -> {
                val bullets = rng.nextInt(5, 25).coerceAtMost(s.inventory.bullets)
                if (bullets > 0) {
                    s = s.copy(inventory = s.inventory.copy(bullets = s.inventory.bullets - bullets))
                    events += GameEvent.SuppliesStolen("ammunition", bullets)
                }
            }
            in 50..59 -> {
                s = s.copy(party = s.party.adjustHealth(5))
                events += GameEvent.BadWater
            }
            in 60..69 -> {
                milesLost = rng.nextInt(5, 15)
                events += GameEvent.LostTime("heavy fog", milesLost)
            }
            in 70..77 -> {
                milesLost = rng.nextInt(8, 20)
                events += GameEvent.LostTime("an impassable trail", milesLost)
            }
            in 78..84 -> {
                val victim = s.party.survivors.filter { it.ailment == null }.randomOrNull(rng)
                if (victim != null) {
                    val injury = rng.pick(
                        listOf(Ailment.SNAKEBITE, Ailment.BROKEN_ARM, Ailment.BROKEN_LEG)
                    )
                    events += GameEvent.FellIll(victim.name, injury)
                    s = s.copy(
                        party = s.party.withMember(victim.name) { it.copy(ailment = injury) }
                    )
                }
            }
            in 85..92 -> {
                val found = rng.nextInt(10, 45)
                s = s.copy(inventory = s.inventory.copy(foodPounds = s.inventory.foodPounds + found))
                events += GameEvent.SuppliesFound("wild fruit", found)
            }
            else -> {
                val found = rng.nextInt(5, 20)
                s = s.copy(inventory = s.inventory.copy(bullets = s.inventory.bullets + found))
                events += GameEvent.SuppliesFound("abandoned supplies", found)
            }
        }

        return EventOutcome(s, milesLost, stranded)
    }

    private fun <T> List<T>.randomOrNull(rng: Rng): T? =
        if (isEmpty()) null else rng.pick(this)
}
