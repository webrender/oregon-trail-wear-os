package com.oregontrail.wear.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws a single piece of art, scaled to fit a [size] x [size] box.
 *
 * The size is a parameter rather than something read off the [modifier] because the
 * loader needs it *before* layout runs — see [ArtLoader]. Callers that want a
 * non-square footprint should reach for [Scene] instead.
 */
@Composable
fun PixelArtImage(
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val maxEdgePx = with(LocalDensity.current) { size.roundToPx() }
    // ArtLoader.generation is in the key so that art which was not ready on the first
    // composition is picked up when it arrives — see [ArtLoader]. It never changes on the
    // watch, where a load either succeeds or the file is missing.
    val image = remember(name, maxEdgePx, ArtLoader.generation) {
        ArtLoader.loadOrNull(name, maxEdgePx)
    } ?: return

    Canvas(modifier = modifier.size(size)) {
        drawArt(image)
    }
}

/**
 * Draws [image] centred in the current [DrawScope], as large as it can be without
 * distorting its shape or spilling out.
 *
 * The art no longer arrives on a tiny hand-placed grid that has to be magnified by whole
 * numbers to stay crisp; it arrives far larger than the space it goes in, already
 * downsampled close to the target by the loader. So the remaining fraction is drawn with
 * [FilterQuality.Low] — bilinear. Nearest-neighbour here would alias the leftover
 * fractional minification into ragged, shimmering edges.
 */
fun DrawScope.drawArt(image: ImageBitmap) {
    val scale = min(size.width / image.width, size.height / image.height)
    val width = (image.width * scale).roundToInt()
    val height = (image.height * scale).roundToInt()

    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(
            ((size.width - width) / 2f).roundToInt(),
            ((size.height - height) / 2f).roundToInt(),
        ),
        dstSize = IntSize(width, height),
        filterQuality = FilterQuality.Low,
    )
}
