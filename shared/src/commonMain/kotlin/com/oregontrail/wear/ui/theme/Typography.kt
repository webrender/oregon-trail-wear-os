package com.oregontrail.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Shaston — the Apple IIGS (GS/OS) system font, by Kreative Software, vendored verbatim
 * under the Kreative Software Relay Fonts Free Use License, a copy of which ships
 * alongside the art at `assets/licenses/`. That licence permits embedding and
 * redistribution free of charge but **forbids modification**, so the file must stay
 * byte-identical to upstream — do not run it through a font subsetter or optimiser, and
 * do not enable resource shrinking on it.
 *
 * This is the `Shaston320` variant, meaning the 1-by-1 pixel aspect ratio used by the
 * IIGS's 320x200 mode. The `640` variants are drawn for that mode's half-width pixels and
 * would come out stretched on a square-pixel display; do not swap one in without
 * squashing it, which the licence forbids anyway.
 *
 * It is **proportional** — advances run from 4 to 9 pixels. It has an 800-unit em with an
 * ascent of 700, so the one-pixel-is-an-eighth-of-an-em rule in [cellStyle] holds for it.
 *
 * Where the bytes come from differs by platform — an Android font resource on the watch,
 * a fetched `.ttf` in the browser — which is the only reason this is `expect`.
 */
@Composable
expect fun shastonFontFamily(): FontFamily

/**
 * The height in pixels of one character cell in the source bitmap. The Apple II
 * 40-column cell is 7 wide by 8 tall.
 */
private const val CELL_HEIGHT_PX = 8

/**
 * How many device pixels one *watch* pixel occupies.
 *
 * One, on the watch, by definition. The browser is where this earns its keep. Every size
 * in this file is pinned to whole device pixels (see [cellStyle]), which is exactly right
 * on a 384-pixel display held at arm's length and exactly wrong in a 1400-pixel browser
 * window, where the same text would be a quarter of the size relative to the screen it
 * sits on. The web build magnifies the whole watch by a whole number and reports it here,
 * so the type scales with everything else instead of shrinking into the middle of it.
 *
 * Whole numbers only. A fractional magnification would put the cell back off the pixel
 * grid, which is the one thing this entire file exists to prevent.
 */
val LocalWatchScale = staticCompositionLocalOf { 1 }

/**
 * The platform's own say in how a line box is measured, which only Android has one of.
 *
 * Android adds "font padding" above and below a line by default — extra room derived from
 * the font's own ascent and descent metrics, so that tall accents and deep descenders in
 * *some other* font would still fit. This font's line box is the glyph, so that padding is
 * pure error: it makes every line taller than the cell and pushes text off the grid. Skia,
 * which is what the browser draws with, has no such notion, hence null there.
 */
internal expect val cellPlatformStyle: PlatformTextStyle?

/**
 * A text style at a whole-number magnification of the original character cell.
 *
 * The font declares 800 units per em with an ascent of 700 and a descent of -100, which
 * puts the 8-pixel cell at exactly one em — so **one source pixel is one eighth of the
 * font size**. Render at any size that is not a multiple of 8 device pixels and the
 * glyph edges land between pixels, where the rasteriser antialiases them into the soft
 * grey mush this whole art style exists to avoid.
 *
 * The size is therefore computed in *pixels* and converted to sp, rather than written as
 * an sp literal. Two things would otherwise break it: a device whose density is not 2
 * (a dp literal would land on a fractional pixel size), and the user's font-scale
 * accessibility setting (an sp literal is multiplied by it). Converting from pixels
 * pins the result through both. The cost is that this text does not honour the system
 * font-size preference — accepted because at 1x the cell is 8 pixels tall and unreadable
 * on a watch, so the only available sizes are these three anyway.
 */
@Composable
fun cellStyle(scale: Int): TextStyle {
    val density = LocalDensity.current
    val pixels = CELL_HEIGHT_PX * scale * LocalWatchScale.current
    return TextStyle(
        fontFamily = shastonFontFamily(),
        fontWeight = FontWeight.Normal,
        fontSize = with(density) { pixels.toSp() },
        // Half a cell of leading. Also a whole number of pixels, so successive lines stay
        // on the grid rather than accumulating a fractional offset down a paragraph.
        lineHeight = with(density) { (pixels * 3 / 2).toSp() },
        // Must be zero. The default Wear styles carry fractional tracking, which would
        // shift each glyph's origin off the pixel grid and undo everything above.
        letterSpacing = 0.sp,
        // Without this the top pixel row of a line gets shaved off inside a Chip. By
        // default Compose trims the leading above the first line and the space below the
        // last, which is the right call for a font with generous built-in padding and the
        // wrong one here: this font's line box *is* the glyph, so trimming it cuts into
        // the letters. Centring the extra leading instead keeps every row intact.
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
        platformStyle = cellPlatformStyle,
    )
}

/**
 * The type scale, in multiples of the character cell.
 *
 * A bitmap font has no sizes between its multiples, so the usual twelve-step Material
 * scale collapses into three: x4 for the one big number in the store, x3 for headings
 * and chip labels, x2 for body and captions. Captions end up the same size as body text
 * and are told apart by colour instead — at x1 the cell is 8 pixels tall, which no one
 * is reading on a 1.2" display.
 *
 * These sizes put a hard budget on copy, and it is tighter than it looks: on the
 * 384-pixel watch a chip label at x3 fits about 11 characters per line over three lines,
 * and a secondary label at x2 fits about 17 per line over two. Budget against the watch
 * and not the 454-pixel emulator, whose chip column is a third wider — and not against
 * the browser either, which is the same 192dp screen and so has exactly the watch's
 * budget however large the window is. Several strings had to be shortened to land inside
 * that, including two route labels in `Trail.kt`. Check new copy against it — the failure
 * mode is a silent ellipsis, not a warning.
 *
 * Named after the Wear type scale it replaced, so that the screens' references did not
 * all have to be re-reasoned when they stopped going through `MaterialTheme`.
 */
object AppleIIType {
    val display1: TextStyle @Composable get() = cellStyle(4)
    val display2: TextStyle @Composable get() = cellStyle(4)
    val display3: TextStyle @Composable get() = cellStyle(4)
    val title1: TextStyle @Composable get() = cellStyle(3)
    val title2: TextStyle @Composable get() = cellStyle(3)
    val title3: TextStyle @Composable get() = cellStyle(3)
    val body1: TextStyle @Composable get() = cellStyle(3)
    val body2: TextStyle @Composable get() = cellStyle(2)
    val button: TextStyle @Composable get() = cellStyle(3)
    val caption1: TextStyle @Composable get() = cellStyle(2)
    val caption2: TextStyle @Composable get() = cellStyle(2)
    val caption3: TextStyle @Composable get() = cellStyle(2)
}
