package com.oregontrail.wear.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the sourced price table from docs/reference/game-mechanics-1985.md.
 *
 * Every base price divides evenly at 25-point markup steps, so these are exact integer
 * cents with no rounding anywhere — that property is what makes holding money in cents
 * worthwhile, and this test would catch its loss.
 */
class StoreTest {

    private data class Row(
        val markup: Int,
        val oxenPerYoke: Int,
        val partsOrClothes: Int,
        val bulletBox: Int,
        val foodPerPound: Int,
    )

    private val sourcedTable = listOf(
        Row(markup = 0, oxenPerYoke = 20_00, partsOrClothes = 10_00, bulletBox = 2_00, foodPerPound = 20),
        Row(markup = 25, oxenPerYoke = 25_00, partsOrClothes = 12_50, bulletBox = 2_50, foodPerPound = 25),
        Row(markup = 50, oxenPerYoke = 30_00, partsOrClothes = 15_00, bulletBox = 3_00, foodPerPound = 30),
        Row(markup = 75, oxenPerYoke = 35_00, partsOrClothes = 17_50, bulletBox = 3_50, foodPerPound = 35),
        Row(markup = 100, oxenPerYoke = 40_00, partsOrClothes = 20_00, bulletBox = 4_00, foodPerPound = 40),
        Row(markup = 125, oxenPerYoke = 45_00, partsOrClothes = 22_50, bulletBox = 4_50, foodPerPound = 45),
        Row(markup = 150, oxenPerYoke = 50_00, partsOrClothes = 25_00, bulletBox = 5_00, foodPerPound = 50),
    )

    @Test
    fun `prices match the sourced table at every markup`() {
        for (row in sourcedTable) {
            val store = Store("test", row.markup)
            assertEquals("oxen at +${row.markup}%", row.oxenPerYoke, store.priceCents(Good.OXEN))
            assertEquals("clothing at +${row.markup}%", row.partsOrClothes, store.priceCents(Good.CLOTHING))
            assertEquals("wheel at +${row.markup}%", row.partsOrClothes, store.priceCents(Good.SPARE_WHEEL))
            assertEquals("bullets at +${row.markup}%", row.bulletBox, store.priceCents(Good.BULLETS))
            assertEquals("food at +${row.markup}%", row.foodPerPound, store.priceCents(Good.FOOD))
        }
    }

    @Test
    fun `each fort on the trail charges its sourced markup`() {
        val expected = mapOf(
            LandmarkId.FORT_KEARNEY to 25,
            LandmarkId.FORT_LARAMIE to 50,
            LandmarkId.FORT_BRIDGER to 75,
            LandmarkId.FORT_HALL to 100,
            LandmarkId.FORT_BOISE to 125,
            LandmarkId.FORT_WALLA_WALLA to 150,
        )
        for ((id, markup) in expected) {
            val store = Store.at(Trail[id])
            assertEquals("no store at $id", markup, store?.markupPercent)
        }
    }

    @Test
    fun `Matt's General Store sells at base price`() {
        assertEquals(0, Store.matts.markupPercent)
        assertEquals(20, Store.matts.priceCents(Good.FOOD))
    }

    @Test
    fun `there is no store at a river or a waypoint`() {
        assertEquals(null, Store.at(Trail[LandmarkId.KANSAS_RIVER]))
        assertEquals(null, Store.at(Trail[LandmarkId.CHIMNEY_ROCK]))
        assertEquals(null, Store.at(Trail[LandmarkId.OREGON_CITY]))
    }

    @Test
    fun `buying a yoke adds two oxen and a box adds twenty bullets`() {
        val start = Inventory(cashCents = 100_00)
        val withOxen = Store.matts.buy(start, Good.OXEN, quantity = 3)
        assertEquals(6, withOxen.oxen)
        assertEquals(100_00 - 60_00, withOxen.cashCents)

        val withAmmo = Store.matts.buy(withOxen, Good.BULLETS, quantity = 2)
        assertEquals(40, withAmmo.bullets)
        assertEquals(40_00 - 4_00, withAmmo.cashCents)
    }

    @Test
    fun `food is bought by the pound`() {
        val bought = Store.matts.buy(Inventory(cashCents = 50_00), Good.FOOD, quantity = 200)
        assertEquals(200, bought.foodPounds)
        assertEquals(50_00 - 40_00, bought.cashCents)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cannot buy what cannot be afforded`() {
        Store.matts.buy(Inventory(cashCents = 100), Good.OXEN, quantity = 1)
    }

    @Test
    fun `affordable reports how many purchase units the cash covers`() {
        val store = Store("Fort Hall", markupPercent = 100)
        // Oxen are $40 a yoke at +100%, so $100 buys two yokes with change.
        assertEquals(2, store.affordable(Good.OXEN, cashCents = 100_00))
    }

    @Test
    fun `spare parts count together for scoring purposes`() {
        val inv = Inventory(spareWheels = 2, spareAxles = 3, spareTongues = 1)
        assertEquals(6, inv.spareParts)
    }

    @Test
    fun `prices rise monotonically along the trail`() {
        val prices = Trail.forts.map { Store.at(it)!!.priceCents(Good.FOOD) }
        assertTrue("food should get dearer westward: $prices", prices == prices.sorted())
        assertTrue(prices.toSet().size == prices.size)
    }
}
