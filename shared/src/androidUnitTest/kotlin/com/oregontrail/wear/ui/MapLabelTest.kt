package com.oregontrail.wear.ui

import com.oregontrail.wear.core.LandmarkId
import com.oregontrail.wear.core.Trail
import com.oregontrail.wear.ui.screens.MAP_ASPECT
import com.oregontrail.wear.ui.screens.TITLE_WIDTH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks that the text on the map screen fits the part of the display that exists.
 *
 * This is the sibling of `ArtNamesTest`, and it earns its keep the same way: by catching a
 * failure that is otherwise *silent*. A label too wide for the round display is not
 * truncated, does not warn, and does not throw — Compose lays it out happily against the
 * square framebuffer, and the bezel then crops both ends off in hardware. "Green River
 * Crossing" shipped that way and read as "een River Crossin" on the watch.
 *
 * So the widths here are measured from the real font rather than counted in characters.
 * Shaston is proportional — advances run from 4 to 9 pixels — so a character count would be
 * off by nearly half between "Fort Hall" and "Willamette", which is more than the margin
 * this is checking.
 */
class MapLabelTest {

    /**
     * The physical Pixel Watch 2, not the emulator.
     *
     * 384x384 at density 2. The common emulator image is 454x454, and a label budgeted
     * against that is 18% too generous — every string here would pass and the watch would
     * still crop it.
     */
    private val displayPx = 384.0

    /** `caption1` is `cellStyle(2)`: two 8-pixel character cells. See `Typography.kt`. */
    private val captionPx = 16.0

    private val font = Shaston(File("src/androidMain/assets/fonts/shaston_320.ttf"))

    /** What [TITLE_WIDTH] actually permits, in pixels. */
    private val budgetPx = displayPx * TITLE_WIDTH

    @Test
    fun `every landmark's map label fits the visible width`() {
        for (id in LandmarkId.entries) {
            val label = Trail[id].shortName
            val width = font.widthPx(label, captionPx)
            assertTrue(
                "\"$label\" is ${width.toInt()}px wide, over the ${budgetPx.toInt()}px budget " +
                    "— it will be cropped by the bezel, silently",
                width <= budgetPx,
            )
        }
    }

    /**
     * The line under the map, which is generated rather than drawn from the trail table and
     * so cannot be checked by walking [LandmarkId]. These are its longest forms.
     */
    @Test
    fun `the distance line fits the visible width in its longest form`() {
        for (line in listOf("Not on your road", "1,234 miles back", "1,234 miles on", "You are here")) {
            val width = font.widthPx(line, captionPx)
            assertTrue(
                "\"$line\" is ${width.toInt()}px wide, over the ${budgetPx.toInt()}px budget",
                width <= budgetPx,
            )
        }
    }

    /**
     * Pins the budget to the geometry it was derived from.
     *
     * [TITLE_WIDTH] is the chord of the visible circle at the height the title sits at, and
     * that height depends on the map band's aspect — so raising the band's height without
     * lowering this fraction would put the text back under the bezel. This is the check that
     * notices, since nothing about the two constants otherwise says they are related.
     */
    @Test
    fun `the title budget does not exceed the visible chord at the title's height`() {
        // Column contents, centred: one caption line at 24px (16px of glyph plus half a
        // cell of leading), a 4dp gap, the band, a 6dp gap, and a second caption line.
        // The gaps are dp and the watch is density 2, so they are 8 and 12 pixels — the
        // conversion this test existed for all of ten minutes without, and which put the
        // first draft of the budget 7px over the chord.
        val bandPx = displayPx / MAP_ASPECT
        val columnPx = 24 + 8 + bandPx + 12 + 24
        val titleTopPx = (displayPx - columnPx) / 2

        val radius = displayPx / 2
        val dy = radius - titleTopPx
        val chordPx = 2 * Math.sqrt(radius * radius - dy * dy)

        assertTrue(
            "the title may use ${budgetPx.toInt()}px but only ${chordPx.toInt()}px is visible " +
                "at y=${titleTopPx.toInt()}",
            budgetPx <= chordPx,
        )
    }

    @Test
    fun `short names drop what the screen already says and keep what it does not`() {
        assertEquals("Green River", Trail[LandmarkId.GREEN_RIVER].shortName)
        assertEquals("Independence", Trail[LandmarkId.INDEPENDENCE].shortName)
        assertEquals("Columbia River", Trail[LandmarkId.COLUMBIA_RIVER].shortName)
        // The article is part of this one's name, and the rule that trims the Columbia's
        // must not reach it.
        assertEquals("The Dalles", Trail[LandmarkId.THE_DALLES].shortName)
    }
}
