package com.oregontrail.wear.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RngTest {

    @Test
    fun `the same seed produces the same sequence`() {
        val a = Rng.seeded(99).let { r -> List(50) { r.nextInt(1000) } }
        val b = Rng.seeded(99).let { r -> List(50) { r.nextInt(1000) } }
        assertEquals(a, b)
    }

    @Test
    fun `different seeds diverge`() {
        val a = Rng.seeded(1).let { r -> List(50) { r.nextInt(1000) } }
        val b = Rng.seeded(2).let { r -> List(50) { r.nextInt(1000) } }
        assertNotEquals(a, b)
    }

    @Test
    fun `a fork continues independently of its parent`() {
        val parent = Rng.seeded(7)
        repeat(10) { parent.nextLong() }

        val fork = parent.fork()
        val forkValues = List(20) { fork.nextInt(1000) }
        val parentValues = List(20) { parent.nextInt(1000) }

        // Same state means the same continuation...
        assertEquals(parentValues, forkValues)
        // ...but draining the fork must not have moved the parent, which is the whole
        // point: GameState.copy() shares the RNG reference.
        assertEquals(parent.snapshot(), fork.snapshot())
    }

    @Test
    fun `deriving does not consume the parent's output`() {
        val parent = Rng.seeded(42)
        val before = parent.snapshot()
        val derived = parent.derive(salt = 5)
        repeat(100) { derived.nextLong() }
        assertEquals(before, parent.snapshot())
    }

    @Test
    fun `deriving with the same salt is stable and different salts differ`() {
        val parent = Rng.seeded(42)
        val first = parent.derive(3).nextInt(1_000_000)
        val again = parent.derive(3).nextInt(1_000_000)
        val other = parent.derive(4).nextInt(1_000_000)

        assertEquals(first, again)
        assertNotEquals(first, other)
    }

    @Test
    fun `nextInt stays within bounds`() {
        val rng = Rng.seeded(3)
        repeat(10_000) {
            val v = rng.nextInt(10)
            assertTrue("$v out of range", v in 0..9)
        }
    }

    @Test
    fun `nextInt with a range is inclusive at both ends`() {
        val rng = Rng.seeded(3)
        val seen = mutableSetOf<Int>()
        repeat(10_000) { seen += rng.nextInt(-2, 2) }
        assertEquals(setOf(-2, -1, 0, 1, 2), seen)
    }

    @Test
    fun `nextDouble stays in the unit interval`() {
        val rng = Rng.seeded(11)
        repeat(10_000) {
            val v = rng.nextDouble()
            assertTrue("$v out of range", v >= 0.0 && v < 1.0)
        }
    }

    @Test
    fun `chance is roughly fair`() {
        val rng = Rng.seeded(5)
        val hits = (1..100_000).count { rng.chance(0.25) }
        assertTrue("got $hits hits in 100k at p=0.25", hits in 24_000..26_000)
    }

    @Test
    fun `restoring from a snapshot resumes the same sequence`() {
        val original = Rng.seeded(2024)
        repeat(37) { original.nextLong() }
        val restored = Rng(original.snapshot())

        assertEquals(
            List(20) { original.nextInt(10_000) },
            List(20) { restored.nextInt(10_000) },
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero is rejected as a raw seed`() {
        Rng(0)
    }

    @Test
    fun `seeded maps the illegal zero seed to something usable`() {
        assertNotEquals(0L, Rng.seeded(0).snapshot())
    }
}
