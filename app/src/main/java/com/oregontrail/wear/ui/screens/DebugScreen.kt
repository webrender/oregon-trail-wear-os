package com.oregontrail.wear.ui.screens

import androidx.compose.runtime.Composable
import com.oregontrail.wear.core.GameState
import com.oregontrail.wear.core.Inventory
import com.oregontrail.wear.core.LandmarkId
import com.oregontrail.wear.core.LandmarkKind
import com.oregontrail.wear.core.Outcome
import com.oregontrail.wear.core.PartyNames
import com.oregontrail.wear.core.Profession
import com.oregontrail.wear.core.Trail
import com.oregontrail.wear.ui.GameController
import com.oregontrail.wear.ui.Screen
import com.oregontrail.wear.ui.components.MenuChip
import com.oregontrail.wear.ui.components.RotaryColumn
import com.oregontrail.wear.ui.components.ScreenTitle

/**
 * Debug-only: every landmark, one tap from its arrival/river screen — for checking a
 * scene's art and layout without playing a run to reach it. Reachable from
 * [TitleScreen] behind `BuildConfig.DEBUG`. See [GameController.debugJumpTo].
 *
 * Oregon City is special-cased to the ending screen rather than [Screen.Arrival],
 * because that's the only screen a real run ever shows for it —
 * [com.oregontrail.wear.ui.demandedScreen] routes there via `isOver` before an arrival
 * screen would ever come up.
 */
@Composable
fun DebugScreen(controller: GameController) {
    RotaryColumn {
        item { ScreenTitle("Debug: jump to") }
        items(Trail.landmarks.size) { index ->
            val landmark = Trail.landmarks[index]
            MenuChip(
                label = landmark.name,
                secondaryLabel = when (landmark.kind) {
                    is LandmarkKind.River -> "River crossing"
                    is LandmarkKind.Fort -> "Fort"
                    LandmarkKind.Destination -> "Journey's end"
                    LandmarkKind.Departure, LandmarkKind.Waypoint -> null
                },
                onClick = {
                    val base = debugOutfittedState().copy(at = landmark.id, heading = null)
                    when {
                        landmark.id == LandmarkId.OREGON_CITY ->
                            controller.debugJumpTo(
                                base.copy(outcome = Outcome.Arrived),
                                Screen.GameOver,
                            )
                        landmark.kind is LandmarkKind.River ->
                            controller.debugJumpTo(base, Screen.River)
                        else ->
                            controller.debugJumpTo(base, Screen.Arrival)
                    }
                },
            )
        }
    }
}

/**
 * A run outfitted the way [com.oregontrail.wear.core.Store] would leave it after a
 * sensible shopping trip — mirrors the test suite's `TestStates.outfitted()`, which
 * lives outside the main source set and can't be imported here.
 */
private fun debugOutfittedState(): GameState =
    GameState.newGame(
        profession = Profession.BANKER,
        memberNames = PartyNames.defaultParty,
        startMonth = 4,
        seed = 12345L,
    ).copy(
        inventory = Inventory(
            oxen = 6,
            foodPounds = 1000,
            clothingSets = 5,
            bullets = 200,
            spareWheels = 2,
            spareAxles = 2,
            spareTongues = 2,
            cashCents = 200_00,
        ),
    )
