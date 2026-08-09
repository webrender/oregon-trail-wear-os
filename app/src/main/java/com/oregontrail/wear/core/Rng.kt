package com.oregontrail.wear.core

import kotlinx.serialization.Serializable

/**
 * Deterministic, seedable, serialisable PRNG (xorshift64*).
 *
 * Two requirements drove writing one rather than using [kotlin.random.Random]:
 *
 * 1. A given seed must replay identically, so the turn engine can be tested without
 *    stubbing randomness at every call site.
 * 2. It must survive being saved and restored mid-run. `kotlin.random.Random` keeps its
 *    state private and unreachable, so a resumed game would silently diverge from the
 *    one that was saved.
 *
 * Both are satisfied by a generator whose entire state is a single Long.
 *
 * Deliberately mutable. Threading an immutable RNG through every draw in the turn
 * engine costs more clarity than the immutability would buy.
 */
@Serializable
class Rng(private var state: Long) {

    init {
        require(state != 0L) { "xorshift64 degenerates to all-zeroes if seeded with 0" }
    }

    fun nextLong(): Long {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        state = x
        return x * MULTIPLIER
    }

    /** Uniform in `[0.0, 1.0)`. */
    fun nextDouble(): Double = (nextLong() ushr 11).toDouble() / TWO_POW_53

    /** Uniform in `[0, bound)`. */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive, was $bound" }
        return (nextDouble() * bound).toInt().coerceAtMost(bound - 1)
    }

    /** Uniform in `[min, max]`, inclusive at both ends. */
    fun nextInt(min: Int, max: Int): Int {
        require(max >= min) { "max ($max) must be >= min ($min)" }
        return min + nextInt(max - min + 1)
    }

    /** True with probability [p]. */
    fun chance(p: Double): Boolean = nextDouble() < p

    fun <T> pick(items: List<T>): T {
        require(items.isNotEmpty()) { "cannot pick from an empty list" }
        return items[nextInt(items.size)]
    }

    /** Current internal state, exposed for tests and diagnostics. */
    fun snapshot(): Long = state

    /**
     * Value equality on the generator's state.
     *
     * Without this, identity equality would silently break [GameState]'s generated
     * `equals`: two states that are identical in every respect would compare unequal
     * because they hold different `Rng` instances. That matters for round-trip tests,
     * and for Compose, which uses equality to decide whether anything needs redrawing.
     */
    override fun equals(other: Any?): Boolean =
        this === other || (other is Rng && state == other.state)

    override fun hashCode(): Int = state.hashCode()

    override fun toString(): String = "Rng(state=$state)"

    /**
     * An independent generator continuing from this one's current state.
     *
     * This is what keeps [GameState] honestly immutable despite holding a mutable RNG:
     * `data class` copy() would share the reference, so advancing a state would rewind
     * or corrupt any other reference to it — including the one a caller kept in order
     * to re-run a turn. Every engine entry point forks first and returns the fork, so
     * the state passed in is never touched.
     */
    fun fork(): Rng = Rng(state)

    /**
     * A generator derived from this one's state plus [salt], without consuming any of
     * this generator's output.
     *
     * For rolls that must be stable across repeated reads — river conditions are the
     * motivating case, since the UI may ask for them on every recomposition and the
     * river must not change depth each time it's looked at.
     */
    fun derive(salt: Long): Rng = seeded(state xor (salt * GOLDEN_GAMMA))

    companion object {
        private const val MULTIPLIER = 2685821657736338717L
        private const val TWO_POW_53 = 9007199254740992.0

        /** Odd constant with good bit dispersion, for mixing salts in [derive]. */
        private const val GOLDEN_GAMMA = -7046029254386353131L // 0x9E3779B97F4A7C15

        /** Seeds the generator, mapping the illegal seed 0 to 1. */
        fun seeded(seed: Long): Rng = Rng(if (seed == 0L) 1L else seed)
    }
}
