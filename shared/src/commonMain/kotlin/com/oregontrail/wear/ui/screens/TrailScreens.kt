package com.oregontrail.wear.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.oregontrail.wear.ui.theme.AppleIIType
import com.oregontrail.wear.ui.components.Text
import com.oregontrail.wear.core.Good
import com.oregontrail.wear.core.Pace
import com.oregontrail.wear.core.Rations
import com.oregontrail.wear.ui.GameController
import com.oregontrail.wear.ui.Screen
import com.oregontrail.wear.ui.art.ArtNames
import com.oregontrail.wear.ui.art.SCENE_HEIGHT
import com.oregontrail.wear.ui.art.SCENE_WIDTH
import com.oregontrail.wear.ui.art.Scene
import com.oregontrail.wear.ui.art.Sprite
import com.oregontrail.wear.ui.components.Gap
import com.oregontrail.wear.ui.components.IconValue
import com.oregontrail.wear.ui.components.MenuChip
import com.oregontrail.wear.ui.components.RotaryColumn
import com.oregontrail.wear.ui.components.RotaryScrollColumn
import com.oregontrail.wear.ui.components.ScreenText
import com.oregontrail.wear.ui.components.ScreenTitle
import com.oregontrail.wear.ui.components.StaticScreen
import com.oregontrail.wear.ui.counted
import com.oregontrail.wear.ui.describeQuantity
import com.oregontrail.wear.ui.money
import com.oregontrail.wear.ui.theme.AppleII
import com.oregontrail.wear.ui.theme.AppleIIChrome
import kotlinx.coroutines.delay

/** How long a day of travel takes to watch. A whole run is 150-ish days. */
private const val DAY_MILLIS = 1_000L

/** How long each frame of the walk cycle holds. */
private const val WALK_FRAME_MILLIS = 240L

/**
 * Where the wagon stands in the 128x64 scene.
 *
 * The wagon does not travel across the screen. It walks on the spot in the middle, and
 * the world is implied to move past — which is what the 1985 version did, and what the
 * two-frame walk cycle in the art brief is built for. A wagon that actually crossed the
 * frame would have to leave it, and there is nowhere for it to go.
 */
private const val WAGON_WIDTH = 56
private const val WAGON_HEIGHT = 28
private const val WAGON_X = (SCENE_WIDTH - WAGON_WIDTH) / 2

/**
 * Standing on the bottom edge of the scene rather than on the horizon, because the
 * horizon moves: the terrain art puts it anywhere from a third of the way down (the
 * Rockies) to four fifths (the plains), and only the very bottom of the frame is ground
 * in every one of them.
 */
private const val WAGON_Y = SCENE_HEIGHT - WAGON_HEIGHT

/**
 * Off to the right of the wagon, clear of its walk cycle and the terrain's usual busy
 * middle third.
 */
private const val MARKER_WIDTH = 16
private const val MARKER_HEIGHT = 20
private const val MARKER_X = 104
private const val MARKER_Y = SCENE_HEIGHT - MARKER_HEIGHT

/**
 * Roughly one day in [MARKER_PERIOD], the party passes a roadside marker — another
 * party's grave, or a signpost. Purely a flavour beat, so it's derived straight from
 * [GameState.dayOfJourney] rather than rolled from the run's own RNG: nothing about it
 * needs to be unpredictable or to survive a save, only to not flicker on and off between
 * recompositions of the same day.
 */
private const val MARKER_PERIOD = 13

/**
 * The travel screen: a wagon, a landscape, and days going by.
 *
 * Days advance on a timer rather than on a tap. This is the screen the player looks at
 * for most of a run, and making them tap 150 times to cross the continent would turn the
 * journey into a chore. Tapping instead opens the menu, which is the only thing there is
 * to do while travelling.
 */
