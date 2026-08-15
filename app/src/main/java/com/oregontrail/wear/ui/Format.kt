package com.oregontrail.wear.ui

import com.oregontrail.wear.core.Good
import com.oregontrail.wear.core.TrailDate

/**
 * Formats a cent amount the way a shopkeeper would write it.
 *
 * Whole dollars lose their `.00`, because most prices in this game are whole dollars and
 * a column of `$1600.00` reads as noise on a 1.2" screen. Fractions keep both digits.
 */
fun money(cents: Int): String {
    val sign = if (cents < 0) "-" else ""
    val absolute = kotlin.math.abs(cents)
    val dollars = absolute / 100
    val remainder = absolute % 100
    val grouped = dollars.toString()
        .reversed()
        .chunked(3)
        .joinToString(",")
        .reversed()
    return if (remainder == 0) {
        "$sign\$$grouped"
    } else {
        "$sign\$$grouped.${remainder.toString().padStart(2, '0')}"
    }
}

/**
 * How much of a good one press of the stepper buys.
 *
 * Food is the outlier: it is priced per pound and a run needs hundreds of them, so a
 * step of one would mean several hundred taps at the counter. Everything else is bought
 * in ones or twos and steps accordingly.
 */
val Good.purchaseStep: Int
    get() = if (this == Good.FOOD) 25 else 1

/**
 * The purchase unit abbreviated for the store list.
 *
 * A row there carries an icon, a price and a holding, and at x2 that leaves room for
 * about twenty characters — "yoke of 2" alone pushed the holding off the end. The full
 * unit is spelled out on the buy screen instead, which has the room and is where knowing
 * that a yoke is two oxen actually changes a decision.
 */
val Good.shortUnit: String
    get() = when (this) {
        Good.OXEN -> "yoke"
        Good.FOOD -> "lb"
        Good.CLOTHING -> "set"
        Good.BULLETS -> "box"
        Good.SPARE_WHEEL, Good.SPARE_AXLE, Good.SPARE_TONGUE -> "ea"
    }

/**
 * A sane ceiling for the stepper, independent of what the player can afford.
 *
 * Without one, a banker with $1,600 could be offered 8,000 pounds of food, and the
 * stepper would be useless. These are generous — well past what a winning run carries —
 * so the cap never binds on a real decision, it just keeps the control usable.
 */
val Good.purchaseCeiling: Int
    get() = when (this) {
        Good.OXEN -> 5
        Good.FOOD -> 1_000
        Good.CLOTHING -> 12
        Good.BULLETS -> 30
        Good.SPARE_WHEEL, Good.SPARE_AXLE, Good.SPARE_TONGUE -> 5
    }

/**
 * A count and its noun, agreeing in number — "1 ox", "2 oxen", "0 wheels".
 *
 * Zero takes the plural, as English does. Only one of these nouns is irregular, hence the
 * explicit [plural] rather than always appending an "s".
 */
fun counted(quantity: Int, singular: String, plural: String = "${singular}s"): String =
    "$quantity ${if (quantity == 1) singular else plural}"

/**
 * A quantity of [good] in its own internal unit — "3 oxen", "40 lb food", "20 bullets" —
 * as opposed to [shortUnit], which is the *purchase* unit sold at the store (a box of 20
 * bullets, a yoke of 2 oxen). [com.oregontrail.wear.core.Encounters] trades goods
 * directly in the internal unit, so a trade offer needs this instead — labelling 33
 * individual bullets "33 box" would overstate the offer twentyfold.
 *
 * A trade offer can be for exactly one of anything — [com.oregontrail.wear.core.Encounters]
 * rolls its quantities from 1 — and so can a wagon's holding, so every countable noun here
 * has to inflect. "1 wheels" was showing up on the trader's offer line. Food and clothing
 * don't inflect: both are phrased as mass nouns ("40 lb food", "3 clothing") that read the
 * same at any count, and spelling clothing out as "sets of clothing" would overrun the
 * supplies row on the 384 px watch.
 */
fun Good.describeQuantity(quantity: Int): String = when (this) {
    Good.OXEN -> counted(quantity, "ox", "oxen")
    Good.FOOD -> "$quantity lb food"
    Good.CLOTHING -> "$quantity clothing"
    Good.BULLETS -> counted(quantity, "bullet")
    Good.SPARE_WHEEL -> counted(quantity, "wheel")
    Good.SPARE_AXLE -> counted(quantity, "axle")
    Good.SPARE_TONGUE -> counted(quantity, "tongue")
}

/**
 * A place in a ranking — "1st", "2nd", "11th".
 *
 * Only ever called with 1..10 today, since that is the height of the high score table,
 * but the teens are handled anyway: they are the one case a rule written from the last
 * digit alone gets wrong, and they cost two lines to get right.
 */
fun ordinal(place: Int): String {
    val suffix = if (place % 100 in 11..13) {
        "th"
    } else {
        when (place % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
    }
    return "$place$suffix"
}

/**
 * The date with the month abbreviated and the year dropped.
 *
 * Used on the lines beneath a scene, where the full "September 21, 1848" runs to 18
 * characters and wraps, orphaning a word under a landmark name. The year is always 1848,
 * so it carries no information after the first screen that states it.
 */
val TrailDate.short: String get() = "${monthName.take(3)} $day"
