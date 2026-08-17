package com.oregontrail.wear.ui

import com.oregontrail.wear.ui.theme.SNAP_SLACK
import com.oregontrail.wear.ui.theme.WATCH_PIXELS
import com.oregontrail.wear.ui.theme.watchScaleFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.floor

/**
 * Checks how big the browser draws the watch, from the one target that has a test runner.
 *
 * The rule it guards is a compromise with two failure modes facing opposite ways, and the
 * first of them shipped: a whole-numbers-only magnification drew a 384-pixel watch across
 * half a phone, and an exact fit at every size would put the type off the pixel grid on the
 * desktops where that is visible. Neither shows up in a screenshot taken on the other's
 * device, which is what the window sizes below are for — they are a list of screens, by the
 * numbers those screens actually report.
 *
 * See `Main.kt` for the argument and `ui/theme/WatchScale.kt` for the rule.
 */
class WatchScaleTest {

    /**
     * Device pixels down the shorter side of some windows worth not regressing: CSS pixels
     * times device pixel ratio, which is what a Compose canvas is measured in.
     */
    private val windows = mapOf(
        "a 360x800 phone at ratio 2" to 720,
        "a 390x844 phone at ratio 3" to 1170,
        "a 412x915 phone at ratio 2.625" to 1081,
        "a 1512x982 laptop at ratio 2" to 1964,
        "a 1920x1080 monitor at ratio 1" to 1080,
        "a 2560x1440 monitor at ratio 1" to 1440,
    )

    /**
     * The regression this rule exists for, stated as the thing a player sees: the watch is
     * never a small picture in the middle of a big black page.
     *
     * At 720 — a very ordinary Android phone — the old rule produced 384 pixels of watch,
     * which is 53% and is what this is here to catch.
     */
    @Test
    fun `every window is filled to at least the slack`() {
        for ((window, shortest) in windows) {
            val drawn = drawnPx(shortest)
            assertTrue(
                "$window has $shortest pixels and gets a ${drawn.toInt()}-pixel watch, " +
                    "which is ${(100 * drawn / shortest).toInt()}% of it",
                drawn >= shortest * SNAP_SLACK,
            )
        }
        for (shortest in WATCH_PIXELS..8192) {
            assertTrue(
                "$shortest pixels gets a ${drawnPx(shortest).toInt()}-pixel watch",
                drawnPx(shortest) >= shortest * SNAP_SLACK,
            )
        }
    }

    /** The other half of "fits": the shorter side is the one the watch has to fit inside. */
    @Test
    fun `no window is overflowed`() {
        for (shortest in WATCH_PIXELS..8192) {
            val drawn = drawnPx(shortest)
            assertTrue("$shortest pixels got a ${drawn.toInt()}-pixel watch", drawn <= shortest)
        }
    }

    /**
     * Two branches and no third: either the whole number below, which is what keeps the
     * type on the pixel grid, or the exact fit. Anything else — a half step, a rounding to
     * the nearest — would be a magnification that is neither crisp nor filling.
     */
    @Test
    fun `the answer is always either the whole number below or the exact fit`() {
        for (shortest in WATCH_PIXELS..8192) {
            val scale = watchScaleFor(shortest)
            val exact = shortest.toFloat() / WATCH_PIXELS
            assertTrue(
                "$shortest pixels gave $scale, which is neither ${floor(exact)} nor $exact",
                scale == floor(exact) || scale == exact,
            )
        }
    }

    /**
     * The whole number is *preferred*, not merely permitted: where one fills the window
     * comfortably it must be the one taken, because that is the case where crisp type costs
     * nothing at all. Tested with a margin above [SNAP_SLACK] rather than at it, since a
     * case sitting exactly on the threshold turns on the last bit of a float.
     */
    @Test
    fun `a whole magnification is taken whenever it comfortably fills the window`() {
        for (shortest in WATCH_PIXELS..8192) {
            val whole = floor(shortest.toFloat() / WATCH_PIXELS)
            if (whole * WATCH_PIXELS >= shortest * (SNAP_SLACK + 0.02f)) {
                assertEquals("$shortest pixels", whole, watchScaleFor(shortest), 0f)
            }
        }
    }

    /**
     * A window with nothing to spare clips rather than shrinking below the watch's own
     * size: under 1x the character cell is under 8 pixels tall and stops being readable, so
     * there is nothing down there worth scaling to.
     */
    @Test
    fun `a window smaller than the watch still gets a whole watch's worth of pixels`() {
        for (shortest in 0..WATCH_PIXELS) {
            assertEquals("$shortest pixels", 1f, watchScaleFor(shortest), 0f)
        }
    }

    private fun drawnPx(shortest: Int): Float = watchScaleFor(shortest) * WATCH_PIXELS
}