@Composable
fun TrailScreen(controller: GameController) {
    val state = controller.game

    // The engine refuses to advance while a decision is outstanding, and the controller
    // no-ops when something is waiting to be read, so this loop can be unconditional.
    LaunchedEffect(Unit) {
        while (true) {
            delay(DAY_MILLIS)
            controller.advanceDay()
        }
    }

    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(WALK_FRAME_MILLIS)
            frame = 1 - frame
        }
    }

    val moving = !controller.cannotMove
    val wagon = if (moving) ArtNames.wagonWalk[frame] else ArtNames.WAGON_STOPPED
    val heading = state.heading ?: state.at

    StaticScreen(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { controller.go(Screen.TrailMenu) }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconValue(
                    iconArt = ArtNames.weather(state.weather),
                    value = state.date.toString(),
                )
            }
            Gap(4)
            Scene(
                background = ArtNames.terrain(heading, state.weather),
                // 0.85 rather than full width so the scene's corners stay clear of the
                // bezel on a round display.
                modifier = Modifier.fillMaxWidth(0.85f).aspectRatio(2f),
                sprites = buildList {
                    add(Sprite(wagon, WAGON_X, WAGON_Y, WAGON_WIDTH, WAGON_HEIGHT))
                    if (state.dayOfJourney > 0 && state.dayOfJourney % MARKER_PERIOD == 0) {
                        add(
                            Sprite(
                                ArtNames.TRAIL_MARKER,
                                MARKER_X, MARKER_Y, MARKER_WIDTH, MARKER_HEIGHT,
                            )
                        )
                    }
                },
            )
            Gap(4)
            Text(
                text = if (moving) {
                    counted(state.milesToNextLandmark, "mile") +
                        " to ${state.headingLandmark?.name ?: "?"}"
                } else {
                    "The wagon cannot move"
                },
                color = if (moving) AppleII.Green else AppleII.Orange,
                textAlign = TextAlign.Center,
                style = AppleIIType.caption1,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(0.8f),
            )
            // The bitmap font has no descender slack to spare, so two adjacent blocks of
            // text sit close enough to read as one. This gap is doing real work.
            Gap(6)
            Text(
                text = controller.ticker ?: "Tap for options",
                color = AppleIIChrome.MutedGreen,
                textAlign = TextAlign.Center,
                style = AppleIIType.caption3,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(0.8f),
            )
        }
    }
}

@Composable
fun TrailMenuScreen(controller: GameController) {
    val state = controller.game
    RotaryColumn {
        item { ScreenTitle(state.date.toString()) }
        item {
            MenuChip(
                label = "Continue on",
                primary = true,
                onClick = { controller.go(Screen.Trail) },
            )
        }
        item {
            MenuChip(
                label = "Rest a day",
                secondaryLabel = "Health up, food eaten",
                onClick = { controller.restOneDay() },
            )
        }
        item {
            MenuChip(
                label = "Check supplies",
                onClick = { controller.go(Screen.Supplies) },
            )
        }
        item {
            MenuChip(
                label = "Look at the map",
                secondaryLabel = counted(state.totalMiles, "mile") + " so far",
                onClick = { controller.go(Screen.Map) },
            )
        }
        item {
            MenuChip(
                label = "Go hunting",
                secondaryLabel = if (controller.canHunt) {
                    controller.huntGround?.displayName
                } else {
                    "Out of ammunition"
                },
                enabled = controller.canHunt,
                onClick = { controller.startHunt() },
            )
        }
        item {
            MenuChip(
                label = "Change pace",
                secondaryLabel = state.pace.displayName,
                onClick = { controller.go(Screen.ChoosePace) },
            )
        }
        item {
            MenuChip(
                label = "Change rations",
                secondaryLabel = state.rations.displayName,
                onClick = { controller.go(Screen.ChooseRations) },
            )
        }
        if (state.store != null) {
            item {
                MenuChip(
                    label = "Buy supplies",
                    secondaryLabel = state.store?.name,
                    onClick = { controller.go(Screen.Store) },
                )
            }
        }
        item {
            MenuChip(
                label = "Abandon journey",
                secondaryLabel = "Ends this run",
                onClick = { controller.go(Screen.ConfirmAbandon) },
            )
        }
    }
}

