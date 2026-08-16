package com.oregontrail.wear.core

/**
 * Something met on the trail between landmarks — distinct from the misfortunes and
 * windfalls [TurnEngine]'s own daily roll already covers, because these come with a
 * decision attached rather than just narrating an outcome.
 *
 * Every number here is a tuning knob. The *shape* is partly sourced: the 1985 version
 * split this across talking to people (a landmark menu option, three fixed characters
 * per landmark, hints anchored to that place), attempting to trade (player-initiated,
 * between landmarks), and hiring a guide (a river crossing option at the Snake, paid in
 * clothing) — see "People on the trail" in docs/reference/game-mechanics-1985.md. We keep
 * the guide where the original put it ([CrossingMethod.HIRE_GUIDE]) and turn the rest
 * into interrupts, because a watch has no room for a landmark submenu three levels deep.
 */
sealed interface Encounter {

    /**
     * A party heading the other way, who has already walked the road ahead.
     *
     * The report is *derived from the run*, never picked from a list of sayings. That is
     * the whole justification for the encounter existing: a fixed string about rivers
     * running high is decoration, but "no ferry at the Snake, and they want clothing to
     * pilot you across" is a reason to spend money at Fort Hall. See [Encounters.reports].
     *
     * **Never rolled.** [Encounters.roll] no longer produces one: in play the reports read
     * as trivia rather than as something to act on, and stopping the journey to deliver
     * them cost more than they were worth. Everything needed to reinstate it is kept and
     * tested — the type, [Encounters.reports] and its derivations, the screen, and the
     * portrait — because what failed was the interrupt, not the derivation. Rolling it
     * again is a one-line change in [Encounters.roll].
     */
    data class Scout(val report: String) : Encounter

    /** A trader offers to swap goods. No cash changes hands either way. */
    data class Trade(
        val wants: Good,
        val wantsQuantity: Int,
        val gives: Good,
        val givesQuantity: Int,
    ) : Encounter

    /**
     * A frontier doctor, who charges accordingly.
     *
     * Priced well above a ferry because the decision should hurt: at the sick rate this is
     * a tenth of a farmer's entire starting purse. [patient] is the ill traveller they
     * would treat, or null if nobody is ill — which is what separates the two rates. A
     * doctor who can only sell rest and advice is worth less than one standing over
     * someone with cholera, and charges less.
     */
    data class Healer(
        val costCents: Int,
        val healthBenefit: Int,
        val patient: String?,
    ) : Encounter

    /**
     * Riders shadowing the wagon, meaning to take what they can.
     *
     * The one encounter with no way to simply walk on. Descended from the 1978 text
     * version's "riders ahead" — the 1985 redesign dropped it, and dropped its framing
     * with it: there the riders were Native American and the game asked whether to shoot
     * them. Here they are road agents, which keeps the decision and not the libel. See
     * docs/reference/game-mechanics-1985.md.
     *
     * [count] is shown to the player next to a portrait they can count heads in, so it is
     * always [Encounters.RIDERS_COUNT] rather than a roll. It stays a field because the
     * fight math reads it.
     */
    data class Riders(val count: Int) : Encounter
}

/** What the party does about [Encounter.Riders]. */
enum class RidersResponse(val displayName: String) {
    /** Spend ammunition on the chance of losing nothing else. */
    FIGHT("Fight them off"),

    /** Spend distance and strength on the chance of losing nothing else. */
    FLEE("Ride hard"),

    /** Spend supplies, certainly, and lose nothing else at all. */
    PAY("Hand over supplies"),
}

/**
 * Rolls and resolves trail encounters.
 */
object Encounters {

    /**
     * Chance per day of travel that the party meets someone.
     *
     * Halved from 0.10 after play-testing: at the old rate the trail was busier than a road
     * through empty country should feel, and an interrupt every week or so stopped reading
     * as an event. [RIDERS_SHARE] was doubled to compensate, because riders are the one
     * encounter with a floor on how often it must happen — see the note there.
     */
    const val CHANCE = 0.05

    // ---- Tuning knobs ----

    private const val HEALER_HEALTH_BENEFIT = 12
    private const val HEALER_MIN_COST_CENTS = 15_00
    private const val HEALER_MAX_COST_CENTS = 25_00

