package com.oregontrail.wear.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.oregontrail.wear.ui.art.PixelArtImage
import com.oregontrail.wear.ui.theme.AppleII
import kotlinx.coroutines.launch

/**
 * The watch's widgets: Wear Compose, called with exactly the parameters the round display
 * was laid out against.
 *
 * Nothing here is an improvement on what came before the port — it is deliberately the
 * same code, moved. Every measurement recorded in `docs/` and every screenshot in the
 * README is a metric of these components at `androidx.wear.compose:1.3.1`, so a change
 * here is a layout change to be re-measured on hardware, not a refactor.
 */

/** Adapts Wear's lazy list builder to the small [MenuScope] the screens are written to. */
private class ScalingMenuScope(private val scope: ScalingLazyListScope) : MenuScope {
    override fun item(content: @Composable () -> Unit) = scope.item { content() }
    override fun items(count: Int, content: @Composable (Int) -> Unit) =
        scope.items(count) { content(it) }
}

/**
 * Note that the rotary modifier lives in `androidx.compose.ui.input.rotary`, not in the
 * Wear foundation package where it would be reasonable to expect it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun RotaryColumn(
    modifier: Modifier,
    content: MenuScope.() -> Unit,
) {
    val state = rememberScalingLazyListState()
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
            content = { ScalingMenuScope(this).content() },
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun RotaryScrollColumn(
    modifier: Modifier,
    topPadding: Dp,
    horizontalPadding: Dp,
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

@Composable
actual fun MenuChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    secondaryLabel: String?,
    iconArt: String?,
    enabled: Boolean,
    primary: Boolean,
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

@Composable
actual fun CompactActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    CompactChip(
        onClick = onClick,
        modifier = modifier,
        label = { Text(label) },
    )
}

@Composable
actual fun StepperButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.secondaryButtonColors(),
    ) {
        Text(label, style = MaterialTheme.typography.title2)
    }
}

/**
 * The crown.
 *
 * The focus request is the part that is easy to leave out and impossible to notice
 * missing by reading the code: without it the screen scrolls perfectly by touch and
 * ignores the crown entirely, which reads as a hardware fault.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun Modifier.rotaryInput(onScroll: (pixels: Float) -> Unit): Modifier {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    return this
        .onRotaryScrollEvent { event ->
            onScroll(event.verticalScrollPixels)
            true
        }
        .focusRequester(focusRequester)
        .focusable()
}