/**
 * "Are you sure?" — [GameController.abandonRun] clears the save with no way back, so
 * this is the one action in the trail menu that doesn't fire on the first tap. The safe
 * option is [primary] rather than the destructive one, matching how a confirmation
 * dialog should default: a mis-tap should have to happen twice to actually cost a run.
 */
@Composable
fun ConfirmAbandonScreen(controller: GameController) {
    RotaryScrollColumn {
        ScreenTitle("Abandon journey?")
        ScreenText(
            "This ends your run for good. There is no going back.",
            color = AppleII.Orange,
        )
        Gap(12)
        MenuChip(
            label = "No, keep going",
            primary = true,
            onClick = { controller.go(Screen.TrailMenu) },
        )
        Gap(6)
        MenuChip(
            label = "Yes, start over",
            onClick = {
                controller.abandonRun()
                controller.startNewGame()
            },
        )
    }
}

@Composable
fun SuppliesScreen(controller: GameController) {
    val state = controller.game
    val inventory = state.inventory

    RotaryColumn {
        item { ScreenTitle("Supplies") }
        item {
            IconValue("icon_health", "${state.party.health.displayName}, ${state.party.survivorCount} alive")
        }
        // Same phrasing as a trade offer, and for the same reason: these are internal
        // units, not the store's purchase units. Sharing describeQuantity keeps the
        // singular/plural agreement in one place rather than two.
        item { IconValue("icon_food", Good.FOOD.describeQuantity(inventory.foodPounds)) }
        item { IconValue("icon_ox", Good.OXEN.describeQuantity(inventory.oxen)) }
        item { IconValue("icon_clothing", Good.CLOTHING.describeQuantity(inventory.clothingSets)) }
        item { IconValue("icon_bullets", Good.BULLETS.describeQuantity(inventory.bullets)) }
        item { IconValue("icon_wheel", Good.SPARE_WHEEL.describeQuantity(inventory.spareWheels)) }
        item { IconValue("icon_axle", Good.SPARE_AXLE.describeQuantity(inventory.spareAxles)) }
        item { IconValue("icon_tongue", Good.SPARE_TONGUE.describeQuantity(inventory.spareTongues)) }
        item { IconValue("icon_money", money(inventory.cashCents)) }
        item { IconValue("icon_wagon", counted(state.totalMiles, "mile")) }
        item {
            ScreenText(
                "Party: " + state.party.members.joinToString(", ") {
                    if (it.alive) it.name else "${it.name} (dead)"
                },
                color = AppleIIChrome.MutedGreen,
            )
        }
        item {
            MenuChip(label = "Back", onClick = { controller.go(Screen.TrailMenu) })
        }
    }
}

@Composable
fun PaceScreen(controller: GameController) {
    RotaryColumn {
        item { ScreenTitle("Pace") }
        items(Pace.entries.size) { index ->
            val pace = Pace.entries[index]
            MenuChip(
                label = pace.displayName,
                secondaryLabel = paceAdvice(pace),
                primary = pace == controller.game.pace,
                onClick = { controller.setPace(pace) },
            )
        }
    }
}

private fun paceAdvice(pace: Pace): String = when (pace) {
    Pace.STEADY -> "Easy on the party"
    Pace.STRENUOUS -> "Faster, and it tells"
    Pace.GRUELING -> "As fast as you can go"
}

@Composable
fun RationsScreen(controller: GameController) {
    val state = controller.game
    RotaryColumn {
        item { ScreenTitle("Rations") }
        items(Rations.entries.size) { index ->
            val rations = Rations.entries[index]
            MenuChip(
                label = rations.displayName,
                secondaryLabel = "${rations.poundsPerPersonPerDay * state.party.survivorCount} lb a day",
                primary = rations == state.rations,
                onClick = { controller.setRations(rations) },
            )
        }
    }
}
