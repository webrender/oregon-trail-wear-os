package com.oregontrail.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Typography
import com.oregontrail.wear.R

/**
 * Shaston — the Apple IIGS (GS/OS) system font, by Kreative Software, vendored verbatim
 * at `res/font/shaston_320.ttf` under the Kreative Software Relay Fonts Free Use
 * License, a copy of which ships inside the APK at
 * `assets/licenses/kreative-relay-fonts-free-use-license.txt`. That licence permits
 * embedding and redistribution free of charge but **forbids modification**, so the file
 * must stay byte-identical to upstream — do not run it through a font subsetter or
 * optimiser, and do not enable resource shrinking on it.
 *
 * This is the `Shaston320` variant, meaning the 1-by-1 pixel aspect ratio used by the
 * IIGS's 320x200 mode. The `640` variants are drawn for that mode's half-width pixels and
 * would come out stretched on a square-pixel display; do not swap one in without
 * squashing it, which the licence forbids anyway.
 *
 * It is **proportional** — advances run from 4 to 9 pixels. It has an 800-unit em with an
 * ascent of 700, so the one-pixel-is-an-eighth-of-an-em rule in [cellStyle] holds for it.
 */
val Shaston = FontFamily(Font(R.font.shaston_320))

/** The face the whole UI is set in. */
private val uiFont = Shaston

/**
 * The height in pixels of one character cell in the source bitmap. The Apple II
 * 40-column cell is 7 wide by 8 tall.
 */
private const val CELL_HEIGHT_PX = 8

/**
 * A text style at a whole-number magnification of the original character cell.
 *
 * The font declares 800 units per em with an ascent of 700 and a descent of -100, which
 * puts the 8-pixel cell at exactly one em — so **one source pixel is one eighth of the
 * font size**. Render at any size that is not a multiple of 8 device pixels and the
 * glyph edges land between pixels, where Android's rasteriser antialiases them into the
 * soft grey mush this whole art style exists to avoid.
 *
 * The size is therefore computed in *pixels* and converted to sp, rather than written as
 * an sp literal. Two things would otherwise break it: a device whose density is not 2
 * (a dp literal would land on a fractional pixel size), and the user's font-scale
 * accessibility setting (an sp literal is multiplied by it). Converting from pixels
 * pins the result through both. The cost is that this text does not honour the system
 * font-size preference — accepted because at 1x the cell is 8 pixels tall and unreadable
 * on a watch, so the only available sizes are these three anyway.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
private fun cellStyle(scale: Int): TextStyle {
    val density = LocalDensity.current
    return TextStyle(
        fontFamily = uiFont,
        fontWeight = FontWeight.Normal,
        fontSize = with(density) { (CELL_HEIGHT_PX * scale).toSp() },
        // Half a cell of leading. Also a whole number of pixels, so successive lines stay
        // on the grid rather than accumulating a fractional offset down a paragraph.
        lineHeight = with(density) { (CELL_HEIGHT_PX * scale * 3 / 2).toSp() },
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
        platformStyle = PlatformTextStyle(includeFontPadding = false),
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
 * These sizes put a hard budget on copy, and it is tighter than it looks: a chip label at
 * x3 fits about 15 characters per line over two lines, and a secondary label at x2 fits
 * about 23 on its one line. Several strings had to be shortened to land inside that,
 * including two route labels in `Trail.kt`. Check new copy against it — the failure mode
 * is a silent ellipsis, not a warning.
 */
@Composable
fun appleIITypography(): Typography = Typography(
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
)
