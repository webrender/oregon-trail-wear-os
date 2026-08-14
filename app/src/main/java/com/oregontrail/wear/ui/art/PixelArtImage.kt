package com.oregontrail.wear.ui.art

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Loads art from the APK's assets, downsampled to the size it will actually be drawn at.
 *
 * The art is authored far larger than any watch can show it — a landmark scene is
 * 1672x941, a supplies icon 1018x848 — and decoding those at full size would cost about
 * 420MB of bitmap for the seventy-odd assets in the game. So every load says how big it
 * needs the result to be, and [BitmapFactory] is asked to throw away the detail beyond
 * that while decoding, which is both far cheaper than decoding and scaling afterwards and
 * a better-looking result than letting the GPU do a 10x minification at draw time.
 *
 * The cache is keyed by name *and* the sample size that request resolved to, because the
 * same asset legitimately has two lives: `icon_wagon` is a 32px chip icon in one place
 * and `wagon_ox_1` a 200px sprite in another. It is bounded, because a scene decoded for
 * a 454px screen is still 1.5MB and there are thirty-two of them; landmarks are visited
 * one at a time, so the least-recently-used ones are worth dropping.
 */
object ArtLoader {

    /**
     * How much decoded art to keep, in bytes.
     *
     * Enough for every sprite and icon in the game several times over plus a handful of
     * full scenes, and small enough to be an unremarkable slice of a watch's heap.
     */
    private const val CACHE_BYTES = 16 * 1024 * 1024

    private val cache = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    /**
     * Each asset's longest side on disk, so working out the sample size doesn't reopen
     * the file.
     *
     * Worth its own map because loads are not rare: a hunt rebuilds its sprite list every
     * 40ms tick and asks for each sprite again, and reading a PNG header off the asset
     * manager twenty-five times a second per sprite is real work for an answer that
     * cannot change. Unbounded, but it holds one integer per asset.
     */
    private val sourceEdges = mutableMapOf<String, Int>()

    /**
     * Loads `assets/art/<name>.png`, decoded small enough that neither side much exceeds
     * [maxEdgePx]. Throws if the asset is missing or unreadable.
     */
    fun load(context: Context, name: String, maxEdgePx: Int): ImageBitmap {
        val path = "art/$name.png"
        val sourceEdge = sourceEdges.getOrPut(name) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
            max(bounds.outWidth, bounds.outHeight)
        }

        val sample = sampleSizeFor(sourceEdge, maxEdgePx)
        val key = "$name@$sample"
        cache.get(key)?.let { return it }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("art/$name.png could not be decoded")
        return bitmap.asImageBitmap().also { cache.put(key, it) }
    }

    /** Loads art, returning null rather than throwing if it isn't there yet. */
    fun loadOrNull(context: Context, name: String, maxEdgePx: Int): ImageBitmap? = try {
        load(context, name, maxEdgePx)
    } catch (e: Exception) {
        null
    }
}

/**
 * The `inSampleSize` that gets a [sourceEdge]-pixel image down to about [maxEdgePx].
 *
 * [BitmapFactory] only honours powers of two, and rounds a non-power-of-two *down* to
 * one, so this halves rather than dividing. It stops at the last size that is still at
 * least as big as asked for: undershooting would mean scaling the art back up at draw
 * time, which is the one thing worth avoiding — a downscale of pixel art loses detail
 * gracefully, an upscale of an already-downscaled copy just looks soft.
 */
internal fun sampleSizeFor(sourceEdge: Int, maxEdgePx: Int): Int {
    if (sourceEdge <= 0 || maxEdgePx <= 0) return 1
    var sample = 1
    while (sourceEdge / (sample * 2) >= maxEdgePx) sample *= 2
    return sample
}

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
    val context = LocalContext.current
    val maxEdgePx = with(LocalDensity.current) { size.roundToPx() }
    val image = remember(name, maxEdgePx) { ArtLoader.loadOrNull(context, name, maxEdgePx) } ?: return

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
