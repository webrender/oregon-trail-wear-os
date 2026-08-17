package com.oregontrail.wear.ui

import com.oregontrail.wear.ui.components.SteeringHints
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks that the rafting briefing's control line fits on one line.
 *
 * A sibling of `MapLabelTest`, guarding a different silent failure. Nothing crops here —
 * the line simply wraps, which looks harmless and is not: the briefing is five lines in a
 * fixed-height circle, and a sixth pushes the "Push off" chip past the bottom of the
 * display. The player then has to scroll a briefing to find the button, and a briefing you
 * have to scroll is one you skip — which on the one screen in the game with a control
 * scheme of its own means learning it by wrecking.
 *
 * All three wordings are checked, because the browser's two are the ones no other test on
 * this classpath can see. "Arrows or wheel steer." was the first attempt at one of them, at
 * 316px against a 275px line, and it wrapped exactly as described.
 */
class SteeringHintTest {

    private val font = Shaston(File("src/androidMain/assets/fonts/shaston_320.ttf"))

    /**
     * What one line of `ScreenText` gets, in device pixels on the 384-pixel watch.
     *
     * The briefing is a `SceneScrollColumn`, whose content sits in a column with 16dp of
     * padding either side — 160dp of the watch's 192 — and `ScreenText` then takes 0.86
     * of that to keep its ends off the curve. At density 2 that is 275 pixels.
     *
     * The browser is the same 192dp screen, whatever it is magnified by, so the budget is
     * the same there in every unit that matters. See `Main.kt`.
     */
    private val budgetPx = (192 - 32) * 0.86 * 2

    /** `caption2` is `cellStyle(2)`: two 8-pixel character cells. See `Typography.kt`. */
    private val captionPx = 16.0

    @Test
    fun `every steering hint fits on one line`() {
        for (hint in listOf(SteeringHints.CROWN, SteeringHints.KEYBOARD, SteeringHints.TOUCH)) {
            val width = font.widthPx(hint, captionPx)
            assertTrue(
                "\"$hint\" is ${width.toInt()}px wide against a ${budgetPx.toInt()}px line — " +
                    "it wraps, and the second line pushes \"Push off\" off the display",
                width <= budgetPx,
            )
        }
    }

    /**
     * The two lines under it, which share the budget and are the reason there is no room
     * for a sixth. If either of these ever wraps the hint's margin is gone too.
     */
    @Test
    fun `the rest of the briefing's short lines fit on one line`() {
        for (line in listOf("Miss the rocks.", "Land at sign three.")) {
            val width = font.widthPx(line, captionPx)
            assertTrue(
                "\"$line\" is ${width.toInt()}px wide against a ${budgetPx.toInt()}px line",
                width <= budgetPx,
            )
        }
    }
}
