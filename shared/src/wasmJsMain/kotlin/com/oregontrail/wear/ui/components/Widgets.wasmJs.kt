package com.oregontrail.wear.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oregontrail.wear.ui.art.PixelArtImage
import com.oregontrail.wear.ui.theme.AppleII
import com.oregontrail.wear.ui.theme.AppleIIChrome
import com.oregontrail.wear.ui.theme.AppleIIType
import kotlinx.browser.window
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
 *
 * ### Rounded shapes are drawn, not clipped
 *
 * Every widget below takes its shape from `Modifier.background(colour, shape)` rather than
 * from the more usual `Modifier.clip(shape)` and a flat background under it. The two are the
 * same picture and are not the same work.
 *
 * `Main.kt` wraps the whole display in `clip(CircleShape)`, so a chip that reaches the edge
 * of the screen used to be a rounded-rect clip *nested inside* a rounded-rect clip. Skia
 * carries one analytic rounded rect in the clip stack and has to fall back to a rasterised
 * mask for a second one, and that fallback is where a GPU can disagree with itself: the
 * reported symptom — a chip losing its bottom-left corner to a straight diagonal, and the
 * bezel losing a bite out of its lower left — is what a mask that has gone wrong looks like.
 * It has not been reproduced in software rendering, so this is a narrowing rather than a
 * proven fix; what it does prove is that the second clip was never needed.
 *
 * Drawing the shape leaves exactly one clip in the stack — the circle — with an ordinary
 * rounded rect drawn into it. Nothing here has content that overflows its own background,
 * so the clip was buying nothing else.
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

/**
 * How much `scrollDelta` a browser reports for one notch of a wheel.
 *
 * **`scrollDelta.y` is not a count of notches.** It is the DOM `WheelEvent`'s `deltaY`,
 * passed through unchanged — CSS pixels of scroll intent, and about 100 of them per notch
 * in Chrome, Edge and Safari. Measured, not assumed: a synthetic wheel of `deltaY = 4` on
 * the store's food screen moved the quantity by exactly four 25lb steps.
 *
 * Treating it as a notch count is what this constant exists to prevent, and the bug it
 * caused was not subtle — one click of a real wheel reported 100, which at 20dp *each*
 * asked for 100 steps and slammed the food quantity from nothing to the 1,000lb ceiling in
 * a single detent. Dividing by it restores the intended relationship: one notch of wheel is
 * one detent of crown, which is one store step, one landmark on the map, and
 * [LIST_SCROLL_PER_NOTCH] of a scrolling list.
 *
 * Firefox is the known gap. It reports mouse wheels in *lines* (`deltaMode = 1`, about 3
 * per notch) rather than pixels, and nothing here can see `deltaMode` to tell the
 * difference — so a mouse wheel there will scroll roughly a thirtieth as far. That is a
 * slow control rather than a broken one, which is the right way round for a failure that
 * cannot be tested from here, and [wheelSensitivity] is the way out of it. Deliberately
 * *not* guessed at from the magnitude of the delta: trackpads legitimately send single-digit
 * pixel deltas, so "small means lines" would make every trackpad in Chrome unusably fast.
 */
private const val WHEEL_DELTA_PER_NOTCH = 100f

/**
 * A global multiplier on wheel input, from `?wheel=` in the URL. Default 1.
 *
 * Kept because no single constant above can be right everywhere: a mouse, a high-resolution
 * wheel, a trackpad with momentum, and Firefox's line-mode deltas all report differently,
 * and none of them can be detected from inside Compose. Rather than pretend otherwise, the
 * number is adjustable in a few seconds — `?wheel=3` for Firefox, `?wheel=0.5` for a wheel
 * that runs away — instead of by rebuilding 12MB of wasm.
 *
 * Read once, at startup. Unparseable and out-of-range values fall back to 1 rather than
 * breaking the page, and the arrow keys deliberately ignore it: a key press is exactly one
 * detent by construction and has no device variation to compensate for.
 */
private val wheelSensitivity: Float = urlFloat("wheel", default = 1f, max = 20f)

/**
 * How far one notch of wheel scrolls a *list*, as against how far it turns the crown.
 *
 * The one place the browser deliberately does not match the crown one-for-one, and it needs
 * its own number rather than a fraction of [WHEEL_NOTCH] because it is not the same kind of
 * quantity. A detent is the right quantum for a control that lands on something — a store
 * step, a landmark — where you get discrete feedback confirming where you arrived. A list
 * has nothing to land on, so the same impulse is only distance, and 20dp of it per notch
 * would be a crawl.
 *
 * 30dp is a little under the ~39dp per notch that Compose's own built-in wheel scrolling
 * was doing before this file took the events over, which is what "a teeny bit fast" was
 * describing. Multiply it with `?scroll=` to taste.
 *
 * Only the two scrolling columns apply this. The store, the map and the raft keep the
 * crown-equivalent calibration, which is also what keeps buying 1,000lb of food from taking
 * fifty-odd notches of wheel.
 */
