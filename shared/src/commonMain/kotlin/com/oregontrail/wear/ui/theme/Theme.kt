package com.oregontrail.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Chrome colours, derived from the hi-res palette in [AppleII].
 *
 * The art itself is limited to the six colours the hardware could show, and the UI stays
 * close to them so that chips and scenes read as one screen rather than as a game inside
 * an app. The one addition is [DimGreen]: a chip filled with full-strength green is far
 * too loud five-to-a-list, so list rows use green at low intensity over black, which is
 * roughly what a phosphor monitor did with a dim pixel anyway.
 */
object AppleIIChrome {
    /** Green at about a fifth intensity over black — a filled row that doesn't shout. */
    val DimGreen = Color(0xFF0C2C00)

    /** Green at about half intensity, for text that should recede. */
    val MutedGreen = Color(0xFF1E6B00)

    /** The dim green a disabled row and its label fall back to. */
    val DisabledGreen = Color(0xFF15380A)
}

/**
 * Wraps the app in whatever the platform's widgets need to look like themselves.
 *
 * On the watch that is Wear's `MaterialTheme`, because the chips and buttons underneath
 * are Wear's and read their colours out of it. In the browser there is no Material
 * underneath at all — the widgets are drawn from the same palette directly — so this is
 * little more than a place to hang the magnification the whole watch is scaled by.
 */
@Composable
expect fun OregonTrailTheme(content: @Composable () -> Unit)
