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
import com.oregontrail.wear.ui.theme.watchScaleFor
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
 * to fill the window.
 *
 * ### The magnification is a whole number where one fits, and otherwise it is not
 *
 * A whole number is what the type wants: it is pinned to the pixel grid — see
 * `ui/theme/Typography.kt` — and at 2.5x a glyph's advances land between pixels, where the
 * rasteriser antialiases them.
 *
 * Whole numbers *only* was the original rule, and it was wrong for a reason that does not
 * show up on a desktop: the steps are 384 device pixels apart, and a window is very often
 * most of the way to the next one. A 360-CSS-pixel phone at device pixel ratio 2 has 720
 * pixels to play with, takes the 1x step, and draws a watch across half its width with the
 * rest of the page black — which is the whole of the "it's tiny on my phone" complaint.
 * The same arithmetic strands a 1964-pixel laptop window at 768.
 *
 * So the whole number is *preferred* rather than required: it is taken when it leaves no
 * more than a tenth of the shorter side unused, and otherwise the watch is magnified
 * to fit exactly. Two things make the fraction cheap when it is reached for. The cell size
 * is rounded back to whole pixels by `cellStyle`, so the glyphs themselves stay on the
 * grid even when their origins do not. And the windows that need a fraction are the small
 * ones, which in 2026 are overwhelmingly phones at device pixel ratio 2 or 3, where half a
 * device pixel is a sixth of a CSS pixel and invisible. A desktop at ratio 1 — where the
 * grid is visible — has a window big enough that a whole number is nearly always within
 * the slack.
 *
 * ### What filling the window costs
 *
 * The art, above about 2x. `scripts/prepare-art.py` caps each asset at the largest size it
 * is ever drawn on a *watch* plus half again of headroom — 820 pixels for a full-bleed
 * backdrop, which covers the display and overshoots it by about 1.14x. So a 768-pixel
 * watch asks for 875 and is a 7% upscale nobody can see, and a 1920-pixel one asks for
 * 2189 and is soft. There used to be a cap here holding the magnification at 2x for
 * exactly that reason, and it was the wrong trade: a sharp watch occupying a third of a
 * laptop screen is not what anyone opens the page to look at. The type — which is a font
 * rather than a bitmap, and is what the game is mostly made of — stays crisp all the way
 * up. Regenerating the art from the masters at a larger cap would fix the rest of it, at
 * the price of those pixels on every download.
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
    val scale = watchScaleFor(shortest)

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

/** The Pixel Watch 2's screen is 192dp across 384 device pixels, so density 2. */
private const val WATCH_DP = 192
private const val WATCH_DENSITY = 2f

private val BEZEL = Color(0xFF0A0A0A)
private val BEZEL_EDGE = Color(0xFF1E1E1E)

private suspend fun fetchBytes(path: String): ByteArray {
    val response = window.fetch(path).await<Response>()
    val buffer = response.arrayBuffer().await<ArrayBuffer>()
    val view = Int8Array(buffer, 0, buffer.byteLength)
    return ByteArray(view.length) { view[it] }
}
