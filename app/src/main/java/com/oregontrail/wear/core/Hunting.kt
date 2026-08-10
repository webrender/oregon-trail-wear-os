package com.oregontrail.wear.core

/**
 * Game animals, sized by the meat one kill yields. Bison and bear can outweigh the
 * carry limit on a single kill on purpose — see [Hunting.CARRY_LIMIT_POUNDS].
 */
enum class Animal(val displayName: String, val meatPounds: IntRange) {
    SQUIRREL("Squirrel", 1..3),
    RABBIT("Rabbit", 2..6),
    DEER("Deer", 30..60),
    BEAR("Bear", 80..150),
    BISON("Bison", 200..400),
}

/**
 * What shows up to hunt in a [TerrainRegion]. A species repeated in the list spawns more
 * often than one that appears once — this is spawn weighting, not a roster.
 */
enum class HuntingGround(val displayName: String, val animals: List<Animal>) {
    PRAIRIE("the prairie", listOf(Animal.RABBIT, Animal.RABBIT, Animal.SQUIRREL, Animal.BISON)),
    PLAINS("the plains", listOf(Animal.BISON, Animal.BISON, Animal.RABBIT)),
    ROCKIES("the foothills", listOf(Animal.DEER, Animal.DEER, Animal.BEAR)),
    DESERT("the scrubland", listOf(Animal.RABBIT, Animal.RABBIT, Animal.SQUIRREL)),
    FOREST("the woods", listOf(Animal.DEER, Animal.DEER, Animal.SQUIRREL, Animal.BEAR));

    companion object {
        fun of(region: TerrainRegion): HuntingGround = when (region) {
            TerrainRegion.PRAIRIE -> PRAIRIE
            TerrainRegion.PLAINS -> PLAINS
            TerrainRegion.ROCKIES -> ROCKIES
            TerrainRegion.DESERT -> DESERT
            TerrainRegion.FOREST -> FOREST
        }
    }
}

/** What a finished hunt did to the run. */
data class HuntOutcome(
    val day: DayResult,
    val meatPoundsCarried: Int,
    val meatPoundsWasted: Int,
    val bearsEscaped: Int,
)

/**
 * Hunting, off to the side of travel.
 *
 * The 1985 design calls it "real-time, skill-based" — a hunter moves around a terrain
 * screen and shoots animals as they appear — and that whole loop is a UI concern: aiming,
 * animal movement and hit detection all live in the Compose layer, which reports what
 * happened. This object only owns what has to survive a save/resume and be replayable:
 * meat yields (rolled from the run's own RNG, per [start]), ammunition spent, and the day
 * it costs. A hunt interrupted by the OS killing the app mid-session loses nothing but the
 * unsaved minigame itself, exactly like backing out of any other menu without saving.
 */
object Hunting {

    /**
     * Pounds the wagon can carry back from one hunt, regardless of how much was shot.
     * The 1985 design's documented sustainability lesson, not a bug — a downed bison
     * yields far more than this, and the rest is left on the ground.
     */
    const val CARRY_LIMIT_POUNDS = 100

    /** Tuning knob: health cost per bear that reaches the hunter unshot. */
    private const val BEAR_CHARGE_HEALTH_PENALTY = 4

    /** Available between landmarks, and only with ammunition to spend. */
    fun canHunt(state: GameState): Boolean =
        !state.isOver && !state.awaitingDecision && state.inventory.bullets > 0

    /** What's out there to shoot, based on where the party is right now. */
    fun groundAt(state: GameState): HuntingGround =
        HuntingGround.of(TerrainRegion.of(state.heading ?: state.at))

    /**
     * Forks the run's RNG for a hunt session to roll its meat yields from — see
     * [Rng.fork]. Threading a dedicated fork through the session, rather than reading
     * [GameState.rng] directly, keeps a hunt's rolls exactly reproducible from a save
     * without letting a long session (which may make many rolls, one per hit) disturb
     * the state the player is still holding a reference to while the hunt plays out.
     */
    fun start(state: GameState): Rng = state.rng.fork()

    /** Meat one hit on [animal] yields, rolled from the hunt session's [rng]. */
    fun meatFor(rng: Rng, animal: Animal): Int =
        rng.nextInt(animal.meatPounds.first, animal.meatPounds.last)

    /**
     * Books a finished hunt against [state]: ammunition is spent whether or not a shot
     * connected, meat is capped at the carry limit, any bear that reached the hunter
     * unshot costs health, and the day itself is spent — fed and aged like [rest], but
     * without its recovery bonus, since a hunt is exertion on foot, not a day sitting
     * still.
     */
    fun finish(
        state: GameState,
        rng: Rng,
        bulletsUsed: Int,
        meatPoundsShot: Int,
        bearsEscaped: Int = 0,
    ): HuntOutcome {
        val carried = meatPoundsShot.coerceAtMost(CARRY_LIMIT_POUNDS)
        val wasted = meatPoundsShot - carried
        val stocked = state.copy(
            rng = rng,
            party = state.party.adjustHealth(bearsEscaped * BEAR_CHARGE_HEALTH_PENALTY),
            inventory = state.inventory.copy(
                bullets = (state.inventory.bullets - bulletsUsed).coerceAtLeast(0),
                foodPounds = state.inventory.foodPounds + carried,
            ),
        )
        return HuntOutcome(TurnEngine.huntingDay(stocked), carried, wasted, bearsEscaped)
    }
}