private val LIST_SCROLL_PER_NOTCH = 30.dp

/** [LIST_SCROLL_PER_NOTCH] as a multiple of a detent, times the `?scroll=` tuning value. */
private val listScrollRatio: Float =
    (LIST_SCROLL_PER_NOTCH / WHEEL_NOTCH) * urlFloat("scroll", default = 1f, max = 4f)

/**
 * A tuning value from the query string, or [default] if it is absent or nonsense.
 *
 * These exist because both numbers can only be set by feel, and feel varies by pointing
 * device — so the next person to find one wrong should be able to land on the value that
 * suits them in a few seconds rather than by rebuilding 12MB of wasm. Read once, at
 * startup; anything unparseable or out of range falls back rather than breaking the page.
 */
private fun urlFloat(name: String, default: Float, max: Float): Float {
    val requested = window.location.search
        .removePrefix("?")
        .split("&")
        .firstOrNull { it.startsWith("$name=") }
        ?.removePrefix("$name=")
        ?.toFloatOrNull()
    return if (requested != null && requested > 0f && requested <= max) requested else default
}

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
                // A list scrolls further per notch than a detent turns: [LIST_SCROLL_PER_NOTCH].
                .rotaryInput { pixels ->
                    scope.launch { state.scrollBy(pixels * listScrollRatio) }
                },
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
                // A list scrolls further per notch than a detent turns: [LIST_SCROLL_PER_NOTCH].
                .rotaryInput { pixels ->
                    scope.launch { scrollState.scrollBy(pixels * listScrollRatio) }
                }
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
            // Shaped background rather than `clip`: see the note at the top of this file.
            .background(background, RoundedCornerShape(CHIP_CORNER))
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
            // Shaped background rather than `clip`: see the note at the top of this file.
            .background(AppleII.Green, RoundedCornerShape(16.dp))
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
            // Shaped background rather than `clip`: see the note at the top of this file.
            .background(
                color = if (enabled) AppleIIChrome.DimGreen else AppleIIChrome.DisabledGreen,
                shape = CircleShape,
            )
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
        // `Initial`, and the changes are consumed, because on a scrolling list this is not
        // the only handler. `LazyColumn` and `verticalScroll` bring their own mouse-wheel
        // support, it runs on the `Main` pass — which for pointer input reaches the inner
        // node *first* — and it is not adjustable. Handling on `Main` therefore left the
        // built-in scrolling in charge and made every constant here inert on exactly the
        // screens where it was being tuned: damping the list to a hundredth changed
        // nothing at all, which is how this was found. Taking the event on `Initial` and
        // consuming it puts the rate back under this file's control on every screen
        // equally. Drag is untouched — only `Scroll` events are claimed.
        .onPointerEvent(PointerEventType.Scroll, pass = PointerEventPass.Initial) { event ->
            val delta = event.changes.fold(0f) { sum, change -> sum + change.scrollDelta.y }
            if (delta != 0f) {
                event.changes.forEach { it.consume() }
                onScroll(delta / WHEEL_DELTA_PER_NOTCH * notch * wheelSensitivity)
            }
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

/**
 * The finger, which on a phone is the only control there is.
 *
 * Neither of the two stand-ins above exists on a touchscreen: there is no wheel, and the
 * on-screen keyboard never opens because nothing on any screen takes text. That leaves the
 * raft and the map — the two screens that are not lists — with no control at all, which is
 * how the browser build was shipped and how it was played on a phone exactly once.
 *
 * Horizontal, and only horizontal, so that this is safe to have on the same screens the
 * lists are on. A vertical drag is how a list scrolls, and `LazyColumn` claims it; taking
 * the sideways axis and leaving that one alone means the two never argue about a diagonal.
 * The drag is consumed so that a `Modifier.clickable` under it — the map's tap-to-leave —
 * does not also fire when the finger comes up.
 */
@Composable
actual fun Modifier.horizontalDragInput(onDrag: (pixels: Float) -> Unit): Modifier {
    // The gesture detector is started once and outlives every recomposition, so the lambda
    // it captured would be the first one forever. That happens to be harmless for both of
    // this modifier's callers — they write to remembered state, which is the same object
    // each time — and it is the sort of harmless that stops being true the moment a third
    // caller closes over something else.
    val current by rememberUpdatedState(onDrag)
    return this.pointerInput(Unit) {
        detectHorizontalDragGestures { change, dragAmount ->
            change.consume()
            current(dragAmount)
        }
    }
}
