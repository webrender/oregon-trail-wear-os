package com.oregontrail.wear.ui

import androidx.compose.runtime.Composable
import com.oregontrail.wear.ui.screens.ArrivalScreen
import com.oregontrail.wear.ui.screens.CrossingResultScreen
import com.oregontrail.wear.ui.screens.EncounterScreen
import com.oregontrail.wear.ui.screens.EventScreen
import com.oregontrail.wear.ui.screens.ForkScreen
import com.oregontrail.wear.ui.screens.GameOverScreen
import com.oregontrail.wear.ui.screens.HuntingScreen
import com.oregontrail.wear.ui.screens.MonthScreen
import com.oregontrail.wear.ui.screens.PaceScreen
import com.oregontrail.wear.ui.screens.ProfessionScreen
import com.oregontrail.wear.ui.screens.RationsScreen
import com.oregontrail.wear.ui.screens.RiverScreen
import com.oregontrail.wear.ui.screens.StoreItemScreen
import com.oregontrail.wear.ui.screens.StoreScreen
import com.oregontrail.wear.ui.screens.SuppliesScreen
import com.oregontrail.wear.ui.screens.TitleScreen
import com.oregontrail.wear.ui.screens.TrailMenuScreen
import com.oregontrail.wear.ui.screens.TrailScreen
import com.oregontrail.wear.ui.theme.OregonTrailTheme

/**
 * The whole app: one composable that decides what is on screen.
 *
 * Two things take precedence over [GameController.screen], and both are interruptions
 * rather than destinations — a river result and an unread event can each arrive while
 * the player is on any screen, and neither should be navigable *to*. Checking them here
 * rather than inside each screen keeps that rule in one place.
 */
@Composable
fun OregonTrailApp(controller: GameController) {
    OregonTrailTheme {
        when {
            controller.crossingResult != null -> CrossingResultScreen(controller)
            controller.pendingEvents.isNotEmpty() -> EventScreen(controller)
            controller.pendingEncounter != null -> EncounterScreen(controller)
            else -> Destination(controller)
        }
    }
}

@Composable
private fun Destination(controller: GameController) {
    when (val screen = controller.screen) {
        Screen.Title -> TitleScreen(controller)
        Screen.ChooseProfession -> ProfessionScreen(controller)
        Screen.ChooseMonth -> MonthScreen(controller)
        Screen.Store -> StoreScreen(controller)
        is Screen.StoreItem -> StoreItemScreen(controller, screen.good)
        Screen.Trail -> TrailScreen(controller)
        Screen.TrailMenu -> TrailMenuScreen(controller)
        Screen.Supplies -> SuppliesScreen(controller)
        Screen.Hunt -> HuntingScreen(controller)
        Screen.ChoosePace -> PaceScreen(controller)
        Screen.ChooseRations -> RationsScreen(controller)
        Screen.Arrival -> ArrivalScreen(controller)
        Screen.River -> RiverScreen(controller)
        Screen.Fork -> ForkScreen(controller)
        Screen.GameOver -> GameOverScreen(controller)
    }
}