    /** A doctor with someone actually to treat: cures the ailment, and charges for it. */
    private const val HEALER_SICK_HEALTH_BENEFIT = 20
    private const val HEALER_SICK_MIN_COST_CENTS = 35_00
    private const val HEALER_SICK_MAX_COST_CENTS = 60_00

    /**
     * Share of encounters that are riders, within the country where they can happen.
     *
     * Tuned against `EncounterTest` rather than picked: the target is that a full journey
     * meets them once or twice, so this is the number that has to move if the encounter
     * rate or the trail's length ever changes.
     *
     * Doubled from 0.09 when [CHANCE] was halved, so riders stay at the same absolute rate
     * while everything else got rarer. They are the only encounter with a floor rather than
     * a ceiling: a journey that never meets them never has to make the one decision that
     * cannot be walked away from, so thinning them out costs the trail its teeth.
     */
    private const val RIDERS_SHARE = 0.18

    /**
     * How many riders, always.
     *
     * Pinned to the art, not rolled: `assets/art/portrait_riders.png` shows four riders
     * plainly enough to count, and the screen prints the number beside it. It used to roll
     * 3..6, so three times in four the sentence contradicted the picture the player was
     * looking at. The picture is the fixed thing — it is authored outside the repo — so the
     * number bends to it. Change this only alongside new art.
     *
     * The fight cost still varies: [fight] spends `count * 3` to `count * 8` bullets, which
     * at four riders is the same 12..32 spread it drew before.
     */
    internal const val RIDERS_COUNT = 4

    private const val RIDERS_MIN_BULLETS_TO_FIGHT = 20

    /**
     * Where riders are possible: the long middle of the trail, from the plains through
     * the Snake River desert.
     *
     * Region-gated rather than uniform for two reasons. The prairie out of Independence is
     * the busiest, most-travelled stretch and the party has barely anything worth taking
     * yet — an encounter that can kill someone in week one, before there is ammunition to
     * answer it with, is a coin flip rather than a decision. And the last run through the
     * forest belongs to the mountains and the Columbia.
     */
    private val RIDER_COUNTRY = setOf(
        TerrainRegion.PLAINS,
        TerrainRegion.ROCKIES,
        TerrainRegion.DESERT,
    )

    /**
     * One encounter, weighted toward the one that costs nothing to walk away from.
     *
     * [Encounter.Scout] is deliberately absent: play-testing found the reports didn't earn
     * the interrupt, however truthful they were. The type and [reports] are kept intact —
     * see the note on [Encounter.Scout] — so this is the one line to change to bring them
     * back. The old 45/35/20 scout/trade/doctor split becomes 64/36 with the scout's share
     * redistributed in proportion, which leaves the encounter rate itself untouched.
     */
    fun roll(state: GameState, rng: Rng): Encounter {
        if (ridersPossible(state) && rng.chance(RIDERS_SHARE)) {
            return Encounter.Riders(RIDERS_COUNT)
        }
        return when (rng.nextInt(100)) {
            in 0..63 -> rollTrade(state, rng) ?: rollHealer(state, rng)
            else -> rollHealer(state, rng)
        }
    }

    private fun ridersPossible(state: GameState): Boolean {
        val heading = state.heading ?: return false
        // Nothing worth taking means nothing worth the encounter.
        if (state.inventory.foodPounds <= 0 && state.inventory.oxen <= 0) return false
        return TerrainRegion.of(heading) in RIDER_COUNTRY
    }

    private fun rollHealer(state: GameState, rng: Rng): Encounter.Healer {
        val patient = state.party.ill.firstOrNull()
        return if (patient == null) {
            Encounter.Healer(
                costCents = rng.nextInt(HEALER_MIN_COST_CENTS, HEALER_MAX_COST_CENTS),
                healthBenefit = HEALER_HEALTH_BENEFIT,
                patient = null,
            )
        } else {
            Encounter.Healer(
                costCents = rng.nextInt(HEALER_SICK_MIN_COST_CENTS, HEALER_SICK_MAX_COST_CENTS),
                healthBenefit = HEALER_SICK_HEALTH_BENEFIT,
                patient = patient.name,
            )
        }
    }

    // ---- What a scout can tell you ----

