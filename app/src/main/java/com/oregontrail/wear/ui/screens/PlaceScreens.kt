package com.oregontrail.wear.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oregontrail.wear.core.CrossingMethod
import com.oregontrail.wear.core.CrossingResult
import com.oregontrail.wear.core.Encounter
import com.oregontrail.wear.core.Outcome
import com.oregontrail.wear.core.Rivers
import com.oregontrail.wear.core.Scoring
import com.oregontrail.wear.ui.EventText
import com.oregontrail.wear.ui.GameController
import com.oregontrail.wear.ui.Screen
import com.oregontrail.wear.ui.art.ArtNames
import com.oregontrail.wear.ui.art.PixelArtImage
import com.oregontrail.wear.ui.components.Gap
import com.oregontrail.wear.ui.components.MenuChip
import com.oregontrail.wear.ui.components.RotaryScrollColumn
import com.oregontrail.wear.ui.components.SceneScrollColumn
import com.oregontrail.wear.ui.components.ScreenText
import com.oregontrail.wear.ui.components.ScreenTitle
import com.oregontrail.wear.ui.components.StaticScreen
import com.oregontrail.wear.ui.counted
import com.oregontrail.wear.ui.describeQuantity
import com.oregontrail.wear.ui.money
import com.oregontrail.wear.ui.short
import com.oregontrail.wear.ui.theme.AppleII
import com.oregontrail.wear.ui.theme.AppleIIChrome

/**
 * Standing at a landmark.
 *
 * The scene comes before the options deliberately. Landmarks are the reward for the days
 * of nothing that precede them — showing Chimney Rock and then asking what to do is a
 * different experience from a menu with a place name at the top.
 */
@Composable
fun ArrivalScreen(controller: GameController) {
    val state = controller.game
    val landmark = state.currentLandmark

    SceneScrollColumn(background = ArtNames.landmark(landmark.id)) {
        Gap(4)
        ScreenTitle(landmark.name)
        ScreenText(
            "${state.date.short} · ${state.totalMiles} miles",
            color = AppleIIChrome.MutedGreen,
            small = true,
        )
        Gap(10)
        MenuChip(
            label = "Continue on",
            primary = true,
            onClick = { controller.go(com.oregontrail.wear.ui.resumeScreen(state)) },
        )
        Gap(6)
        if (state.store != null) {
            MenuChip(
                label = "Buy supplies",
                secondaryLabel = state.store?.name,
                onClick = { controller.go(Screen.Store) },
            )
            Gap(6)
        }
        MenuChip(label = "Rest a day", onClick = { controller.restOneDay() })
        Gap(6)
        MenuChip(label = "Check supplies", onClick = { controller.go(Screen.Supplies) })
    }
}

/**
 * The river decision.
 *
 * Depth is stated in feet next to the three-foot threshold rather than as a verdict,
 * because judging it is the decision. The warning below it only appears once the river
 * is actually over the line.
 */
@Composable
fun RiverScreen(controller: GameController) {
    val state = controller.game
    val conditions = controller.riverConditions ?: return
    val landmark = state.currentLandmark
    val available = conditions.available(state.inventory.cashCents)

    SceneScrollColumn(background = ArtNames.landmark(landmark.id)) {
        Gap(4)
        ScreenTitle(landmark.name.removeSuffix(" Crossing"))
        ScreenText(
            "${conditions.widthFeet}ft wide, ${conditions.depthFeet}ft deep",
            color = AppleII.Green,
            small = true,
        )
        if (conditions.isDangerousToFord) {
            ScreenText(
                "Over ${Rivers.DANGEROUS_DEPTH_FEET.toInt()} feet. Fording is a gamble.",
                color = AppleII.Orange,
                small = true,
            )
        }
        Gap(10)
        for (method in available) {
            MenuChip(
                label = method.displayName,
                secondaryLabel = crossingAdvice(method, controller),
                primary = method == CrossingMethod.FORD && !conditions.isDangerousToFord,
                onClick = { controller.cross(method) },
            )
            Gap(6)
        }
    }
}

