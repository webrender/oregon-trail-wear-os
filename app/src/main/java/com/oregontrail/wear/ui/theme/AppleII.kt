package com.oregontrail.wear.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The Apple II hi-res palette.
 *
 * These are the values AppleWin actually renders (the "Linards tweaked" set from its
 * `source/RGBMonitor.cpp`), not the naive full-saturation primaries — real hardware
 * produced these colours via NTSC artifacting, so pure #00FF00 green reads as wrong to
 * anyone who remembers the machine.
 *
 * Hi-res was 280x192, but adjacent pixel pairs formed a single colour cell, giving an
 * effective colour resolution of 140x192 with colour constrained per 7-pixel byte
 * group. Art for this game is authored against that constraint deliberately — see
 * docs/reference/game-mechanics-1985.md.
 */
object AppleII {
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    val Green = Color(0xFF38CB00)
    val Violet = Color(0xFFC734FF)
    val Orange = Color(0xFFF25E00)
    val Blue = Color(0xFF0DA1FF)

    /** The six colours a hi-res screen could actually show. */
    val palette = listOf(Black, White, Green, Violet, Orange, Blue)
}
