package com.oregontrail.wear.ui

import com.oregontrail.wear.core.GameState
import com.oregontrail.wear.core.Good
import com.oregontrail.wear.core.LandmarkKind

/**
 * Where the player is in the app.
 *
 * A plain sealed hierarchy rather than a navigation library: the graph is small, every
 * transition is driven by game state that already exists, and the back stack is one deep
 * everywhere it exists at all. A library would add a dependency and a second source of
 * truth for something [GameState] already knows.
 */
sealed interface Screen {

    /** Front page: start a new run, or resume a saved one. */
    data object Title : Screen

    data object ChooseProfession : Screen

    data object ChooseMonth : Screen

    /** The shop counter — a list of goods. */
    data object Store : Screen

    /** Buying a quantity of one good. */
    data class StoreItem(val good: Good) : Screen

    /** The travel screen, where days pass. */
    data object Trail : Screen

    /** The menu reached by tapping while travelling. */
    data object TrailMenu : Screen

    data object Supplies : Screen

    /** The hunting minigame, reached by choice from [TrailMenu] — never demanded. */
    data object Hunt : Screen

    /**
     * The trail map. Somewhere the player goes and looks, not a replacement for [Trail] —
     * see docs/adr/0006-map-screen.md.
     */
    data object Map : Screen

    data object ChoosePace : Screen

    data object ChooseRations : Screen

    /** Standing at a landmark that has just been reached. */
    data object Arrival : Screen

    /** At a river, choosing how to cross. */
    data object River : Screen

    /** Running the Columbia on a raft — the last leg, if the player chose the water. */
    data object Raft : Screen

    /** At a branch in the trail, choosing a route. */
    data object Fork : Screen

    /** The run is over — arrived, or everyone died. */
    data object GameOver : Screen

    /**
     * The high score table. Reachable from [Title] and from [GameOver], and nowhere else —
     * see [GameController.closeHighScores] for how it finds its way back without a back
     * stack.
     */
    data object HighScores : Screen

    /** "Are you sure?" before [GameController.abandonRun] — there is no undo. */
    data object ConfirmAbandon : Screen

    /**
     * Debug-only: jump straight to any landmark's arrival/river screen without playing
     * to it. Reachable only from [Screen.Title] behind `BuildInfo.isDebug` — see
     * [com.oregontrail.wear.ui.screens.DebugScreen].
     */
    data object Debug : Screen
}

/**
 * The screen a state *requires*, or null if the state imposes no particular screen.
 *
 * This exists because [GameState.awaitingDecision] is the engine's way of refusing to
 * advance, and something has to translate that refusal into the screen that resolves it.
 * Getting this wrong strands the player on a travel screen where days silently fail to
 * pass — which is exactly the failure [com.oregontrail.wear.core.TurnEngine.advanceDay]'s
 * guard was written to make loud, so it is worth testing directly.
 */
fun demandedScreen(state: GameState): Screen? = when {
    state.isOver -> Screen.GameOver
    !state.awaitingDecision -> null
    state.currentLandmark.kind is LandmarkKind.River -> Screen.River
    state.currentLandmark.kind is LandmarkKind.Rapids -> Screen.Raft
    state.currentLandmark.isBranch -> Screen.Fork
    // Awaiting a decision with neither a river nor a branch to resolve would be a bug in
    // the trail table — a landmark with no way onward that is not the destination. Route
    // to the fork screen, which will show an empty list rather than hanging silently.
    else -> Screen.Fork
}

/** The screen to show once the player is done reading whatever they were shown. */
fun resumeScreen(state: GameState): Screen = demandedScreen(state) ?: Screen.Trail

/**
 * Resolves a screen the UI asked for against what [state] will actually allow.
 *
 * [Screen.Trail] and [Screen.TrailMenu] are the two screens that assume the wagon can
 * roll, and both are reachable by taps that don't consult the state at all — the trail
 * menu's "Continue on", and the supplies list's "Back", which returns to the trail menu
 * whichever screen the player opened it from. Standing at a branch, tapping "Check
 * supplies" and then "Back" lands on the trail menu; "Continue on" from there used to
 * put the player on the travel screen with the fork still unresolved, where
 * [GameState.awaitingDecision] makes every day a no-op. The wagon sits still, the date
 * never changes, and the readout reads "0 miles to ?" because there is no heading to
 * name. Only "Rest a day" escaped, because it re-routes through [resumeScreen] on its
 * way out.
 *
 * Fixing it here rather than at those two taps is deliberate: any future route to the
 * travel screen would otherwise reintroduce the same freeze, and nothing about the two
 * call sites makes their mistake visible.
 */
fun allowedScreen(state: GameState?, requested: Screen): Screen = when (requested) {
    Screen.Trail, Screen.TrailMenu -> state?.let { demandedScreen(it) } ?: requested
    else -> requested
}
