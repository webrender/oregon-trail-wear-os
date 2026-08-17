package com.oregontrail.wear.ui.art

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * The width of the simulated watch, not of the browser window.
 *
 * The window is the bezel's surround and is not drawn into; the screen inside it is a
 * fixed 192dp, magnified by whatever factor the density carries — see `Main.kt`. So this
 * is 384 device pixels at 1x, 768 at 2x, and asking Compose's window info instead would
 * return the size of the page and have the art decoded several times larger than anything
 * that could show it.
 */
@Composable
actual fun displayWidthPx(): Int = with(LocalDensity.current) { 192.dp.roundToPx() }
