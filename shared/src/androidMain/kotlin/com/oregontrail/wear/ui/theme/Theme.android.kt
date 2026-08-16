package com.oregontrail.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Typography

/**
 * Shaston, read straight out of the assets rather than out of `res/font`.
 *
 * An asset and not a resource so that the watch and the browser load the *same file* —
 * the browser fetches `fonts/shaston_320.ttf` over HTTP, and the art staging task copies
 * the assets directory wholesale to serve it. A second copy under `res/font` would be a
 * second thing to keep byte-identical to upstream, which the licence requires and no
 * build step enforces. Being an asset also keeps it clear of resource shrinking, which
 * the licence's no-modification clause forbids anyway.
 */
@Composable
actual fun shastonFontFamily(): FontFamily {
    val assets = LocalContext.current.assets
    return remember(assets) { FontFamily(Font("fonts/shaston_320.ttf", assets)) }
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

/**
 * Wear's `MaterialTheme`, carrying the palette and the type scale.
 *
 * The type scale is handed to Wear as well as being available directly through
 * [AppleIIType], and both are needed: the screens read their styles from [AppleIIType],
 * but Wear's own widgets — a `CompactChip`'s label, a `Chip`'s disabled state — reach into
 * `MaterialTheme.typography` for defaults the caller never passes. Feeding it the same
 * scale is what keeps those from arriving in Material's stock sans-serif.
 */
@Composable
actual fun OregonTrailTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = appleIIColors,
        typography = Typography(
            display1 = cellStyle(4),
            display2 = cellStyle(4),
            display3 = cellStyle(4),
            title1 = cellStyle(3),
            title2 = cellStyle(3),
            title3 = cellStyle(3),
            body1 = cellStyle(3),
            body2 = cellStyle(2),
            button = cellStyle(3),
            caption1 = cellStyle(2),
            caption2 = cellStyle(2),
            caption3 = cellStyle(2),
        ),
        content = content,
    )
}

/**
 * Without this the top pixel row of a line gets shaved off inside a Chip — see the common
 * declaration. It is Android-only because the constructor that turns font padding off is.
 */
internal actual val cellPlatformStyle: androidx.compose.ui.text.PlatformTextStyle? =
    androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
