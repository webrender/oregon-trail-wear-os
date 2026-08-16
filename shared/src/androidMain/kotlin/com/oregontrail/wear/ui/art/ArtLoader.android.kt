package com.oregontrail.wear.ui.art

import android.content.Context
import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * Loads art from the APK's assets, downsampled to the size it will actually be drawn at.
 *
 * Decoding the set at full size would cost about 60MB of bitmap for the ninety-odd assets
 * in the game, so every load says how big it needs the result to be and [BitmapFactory] is
 * asked to throw away the detail beyond that while decoding.
 *
 * The cache is keyed by name *and* the sample size that request resolved to, because the
 * same asset legitimately has two lives: `icon_wagon` is a 32px chip icon in one place
 * and `wagon_ox_1` a 200px sprite in another. It is bounded, because a scene decoded for
 * a 454px screen is still 1.5MB and there are thirty-two of them; landmarks are visited
 * one at a time, so the least-recently-used ones are worth dropping.
 */
actual object ArtLoader {

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
     *
     * Concurrent because [prewarm] loads from a background thread while composition loads
     * from the main one. A plain `HashMap` written from two threads can corrupt its own
     * table and spin forever on a later read — a hang, not an exception, and one that
     * would surface as a frozen watch long after the code that caused it.
     */
    private val sourceEdges = ConcurrentHashMap<String, Int>()

    private lateinit var assets: AssetManager

    /**
     * Hands the loader the asset manager it reads through.
     *
     * Called once, from `MainActivity`. This is the seam that used to be a `Context`
     * threaded through every call — which worked while a watch was the only target, and
     * stopped working the moment [loadOrNull] had to be declared in common code that has
     * never heard of one. An application context, so holding it costs nothing and leaks
     * nothing.
     */
    fun install(context: Context) {
        assets = context.applicationContext.assets
    }

    /**
     * Always zero: this loader either has the art or the file is not in the APK, and
     * neither answer changes while the app is running. See the common declaration for
     * why the browser's does change.
     */
    actual val generation: Int get() = 0

    /**
     * Loads `assets/art/<name>.png`, decoded small enough that neither side much exceeds
     * [maxEdgePx]. Throws if the asset is missing or unreadable.
     */
    fun load(name: String, maxEdgePx: Int): ImageBitmap {
        val path = "art/$name.png"
        val sourceEdge = sourceEdges.getOrPut(name) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
            max(bounds.outWidth, bounds.outHeight)
        }

        val sample = sampleSizeFor(sourceEdge, maxEdgePx)
        val key = "$name@$sample"
        cache.get(key)?.let { return it }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("art/$name.png could not be decoded")
        return bitmap.asImageBitmap().also { cache.put(key, it) }
    }

    actual fun loadOrNull(name: String, maxEdgePx: Int): ImageBitmap? = try {
        load(name, maxEdgePx)
    } catch (e: Exception) {
        null
    }

    /** Decodes art ahead of the screen that needs it, for calling off the main thread. */
    actual fun prewarm(names: Iterable<String>, maxEdgePx: Int) {
        for (name in names) loadOrNull(name, maxEdgePx)
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
