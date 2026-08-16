package com.oregontrail.wear.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.oregontrail.wear.ui.theme.AppleII
import com.oregontrail.wear.ui.theme.AppleIIChrome

/**
 * The curved scroll indicator that hugs the right edge of a round display.
 *
 * Wear supplies this as `PositionIndicator` and the watch build uses that one. The
 * browser has to draw its own, and drawing it is the point rather than an accident: a
 * straight scrollbar down the side of a circle is the single clearest way to make a web
 * page stop looking like a watch. It is the same shape Wear draws — a short bright arc
 * riding a longer dim one, both following the bezel.
 *
 * Both parameters are lambdas so they are read in the draw phase. Scrolling then repaints
 * this one canvas instead of recomposing the screen underneath it.
 */
@Composable
fun ScrollArc(
    fraction: () -> Float,
    visible: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (!visible()) return@Canvas

        // The arc sits just inside the bezel, spanning 44° centred on the right-hand side
        // — where a watch's is, and where a thumb is not.
        val inset = size.minDimension * 0.025f
        val diameter = size.minDimension - inset * 2
        val thickness = size.minDimension * 0.009f
        val sweep = 44f
        val start = -sweep / 2f

        drawArc(
            color = AppleIIChrome.DimGreen,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(diameter, diameter),
            style = Stroke(width = thickness, cap = StrokeCap.Round),
        )

        // A thumb a quarter of the track long, so that it reads as a position rather than
        // as a progress bar even when there is very little to scroll.
        val thumbSweep = sweep * 0.25f
        drawArc(
            color = AppleII.Green,
            startAngle = start + (sweep - thumbSweep) * fraction().coerceIn(0f, 1f),
            sweepAngle = thumbSweep,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(diameter, diameter),
            style = Stroke(width = thickness, cap = StrokeCap.Round),
        )
    }
}
