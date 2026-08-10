package com.oregontrail.wear.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min

/** Scene art is authored on a fixed 128x64 grid — see docs/art-brief.md. */
const val SCENE_WIDTH = 128
const val SCENE_HEIGHT = 64

/**
 * A sprite placed by its top-left corner, in scene coordinates rather than pixels.
 *
 * [flip] mirrors the art horizontally in place, for a sprite authored facing one way
 * that needs to face the other — a walk cycle drawn facing right, used for a character
 * walking left, for instance.
 */
data class Sprite(val name: String, val x: Int, val y: Int, val flip: Boolean = false)

/**
 * A 128x64 scene with sprites composited on top, drawn at a whole-number scale.
 *
 * Positioning is expressed in *scene* coordinates, not device pixels or dp, so a sprite
 * placed on the horizon stays on the horizon at any scale or density. That matters more
 * than it sounds: the watch reports 454x454 pixels at density 2, so laying art out in dp
 * would mean every position was a fraction of a source pixel and the whole grid would
 * drift.
 *
 * The default 2:1 aspect ratio is what makes the scene fit the round display. The visible
 * area is a circle of radius 227 within the frame, so a full-width band is only safe
 * where the circle is widest — across the middle. A taller box would push art into the
 * corners, where the bezel physically hides it.
 */
@Composable
fun Scene(
    background: String?,
    modifier: Modifier = Modifier.fillMaxWidth().aspectRatio(2f),
    sprites: List<Sprite> = emptyList(),
    /**
     * Reports a tap in scene coordinates rather than device pixels — see the class
     * doc. Kept as an [ArtLoader]-adjacent concern of this file rather than pushed onto
     * the caller, so the origin/scale maths that positions sprites is the one place that
     * also has to invert it.
     */
    onTap: ((x: Int, y: Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val backdrop = remember(background) {
        background?.let { ArtLoader.loadOrNull(context, it) }
    }
    val loaded = remember(sprites) {
        sprites.mapNotNull { sprite ->
            ArtLoader.loadOrNull(context, sprite.name)?.let { it to sprite }
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
                val scale = fittingScale(
                    SCENE_WIDTH, SCENE_HEIGHT, size.width.toFloat(), size.height.toFloat(),
                )
                val originX = (size.width - SCENE_WIDTH * scale) / 2f
                val originY = (size.height - SCENE_HEIGHT * scale) / 2f
                currentOnTap.value?.invoke(
                    ((offset.x - originX) / scale).toInt(),
                    ((offset.y - originY) / scale).toInt(),
                )
            }
        }
    } else {
        Modifier
    }

    Canvas(modifier = modifier.then(tapModifier)) {
        val scale = fittingScale(SCENE_WIDTH, SCENE_HEIGHT, size.width, size.height)
        val originX = ((size.width - SCENE_WIDTH * scale) / 2f).toInt()
        val originY = ((size.height - SCENE_HEIGHT * scale) / 2f).toInt()

        if (backdrop != null) drawAt(backdrop, originX, originY, scale)
        for ((image, sprite) in loaded) {
            drawAt(image, originX + sprite.x * scale, originY + sprite.y * scale, scale, sprite.flip)
        }
    }
}

/**
 * Draws [image] with its top-left at [left], [top], magnified [scale] times.
 *
 * [FilterQuality.None] is not optional — the default bilinear filter blurs every edge and
 * turns hand-placed pixels to mush.
 */
internal fun DrawScope.drawAt(
    image: ImageBitmap,
    left: Int,
    top: Int,
    scale: Int,
    flip: Boolean = false,
) {
    val dstOffset = IntOffset(left, top)
    val dstSize = IntSize(image.width * scale, image.height * scale)

    fun draw() = drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = dstOffset,
        dstSize = dstSize,
        filterQuality = FilterQuality.None,
    )

    if (flip) {
        val pivot = Offset(left + dstSize.width / 2f, top + dstSize.height / 2f)
        scale(scaleX = -1f, scaleY = 1f, pivot = pivot) { draw() }
    } else {
        draw()
    }
}

/**
 * The largest whole-number scale at which the scene fits the space it is given.
 *
 * Shares its reasoning with [fittingScale] in PixelArtImage.kt: a fractional scale makes
 * some source pixels two device pixels wide and others three, so a straight line comes
 * out visibly uneven.
 */
private fun fittingScale(
    artWidth: Int,
    artHeight: Int,
    availableWidth: Float,
    availableHeight: Float,
): Int = max(
    1,
    min((availableWidth / artWidth).toInt(), (availableHeight / artHeight).toInt()),
)
