package com.oregontrail.wear.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailDateTest {

    @Test
    fun `day zero is the first of the departure month`() {
        assertEquals(TrailDate(3, 1), TrailDate.of(startMonth = 3, dayOfJourney = 0))
        assertEquals(TrailDate(7, 1), TrailDate.of(startMonth = 7, dayOfJourney = 0))
    }

    @Test
    fun `dates roll into the following month`() {
        // March has 31 days, so day 31 is April 1st.
        assertEquals(TrailDate(4, 1), TrailDate.of(startMonth = 3, dayOfJourney = 31))
        assertEquals(TrailDate(3, 31), TrailDate.of(startMonth = 3, dayOfJourney = 30))
    }

    @Test
    fun `1848 is treated as a leap year`() {
        // 29 days in February: departing 1 February, day 29 must be 1 March.
        assertEquals(TrailDate(3, 1), TrailDate.of(startMonth = 2, dayOfJourney = 29))
    }

    @Test
    fun `a typical journey lands in late summer`() {
        // A 150-day run from a 1 April departure ends on 29 August — comfortably ahead
        // of the mountain snows, which is what an on-schedule journey should look like.
        assertEquals(TrailDate(8, 29), TrailDate.of(startMonth = 4, dayOfJourney = 150))
    }

    @Test
    fun `a late or slow journey runs into winter`() {
        // Departing in July and taking 150 days puts the party in the mountains in
        // late November, which is how runs get killed by weather rather than by events.
        assertEquals(TrailDate(11, 28), TrailDate.of(startMonth = 7, dayOfJourney = 150))
    }

    @Test
    fun `month names are rendered for display`() {
        assertEquals("March", TrailDate.of(3, 0).monthName)
        assertEquals("July 4, 1848", TrailDate.of(7, 3).toString())
    }

    @Test
    fun `dates stay valid across a long journey`() {
        for (day in 0..400) {
            val date = TrailDate.of(startMonth = 3, dayOfJourney = day)
            assertTrue("month ${date.month} out of range on day $day", date.month in 1..12)
            assertTrue("day ${date.day} out of range on day $day", date.day in 1..31)
        }
    }

    @Test
    fun `the departure window is March through July`() {
        assertEquals(3..7, TrailDate.DEPARTURE_MONTHS)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `departing outside the window is rejected`() {
        GameState.newGame(Profession.BANKER, TestStates.party, startMonth = 12, seed = 1)
    }

    @Test
    fun `a new game starts on the road to the Kansas River`() {
        val state = TestStates.fresh(startMonth = 5)
        assertEquals(LandmarkId.INDEPENDENCE, state.at)
        assertEquals(LandmarkId.KANSAS_RIVER, state.heading)
        assertEquals(TrailDate(5, 1), state.date)
        assertEquals(102, state.milesToNextLandmark)
        assertEquals(Profession.BANKER.startingCents, state.inventory.cashCents)
    }
}
