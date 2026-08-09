package com.oregontrail.wear.core

/** Shared fixtures for the core tests. */
object TestStates {

    val party = listOf("Amanda", "Thomas", "Eliza", "Samuel", "Grace")

    /** A brand new run: cash in hand, nothing bought yet. */
    fun fresh(
        profession: Profession = Profession.BANKER,
        startMonth: Int = 4,
        seed: Long = 12345L,
    ): GameState = GameState.newGame(profession, party, startMonth, seed)

    /**
     * A run that has been outfitted and is on the road. Generously supplied by default
     * so that tests exercising one mechanic aren't derailed by starvation.
     */
    fun outfitted(
        profession: Profession = Profession.BANKER,
        oxen: Int = 6,
        foodPounds: Int = 1000,
        clothingSets: Int = 5,
        bullets: Int = 200,
        cashCents: Int = 200_00,
        seed: Long = 12345L,
    ): GameState = fresh(profession = profession, seed = seed).copy(
        inventory = Inventory(
            oxen = oxen,
            foodPounds = foodPounds,
            clothingSets = clothingSets,
            bullets = bullets,
            spareWheels = 2,
            spareAxles = 2,
            spareTongues = 2,
            cashCents = cashCents,
        ),
    )

    /**
     * Advances [days] days, returning every state and event along the way.
     *
     * Rivers and branch points are resolved automatically — caulk across, take the
     * first route — because [TurnEngine.advanceDay] deliberately refuses to travel
     * while a decision is pending. Without that, any test running longer than the first
     * 102 miles would silently stall at the Kansas River.
     */
    fun run(state: GameState, days: Int): List<DayResult> {
        val results = mutableListOf<DayResult>()
        var s = state
        repeat(days) {
            if (s.isOver) return@repeat
            if (s.awaitingDecision) {
                val conditions = RiverCrossing.conditionsAt(s)
                s = if (conditions != null) {
                    RiverCrossing.cross(s, conditions, CrossingMethod.CAULK).state
                } else {
                    TurnEngine.chooseRoute(s, s.currentLandmark.routes.first().to)
                }
                if (s.isOver) return@repeat
            }
            val result = TurnEngine.advanceDay(s)
            results += result
            s = result.state
        }
        return results
    }
}
