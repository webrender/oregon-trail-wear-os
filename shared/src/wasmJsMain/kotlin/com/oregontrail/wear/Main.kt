package com.oregontrail.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import com.oregontrail.wear.data.HighScoreRepository
import com.oregontrail.wear.data.LocalStorage
import com.oregontrail.wear.data.SaveRepository
import com.oregontrail.wear.platform.BuildInfo
import com.oregontrail.wear.ui.GameController
import com.oregontrail.wear.ui.OregonTrailApp
import com.oregontrail.wear.ui.theme.AppleII
import com.oregontrail.wear.ui.theme.LocalWatchScale
import com.oregontrail.wear.ui.theme.installShaston
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.fetch.Response

/**
 * The watch, in a browser.
 *
 * ### The screen is still 192dp square
 *
 * Not a "responsive" layout, and deliberately so. Every decision in this game is a
 * consequence of a 1.2" circular display: the copy budget, the three type sizes, the
 * scene aspect ratio, the choice to put a `Back` chip on screen because there is no back
 * gesture. Reflowing it into a browser window would keep none of that and would not be
 * the same game. So the browser gets the same 192dp round screen the watch has, magnified
 * by a whole number to fill the window.
 *
 * A *whole* number, because the type is pinned to the pixel grid — see
 * `ui/theme/Typography.kt`. Magnify by 2.5 and every glyph edge lands between pixels and
 * the font turns to grey mush, which is the one failure this art style cannot absorb. So
 * the watch grows in steps: 384 device pixels, then 768, then 1152. The window is
 * letterboxed around whichever step fits, which is why there is a bezel.
 *
 * ### Why the density is overridden
 *
 * The watch is density 2 — 192dp across a 384-pixel screen — and the whole layout is
 * written to that. Reporting the browser's own device pixel ratio instead would make the
 * screen some other number of dp across and rearrange everything. So the density is
 * *derived* from the magnification: at 2x the watch, density 4, and the screen is still
 * 192dp. Everything above this line then measures exactly as it does on the wrist, and
 * gets more pixels to be drawn with.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // `?debug` opens the same jump-to-any-landmark menu the debug APK has. There is no
    // separate debug build on the web, and gating it on a query parameter costs nothing:
    // the screen is a development tool, not a secret.
    BuildInfo.isDebug = window.location.search.contains("debug")

    val controller = GameController(
        repository = SaveRepository(LocalStorage("oregontrail.run")),
        // A key of its own, so that clearing a run — abandoning it, or finishing one and
        // starting the next — cannot take the high score table with it.
        scoreboard = HighScoreRepository(LocalStorage("oregontrail.scores")),
    )

    // The font is fetched before anything is drawn rather than alongside it. See
    // `installShaston`: this UI is measured against Shaston's advances, and a frame of
    // fallback would lay every screen out wrong and then visibly correct itself.
    CoroutineScope(Dispatchers.Main).launch {
        runCatching { installShaston(fetchBytes("fonts/shaston_320.ttf")) }
        // Compose appends its canvas to the body rather than replacing what is there, so
        // the placeholder has to be taken out by hand. Leaving it in is not merely untidy:
        // it keeps its height, which pushes the canvas down the page and puts the watch
        // off centre by exactly the height of one line of text.
        document.getElementById("loading")?.remove()
        ComposeViewport(document.body!!) { Watch(controller) }
    }
}

/**
 * The bezel, and the round screen inside it.
 *
 * The surround is not decoration. Without it the round clip reads as a circular *window*
 * onto a page, and the corners the bezel is supposed to be hiding come back as "why is
 * that cut off" — the same question the physical watch answers by being an object.
 */
@Composable
private fun Watch(controller: GameController) {
    val available = LocalWindowInfo.current.containerSize
    val shortest = minOf(available.width, available.height)

    // How many times the watch fits in the window, floored, never below one. A window too
    // small for even 1x gets 1x and clips, which is the right failure: the alternative is
    // a fractional scale that makes the text unreadable rather than merely cropped.
    val scale = (shortest / WATCH_PIXELS).coerceIn(1, MAX_SCALE)

    Box(
        modifier = Modifier.fillMaxSize().background(BEZEL),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalDensity provides Density(WATCH_DENSITY * scale),
            LocalWatchScale provides scale,
        ) {
            Box(
                modifier = Modifier
                    .size(WATCH_DP.dp)
                    // The clip is what makes the corners physically unreachable, exactly
                    // as the bezel does on the watch. Several screens rely on it — the
                    // full-bleed scenes are authored oversized on the assumption that
                    // their corners will be cropped into a circle.
                    .clip(CircleShape)
                    .background(AppleII.Black)
                    .border(width = 1.dp, color = BEZEL_EDGE, shape = CircleShape),
            ) {
                OregonTrailApp(controller)
            }
        }
    }
}

/** The Pixel Watch 2's screen: 384 device pixels across 192dp, so density 2. */
private const val WATCH_PIXELS = 384
private const val WATCH_DP = 192
private const val WATCH_DENSITY = 2f

/**
 * The largest the watch is drawn, and the limit is the art rather than the window.
 *
 * `scripts/prepare-art.py` caps each asset at the biggest size it is ever drawn on a
 * *watch*, plus half again of headroom — 820 pixels for a full-bleed backdrop. A backdrop
 * covers the display and overshoots it by about 1.14x, so 2x (a 768-pixel screen) asks for
 * 875 and is a 7% upscale nobody can see. 3x would ask for 1313 from the same 820 and be a
 * 60% upscale, which on art this soft-edged is very visible indeed.
 *
 * A 768-pixel watch in a 1400-pixel window looks like a watch, which is the intent. Raising
 * this means regenerating the art from the masters at a larger cap, and paying for those
 * pixels on every download.
 */
private const val MAX_SCALE = 2

private val BEZEL = Color(0xFF0A0A0A)
private val BEZEL_EDGE = Color(0xFF1E1E1E)

private suspend fun fetchBytes(path: String): ByteArray {
    val response = window.fetch(path).await<Response>()
    val buffer = response.arrayBuffer().await<ArrayBuffer>()
    val view = Int8Array(buffer, 0, buffer.byteLength)
    return ByteArray(view.length) { view[it] }
}
