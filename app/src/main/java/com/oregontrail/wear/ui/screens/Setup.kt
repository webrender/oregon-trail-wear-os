package com.oregontrail.wear.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.oregontrail.wear.BuildConfig
import com.oregontrail.wear.core.Profession
import com.oregontrail.wear.core.TrailDate
import com.oregontrail.wear.ui.GameController
import com.oregontrail.wear.ui.Screen
import com.oregontrail.wear.ui.components.Gap
import com.oregontrail.wear.ui.components.MenuChip
import com.oregontrail.wear.ui.components.RotaryColumn
import com.oregontrail.wear.ui.components.SceneScrollColumn
import com.oregontrail.wear.ui.components.ScreenText
import com.oregontrail.wear.ui.components.ScreenTitle
import com.oregontrail.wear.ui.money
import com.oregontrail.wear.ui.theme.AppleIIChrome

@Composable
fun TitleScreen(controller: GameController) {
    SceneScrollColumn(background = "title_banner", twinkle = true) {
        Gap(4)
        ScreenTitle("THE OREGON TRAIL")
        Gap(12)
        if (controller.hasSave) {
            MenuChip(
                label = "Continue",
                secondaryLabel = "Resume your journey",
                primary = true,
                onClick = { controller.resumeSavedGame() },
            )
            Gap(6)
        }
        MenuChip(
            label = "New Journey",
            primary = !controller.hasSave,
            onClick = { controller.startNewGame() },
        )
        Gap(6)
        MenuChip(label = "High Scores", onClick = { controller.showHighScores() })
        if (BuildConfig.DEBUG) {
            Gap(6)
            MenuChip(label = "Debug: jump to...", onClick = { controller.go(Screen.Debug) })
        }
    }
}

/**
 * Profession, which is this game's difficulty setting.
 *
 * The starting money and the score multiplier are both shown because they are the whole
 * trade-off — a farmer's x3 only means something next to the $400 it comes with.
 */
@Composable
fun ProfessionScreen(controller: GameController) {
    RotaryColumn {
        item { ScreenTitle("Who are you?") }
        items(Profession.entries.size) { index ->
            val profession = Profession.entries[index]
            MenuChip(
                label = profession.displayName,
                secondaryLabel = "${money(profession.startingCents)}  " +
                    "x${profession.scoreMultiplier}  ${profession.difficulty}",
                onClick = { controller.chooseProfession(profession) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Departure month — the other half of the difficulty decision, and the more interesting
 * one, because it is a gamble rather than a setting. Leave early and the grass has not
 * grown; leave late and the mountains fill with snow before you reach them.
 */
@Composable
fun MonthScreen(controller: GameController) {
    RotaryColumn {
        item { ScreenTitle("When do you leave?") }
        item {
            ScreenText(
                "Leave too early and there is no grass. Too late and the snow beats you " +
                    "to the mountains.",
                color = AppleIIChrome.MutedGreen,
            )
        }
        items(TrailDate.DEPARTURE_MONTHS.count()) { index ->
            val month = TrailDate.DEPARTURE_MONTHS.first + index
            MenuChip(
                label = TrailDate.MONTH_NAMES[month - 1],
                secondaryLabel = departureAdvice(month),
                onClick = { controller.chooseMonth(month) },
            )
        }
    }
}

private fun departureAdvice(month: Int): String = when (month) {
    3 -> "Muddy, thin grass"
    4 -> "Early, but workable"
    5 -> "The usual choice"
    6 -> "Hot, and getting late"
    else -> "Snow will find you"
}
