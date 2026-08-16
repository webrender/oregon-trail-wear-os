package com.oregontrail.wear.ui.art

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Art, decoded to about the size it will be drawn at.
 *
 * The art is authored far larger than any watch can show it — a landmark scene is 820x461
 * against a 384-pixel display — so every request says how big it needs the result to be
 * and the platform gets to decide how to honour that. On the watch the PNG decoder is
 * asked to throw the extra detail away *while* decoding, which is both far cheaper than
 * decoding and scaling afterwards and a better-looking result than letting the GPU do a
 * 10x minification at draw time.
 *
 * ### Why this is an interface and not just a function
 *
 * The two platforms answer the same question on very different schedules. Android can
 * open an asset and decode it synchronously inside composition. A browser cannot: there
 * is no synchronous read of anything at all, so the browser's loader answers "not yet",
 * starts a fetch, and the picture appears a frame or two later.
 *
 * [generation] is what makes "not yet" recoverable rather than permanent. Callers cache
 * their decoded art in a `remember` — [Scene] rebuilds a whole sprite list that way — and
 * a `remember` that captured a null would keep it forever. Keying that `remember` on
 * [generation] as well means the lookup is retried exactly when a new asset has landed,
 * and never otherwise. On the watch it is a constant and nothing re-runs.
 */
expect object ArtLoader {

    /**
     * Bumped whenever art that was not previously available becomes available. A Compose
     * snapshot state read, so a composable that reads it recomposes when it changes.
     *
     * Constant on the watch, where a load either succeeds immediately or is a missing
     * file. It varies only in the browser.
     */
    val generation: Int

    /**
     * The art called [name], decoded small enough that neither side much exceeds
     * [maxEdgePx] — or null if it is missing, unreadable, or (in the browser) simply not
     * here yet.
     *
     * Null rather than an exception because a missing picture must never take a screen
     * down with it: [PixelArtImage] draws nothing and [Scene] skips the sprite. The cost
     * is that a typo in [ArtNames] is invisible at runtime, which is what `ArtNamesTest`
     * exists to catch at build time instead.
     */
    fun loadOrNull(name: String, maxEdgePx: Int): ImageBitmap?

    /**
     * Decodes art ahead of the screen that needs it.
     *
     * [loadOrNull] does its work inside composition, so on the watch a cache miss inflates
     * a PNG in the middle of the frame that was supposed to draw it. A hunt cannot afford
     * that: it ticks every 40ms, and the first time each species crosses the scene it
     * would decode two new frames inside a tick — a visible stumble in the one part of the
     * game that is real-time and asks the player to lead a moving target.
     *
     * [maxEdgePx] has to be the size the screen will go on to ask for, or rather the same
     * bucket: the cache is keyed by the decoded size a request resolves to, so a prewarm
     * at the wrong size is not merely wasted, it is invisible — it fills the cache with
     * entries nothing ever looks up and leaves the stutter exactly where it was. Callers
     * get that size from [spriteEdgePx], which is what [Scene] itself uses.
     */
    fun prewarm(names: Iterable<String>, maxEdgePx: Int)
}
