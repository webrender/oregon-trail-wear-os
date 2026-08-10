package com.oregontrail.wear.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import com.oregontrail.wear.ui.art.PixelArtImage
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
                .padding(start = 16.dp, top = 48.dp, end = 16.dp, bottom = 32.dp),
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
                maxLines = 2,
            )
        },
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        secondaryLabel = secondaryLabel?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.caption2,
                    maxLines = 1,
                )
            }
        },
        icon = iconArt?.let {
            { PixelArtImage(name = it, modifier = Modifier.size(24.dp), maxScale = 2) }
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
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        PixelArtImage(name = iconArt, modifier = Modifier.size(16.dp), maxScale = 2)
        Spacer(Modifier.width(4.dp))
        Text(
            text = value,
            color = AppleII.Green,
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
