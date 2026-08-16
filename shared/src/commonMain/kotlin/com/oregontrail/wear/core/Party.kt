package com.oregontrail.wear.core

import kotlinx.serialization.Serializable

/**
 * Party health, as the four levels the original reported and scored against.
 *
 * Health is a property of the whole party, not of individuals — that's how the 1985
 * game modelled it, and it's what the scoring table keys off (each *survivor* is worth
 * points according to the *party's* health). Individual travellers carry their own
 * [Ailment] and their own alive/dead state on top of this.
 */
enum class HealthLevel(val displayName: String, val pointsPerSurvivor: Int) {
    GOOD("Good", 500),
    FAIR("Fair", 400),
    POOR("Poor", 300),
    VERY_POOR("Very Poor", 200);

    companion object {
        /**
         * Maps the internal health accumulator to a level. Higher is worse, following
         * the original's design where hardship adds to a running total.
         *
         * These thresholds are tuning knobs, not sourced values.
         */
        fun fromPoints(points: Int): HealthLevel = when {
            points < 35 -> GOOD
            points < 70 -> FAIR
            points < 105 -> POOR
            else -> VERY_POOR
        }
    }
}

/** The illnesses and injuries named in the 1985 version. */
enum class Ailment(val displayName: String, val severity: Int) {
    EXHAUSTION("exhaustion", 2),
    FEVER("a fever", 2),
    MEASLES("the measles", 3),
    DYSENTERY("dysentery", 4),
    TYPHOID("typhoid", 4),
    CHOLERA("cholera", 5),
    BROKEN_ARM("a broken arm", 2),
    BROKEN_LEG("a broken leg", 3),
    SNAKEBITE("a snakebite", 3);

    /** True for the ailments that read as disease rather than injury. */
    val isDisease: Boolean
        get() = this in setOf(EXHAUSTION, FEVER, MEASLES, DYSENTERY, TYPHOID, CHOLERA)
}

@Serializable
data class Traveler(
    val name: String,
    val isLeader: Boolean = false,
    val alive: Boolean = true,
    val ailment: Ailment? = null,
    /** Day of the journey this traveller died on, or null if still alive. */
    val diedOnDay: Int? = null,
) {
    val isIll: Boolean get() = alive && ailment != null
}

@Serializable
data class Party(
    val members: List<Traveler>,
    /** Internal health accumulator; higher is worse. See [HealthLevel.fromPoints]. */
    val healthPoints: Int = 0,
) {
    init {
        require(members.isNotEmpty()) { "a party needs at least one traveller" }
    }

    val health: HealthLevel get() = HealthLevel.fromPoints(healthPoints)

    val survivors: List<Traveler> get() = members.filter { it.alive }

    val survivorCount: Int get() = survivors.size

    val ill: List<Traveler> get() = members.filter { it.isIll }

    /** True once everyone has died — the run is over regardless of supplies. */
    val isWipedOut: Boolean get() = survivors.isEmpty()

    val leader: Traveler? get() = members.firstOrNull { it.isLeader }

    fun adjustHealth(delta: Int): Party =
        copy(healthPoints = (healthPoints + delta).coerceAtLeast(0))

    fun withMember(name: String, transform: (Traveler) -> Traveler): Party =
        copy(members = members.map { if (it.name == name) transform(it) else it })
}

/**
 * Curated names offered at setup.
 *
 * ADR 0004 forbids free text anywhere in the app, so the player picks from this list
 * rather than typing. The loss of "name your party whatever you like" is a real cost of
 * that decision and is recorded there.
 */
object PartyNames {
    val suggestions: List<String> = listOf(
        "Amanda", "Abigail", "Alice", "Charles", "Clara", "Edward",
        "Eliza", "Emmett", "Grace", "Hannah", "Henry", "Jesse",
        "Josiah", "Martha", "Nathaniel", "Rebecca", "Samuel", "Sarah",
        "Silas", "Thomas", "Walter", "Willa",
    )

    /** The party the game starts with if the player takes the express path. */
    val defaultParty: List<String> = listOf("Amanda", "Thomas", "Eliza", "Samuel", "Grace")

    const val PARTY_SIZE = 5
}
