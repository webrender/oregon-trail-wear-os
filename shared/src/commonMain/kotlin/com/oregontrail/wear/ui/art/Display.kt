package com.oregontrail.wear.ui.art

import androidx.compose.runtime.Composable

/**
 * How wide the round display is, in device pixels.
 *
 * Art sizing needs this *before* layout has measured anything — [ArtLoader] is told how
 * big a decode to produce, and [Scene] maps its 128-unit grid across the display width —
 * so it cannot come from a `Modifier` or a measured constraint. It is an upper bound by
 * construction: nothing in the game is drawn wider than the screen, so decoding to this
 * never undershoots, and overshooting only costs a little memory.
 */
@Composable
expect fun displayWidthPx(): Int
