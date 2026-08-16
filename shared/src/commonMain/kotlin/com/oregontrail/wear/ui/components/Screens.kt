package com.oregontrail.wear.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oregontrail.wear.ui.art.BLEED_SCENE_HEIGHT
import com.oregontrail.wear.ui.art.BLEED_SCENE_WIDTH
import com.oregontrail.wear.ui.art.PixelArtImage
import com.oregontrail.wear.ui.art.Scene
import com.oregontrail.wear.ui.theme.AppleII
import com.oregontrail.wear.ui.theme.AppleIIType

/**
 * Text, in the game's own font and palette.
 *
 * [BasicText] rather than a Material `Text`, because there is no Material here: the
 * screens take their styles from [AppleIIType] directly, and everything a Material `Text`
 * would add on top of `BasicText` — a content colour from the theme, a typography default
 * — is something this game overrides at every call site anyway.
 *
 * The defaults are chosen to match what Wear's `Text` resolved to before the port, so
 * that a call site which passed nothing still gets what it got: green on black, body
 * size, and — note — [TextOverflow.Clip]. Clip chops mid-glyph with no ellipsis, which
 * reads as a rendering fault rather than as truncation; it is kept as the default anyway
 * because every place where it matters has been measured and fitted instead, and an
 * ellipsis would silently permit copy that no longer fits.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AppleII.Green,
    style: TextStyle = AppleIIType.body2,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(
            color = color,
            textAlign = textAlign ?: TextAlign.Unspecified,
        ),
        maxLines = maxLines,
        overflow = overflow,
    )
}

/**
 * A screen that leads with a scene bled to the true screen edges (top, left, and
 * right), the round display cropping whatever the wider/taller canvas overshoots into
 * the correct curved silhouette — see docs/art-brief.md's "Full-bleed scenes" section.
 * [background] must be a [BLEED_SCENE_WIDTH]x[BLEED_SCENE_HEIGHT] asset, not a
 * standard 128x64 one, or it'll be centred with the usual black margins instead.
 *
 * Everything after the scene keeps the normal 16dp side clearance, via a plain
 * [Column] wrapping [content] — see [RotaryScrollColumn]'s `horizontalPadding` doc for
 * why that padding can't just live on the outer scrolling column here.
 */
@Composable
fun SceneScrollColumn(
    background: String?,
    modifier: Modifier = Modifier,
    twinkle: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    RotaryScrollColumn(modifier = modifier, topPadding = 0.dp, horizontalPadding = 0.dp) {
        Scene(
            background = background,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(BLEED_SCENE_WIDTH.toFloat() / BLEED_SCENE_HEIGHT),
            twinkle = twinkle,
        )
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/**
 * A full-screen page that does not scroll, with a tap target over the whole surface.
 *
 * Used for the screens that show a scene and wait — arrivals, events, endings. Tapping
 * anywhere is the right target on a 1.2" round display; a small button would be a worse
 * one for the same action.
 */
@Composable
fun StaticScreen(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppleII.Black),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** A heading above a group of rows. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        color = AppleII.Green,
        textAlign = TextAlign.Center,
        style = AppleIIType.title3,
    )
}

/**
 * Body copy, centred.
 *
 * [small] is not decoration. On a screen leading with a 192-pixel scene, a subtitle that
 * wraps to two lines is enough to push the primary chip off the bottom of the display,
 * so the supporting lines under a scene use the smaller size to stay on one line.
 */
@Composable
fun ScreenText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AppleII.White,
    small: Boolean = false,
) {
    Text(
        text = text,
        // Not the full column width. Body copy sits below a scene, which puts it in the
        // lower third of a *circular* display where the visible width narrows sharply —
        // a full-width centred line has its first and last characters cut off by the
        // bezel. Wrapping early is the lesser evil.
        modifier = modifier.fillMaxWidth(0.86f),
        color = color,
        textAlign = TextAlign.Center,
        style = if (small) AppleIIType.caption2 else AppleIIType.body2,
    )
}

/**
 * An icon and a value side by side — the supplies readout.
 *
 * Icons are 16x16 authored, drawn at x2 as the art brief specifies. At density 2 that is
 * 16.dp, so the size is given in dp deliberately rather than being left to fill.
 */
@Composable
fun IconValue(
    iconArt: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = AppleII.Green,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        PixelArtImage(name = iconArt, size = 16.dp)
        Spacer(Modifier.width(4.dp))
        Text(
            text = value,
            color = color,
            style = AppleIIType.caption1,
        )
    }
}

/** Vertical breathing room, in the one place list padding cannot provide it. */
@Composable
fun Gap(height: Int = 8) {
    Spacer(Modifier.height(height.dp))
}

/** Padding that keeps list content clear of the round display's edges. */
val listPadding = PaddingValues(horizontal = 12.dp, vertical = 24.dp)

/** Keeps a row of readouts off the curved edge. */
val readoutWidth = Modifier.fillMaxWidth(0.82f)
