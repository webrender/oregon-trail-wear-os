package com.oregontrail.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font

/**
 * Shaston, from bytes fetched before the first frame.
 *
 * Filled in by `main()` — see `Main.kt` — rather than loaded lazily here. Compose's
 * asynchronous font loading would show the whole UI in a fallback face for a frame or two
 * and then reflow it, and this UI cannot survive that: every layout decision in the game
 * is measured against *this* font's advances, and the fallback's are different enough
 * that chips would resize and text would rewrap in front of the player. A blank moment
 * before the title screen is the better trade.
 */
private var loaded: FontFamily? = null

internal fun installShaston(bytes: ByteArray) {
    loaded = FontFamily(Font(identity = "Shaston320", data = bytes))
}

@Composable
actual fun shastonFontFamily(): FontFamily = loaded ?: FontFamily.Default

/**
 * No Material underneath, so nothing to configure.
 *
 * The browser's widgets read the palette in [AppleII] and [AppleIIChrome] directly rather
 * than through a theme, because a theme would be a second place for the same six colours
 * to live. What magnification the watch is drawn at is decided further out, in `Main.kt`,
 * since it depends on the size of the window rather than on anything about the game.
 */
@Composable
actual fun OregonTrailTheme(content: @Composable () -> Unit) {
    content()
}

/** Skia has no font padding to switch off. See the common declaration. */
internal actual val cellPlatformStyle: androidx.compose.ui.text.PlatformTextStyle? = null