    /**
     * Everything true the party could be told about the road ahead, right now.
     *
     * Never empty: [arrivalForecast] falls back to a reading of the calendar, which is
     * available from the first day. Each entry is computed from the run, so a scout cannot
     * say anything the player will not later find to be the case — it is the property to
     * protect if these are edited.
     *
     * Nothing in the game calls this now that [Encounter.Scout] is never rolled; it is kept
     * working and under test so the encounter can be reinstated. See [Encounter.Scout].
     */
    fun reports(state: GameState): List<String> =
        listOfNotNull(riverAhead(state), fortAhead(state), arrivalForecast(state))
            .ifEmpty { listOf("The road ahead runs much as this one does.") }

    /**
     * The next river, and what it will cost to get over it.
     *
     * Only looks as far as the next branch point — see [scanAhead]. Past a fork the road
     * is the player's choice and not yet made, and a scout who guesses is exactly the
     * flavour text this replaced.
     */
    private fun riverAhead(state: GameState): String? {
        val (landmark, miles) = scanAhead(state).firstOrNull { it.first.kind is LandmarkKind.River }
            ?: return null
        val river = landmark.kind as LandmarkKind.River
        val name = landmark.name.removeSuffix(" Crossing")
        return when {
            river.guideAvailable ->
                "No ferry at the $name, $miles miles on. Guides there take clothing."

            river.typicalDepthFeet > Rivers.DANGEROUS_DEPTH_FEET && river.ferryAvailable ->
                "The $name runs deep, $miles miles on. There is a ferry."

            river.typicalDepthFeet > Rivers.DANGEROUS_DEPTH_FEET ->
                "The $name runs deep, $miles miles on, and has no ferry."

            else -> "The $name is $miles miles on. Shallow enough to ford."
        }
    }

    /** The next fort, and what it will charge — which is not visible until you arrive. */
    private fun fortAhead(state: GameState): String? {
        val (landmark, miles) = scanAhead(state).firstOrNull { it.first.kind is LandmarkKind.Fort }
            ?: return null
        val fort = landmark.kind as LandmarkKind.Fort
        return "${landmark.name} is $miles miles on. They charge " +
            "${fort.markupPercent}% over Independence."
    }

    /**
     * When the party will reach Oregon at the pace they have actually kept.
     *
     * The most useful thing a scout says, because it is the one thing the player cannot
     * work out from the screen: the travel display shows today's miles and today's date,
     * and nothing anywhere multiplies the two out against the distance left. Falling
     * behind the season is the quiet way a run dies.
     */
    private fun arrivalForecast(state: GameState): String? {
        val heading = state.heading ?: return null
        if (state.dayOfJourney < MIN_DAYS_FOR_FORECAST || state.totalMiles <= 0) return null

        val milesPerDay = state.totalMiles.toDouble() / state.dayOfJourney
        // Shortest remaining road, so the forecast is the optimistic one: a scout who
        // assumed the long way round would read as pessimism the player can't check.
        val onward = Trail.milesBetween(heading, LandmarkId.OREGON_CITY) ?: return null
        val daysLeft = ((state.milesToNextLandmark + onward) / milesPerDay).toInt()
        val arrival = TrailDate.of(state.startMonth, state.dayOfJourney + daysLeft)

        val forecast = "At this pace you reach Oregon in ${arrival.monthName}."
        // Wrapping past December means the pace is hopeless, and the month name alone
        // would read as good news.
        return if (arrival.month >= LATE_ARRIVAL_MONTH || daysLeft > DAYS_IN_YEAR) {
            "$forecast The passes close before then."
        } else {
            forecast
        }
    }

    private const val MIN_DAYS_FOR_FORECAST = 8
    private const val LATE_ARRIVAL_MONTH = 10
    private const val DAYS_IN_YEAR = 365

    /**
     * The landmarks ahead that are certain, each with the miles to reach it.
     *
     * Stops at the first branch point, because past one the road is undecided.
     */
    private fun scanAhead(state: GameState): List<Pair<Landmark, Int>> {
        val ahead = mutableListOf<Pair<Landmark, Int>>()
        var next = state.heading ?: return ahead
        var miles = state.milesToNextLandmark
        while (true) {
            val landmark = Trail[next]
            ahead += landmark to miles
            val route = landmark.routes.singleOrNull() ?: return ahead
            miles += route.miles
            next = route.to
        }
    }

