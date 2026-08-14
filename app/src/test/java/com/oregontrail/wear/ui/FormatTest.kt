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

    @Test
    fun `a count agrees with its noun`() {
        assertEquals("0 wheels", counted(0, "wheel"))
        assertEquals("1 wheel", counted(1, "wheel"))
        assertEquals("2 wheels", counted(2, "wheel"))
    }

    @Test
    fun `an irregular plural is spelled out`() {
        assertEquals("1 ox", counted(1, "ox", "oxen"))
        assertEquals("2 oxen", counted(2, "ox", "oxen"))
    }

    /**
     * A trade offer rolls from 1, so every countable good can be offered singly — this is
     * what produced "1 wheels" on the trader's line. Food and clothing are deliberately
     * exempt: both are phrased as mass nouns that read the same at any count.
     */
    @Test
    fun `one of any good reads as a singular`() {
        assertEquals("1 ox", Good.OXEN.describeQuantity(1))
        assertEquals("1 bullet", Good.BULLETS.describeQuantity(1))
        assertEquals("1 wheel", Good.SPARE_WHEEL.describeQuantity(1))
        assertEquals("1 axle", Good.SPARE_AXLE.describeQuantity(1))
        assertEquals("1 tongue", Good.SPARE_TONGUE.describeQuantity(1))
        assertEquals("1 lb food", Good.FOOD.describeQuantity(1))
        assertEquals("1 clothing", Good.CLOTHING.describeQuantity(1))
    }

    @Test
    fun `more than one of any good reads as a plural`() {
        assertEquals("6 oxen", Good.OXEN.describeQuantity(6))
        assertEquals("20 bullets", Good.BULLETS.describeQuantity(20))
        assertEquals("2 wheels", Good.SPARE_WHEEL.describeQuantity(2))
        assertEquals("2 axles", Good.SPARE_AXLE.describeQuantity(2))
        assertEquals("2 tongues", Good.SPARE_TONGUE.describeQuantity(2))
        assertEquals("400 lb food", Good.FOOD.describeQuantity(400))
        assertEquals("3 clothing", Good.CLOTHING.describeQuantity(3))
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