private fun crossingAdvice(method: CrossingMethod, controller: GameController): String =
    when (method) {
        CrossingMethod.FORD -> "Free, if it is shallow"
        CrossingMethod.CAULK -> "Risky on wide rivers"
        CrossingMethod.FERRY ->
            money(controller.riverConditions?.ferryCostCents ?: 0) + ", and safe"
        CrossingMethod.WAIT -> "3 days; it may drop"
    }

/** What the river did. Shown once, then dismissed by a tap. */
@Composable
fun CrossingResultScreen(controller: GameController) {
    val result = controller.crossingResult ?: return
    val method = controller.crossingMethod

    SceneScrollColumn(background = crossingArt(result, method, controller)) {
        Gap(4)
        ScreenTitle(crossingHeadline(result))
        ScreenText(crossingDetail(result), color = AppleII.White)
        Gap(12)
        MenuChip(
            label = "Continue",
            primary = true,
            onClick = { controller.dismissCrossing() },
        )
    }
}

private fun crossingArt(
    result: CrossingResult,
    method: CrossingMethod?,
    controller: GameController,
): String = when {
    result is CrossingResult.Capsized || result is CrossingResult.LostSupplies -> "river_capsize"
    method == CrossingMethod.FERRY -> "river_ferry"
    method == CrossingMethod.CAULK -> "river_caulk"
    method == CrossingMethod.FORD -> "river_ford"
    // Waiting leaves you looking at the same river you were looking at before.
    else -> ArtNames.landmark(controller.game.currentLandmark.id)
}

private fun crossingHeadline(result: CrossingResult): String = when (result) {
    CrossingResult.Safe -> "Across safely"
    is CrossingResult.LostSupplies -> "A hard crossing"
    is CrossingResult.Capsized -> "The wagon tipped"
    is CrossingResult.Waited -> "You waited"
}

private fun crossingDetail(result: CrossingResult): String = when (result) {
    CrossingResult.Safe -> "The wagon is on the far bank, and everything with it."

    is CrossingResult.LostSupplies -> buildString {
        append("The river took ${result.foodPounds} lb of food")
        if (result.clothingSets > 0) {
            append(" and ${counted(result.clothingSets, "set", "sets")} of clothing")
        }
        append(".")
    }

    is CrossingResult.Capsized -> buildString {
        append("You lost ${result.foodPounds} lb of food")
        if (result.oxenLost > 0) append(" and ${counted(result.oxenLost, "ox", "oxen")}")
        append(".")
        if (result.drowned != null) append(" ${result.drowned} drowned.")
    }

    is CrossingResult.Waited ->
        "${result.days} days gone. The river is not the same one you looked at before."
}

/** A branch in the trail. */
@Composable
fun ForkScreen(controller: GameController) {
    val state = controller.game
    val landmark = state.currentLandmark

    SceneScrollColumn(background = ArtNames.landmark(landmark.id)) {
        Gap(4)
        ScreenTitle("Which way?")
        Gap(8)
        for (route in landmark.routes) {
            MenuChip(
                label = route.label.ifEmpty { com.oregontrail.wear.core.Trail[route.to].name },
                secondaryLabel = "${route.miles} miles",
                onClick = { controller.chooseRoute(route.to) },
            )
            Gap(6)
        }
    }
}

/**
 * A single event, held on screen until acknowledged.
 *
 * Rendered over whatever screen the player was on, because an event can interrupt travel,
 * a rest, or a river crossing, and all three want the same treatment: stop, say what
 * happened, wait.
 */
@Composable
fun EventScreen(controller: GameController) {
    val event = controller.pendingEvents.firstOrNull() ?: return
    val text = EventText.describe(event) ?: return
    val art = ArtNames.forEvent(event)

    StaticScreen(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { controller.dismissEvent() }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.75f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (art != null) {
                PixelArtImage(name = art, size = 120.dp)
                Gap(8)
            }
            ScreenText(text)
            Gap(8)
            ScreenText("Tap to continue", color = AppleIIChrome.MutedGreen)
        }
    }
}

