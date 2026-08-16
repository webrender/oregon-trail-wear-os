package com.oregontrail.wear.ui

import com.oregontrail.wear.core.LandmarkId
import com.oregontrail.wear.core.Trail
import com.oregontrail.wear.ui.art.ArtNames
import com.oregontrail.wear.ui.art.TrailMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Ties the map's coordinate table to the trail it is drawing.
 *
 * The table is hand-placed against the art, so nothing about it is derived and nothing
 * about it fails loudly on its own: a landmark with no position, or one placed east of
 * somewhere it is supposed to be west of, would draw a road doubling back on itself on a
 * 1.2" display and would be very easy to miss. These are the invariants the screen assumes
 * and never checks.
 */
class TrailMapTest {

    @Test
    fun `every landmark has a position`() {
        for (id in LandmarkId.entries) {
            // Throws rather than returning null if the table is missing one.
            TrailMap[id]
        }
    }

    @Test
    fun `every position is inside the strip`() {
        for (id in LandmarkId.entries) {
            val point = TrailMap[id]
            assertTrue(
                "$id is at x=${point.x}, outside the ${TrailMap.TILES}-tile strip",
                point.x > 0f && point.x < TrailMap.TILES,
            )
            assertTrue("$id is at y=${point.y}, outside the tile", point.y > 0f && point.y < 1f)
        }
    }

    /**
     * The map has to hold every landmark's marker clear of the round bezel, and the band
     * shows the full height of a tile — so a landmark too near the top or bottom edge
     * would have its marker clipped when it scrolled to the side of the display.
     */
    @Test
    fun `no landmark sits near the top or bottom edge`() {
        for (id in LandmarkId.entries) {
            val y = TrailMap[id].y
            assertTrue("$id is at y=$y, too close to an edge to draw a marker", y in 0.2f..0.85f)
        }
    }

    /**
     * The one that earns its keep. The trail only ever runs westward, so every route must
     * go from a larger x to a smaller one — a road that ran backwards would be drawn as a
     * road that ran backwards.
     */
    @Test
    fun `every route runs east to west`() {
        for (landmark in Trail.landmarks) {
            for (route in landmark.routes) {
                val from = TrailMap[landmark.id].x
                val to = TrailMap[route.to].x
                assertTrue(
                    "${landmark.id} at x=$from is not east of ${route.to} at x=$to",
                    from > to,
                )
            }
        }
    }

    /**
     * What lets [TrailMap.stops] be [LandmarkId.entries] unchanged rather than a sort.
     *
     * The crown scrubs through that list, so if the enum ever stopped being in west-to-east
     * order the map would jump about instead of scrolling. Sorting in [TrailMap] would hide
     * that; failing here says it.
     */
    @Test
    fun `declaration order is east to west order`() {
        val byPosition = LandmarkId.entries.sortedByDescending { TrailMap[it].x }
        assertEquals(byPosition, TrailMap.stops)
    }

    /**
     * Two landmarks drawn on top of each other cannot be told apart, and the crown would
     * appear to skip one. The tightest real pair is Fort Bridger and the Green River
     * crossing, which is why the table pushes Fort Bridger further south than it lies.
     *
     * A tenth of a tile is about 26 device pixels on the 384px watch, against a 7dp marker.
     */
    @Test
    fun `no two landmarks are drawn on top of each other`() {
        val entries = LandmarkId.entries
        for (i in entries.indices) {
            for (j in i + 1 until entries.size) {
                val a = TrailMap[entries[i]]
                val b = TrailMap[entries[j]]
                val apart = kotlin.math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
                assertTrue(
                    "${entries[i]} and ${entries[j]} are only $apart tiles apart",
                    apart > 0.1,
                )
            }
        }
    }

    @Test
    fun `the strip is as wide as there are tiles to draw it`() {
        assertEquals(ArtNames.mapTiles.size, TrailMap.TILES)
    }

    /**
     * The tiles are butted edge to edge and the coordinate table is expressed in tiles, so
     * a tile of a different size — or one that was not square — would shear every position
     * to its east.
     */
    @Test
    fun `every map tile is square and the same size as the rest`() {
        val directory = File("src/androidMain/assets/art")
        val sizes = ArtNames.mapTiles.map { name ->
            val file = File(directory, "$name.png")
            assertTrue("missing map tile $name", file.isFile)
            pngSize(file)
        }
        for ((index, size) in sizes.withIndex()) {
            assertEquals(
                "${ArtNames.mapTiles[index]} is not square, it is ${size.first}x${size.second}",
                size.first,
                size.second,
            )
        }
        assertEquals("the map tiles are not all the same size: $sizes", 1, sizes.distinct().size)
    }

    /** A PNG's dimensions, out of its IHDR chunk — see `ArtNamesTest.pngSize`. */
    private fun pngSize(file: File): Pair<Int, Int> {
        val header = file.readBytes().copyOfRange(0, 24)
        fun intAt(offset: Int): Int = (0..3).fold(0) { acc, i ->
            (acc shl 8) or (header[offset + i].toInt() and 0xFF)
        }
        return intAt(16) to intAt(20)
    }
}
