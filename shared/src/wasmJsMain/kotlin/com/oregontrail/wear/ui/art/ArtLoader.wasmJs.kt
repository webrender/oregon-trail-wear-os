package com.oregontrail.wear.ui.art

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.fetch.Response
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Loads art by fetching it over HTTP, decoded to about the size it will be drawn at.
 *
 * ### Why the art is fetched rather than bundled
 *
 * The set is 20MB, and a browser that had to download all of it before the title screen
 * appeared would take most of a minute on anything but a fast connection — to show one
 * picture. Fetching each asset when a screen first asks for it means the title screen
 * costs one banner, and the rest arrives over the course of a journey that takes half an
 * hour anyway. The browser's HTTP cache then makes the second visit free, which is a
 * better cache than anything worth writing here.
 *
 * The cost is that art can be *late*, which the watch's loader never is — hence
 * [generation]. A screen composed before its picture has arrived draws without it and is
 * recomposed when it lands, so a slow connection degrades to a scene that fills in rather
 * than to a blank wait.
 *
 * ### Why there is no eviction
 *
 * The watch's loader keeps a 16MB LRU because a watch's heap is small and a landmark
 * scene is 1.5MB decoded. A browser tab has no such pressure, and the whole decoded set
 * at the sizes actually requested is well under what a single photograph on a news site
 * costs. Dropping an asset would only mean fetching and decoding it again.
 */
actual object ArtLoader {

    /**
     * The decoded art, and the sizes it was decoded at. Keyed exactly as the watch's
     * cache is — name plus the resolved size — because the same asset is legitimately
     * requested at two very different sizes by two different screens.
     */
    private val cache = mutableMapOf<String, ImageBitmap>()

    /** Requests already in flight, so a scene asking for the same sprite on each of
     *  twenty-five ticks a second starts one fetch rather than twenty-five. */
    private val inFlight = mutableSetOf<String>()

    /** Assets the server does not have. Remembered so a typo costs one 404, not one per
     *  frame for the rest of the run. */
    private val missing = mutableSetOf<String>()

    private val generationState = mutableIntStateOf(0)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    actual val generation: Int get() = generationState.value

    actual fun loadOrNull(name: String, maxEdgePx: Int): ImageBitmap? {
        val bucket = bucketFor(maxEdgePx)
        val key = "$name@$bucket"
        cache[key]?.let { return it }
        if (key in inFlight || name in missing) return null

        inFlight += key
        scope.launch {
            val decoded = fetchAndDecode(name, bucket)
            inFlight -= key
            if (decoded == null) {
                missing += name
            } else {
                cache[key] = decoded
                // Read by every caller's `remember` key, so this is what puts the newly
                // arrived picture on screen.
                generationState.value++
            }
        }
        return null
    }

    /**
     * Starts the fetches a screen is about to need. Fire-and-forget: [loadOrNull] already
     * launches its own, so all this does is start them earlier — which is the whole point
     * on a real-time screen, where the first frame that needs a sprite should not be the
     * first frame that asks for it.
     */
    actual fun prewarm(names: Iterable<String>, maxEdgePx: Int) {
        for (name in names) loadOrNull(name, maxEdgePx)
    }

    private suspend fun fetchAndDecode(name: String, maxEdgePx: Int): ImageBitmap? = try {
        val response = window.fetch("art/$name.png").await<Response>()
        if (!response.ok) {
            null
        } else {
            val buffer = response.arrayBuffer().await<ArrayBuffer>()
            decode(buffer.toByteArray(), maxEdgePx)
        }
    } catch (e: Throwable) {
        // A failed fetch draws nothing, exactly as a missing asset does on the watch.
        null
    }

    /**
     * Decodes a PNG, scaled down so that neither side much exceeds [maxEdgePx].
     *
     * Skia decodes at the file's own size — there is no `inSampleSize` equivalent to ask
     * it to throw away detail on the way in, as the watch's [android.graphics.BitmapFactory]
     * does. So the full-size image is decoded, drawn once into a raster surface of the
     * target size, and released. That costs one extra copy per asset, once, and it buys
     * the same thing the watch gets: the GPU is never asked to minify a 820x461 landmark
     * by 10x on every frame that shows it, which is where the shimmering comes from.
     */
    private fun decode(bytes: ByteArray, maxEdgePx: Int): ImageBitmap {
        val image = Image.makeFromEncoded(bytes)
        val sourceEdge = max(image.width, image.height)
        if (sourceEdge <= maxEdgePx) return image.toComposeImageBitmap()

        val scale = maxEdgePx.toFloat() / sourceEdge
        val width = max(1, (image.width * scale).roundToInt())
        val height = max(1, (image.height * scale).roundToInt())

        val surface = Surface.makeRasterN32Premul(width, height)
        surface.canvas.drawImageRect(image, Rect.makeWH(width.toFloat(), height.toFloat()))
        return surface.makeImageSnapshot().toComposeImageBitmap()
    }

    /**
     * Rounds a requested size up to the next power of two.
     *
     * The watch's loader ends up on power-of-two sizes whether it wants to or not, because
     * `inSampleSize` only halves — and that turns out to be the property worth keeping.
     * Sizes here come from layout, so the same sprite asked for at 47px on one screen and
     * 52px on another would otherwise be fetched, decoded, and cached twice for a
     * difference no one can see. Bucketing collapses those to one entry.
     */
    private fun bucketFor(maxEdgePx: Int): Int {
        if (maxEdgePx <= 1) return 1
        var bucket = 1
        while (bucket < maxEdgePx) bucket *= 2
        return bucket
    }
}

/**
 * Copies a fetched [ArrayBuffer] into Kotlin's own heap.
 *
 * Element by element, which looks alarming next to a 500KB PNG and is what every
 * Kotlin/Wasm binding for this does, including Compose's own resource reader: the two
 * heaps are separate address spaces and there is no bulk primitive exposed to Kotlin to
 * move bytes between them. It costs a few milliseconds per asset, off any frame that
 * matters, because this only ever runs inside a fetch's continuation.
 */
private fun ArrayBuffer.toByteArray(): ByteArray {
    val view = Int8Array(this, 0, byteLength)
    return ByteArray(view.length) { view[it] }
}
