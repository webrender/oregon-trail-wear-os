package com.oregontrail.wear.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The coordinate grid every sprite position in the game is expressed in.
 *
 * Positions are in *scene* units, not device pixels or dp, so a wagon placed on the
 * ground stays on the ground at any size or density. That matters more than it sounds:
 * the watch reports 454x454 pixels at density 2, so laying art out in dp would mean every
 * position was a fraction of a unit and the whole composition would drift.
 *
 * The 2:1 shape is what makes a scene fit a round display. The visible area is a circle
 * of radius 227 within the frame, so a full-width band is only safe where the circle is
 * widest — across the middle. A taller box would push art into the corners, where the
 * bezel physically hides it.
 */
const val SCENE_WIDTH = 128
const val SCENE_HEIGHT = 64

/**
 * The shape of a scene that bleeds to the true screen edges instead of stopping short of
 * the bezel — see `SceneScrollColumn` in the `ui.components` package, which is what
 * renders one. Taller than [SCENE_WIDTH] x [SCENE_HEIGHT] because it has to reach the top
 * of the display as well as both sides; no sprites are placed on one, so it is an aspect
 * ratio rather than a coordinate grid.
 */
const val BLEED_SCENE_WIDTH = 150
const val BLEED_SCENE_HEIGHT = 96

/**
 * A sprite occupying a box in scene coordinates, given by its top-left corner.
 *
 * The box is the sprite's *footprint*, not the shape of the file behind it. Art is
 * authored at whatever proportions suit the subject — the ox team is nearly three times
 * as wide as it is tall, a standing deer slightly taller than it is wide — and the
 * artwork is fitted inside the box without distorting it, sitting on the box's bottom
 * edge and centred across it. Anchoring to the bottom is what keeps everything on the
 * same ground line: a sprite that comes back from the artist a little shorter than its
 * box stands on the ground rather than hovering above it.
 *
 * [flip] mirrors the art horizontally in place, for a sprite authored facing one way that
 * needs to face the other — a run cycle drawn facing right, used for an animal running
 * left, for instance.
 */
data class Sprite(
    val name: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val flip: Boolean = false,
)

/**
 * A backdrop with sprites composited on top, laid out on the [SCENE_WIDTH] x
 * [SCENE_HEIGHT] grid.
 *
 * The backdrop always *covers*: it is scaled to fill the canvas and whatever overshoots
 * is cropped. Backdrops are wide landscapes with margin at every edge, and the alternative
 * — fitting one inside a canvas of a different shape — rings the scene with black bars,
 * which is the opposite of what a full-bleed scene is for. Sprite coordinates are mapped
 * from the grid to the whole canvas, so cropping the backdrop never crops a sprite.
 */
