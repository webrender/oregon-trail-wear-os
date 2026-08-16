package com.oregontrail.wear.ui.components

/**
 * What to tell the player the control is, on the one screen that steers rather than
 * scrolls — the rafting descent.
 *
 * The only piece of copy in the game that cannot be shared. Everywhere else the crown and
 * its browser stand-in are interchangeable enough to go unmentioned: a menu scrolls, and
 * how you made it scroll does not need saying. The raft is different because it is the
 * only screen with a control scheme of its own, and the briefing that explains it is the
 * whole reason a first descent is not a wreck. "Crown steers." is useless at a keyboard.
 *
 * Both wordings live here rather than one in each platform's source, so that
 * `SteeringHintTest` can measure both against the display. The one that matters is the
 * one the *other* platform's tests cannot see, which is exactly the string that would
 * otherwise be checked by nobody.
 */
object SteeringHints {

    /** The watch. The crown is the only control the descent has. */
    const val CROWN = "Crown steers."

    /**
     * The browser. Both controls named, because neither is guessable and a trackpad has
     * no wheel — and kept to four words because the line above it already wraps to two
     * and a third line puts "Push off" below the fold. "Arrows or wheel steer." was the
     * first attempt and did exactly that.
     */
    const val KEYBOARD = "Arrows or wheel."
}

/** Whichever of [SteeringHints] this build is being played with. */
expect val steeringHint: String
