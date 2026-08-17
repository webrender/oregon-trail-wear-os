package com.oregontrail.wear.ui.theme

import kotlin.math.floor

/**
 * How many device pixels one watch pixel gets, in a window whose shorter side is
 * [shortestPx] pixels.
 *
 * Only the browser ever asks: the watch is 384 pixels across a 384-pixel screen and the
 * answer there is 1 by definition. It lives in `commonMain` for the same reason
 * [com.oregontrail.wear.ui.components.SteeringHints] does — this is the arithmetic that
 * decides how every screen in the game is sized, and the JVM unit tests are the only tests
 * this project has. In `wasmJsMain` it would be checked by nobody. `Main.kt` holds the
 * argument for the rule; this holds the rule.
 *
 * Whole numbers are what the type wants — see [LocalWatchScale] and [cellStyle] — and a
 * whole number is taken whenever one comes within [SNAP_SLACK] of filling the window. When
 * none does, the exact fit wins instead: the alternative is the 1x watch across half of a
 * phone that a whole-numbers-only rule produced, which is a worse failure than a fraction
 * of a pixel under a glyph.
 *
 * Never returns less than 1. Below that the character cell is under 8 pixels tall and the
 * text stops being text, so a window with less than 384 pixels to spare gets a watch with
 * its edges clipped rather than a legibly-shrunk one, which is not on offer.
 */
fun watchScaleFor(shortestPx: Int): Float {
    val exact = shortestPx.toFloat() / WATCH_PIXELS
    if (exact <= 1f) return 1f
    val whole = floor(exact)
    return if (whole >= exact * SNAP_SLACK) whole else exact
}

/** The Pixel Watch 2's screen: 384 device pixels across 192dp, so density 2. */
const val WATCH_PIXELS = 384

/**
 * How much of the shorter side a whole-number magnification may leave unused before
 * [watchScaleFor] gives up on it and fits the watch exactly instead.
 *
 * Nine tenths, which is about a bezel's worth. It is the dial between two things that
 * cannot both be had, and it sits here because this is roughly where the letterboxing
 * stops reading as a surround and starts reading as a small picture on a big black page.
 */
const val SNAP_SLACK = 0.9f