    // ---- Trading ----

    /**
     * An offer the party can actually fulfil: [Encounter.Trade.wantsQuantity] never
     * exceeds what's currently held, so declining is a real choice rather than the only
     * option. Returns null if the wagon is carrying nothing to trade away yet — early in
     * a run, before the store visit, that's every good.
     */
    private fun rollTrade(state: GameState, rng: Rng): Encounter.Trade? {
        // A party below [GameState.MINIMUM_OXEN] cannot travel at all, and no other
        // encounter, event or screen can put an ox back in the yoke — the store is the
        // only other source and reaching one requires the movement they have lost. Left
        // to chance the trader offered oxen about one day in 250, which made a stranding
        // a formality rather than a setback: the run was over, but it took the party
        // starving to death to say so.
        //
        // So a trader who finds a stranded wagon always offers what it is missing, and
        // enough of it to roll again. The rescue still has to be paid for out of what
        // the wagon is carrying, and still has to be accepted, so this shortens a dead
        // end without making one costless.
        val stranded = state.inventory.oxen < GameState.MINIMUM_OXEN

        // The last ox is not on the table while they are stranded. Trading it away would
        // deepen exactly the hole this is digging them out of.
        val tradeable = if (stranded) Good.entries.filter { it != Good.OXEN } else Good.entries
        val held = tradeable.filter { state.inventory.amountOf(it) > 0 }
        if (held.isEmpty()) return null

        val wants = rng.pick(held)
        val gives = if (stranded) Good.OXEN else rng.pick(Good.entries.filter { it != wants })
        val wantsMax = minOf(state.inventory.amountOf(wants), offerCeiling(wants))
        // Enough to reach the minimum, which is two from nothing and one from a single
        // surviving ox. An offer that left them still stranded would be no offer at all.
        val givesMin = if (stranded) GameState.MINIMUM_OXEN - state.inventory.oxen else 1

        return Encounter.Trade(
            wants = wants,
            wantsQuantity = rng.nextInt(1, wantsMax),
            gives = gives,
            givesQuantity = rng.nextInt(givesMin, offerCeiling(gives)),
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

    // ---- The doctor ----

    /**
     * Pays a doctor. No-ops if the party can't cover the fee.
     *
     * Treats the whole party's health *and* clears the one ailment they were called for,
     * which is what makes the sick rate worth paying: an ailment left alone kills, and the
     * only other cure is resting days the calendar cannot spare.
     */
    fun treat(state: GameState, healer: Encounter.Healer): GameState {
        if (state.inventory.cashCents < healer.costCents) return state
        var party = state.party.adjustHealth(-healer.healthBenefit)
        if (healer.patient != null) {
            party = party.withMember(healer.patient) { it.copy(ailment = null) }
        }
        return state.copy(inventory = state.inventory.spend(healer.costCents), party = party)
    }

    // ---- The riders ----

    /**
     * Whether the party has ammunition enough to make a fight of it.
     *
     * A fight with an empty rifle is not a decision, so the option is withheld rather than
     * offered and lost.
     */
    fun canFight(state: GameState): Boolean =
        state.inventory.bullets >= RIDERS_MIN_BULLETS_TO_FIGHT

    /**
     * Resolves an encounter with riders, three ways.
     *
     * The three responses are deliberately priced in three different currencies, so that
     * which one is right depends on the run rather than on a table: ammunition, distance
     * and strength, or supplies. Only [RidersResponse.PAY] is certain — it is also the
     * only one that can never cost a life.
     */
    fun face(
        state: GameState,
        riders: Encounter.Riders,
        response: RidersResponse,
    ): DayResult {
        // Fork first, so the state handed in is left untouched — see Rng.fork.
        val forked = state.copy(rng = state.rng.fork())
        return when (response) {
            RidersResponse.FIGHT -> fight(forked, riders)
            RidersResponse.FLEE -> flee(forked)
            RidersResponse.PAY -> pay(forked)
        }
    }

    private fun fight(state: GameState, riders: Encounter.Riders): DayResult {
        val rng = state.rng
        val events = mutableListOf<GameEvent>()

        // More riders, more shooting — the count on the encounter is not decoration.
        val bulletsUsed = rng.nextInt(riders.count * 3, riders.count * 8)
            .coerceAtMost(state.inventory.bullets)
        var s = state.copy(inventory = state.inventory.adjust(Good.BULLETS, -bulletsUsed))

        val roll = rng.nextDouble()
        val drivenOff = roll < 0.60
        events += GameEvent.RidersFought(bulletsUsed, drivenOff)

        if (!drivenOff) {
            s = plunder(s, rng, events, share = 0.25)
            // The price of choosing the option that can go worst than any other.
            if (roll >= 0.88) {
                val victim = rng.pick(s.party.survivors)
                s = s.copy(
                    party = s.party.withMember(victim.name) {
                        it.copy(alive = false, diedOnDay = s.dayOfJourney)
                    },
                )
                events += GameEvent.Died(victim.name, "a gunshot wound")
            }
        }
        return finish(s, events)
    }

    private fun flee(state: GameState): DayResult {
        val rng = state.rng
        val events = mutableListOf<GameEvent>()

        // Ground given up outright, plus the toll a hard run takes on the party.
        val milesLost = rng.nextInt(8, 25).coerceAtMost(state.milesIntoSegment)
        var s = state.copy(
            milesIntoSegment = state.milesIntoSegment - milesLost,
            totalMiles = (state.totalMiles - milesLost).coerceAtLeast(0),
            party = state.party.adjustHealth(FLEE_HEALTH_COST),
        )
        events += GameEvent.RidersOutran(milesLost)

        if (rng.chance(0.45)) s = plunder(s, rng, events, share = 0.20)
        return finish(s, events)
    }

    private fun pay(state: GameState): DayResult {
        val events = mutableListOf<GameEvent>()
        val food = PAY_OFF_FOOD_POUNDS.coerceAtMost(state.inventory.foodPounds)
        val bullets = PAY_OFF_BULLETS.coerceAtMost(state.inventory.bullets)
        // A wagon with neither is stripped for what it does have, so that handing over
        // supplies always costs something and never resolves in silence.
        val clothing = if (food == 0 && bullets == 0) {
            PAY_OFF_CLOTHING_SETS.coerceAtMost(state.inventory.clothingSets)
        } else {
            0
        }

        val s = state.copy(
            inventory = state.inventory
                .adjust(Good.FOOD, -food)
                .adjust(Good.BULLETS, -bullets)
                .adjust(Good.CLOTHING, -clothing),
        )
        if (food > 0) events += GameEvent.SuppliesStolen("food", food)
        if (bullets > 0) events += GameEvent.SuppliesStolen("bullets", bullets)
        if (clothing > 0) events += GameEvent.SuppliesStolen("clothing", clothing)
        return finish(s, events)
    }

    /** Takes a [share] of the wagon's food, and sometimes an ox with it. */
    private fun plunder(
        state: GameState,
        rng: Rng,
        events: MutableList<GameEvent>,
        share: Double,
    ): GameState {
        val food = (state.inventory.foodPounds * share).toInt().coerceAtMost(
            state.inventory.foodPounds,
        )
        val oxen = if (rng.chance(0.35) && state.inventory.oxen > 0) 1 else 0

        if (food > 0) events += GameEvent.SuppliesStolen("food", food)
        if (oxen > 0) events += GameEvent.SuppliesStolen("oxen", oxen)

        return state.copy(
            inventory = state.inventory
                .adjust(Good.FOOD, -food)
                .adjust(Good.OXEN, -oxen),
        )
    }

    /** Books the encounter, ending the run if the riders finished the party off. */
    private fun finish(state: GameState, events: MutableList<GameEvent>): DayResult {
        var s = state
        if (s.party.isWipedOut) {
            events += GameEvent.PartyLost
            s = s.copy(outcome = Outcome.PartyLost)
        }
        return DayResult(s, events)
    }

    private const val FLEE_HEALTH_COST = 5
    private const val PAY_OFF_FOOD_POUNDS = 75
    private const val PAY_OFF_BULLETS = 30
    private const val PAY_OFF_CLOTHING_SETS = 2
}
