package com.oregontrail.wear.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The widgets whose implementation is genuinely different on a watch and in a browser.
 *
 * Everything else in this package is written once — see `Screens.kt`. What is here is the
 * short list of controls that on Wear OS are supplied by `androidx.wear.compose.material`
 * and have no multiplatform equivalent: the scaling list, the chip, the circular button,
 * and the crown.
 *
 * The Android implementations delegate to Wear Compose with exactly the parameters they
 * were laid out against — every measurement in `docs/` and every screenshot in the README
 * is a Wear `Chip` metric — so the watch build renders pixel-for-pixel what it always did.
 * The browser implementations redraw the same shapes from the palette in
 * `ui/theme/AppleII.kt`, since there is no Wear Compose to delegate to.
 */

/**
 * The children of a [RotaryColumn], as items rather than as a column of composables.
 *
 * A watch's menu is a lazy, *scaling* list — Wear draws items smaller and dimmer the
 * further they sit from the middle of the round display — and lazy lists take item
 * builders, not a `@Composable` block. This is the smallest interface that spans that and
 * an ordinary column: enough for the nine menus in the game, and nothing more.
 */
interface MenuScope {
    fun item(content: @Composable () -> Unit)
    fun items(count: Int, content: @Composable (index: Int) -> Unit)
}

/**
 * A scrolling menu with the crown wired up.
 *
 * Rotary is the primary control (ADR 0004), and on the watch it is not automatic: the
 * crown delivers events only to a focused composable, so the list has to be focusable
 * *and* actually hold focus. Forgetting the focus request produces a screen that scrolls
 * perfectly by touch and ignores the crown entirely — which looks like a hardware problem
 * rather than a missing modifier.
 */
@Composable
expect fun RotaryColumn(
    modifier: Modifier = Modifier,
    content: MenuScope.() -> Unit,
)

/**
 * A scrolling column that does *not* scale its children, with the crown wired up.
 *
 * [RotaryColumn] is the right choice for menus, but its scaling and fading is death to
 * pixel art: an item part-way up the screen is drawn at some fractional scale, and a
 * sprite authored at exactly x3 comes out with uneven pixel rows. Screens that lead with
 * a scene use this instead, so the art is always drawn at the whole-number scale it was
 * designed for.
 *
 * [topPadding] is leading clearance above the first child. The 48.dp default pushes a
 * standard 128x64 scene (384 device pixels wide at x3) down to roughly the widest part of
 * the display instead of letting the bezel eat its top corners. Pass 0.dp for a scene
 * authored taller and bled to the top edge on purpose, relying on the bezel to crop its
 * corners into the round shape instead of stopping short of it.
 *
 * [horizontalPadding] is side clearance for every child. The 16.dp default keeps chips
 * and text clear of the curved edges. Pass 0.dp for a screen whose first child is a scene
 * meant to bleed to the true screen edges — `verticalScroll` clips its content to this
 * column's own measured bounds, so a child can't escape this padding on its own (a
 * custom-layout "negative padding" trick was tried and gets clipped back down); the
 * column has to not reserve the padding in the first place. Wrap the remaining
 * (non-scene) children in their own `Modifier.padding(horizontal = 16.dp)` column to keep
 * them off the bezel — which is what [SceneScrollColumn] does.
 */
@Composable
expect fun RotaryScrollColumn(
    modifier: Modifier = Modifier,
    topPadding: Dp = 48.dp,
    horizontalPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
)

/** A list row. The workhorse of every menu in the game. */
@Composable
expect fun MenuChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    iconArt: String? = null,
    enabled: Boolean = true,
    primary: Boolean = false,
)

/**
 * A small pill with a one-word label — the store's confirm control, and the only place in
 * the game that wants a button rather than a full-width row.
 */
@Composable
expect fun CompactActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)

/**
 * One of the store's circular ± buttons.
 *
 * Fixed size by construction, not by choice: Wear's `Button` applies
 * `Modifier.requiredSize(ButtonDefaults.DefaultButtonSize)` *after* the caller's
 * modifier, so it cannot be resized from here and the rest of the row has to shrink
 * around it. The browser's copy matches that size rather than improving on it, so the
 * store's arithmetic about what fits in 192dp holds on both.
 */
@Composable
expect fun StepperButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
)

/**
 * Wires the crown — or whatever stands in for it — to a scroll amount in pixels.
 *
 * On the watch this is `onRotaryScrollEvent` plus the focus request that makes it fire at
 * all. In the browser it is the mouse wheel and the arrow keys, both reporting in the same
 * units, so that everything downstream — the store's detents, the map's landmark steps,
 * the raft's steering gain — is calibrated once.
 *
 * Note what does *not* go through here: hunting. Its aim is the tap itself, and a control
 * that only turns cannot express a point on a plane — see ADR 0004.
 */
@Composable
expect fun Modifier.rotaryInput(onScroll: (pixels: Float) -> Unit): Modifier