@Composable
fun Scene(
    background: String?,
    modifier: Modifier = Modifier.fillMaxWidth().aspectRatio(2f),
    sprites: List<Sprite> = emptyList(),
    /**
     * Reports a tap in scene coordinates rather than device pixels — see [SCENE_WIDTH].
     * Kept here rather than pushed onto the caller so that the maths positioning sprites
     * is the one place that also has to invert it.
     */
    onTap: ((x: Int, y: Int) -> Unit)? = null,
) {
    val context = LocalContext.current

    // The loader needs a target size before layout has measured anything, so this is the
    // widest the scene could possibly be drawn: a scene never exceeds the display width,
    // and the grid maps across that width. An over-estimate only costs a little memory;
    // an under-estimate would upscale the art, so the bound errs high.
    val displayWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.roundToPx()
    }
    val maxScale = displayWidthPx.toFloat() / SCENE_WIDTH

    val backdrop = remember(background, displayWidthPx) {
        background?.let { ArtLoader.loadOrNull(context, it, backdropEdgePx(displayWidthPx)) }
    }
    val loaded = remember(sprites, maxScale) {
        sprites.mapNotNull { sprite ->
            val edge = (max(sprite.width, sprite.height) * maxScale).roundToInt()
            ArtLoader.loadOrNull(context, sprite.name, edge)?.let { it to sprite }
        }
    }

    // rememberUpdatedState so the gesture detector below can be keyed on whether a
    // handler exists at all, rather than on the handler itself. A hunt session's onTap
    // closes over state that changes every tick (~40ms), so keying pointerInput on the
    // lambda instance — as this used to — tore down and restarted the gesture-detection
    // coroutine that often, which is often enough that a tap's down and up rarely landed
    // in the same coroutine and the shot was silently dropped almost every time.
    val currentOnTap = rememberUpdatedState(onTap)
    val tapModifier = if (onTap != null) {
        Modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                val scale = size.width.toFloat() / SCENE_WIDTH
                currentOnTap.value?.invoke(
                    (offset.x / scale).toInt(),
                    (offset.y / scale).toInt(),
                )
            }
        }
    } else {
        Modifier
    }

    // clipToBounds because a covering backdrop draws past every edge of the Canvas, and a
    // DrawScope is not clipped to its layout bounds by default — without this the
    // overshoot paints over whatever the parent laid out underneath, which for a
    // SceneScrollColumn is the screen's title.
    Canvas(modifier = modifier.clipToBounds().then(tapModifier)) {
        val scale = size.width / SCENE_WIDTH

        if (backdrop != null) drawCovering(backdrop)
        for ((image, sprite) in loaded) drawInBox(image, sprite, scale)
    }
}

/**
 * How big a backdrop needs decoding for a [displayWidthPx]-wide display.
 *
 * A covering backdrop is scaled until *both* sides fill the canvas, so the side that
 * overshoots is drawn larger than the display. Half again is comfortably past the worst
 * case — a 16:9 backdrop covering the tallest scene the game asks for overshoots by
 * about a fifth.
 */
private fun backdropEdgePx(displayWidthPx: Int): Int = displayWidthPx * 3 / 2

/**
 * Draws [image] scaled to fill the whole [DrawScope], cropping whichever side overshoots.
 */
private fun DrawScope.drawCovering(image: ImageBitmap) {
    val scale = max(size.width / image.width, size.height / image.height)
    val width = image.width * scale
    val height = image.height * scale
    draw(image, (size.width - width) / 2f, (size.height - height) / 2f, width, height)
}

/**
 * Draws [image] inside [sprite]'s box — as large as fits without distortion, sitting on
 * the box's bottom edge and centred across it. See [Sprite].
 */
private fun DrawScope.drawInBox(image: ImageBitmap, sprite: Sprite, scale: Float) {
    val boxWidth = sprite.width * scale
    val boxHeight = sprite.height * scale
    val fit = min(boxWidth / image.width, boxHeight / image.height)
    val width = image.width * fit
    val height = image.height * fit
    val left = sprite.x * scale + (boxWidth - width) / 2f
    val top = sprite.y * scale + (boxHeight - height)

    if (sprite.flip) {
        scale(scaleX = -1f, scaleY = 1f, pivot = Offset(left + width / 2f, top + height / 2f)) {
            draw(image, left, top, width, height)
        }
    } else {
        draw(image, left, top, width, height)
    }
}

/**
 * Draws [image] into a rectangle, rounded to whole device pixels.
 *
 * [FilterQuality.Low] — bilinear — because the art arrives much larger than the space it
 * goes in and has already been decoded down close to its final size by [ArtLoader]. The
 * nearest-neighbour sampling this used to need, back when scenes were 128x64 files
 * magnified by whole numbers, would now alias the remaining fractional minification into
 * ragged edges that crawl as a sprite moves.
 */
private fun DrawScope.draw(image: ImageBitmap, left: Float, top: Float, width: Float, height: Float) {
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        dstSize = IntSize(width.roundToInt(), height.roundToInt()),
        filterQuality = FilterQuality.Low,
    )
}
