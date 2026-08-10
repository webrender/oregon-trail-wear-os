package com.oregontrail.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

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
}

private val appleIIColors = Colors(
    primary = AppleII.Green,
    primaryVariant = AppleIIChrome.MutedGreen,
    secondary = AppleII.Blue,
    secondaryVariant = AppleII.Violet,
    background = AppleII.Black,
    surface = AppleIIChrome.DimGreen,
    error = AppleII.Orange,
    // Black on green rather than white: the original's inverse-video text did the same,
    // and white on this green is well under a readable contrast ratio.
    onPrimary = AppleII.Black,
    onSecondary = AppleII.Black,
    onBackground = AppleII.Green,
    onSurface = AppleII.Green,
    onSurfaceVariant = AppleII.White,
    onError = AppleII.Black,
)

@Composable
fun OregonTrailTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = appleIIColors,
        typography = appleIITypography(),
        content = content,
    )
}