/** The end of the run, either way it went. */
@Composable
fun GameOverScreen(controller: GameController) {
    val state = controller.game

    // The two outcomes get different containers rather than a shared one with
    // conditional padding: Arrived leads with a full-bleed scene, PartyLost with a
    // small centred portrait, and those two shapes don't share a sensible common
    // padding scheme. The trailing "New Journey" chip is repeated in both rather than
    // forced into one — it's three lines, not worth threading a shared container for.
    when (state.outcome) {
        Outcome.Arrived -> SceneScrollColumn(background = "wagon_arrival") {
            Gap(4)
            ScreenTitle("Oregon City")
            ScreenText(
                "${state.date.short} · ${state.totalMiles} miles",
                color = AppleII.Green,
                small = true,
            )
            ScreenText(
                "Party health: ${state.party.health.displayName}",
                color = AppleIIChrome.MutedGreen,
                small = true,
            )
            Gap(12)

            val score = Scoring.score(state)
            for (line in score.lines) {
                ScreenText(
                    // The scoring table spells the party's health out inside its
                    // first label, which is too long for a centred line down here.
                    // Health already has its own line above, so drop the aside.
                    "${line.label.substringBefore(" (")}: ${line.points}",
                    color = AppleIIChrome.MutedGreen,
                    small = true,
                )
            }
            Gap(6)
            ScreenText(
                "${score.subtotal} x ${score.multiplier} (${score.professionName})",
                color = AppleII.White,
            )
            ScreenTitle("${score.total} points")

            Gap(16)
            MenuChip(
                label = "New Journey",
                primary = true,
                onClick = {
                    controller.abandonRun()
                    controller.startNewGame()
                },
            )
        }

        Outcome.PartyLost, null -> RotaryScrollColumn {
            PixelArtImage(name = "tombstone", size = 120.dp)
            Gap(8)
            ScreenTitle("The journey ends")
            ScreenText(
                "Everyone died, ${state.totalMiles} miles out.",
                color = AppleII.White,
            )

            Gap(16)
            MenuChip(
                label = "New Journey",
                primary = true,
                onClick = {
                    controller.abandonRun()
                    controller.startNewGame()
                },
            )
        }
    }
}

/**
 * Someone met on the trail. A portrait rather than a scene, since the point of an
 * encounter is the person, not the place — see [Encounter].
 */
@Composable
fun EncounterScreen(controller: GameController) {
    val encounter = controller.pendingEncounter ?: return
    val state = controller.game

    RotaryScrollColumn {
        PixelArtImage(name = ArtNames.forEncounter(encounter), size = 96.dp)
        Gap(8)
        when (encounter) {
            is Encounter.Traveler -> {
                ScreenTitle("A fellow traveller")
                ScreenText(encounter.tip, color = AppleII.White)
                Gap(12)
                MenuChip(
                    label = "Continue on",
                    primary = true,
                    onClick = { controller.dismissEncounter() },
                )
            }

            is Encounter.Trade -> {
                ScreenTitle("A trader")
                ScreenText(
                    "${encounter.wants.describeQuantity(encounter.wantsQuantity)} for " +
                        encounter.gives.describeQuantity(encounter.givesQuantity),
                    color = AppleII.White,
                )
                Gap(10)
                MenuChip(label = "Trade", primary = true, onClick = { controller.acceptTrade() })
                Gap(6)
                MenuChip(label = "Decline", onClick = { controller.dismissEncounter() })
            }

            is Encounter.Guide -> {
                ScreenTitle("A guide")
                ScreenText("Offers advice for a fee", color = AppleII.White)
                Gap(10)
                MenuChip(
                    label = "Hire",
                    secondaryLabel = money(encounter.costCents),
                    primary = true,
                    enabled = state.inventory.cashCents >= encounter.costCents,
                    onClick = { controller.hireGuide() },
                )
                Gap(6)
                MenuChip(label = "Decline", onClick = { controller.dismissEncounter() })
            }
        }
    }
}
