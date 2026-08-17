package com.oregontrail.wear.ui.components

/**
 * Which of the browser's two wordings this visitor gets.
 *
 * Asked on each read rather than answered once at start-up. It is read on one screen, so
 * there is nothing to save by caching it, and a page that was opened on a laptop and is now
 * being driven from a paired touchscreen gets the right answer without being reloaded.
 */
actual val steeringHint: String
    get() = if (pointerIsCoarse()) SteeringHints.TOUCH else SteeringHints.KEYBOARD

/**
 * Whether the primary pointing device is a finger.
 *
 * The media query rather than a user-agent string, because it asks about the hardware in
 * the visitor's hand instead of about the software reporting it. A touchscreen laptop
 * answers `fine` and so gets the keyboard wording — which is the correct answer and not
 * merely a tolerable one, since it also has the arrow keys that wording names.
 */
private fun pointerIsCoarse(): Boolean = js("window.matchMedia('(pointer: coarse)').matches")
