package com.oregontrail.wear.core

/** One line of the end-of-game score breakdown, shown to the player as a tally. */
data class ScoreLine(val label: String, val quantity: Int, val points: Int)

data class Score(
    val lines: List<ScoreLine>,
    val multiplier: Int,
    val professionName: String,
) {
    val subtotal: Int get() = lines.sumOf { it.points }
    val total: Int get() = subtotal * multiplier
}

/**
 * End-of-game scoring, awarded on arrival in Oregon City and then multiplied by
 * profession.
 *
 * Note the deliberately mismatched units: food scores per 25 lb and ammunition per 50
 * bullets, while ammunition is *bought* in boxes of 20. That is why [Inventory] stores
 * individual bullets rather than boxes.
 *
 * `ScoringTest` pins the documented practical maximum of 2,690 before the multiplier,
 * which is the check that this table is transcribed correctly.
 */
object Scoring {

    private const val POINTS_PER_WAGON = 50
    private const val POINTS_PER_OX = 4
    private const val POINTS_PER_CLOTHING_SET = 2
    private const val POINTS_PER_SPARE_PART = 2
    private const val POUNDS_OF_FOOD_PER_POINT = 25
    private const val BULLETS_PER_POINT = 50
    private const val CENTS_PER_POINT = 5_00

    fun score(state: GameState): Score {
        val party = state.party
        val inv = state.inventory
        val health = party.health

        val lines = buildList {
            add(
                ScoreLine(
                    label = "Surviving party (health: ${health.displayName})",
                    quantity = party.survivorCount,
                    points = party.survivorCount * health.pointsPerSurvivor,
                )
            )
            add(ScoreLine("Wagon", 1, POINTS_PER_WAGON))
            add(ScoreLine("Oxen", inv.oxen, inv.oxen * POINTS_PER_OX))
            add(
                ScoreLine(
                    "Sets of clothing",
                    inv.clothingSets,
                    inv.clothingSets * POINTS_PER_CLOTHING_SET,
                )
            )
            add(ScoreLine("Spare parts", inv.spareParts, inv.spareParts * POINTS_PER_SPARE_PART))
            add(
                ScoreLine(
                    "Food",
                    inv.foodPounds,
                    inv.foodPounds / POUNDS_OF_FOOD_PER_POINT,
                )
            )
            add(ScoreLine("Ammunition", inv.bullets, inv.bullets / BULLETS_PER_POINT))
            add(ScoreLine("Cash", inv.cashCents, inv.cashCents / CENTS_PER_POINT))
        }

        return Score(
            lines = lines.filter { it.points > 0 },
            multiplier = state.profession.scoreMultiplier,
            professionName = state.profession.displayName,
        )
    }
}
