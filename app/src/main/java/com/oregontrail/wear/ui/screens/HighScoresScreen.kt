package com.oregontrail.wear.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.oregontrail.wear.core.HighScoreEntry
import com.oregontrail.wear.ui.GameController
import com.oregontrail.wear.ui.components.Gap
import com.oregontrail.wear.ui.components.MenuChip
import com.oregontrail.wear.ui.components.RotaryScrollColumn
import com.oregontrail.wear.ui.components.ScreenText
import com.oregontrail.wear.ui.components.ScreenTitle
import com.oregontrail.wear.ui.theme.AppleII
import com.oregontrail.wear.ui.theme.AppleIIChrome

/**
 * The high score table, best first.
 *
 * A plain scrolling column rather than the usual [com.oregontrail.wear.ui.components.RotaryColumn]:
 * a scaling list draws its off-centre items at fractional scale, and this font's glyphs
 * only stay crisp at whole multiples of the 8-pixel cell — see
 * [com.oregontrail.wear.ui.theme.appleIITypography]. A menu of chips can absorb that; ten
 * rows of small text, most of which are off-centre at any moment, cannot.
 */
@Composable
fun HighScoresScreen(controller: GameController) {
    val table = controller.scores

    RotaryScrollColumn(topPadding = 32.dp) {
        ScreenTitle("High Scores")
        Gap(10)

        if (table.isEmpty) {
            ScreenText(
                "No one has reached Oregon City yet.",
                color = AppleIIChrome.MutedGreen,
                small = true,
            )
        } else {
            table.entries.forEachIndexed { index, entry ->
                ScoreRow(
                    rank = index + 1,
                    entry = entry,
                    // With no name on a row, colour is the only thing that can pick out
                    // the run the player has just finished. Ranks are 1-based; the
                    // placement is too.
                    isLatest = controller.lastPlacement == index + 1,
                )
            }
        }

        Gap(14)
        MenuChip(label = "Back", onClick = { controller.closeHighScores() })
    }
}

/**
 * One row: place, profession, points.
 *
 * Profession rather than a name because it is what makes two totals comparable — it is
 * the score multiplier — and because ADR 0004 rules out asking the player to type one.
 *
 * Laid out as three columns rather than a single centred string so the point totals line
 * up on their right edge, which is the whole reason a table beats a list here. The rank
 * column is fixed at the width of two digits so the professions align under each other
 * once the list reaches ten.
 *
 * The width was arrived at on the device, and both directions were tried and rejected.
 * "Carpenter" — the longest profession, at nine characters — is what sets it. At the
 * shared [com.oregontrail.wear.ui.components.readoutWidth] with a 10dp gutter it rendered
 * as "Carpente", and at 0.74 as "Carpent", both times with no ellipsis to say so: the
 * default overflow chops mid-glyph. 0.86 is the width that fits it on the 384-pixel watch,
 * and the rank column and gutter are as narrow as two digits allow so that as much of the
 * row as possible goes to the column that actually needs it.
 *
 * Hence the cap, which is the part worth understanding. A fraction of the width is the
 * wrong sizing model for this row on its own: the font is sized in *device pixels* (see
 * [com.oregontrail.wear.ui.theme.appleIITypography]), so the text is exactly as wide on a
 * 454-pixel screen as on a 384-pixel one, while 0.86 of the width is not. On the larger
 * emulator the extra 60 pixels went to the weighted middle column and pushed the totals
 * out under the bezel, which on a *round* display cuts the bottom rows short — the digits
 * were being clipped on a screen with more room than the one they fit on. The cap holds
 * the row to the width the copy actually needs, and the fraction keeps it off the curve on
 * anything smaller.
 */
@Composable
private fun ScoreRow(rank: Int, entry: HighScoreEntry, isLatest: Boolean) {
    val color = if (isLatest) AppleII.White else AppleIIChrome.MutedGreen
    val style = MaterialTheme.typography.caption1

    Row(
        modifier = Modifier
            // 274 device pixels at density 2 — see the note above. A fixed width rather
            // than a fraction, and a single modifier rather than a fraction with a cap:
            // `fillMaxWidth` hands its child a *fixed* constraint, so a `widthIn` after it
            // is silently ignored.
            .width(137.dp)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$rank",
            color = color,
            style = style,
            textAlign = TextAlign.End,
            // Two digits' worth and no more, so the professions stay aligned once the
            // table fills without taking width the middle column needs.
            modifier = Modifier.width(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = entry.profession.displayName,
            color = color,
            style = style,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${entry.points}",
            color = color,
            style = style,
            maxLines = 1,
        )
    }
}
