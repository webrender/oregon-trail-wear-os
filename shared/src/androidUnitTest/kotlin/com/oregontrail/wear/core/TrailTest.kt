package com.oregontrail.wear.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailTest {

    @Test
    fun `every route points at a landmark that exists`() {
        for (landmark in Trail.landmarks) {
            for (route in landmark.routes) {
                assertNotNull(
                    "${landmark.id} routes to ${route.to}, which is not defined",
                    Trail.landmarks.firstOrNull { it.id == route.to },
                )
            }
        }
    }

    @Test
    fun `every landmark is reachable from Independence`() {
        val seen = mutableSetOf<LandmarkId>()
        fun walk(id: LandmarkId) {
            if (!seen.add(id)) return
            Trail[id].routes.forEach { walk(it.to) }
        }
        walk(LandmarkId.INDEPENDENCE)

        val unreachable = LandmarkId.entries.toSet() - seen
        assertTrue("Unreachable landmarks: $unreachable", unreachable.isEmpty())
    }

    @Test
    fun `Oregon City is the only end of the trail`() {
        val ends = Trail.landmarks.filter { it.isEnd }.map { it.id }
        assertEquals(listOf(LandmarkId.OREGON_CITY), ends)
    }

    @Test
    fun `there are exactly three branch points`() {
        val branches = Trail.landmarks.filter { it.isBranch }.map { it.id }
        assertEquals(
            listOf(LandmarkId.SOUTH_PASS, LandmarkId.BLUE_MOUNTAINS, LandmarkId.THE_DALLES),
            branches,
        )
    }

    /**
     * The cross-check that validates the whole distance table.
     *
     * The Green River + Barlow Road route is the one the sourced per-segment distances
     * describe end to end, and it comes to 1,971 miles — matching the designer's stated
     * ~2,000-mile target. Two independent sources agreeing is the reason these numbers
     * are trusted; if this test ever fails, the table was edited, not the arithmetic.
     */
    @Test
    fun `Green River and Barlow Road route totals 1971 miles`() {
        val path = listOf(
            LandmarkId.KANSAS_RIVER,
            LandmarkId.BIG_BLUE_RIVER,
            LandmarkId.FORT_KEARNEY,
            LandmarkId.CHIMNEY_ROCK,
            LandmarkId.FORT_LARAMIE,
            LandmarkId.INDEPENDENCE_ROCK,
            LandmarkId.SOUTH_PASS,
            LandmarkId.GREEN_RIVER,
            LandmarkId.SODA_SPRINGS,
            LandmarkId.FORT_HALL,
            LandmarkId.SNAKE_RIVER,
            LandmarkId.FORT_BOISE,
            LandmarkId.BLUE_MOUNTAINS,
            LandmarkId.THE_DALLES,
            LandmarkId.OREGON_CITY,
        )
        assertEquals(1971, milesAlong(LandmarkId.INDEPENDENCE, path))
    }

    @Test
    fun `Fort Bridger detour is longer than fording the Green River`() {
        val southPass = Trail[LandmarkId.SOUTH_PASS]
        val viaGreenRiver = southPass.routes.single { it.to == LandmarkId.GREEN_RIVER }.miles +
            Trail[LandmarkId.GREEN_RIVER].routes.single().miles
        val viaBridger = southPass.routes.single { it.to == LandmarkId.FORT_BRIDGER }.miles +
            Trail[LandmarkId.FORT_BRIDGER].routes.single().miles

        assertEquals(201, viaGreenRiver)
        assertEquals(287, viaBridger)
        assertTrue("The resupply detour must cost distance", viaBridger > viaGreenRiver)
    }

    @Test
    fun `fort markups climb by 25 points from Kearney to Walla Walla`() {
        val markups = Trail.forts.map { (it.kind as LandmarkKind.Fort).markupPercent }
        assertEquals(listOf(25, 50, 75, 100, 125, 150), markups)
    }

    @Test
    fun `pathTo walks from Independence to the landmark asked for`() {
        for (id in LandmarkId.entries) {
            val path = Trail.pathTo(id)
            assertEquals("path to $id starts in the wrong place", LandmarkId.INDEPENDENCE, path.first())
            assertEquals("path to $id ends in the wrong place", id, path.last())
            for ((from, to) in path.zipWithNext()) {
                assertNotNull(
                    "path to $id steps from $from to $to, which is not a route",
                    Trail[from].routes.firstOrNull { it.to == to },
                )
            }
        }
    }

    /**
     * The reconstruction takes the first route out of a branch, which is the one the table
     * presents as the main road. Fort Bridger is the second, so reaching it is the case
     * where the guess has to give way to the recorded history — see [GameState.journey].
     */
    @Test
    fun `pathTo takes the first arm of a branch, and still reaches the second`() {
        assertTrue(LandmarkId.FORT_BRIDGER !in Trail.pathTo(LandmarkId.SODA_SPRINGS))
        assertTrue(LandmarkId.GREEN_RIVER in Trail.pathTo(LandmarkId.SODA_SPRINGS))
        assertEquals(LandmarkId.FORT_BRIDGER, Trail.pathTo(LandmarkId.FORT_BRIDGER).last())
    }

    @Test
    fun `milesBetween measures forward along the trail and nowhere else`() {
        assertEquals(0, Trail.milesBetween(LandmarkId.FORT_HALL, LandmarkId.FORT_HALL))
        assertEquals(
            102,
            Trail.milesBetween(LandmarkId.INDEPENDENCE, LandmarkId.KANSAS_RIVER),
        )
        // The shortest of the two ways round, not the first.
        assertEquals(
            201,
            Trail.milesBetween(LandmarkId.SOUTH_PASS, LandmarkId.SODA_SPRINGS),
        )
        // Backwards is not a distance, and neither is the arm of a branch not taken.
        assertEquals(null, Trail.milesBetween(LandmarkId.FORT_HALL, LandmarkId.SOUTH_PASS))
        assertEquals(null, Trail.milesBetween(LandmarkId.GREEN_RIVER, LandmarkId.FORT_BRIDGER))
    }

    @Test
    fun `milesAlong agrees with the sourced total for the whole trail`() {
        assertEquals(1971, Trail.milesAlong(Trail.pathTo(LandmarkId.OREGON_CITY)))
    }

    private fun milesAlong(from: LandmarkId, path: List<LandmarkId>): Int {
        var current = from
        var total = 0
        for (next in path) {
            val route = Trail[current].routes.firstOrNull { it.to == next }
                ?: error("No route from $current to $next")
            total += route.miles
            current = next
        }
        return total
    }
}
