package com.oregontrail.wear.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.oregontrail.wear.ui.art.BLEED_SCENE_HEIGHT
import com.oregontrail.wear.ui.art.BLEED_SCENE_WIDTH
import com.oregontrail.wear.ui.art.PixelArtImage
import com.oregontrail.wear.ui.art.Scene
import com.oregontrail.wear.ui.theme.AppleII
import kotlinx.coroutines.launch

/**
 * A scrolling screen with the crown wired up.
 *
 * Rotary is the primary control (ADR 0004), and it is not automatic: the crown delivers
 * [onRotaryScrollEvent]s only to a focused composable, so the list has to be focusable
 * *and* actually hold focus. Forgetting the focus request produces a screen that scrolls
 * perfectly by touch and ignores the crown entirely — which looks like a hardware problem
 * rather than a missing modifier.
 *
 * Note that the rotary modifier lives in `androidx.compose.ui.input.rotary`, not in the
 * Wear foundation package where it would be reasonable to expect it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RotaryColumn(
    modifier: Modifier = Modifier,
    state: ScalingLazyListState = rememberScalingLazyListState(),
    content: ScalingLazyListScope.() -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = state) },
    ) {
        ScalingLazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(AppleII.Black)
                .onRotaryScrollEvent { event ->
                    scope.launch { state.scrollBy(event.verticalScrollPixels) }
                    true
                }
                .focusRequester(focusRequester)
                .focusable(),
            state = state,
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/**
 * A scrolling column that does *not* scale its children, with the crown wired up.
 *
 * [RotaryColumn] is the right choice for menus, but its scaling and fading is death to
 * pixel art: an item part-way up the screen is drawn at some fractional scale, and a
 * sprite authored at exactly x3 comes out with uneven pixel rows. Screens that lead with
 * a scene use this instead, so the art is always drawn at the whole-number scale it was
 * designed for.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RotaryScrollColumn(
    modifier: Modifier = Modifier,
    /**
     * Leading clearance above the first child. The 48.dp default pushes a standard
     * 128x64 scene (384 device pixels wide at x3) down to roughly the widest part of the
     * display instead of letting the bezel eat its top corners — see the padding comment
     * below. Pass 0.dp for a scene authored taller and bled to the top edge on purpose,
     * relying on the bezel to crop its corners into the round shape instead of stopping
     * short of it.
     */
    topPadding: Dp = 48.dp,
    /**
     * Side clearance for every child. The 16.dp default keeps chips and text clear of
     * the curved edges. Pass 0.dp for a screen whose first child is a scene meant to
     * bleed to the true screen edges — `verticalScroll` clips its content to this
     * column's own measured bounds, so a child can't escape this padding on its own
     * (a custom-layout "negative padding" trick was tried and gets clipped back down);
     * the column has to not reserve the padding in the first place. Wrap the remaining
     * (non-scene) children in their own `Modifier.padding(horizontal = 16.dp)` column
     * to keep them off the bezel.
     */
    horizontalPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(positionIndicator = { PositionIndicator(scrollState = scrollState) }) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(AppleII.Black)
                .onRotaryScrollEvent { event ->
                    scope.launch { scrollState.scrollBy(event.verticalScrollPixels) }
                    true
                }
                .focusRequester(focusRequester)
                .focusable()
                .verticalScroll(scrollState)
                // Asymmetric on purpose. A 128x64 scene drawn at x3 is 384 device pixels
                // wide, and the visible circle is only that wide across its middle — so
                // the leading padding pushes the first scene down to roughly the widest
                // part of the display instead of letting the bezel eat its top corners.
                .padding(
                    start = horizontalPadding,
                    top = topPadding,
                    end = horizontalPadding,
                    bottom = 32.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
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

/** A list row. The workhorse of every menu in the game. */
@Composable
fun MenuChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    iconArt: String? = null,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    Chip(
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.button,
                // Three lines, not two. The x3 label fits about 11 characters per line
                // on the 384-pixel watch — not the ~15 the emulator's wider column
                // suggests — so the longest route labels at a fork ("Raft down the
                // Columbia River") need a third line and were silently losing their
                // tail without one. The chip grows to fit; every other label in the
                // game is short enough that this changes nothing for it.
                maxLines = 3,
            )
        },
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        secondaryLabel = secondaryLabel?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.caption2,
                    // Two lines, not one. A chip on the 384-pixel watch leaves only about
                    // 204 pixels for its labels (320 wide, less 28 of chip padding either
                    // side, less a 48-pixel icon and its 12-pixel gap) against 274 on the
                    // 454-pixel emulator — and this font is sized in device pixels, so it
                    // does not shrink to compensate. Anything budgeted against the
                    // emulator loses its tail on the watch, with no ellipsis: the default
                    // overflow chops mid-glyph. A second line is free here — Wear's chip
                    // has a 52.dp minimum height and one label line plus one secondary
                    // line only fills 36 of it — so callers can split a two-fact label
                    // across two short lines rather than shortening the copy.
                    maxLines = 2,
                )
            }
        },
        icon = iconArt?.let {
            { PixelArtImage(name = it, size = 24.dp) }
        },
        colors = if (primary) {
            ChipDefaults.primaryChipColors()
        } else {
            ChipDefaults.secondaryChipColors()
        },
        enabled = enabled,
    )
}

/** A heading above a group of rows. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        color = AppleII.Green,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.title3,
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
    color: androidx.compose.ui.graphics.Color = AppleII.White,
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
        style = if (small) {
            MaterialTheme.typography.caption2
        } else {
            MaterialTheme.typography.body2
        },
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
    color: androidx.compose.ui.graphics.Color = AppleII.Green,
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
            style = MaterialTheme.typography.caption1,
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
