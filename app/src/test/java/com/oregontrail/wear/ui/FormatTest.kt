package com.oregontrail.wear.ui

import com.oregontrail.wear.core.Good
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {

    @Test
    fun `whole dollars lose their cents`() {
        assertEquals("$0", money(0))
        assertEquals("$20", money(20_00))
        assertEquals("$1,600", money(1600_00))
    }

    @Test
    fun `part dollars keep both digits`() {
        assertEquals("$0.20", money(20))
        assertEquals("$12.05", money(12_05))
        assertEquals("$1,234.56", money(123456))
    }

    @Test
    fun `thousands are grouped`() {
        assertEquals("$1,000", money(1000_00))
        assertEquals("$999", money(999_00))
        assertEquals("$1,234,567", money(123456700))
    }

    /**
     * The store's stepper walks `0..ceiling step purchaseStep`, which only lands on the
     * ceiling itself if the two divide. A ceiling the player cannot actually reach would
     * silently cap food below the intended maximum.
     */
    @Test
    fun `every purchase ceiling is reachable in whole steps`() {
        for (good in Good.entries) {
            assertEquals(
                "${good.displayName}: ceiling ${good.purchaseCeiling} is not a whole " +
                    "number of ${good.purchaseStep}-unit steps",
                0,
                good.purchaseCeiling % good.purchaseStep,
            )
            assertTrue("${good.displayName}: ceiling must be positive", good.purchaseCeiling > 0)
        }
    }
}
