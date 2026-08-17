package com.oregontrail.wear.ui.components

/**
 * What to tell the player the control is, on the one screen that steers rather than
 * scrolls — the rafting descent.
 *
 * The only piece of copy in the game that cannot be shared. Everywhere else the crown and
 * its browser stand-in are interchangeable enough to go unmentioned: a menu scrolls, and
 * how you made it scroll does not need saying. The raft is different because it is the
 * only screen with a control scheme of its own, and the briefing that explains it is the
 * whole reason a first descent is not a wreck. "Crown steers." is useless at a keyboard,
 * and "Arrows or wheel." is useless holding a phone.
 *
 * All three wordings live here rather than one in each platform's source, so that
 * `SteeringHintTest` can measure them all against the display. The ones that matter are
 * the ones the *other* platform's tests cannot see, which are exactly the strings that
 * would otherwise be checked by nobody.
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

    /**
     * The browser on a touchscreen, where neither of the above exists: no wheel, and no
     * keyboard, because nothing in this game ever asks for text and so nothing ever raises
     * one. Named separately rather than added to [KEYBOARD] because a phone player reading
     * "Arrows or wheel." concludes the game is unplayable and closes the tab, which is
     * exactly what happened.
     */
    const val TOUCH = "Drag to steer."
}

/** Whichever of [SteeringHints] this build is being played with, on whatever it is being
 *  played on. */
expect val steeringHint: String
