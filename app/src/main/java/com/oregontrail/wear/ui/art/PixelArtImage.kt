package com.oregontrail.wear.ui.art

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.oregontrail.wear.art.PixelArt
import com.oregontrail.wear.art.PixelArtParser
import kotlin.math.max
import kotlin.math.min

/**
 * Loads `.pix` art from the APK's assets, cached by name.
 *
 * Art is small and read repeatedly (every frame of the travel animation), so parsing is
 * done once per asset and the resulting bitmap kept. There are on the order of seventy
 * assets, all tiny, so the cache is never evicted.
 */
object ArtLoader {

    private val cache = mutableMapOf<String, ImageBitmap>()

    /** Loads `assets/art/<name>.pix`. Throws if the asset is missing or malformed. */
    fun load(context: Context, name: String): ImageBitmap = cache.getOrPut(name) {
        val source = context.assets.open("art/$name.pix").bufferedReader().use { it.readText() }
        PixelArtParser.parse(source, fallbackName = name).toImageBitmap()
    }

    /** Loads art, returning null rather than throwing if it isn't there yet. */
    fun loadOrNull(context: Context, name: String): ImageBitmap? = try {
        load(context, name)
    } catch (e: Exception) {
        null
    }
}

fun PixelArt.toImageBitmap(): ImageBitmap =
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

/**
 * Draws pixel art at a whole-number scale with no smoothing.
 *
 * Two things matter for this to look right, and both are easy to get wrong:
 *
 * 1. **[FilterQuality.None]**. The default is bilinear, which blurs every edge and
 *    turns hand-placed pixels into mush — the exact opposite of the intended look.
 * 2. **Integer scaling.** A fractional scale makes some source pixels two device pixels
 *    wide and others three, so a drawn-straight line comes out visibly uneven. The
 *    scale is floored to a whole number and the result centred, accepting a small
 *    border rather than distorting the grid.
 */
@Composable
fun PixelArtImage(
    name: String,
    modifier: Modifier = Modifier,
    maxScale: Int = Int.MAX_VALUE,
) {
    val context = LocalContext.current
    val image = remember(name) { ArtLoader.loadOrNull(context, name) } ?: return

    Canvas(modifier = modifier) {
        drawPixelArt(image, maxScale)
    }
}

/**
 * Draws [image] centred in the current [DrawScope] at the largest whole-number scale
 * that fits.
 */
fun DrawScope.drawPixelArt(
    image: ImageBitmap,
    maxScale: Int = Int.MAX_VALUE,
) {
    val scale = fittingScale(image.width, image.height, size.width, size.height, maxScale)
    val drawnWidth = image.width * scale
    val drawnHeight = image.height * scale
    val left = ((size.width - drawnWidth) / 2f).toInt()
    val top = ((size.height - drawnHeight) / 2f).toInt()

    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(left, top),
        dstSize = IntSize(drawnWidth, drawnHeight),
        filterQuality = FilterQuality.None,
    )
}

/**
 * The largest whole-number scale at which [artWidth] x [artHeight] fits inside
 * [availableWidth] x [availableHeight]. Never returns less than 1 — art too big for the
 * space is clipped rather than shrunk, since a sub-1 scale would drop pixel rows
 * entirely and mangle the image.
 */
internal fun fittingScale(
    artWidth: Int,
    artHeight: Int,
    availableWidth: Float,
    availableHeight: Float,
    maxScale: Int = Int.MAX_VALUE,
): Int {
    if (artWidth <= 0 || artHeight <= 0) return 1
    val byWidth = (availableWidth / artWidth).toInt()
    val byHeight = (availableHeight / artHeight).toInt()
    return max(1, min(min(byWidth, byHeight), maxScale))
}
