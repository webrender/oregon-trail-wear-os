package com.oregontrail.wear.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.onClick
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oregontrail.wear.ui.art.PixelArtImage
import com.oregontrail.wear.ui.theme.AppleII
import com.oregontrail.wear.ui.theme.AppleIIChrome
import com.oregontrail.wear.ui.theme.AppleIIType
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The browser's widgets: the same shapes as Wear Compose's, drawn from the game's own
 * palette.
 *
 * These are reimplementations rather than ports of something, because Wear Compose is an
 * Android library and there is nothing to reuse. The measurements are copied from it
 * deliberately — a 52dp chip, 14dp of horizontal chip padding, a 24dp icon with 6dp of
 * spacing — so that the copy budget worked out on the watch (see
 * `docs/` and the notes on `MenuChip`) is the same budget here. Changing them would make
 * the two builds disagree about what fits, and the watch is the one with no room to spare.
 */

private val CHIP_HEIGHT = 52.dp
private val CHIP_PADDING = 14.dp
private val CHIP_CORNER = 26.dp
private val CHIP_ICON = 24.dp
private val CHIP_ICON_SPACING = 6.dp
private val BUTTON_SIZE = 52.dp

/**
 * How far one notch of a mouse wheel — or one press of an arrow key — turns the crown.
 *
 * In dp rather than pixels, and that is the whole point. The watch's crown reports device
 * pixels at density 2, and the constants tuned against it (the store's step, the map's
 * detent) are therefore implicitly "at density 2". The browser runs at a higher density
 * whenever the watch is magnified, so a notch measured in raw pixels would step the store
 * twice as fast on a big screen as on a small one. 20dp is 40 pixels at density 2, which
 * is one store step — one notch, one step, at any magnification.
 */
private val WHEEL_NOTCH = 20.dp

/** Wear's `ScalingLazyColumn` shrinks and fades items away from the centre by about this
 *  much at the edge of the display. Matched by eye rather than by constant, since the
 *  real one interpolates over a curve this does not reproduce. */
private const val EDGE_SCALE = 0.7f
private const val EDGE_ALPHA = 0.5f

private class CollectedMenuScope : MenuScope {
    val entries = mutableListOf<@Composable () -> Unit>()

    override fun item(content: @Composable () -> Unit) {
        entries += content
    }

    override fun items(count: Int, content: @Composable (index: Int) -> Unit) {
        for (index in 0 until count) entries += { content(index) }
    }
}

@Composable
actual fun RotaryColumn(
    modifier: Modifier,
    content: MenuScope.() -> Unit,
) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val collected = CollectedMenuScope().apply(content)

    Box(Modifier.fillMaxSize().background(AppleII.Black)) {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .rotaryInput { pixels -> scope.launch { state.scrollBy(pixels) } },
            state = state,
            horizontalAlignment = Alignment.CenterHorizontally,
            // Half the display top and bottom, so the first and last rows can reach the
            // middle. Wear's scaling list does the same, and without it the top item can
            // never be the one in focus.
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 72.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(collected.entries.size) { index ->
                Box(
                    // Read inside the layer block rather than in the composable body, so
                    // scrolling repaints without recomposing every visible row.
                    modifier = Modifier.graphicsLayer {
                        val info = state.layoutInfo
                        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                            ?: return@graphicsLayer
                        val viewportCentre =
                            (info.viewportStartOffset + info.viewportEndOffset) / 2f
                        val itemCentre = item.offset + item.size / 2f
                        val half = (info.viewportEndOffset - info.viewportStartOffset) / 2f
                        val away = if (half <= 0f) 0f else (abs(itemCentre - viewportCentre) / half)
                            .coerceIn(0f, 1f)
                        scaleX = 1f - (1f - EDGE_SCALE) * away
                        scaleY = scaleX
                        alpha = 1f - (1f - EDGE_ALPHA) * away
                    },
                ) {
                    collected.entries[index]()
                }
            }
        }
        ScrollArc(
            fraction = {
                val info = state.layoutInfo
                val total = info.totalItemsCount
                if (total == 0) 0f
                else (state.firstVisibleItemIndex.toFloat() / total).coerceIn(0f, 1f)
            },
            visible = { state.layoutInfo.totalItemsCount > 3 },
        )
    }
}

@Composable
actual fun RotaryScrollColumn(
    modifier: Modifier,
    topPadding: Dp,
    horizontalPadding: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(AppleII.Black)) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .rotaryInput { pixels -> scope.launch { scrollState.scrollBy(pixels) } }
                .verticalScroll(scrollState)
                .padding(
                    start = horizontalPadding,
                    top = topPadding,
                    end = horizontalPadding,
                    bottom = 32.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
        ScrollArc(
            fraction = {
                val range = scrollState.maxValue
                if (range <= 0) 0f else (scrollState.value.toFloat() / range).coerceIn(0f, 1f)
            },
            visible = { scrollState.maxValue > 0 },
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
    val background = when {
        !enabled -> AppleIIChrome.DisabledGreen
        primary -> AppleII.Green
        else -> AppleIIChrome.DimGreen
    }
    val content = when {
        !enabled -> AppleIIChrome.MutedGreen
        primary -> AppleII.Black
        else -> AppleII.Green
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = CHIP_HEIGHT)
            .clip(RoundedCornerShape(CHIP_CORNER))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = CHIP_PADDING, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconArt != null) {
            PixelArtImage(name = iconArt, size = CHIP_ICON)
            Spacer(Modifier.width(CHIP_ICON_SPACING))
        }
        Column {
            Text(
                text = label,
                color = content,
                style = AppleIIType.button,
                maxLines = 3,
            )
            if (secondaryLabel != null) {
                Text(
                    text = secondaryLabel,
                    color = content,
                    style = AppleIIType.caption2,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
actual fun CompactActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppleII.Green)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = AppleII.Black, style = AppleIIType.button)
    }
}

@Composable
actual fun StepperButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .size(BUTTON_SIZE)
            .clip(CircleShape)
            .background(if (enabled) AppleIIChrome.DimGreen else AppleIIChrome.DisabledGreen)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) AppleII.Green else AppleIIChrome.MutedGreen,
            style = AppleIIType.title2,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * What stands in for the crown.
 *
 * Three inputs, all reporting the same units so that everything calibrated against the
 * crown — the store's detents, the map's landmark steps, the raft's steering gain — works
 * unchanged. The wheel is the obvious one. The arrow keys are there because a trackpad
 * without a scroll wheel is common and the map and the raft have no other control. Left
 * and right are wired to the same axis as up and down deliberately: the raft steers
 * *sideways* from a crown that turns, and asking someone at a keyboard to steer a river
 * with ArrowUp would be a worse joke than the one this port already is.
 *
 * The focus request is here for the same reason it is on the watch: key events only reach
 * a focused composable.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun Modifier.rotaryInput(onScroll: (pixels: Float) -> Unit): Modifier {
    val notch = with(LocalDensity.current) { WHEEL_NOTCH.toPx() }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    return this
        .onPointerEvent(PointerEventType.Scroll) { event ->
            val delta = event.changes.fold(0f) { sum, change -> sum + change.scrollDelta.y }
            if (delta != 0f) onScroll(delta * notch)
        }
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            when (event.key) {
                Key.DirectionDown, Key.DirectionRight -> onScroll(notch)
                Key.DirectionUp, Key.DirectionLeft -> onScroll(-notch)
                else -> return@onKeyEvent false
            }
            true
        }
        .focusRequester(focusRequester)
        .focusable()
}
