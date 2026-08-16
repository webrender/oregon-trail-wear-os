package com.oregontrail.wear.ui.art

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * The watch's own screen width. Read from the configuration rather than assumed, because
 * the two devices this runs on are different sizes — 384 pixels on the physical Pixel
 * Watch 2 and 454 on the `Wear_OS_Large_Round` emulator — and art sized against the
 * wrong one is a whole scale step out.
 */
@Composable
actual fun displayWidthPx(): Int = with(LocalDensity.current) {
    LocalConfiguration.current.screenWidthDp.dp.roundToPx()
}
