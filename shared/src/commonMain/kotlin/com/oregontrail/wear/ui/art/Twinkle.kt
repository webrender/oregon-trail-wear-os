package com.oregontrail.wear.ui.art

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.oregontrail.wear.ui.theme.AppleII
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A sky that twinkles, drawn over a backdrop that already has a star field painted into
 * it — the title banner.
 *
 * The stars in the artwork cannot be animated: they are pixels in a PNG. So these are
 * *extra* stars, a couple of dozen of them, laid over the painted ones. Each is drawn as
 * a single dot for most of its cycle, which is exactly what a painted star looks like,
 * and every few seconds flares into a small cross before settling back. Adding stars
 * rather than blinking the painted ones is what keeps the effect honest: a star that
 * vanished and reappeared would read as a rendering fault, whereas one that brightens
 * reads as the sky.
 *
 * On and off, never a fade. The hi-res palette this game draws from has one white and no
 * greys, so a star dimming through intermediate values would be the one thing on screen
 * the hardware could not have done.
 */

/**
 * How long a star holds each state. Also the animation's whole frame budget: nothing here
 * moves, so a redraw is only owed when a star changes shape, and at seven ticks a second
 * the flare still lands as a sparkle rather than a strobe.
 */
private const val TICK_MILLIS = 140L

/** Ticks a star spends flared before dropping back to a dot. */
private const val FLARE_TICKS = 2

/**
 * A star in the overlay.
 *
 * [x] and [y] are fractions of the canvas, not scene units, because the scene grid covers
 * a different part of the canvas depending on which shape of scene is being drawn — see
 * [SCENE_HEIGHT] against [BLEED_SCENE_HEIGHT]. A fraction means the same place either
 * way.
 *
 * [period] is how many ticks pass between one flare and the next, and [phase] where in
 * that cycle the star starts. Both are deliberately unrelated between neighbours: give
 * two stars the same period and the eye picks out the pair blinking in time immediately.
 */
private data class TwinkleStar(
    val x: Float,
    val y: Float,
    val period: Int,
    val phase: Int,
)

/**
 * Where the stars go, in canvas fractions.
 *
 * Chosen by hand against the title banner rather than scattered at random, under three
 * constraints. They sit above the mountain line, which crosses the banner at about 0.6 of
 * its height. They keep clear of the moon in the upper left. And they stay inside the
 * circle the round display actually shows: the scene runs to the very top of the screen,
 * where the visible width narrows to almost nothing, so a star at 0.1 of the height has
 * only the middle half of the width to live in.
 */
private val STARS = listOf(
    TwinkleStar(0.455f, 0.177f, period = 19, phase = 3),
    TwinkleStar(0.534f, 0.209f, period = 26, phase = 17),
    TwinkleStar(0.633f, 0.212f, period = 31, phase = 8),
    TwinkleStar(0.720f, 0.171f, period = 23, phase = 21),
    TwinkleStar(0.193f, 0.315f, period = 29, phase = 12),
    TwinkleStar(0.322f, 0.317f, period = 17, phase = 5),
    TwinkleStar(0.515f, 0.333f, period = 34, phase = 28),
    TwinkleStar(0.687f, 0.321f, period = 21, phase = 14),
    TwinkleStar(0.783f, 0.373f, period = 27, phase = 2),
    TwinkleStar(0.316f, 0.430f, period = 37, phase = 19),
    TwinkleStar(0.428f, 0.435f, period = 22, phase = 9),
    TwinkleStar(0.524f, 0.459f, period = 30, phase = 25),
    TwinkleStar(0.622f, 0.494f, period = 18, phase = 11),
    TwinkleStar(0.826f, 0.493f, period = 25, phase = 6),
)

/**
 * The clock the overlay runs on, as a [State] rather than a plain Int so that the caller
 * can read it inside a draw lambda.
 *
 * That distinction is the whole point of returning it this way: a state read in the draw
 * phase invalidates only the drawing, so seven ticks a second cost seven redraws of one
 * canvas and no recomposition at all. Reading it in a composable body instead would
 * recompose the scene — and re-run the sprite loading it does — at the same rate.
 */
@Composable
fun rememberTwinkle(): State<Int> {
    val tick = remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_MILLIS)
            // Wrapped at the least common multiple of nothing in particular — just a
            // bound large enough that every period divides into a long run evenly enough
            // to look random, and small enough never to overflow.
            tick.intValue = (tick.intValue + 1) % 27_720
        }
    }
    return tick
}

/**
 * Draws the overlay at [tick].
 *
 * The dot is sized as a fraction of the canvas width rather than in dp, so it matches the
 * painted stars around it at any display size — those are about a 160th of the banner's
 * width. It is snapped to whole pixels and floored at two, because a star is the smallest
 * thing on screen and a 2.4-pixel square lands as a grey smear rather than a white point.
 */
fun DrawScope.drawTwinkle(tick: Int) {
    val unit = max(2f, (size.width / 160f).roundToInt().toFloat())

    for (star in STARS) {
        val left = (star.x * size.width).roundToInt().toFloat()
        val top = (star.y * size.height).roundToInt().toFloat()
        val flaring = (tick + star.phase) % star.period < FLARE_TICKS

        if (flaring) {
            // A cross: the dot with one cell out in each direction. Two overlapping bars
            // rather than five squares, which is the same five cells in two draw calls.
            drawRect(AppleII.White, Offset(left - unit, top), Size(unit * 3, unit))
            drawRect(AppleII.White, Offset(left, top - unit), Size(unit, unit * 3))
        } else {
            drawRect(AppleII.White, Offset(left, top), Size(unit, unit))
        }
    }
}
