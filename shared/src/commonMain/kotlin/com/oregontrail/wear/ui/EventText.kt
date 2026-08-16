package com.oregontrail.wear.ui

import com.oregontrail.wear.core.GameEvent
import com.oregontrail.wear.core.Trail

/**
 * Turns engine events into the sentences the player actually reads.
 *
 * Separate from [GameEvent] so the engine stays free of presentation, and pure so the
 * wording can be tested without a device. Phrasing follows the original's register —
 * flat, declarative, no exclamation marks. "Amanda has died of typhoid" lands harder
 * than any amount of dressing.
 */
object EventText {

    /** The line shown for [event], or null for events the player needn't be told about. */
    fun describe(event: GameEvent): String? = when (event) {
        // Distance is on screen continuously; repeating it as a message is noise.
        is GameEvent.Traveled -> null

        is GameEvent.ArrivedAt -> "You have reached ${Trail[event.landmark].name}."
        is GameEvent.FellIll -> "${event.traveler} has ${event.ailment.displayName}."
        is GameEvent.Recovered -> "${event.traveler} has recovered."
        is GameEvent.Died -> "${event.traveler} has died of ${event.cause}."

        is GameEvent.OxDied -> when (event.remaining) {
            0 -> "An ox has died. You have no oxen left."
            1 -> "An ox has died. Only one remains."
            else -> "An ox has died. ${event.remaining} remain."
        }

        is GameEvent.WagonBroke ->
            if (event.hadSpare) {
                "A wagon ${event.part} broke. You fitted your spare."
            } else {
                "A wagon ${event.part} broke, and you have no spare. " +
                    "The day is lost to repairs."
            }

        is GameEvent.SuppliesStolen -> "Thieves took ${event.amount} of your ${event.what}."

        is GameEvent.RidersFought ->
            if (event.drivenOff) {
                "You drove them off. ${event.bulletsUsed} rounds spent."
            } else {
                "They took what they came for. ${event.bulletsUsed} rounds spent."
            }

        is GameEvent.RidersOutran ->
            if (event.miles > 0) {
                "You outran them, and gave up ${event.miles} miles doing it."
            } else {
                "You outran them. The party is spent."
            }

        is GameEvent.SuppliesFound -> "You found ${event.amount} of ${event.what}."
        is GameEvent.LostTime -> "You lost ${event.miles} miles to ${event.reason}."
        GameEvent.OutOfFood -> "There is not enough food. The party goes hungry."
        GameEvent.BadWater -> "You drank bad water. The party is weakened."
        GameEvent.PartyLost -> "Everyone has died. The journey ends here."
    }

    /**
     * Whether [event] should stop the wagon and wait for a tap.
     *
     * The dividing line is whether the player might want to *do* something about it. A
     * death, an arrival, or a wagon that cannot be repaired all change what the sensible
     * next move is; finding wild fruit does not. Pausing on everything would make the
     * travel screen unplayable — days pass every few hundred milliseconds — and pausing
     * on nothing would let a party die unnoticed.
     */
    fun pausesTravel(event: GameEvent): Boolean = when (event) {
        is GameEvent.Died,
        GameEvent.PartyLost,
        is GameEvent.ArrivedAt,
        is GameEvent.OxDied,
        is GameEvent.FellIll,
        is GameEvent.RidersFought,
        is GameEvent.RidersOutran,
        -> true

        is GameEvent.WagonBroke -> !event.hadSpare
        else -> false
    }
}
