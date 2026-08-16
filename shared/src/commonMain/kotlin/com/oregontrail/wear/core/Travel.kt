package com.oregontrail.wear.core

import kotlinx.serialization.Serializable

/**
 * Profession sets both starting money and the final score multiplier, and the two are
 * inversely paired: $1,600/x1 through $400/x3. That keeps expected score roughly flat
 * across professions, so the choice reads as pure difficulty rather than as a
 * high-score shortcut. Preserve that relationship if these are ever retuned — a
 * farmer's $400 has to cover the same oxen and food a banker's $1,600 does, which is
 * what makes the x3 feel earned.
 */
enum class Profession(
    val displayName: String,
    val origin: String,
    val startingCents: Int,
    val scoreMultiplier: Int,
) {
    BANKER("Banker", "Boston", 1600_00, 1),
    CARPENTER("Carpenter", "Ohio", 800_00, 2),
    FARMER("Farmer", "Illinois", 400_00, 3);

    val difficulty: String
        get() = when (this) {
            BANKER -> "Easy"
            CARPENTER -> "Medium"
            FARMER -> "Hard"
        }
}

/** Fraction of a full day's travel covered, and what that costs the party. */
enum class Pace(val displayName: String, val multiplier: Double, val healthCost: Int) {
    STEADY("Steady", 0.50, 0),
    STRENUOUS("Strenuous", 0.75, 1),
    GRUELING("Grueling", 1.00, 3),
}

/**
 * Pounds of food per person per day. [healthCost] is added to the party's health
 * accumulator daily — negative values let a well-fed party recover.
 */
enum class Rations(val displayName: String, val poundsPerPersonPerDay: Int, val healthCost: Int) {
    FILLING("Filling", 3, -2),
    MEAGER("Meager", 2, 1),
    BARE_BONES("Bare bones", 1, 3),
}

/**
 * Daily weather. [travelFactor] scales the distance covered; [healthCost] is added to
 * the health accumulator.
 */
enum class Weather(val displayName: String, val travelFactor: Double, val healthCost: Int) {
    HOT("Hot", 0.90, 2),
    WARM("Warm", 1.00, 0),
    PLEASANT("Pleasant", 1.00, -1),
    COOL("Cool", 1.00, 0),
    RAINY("Rainy", 0.80, 1),
    COLD("Cold", 0.85, 2),
    VERY_COLD("Very cold", 0.70, 4),
    SNOWY("Snowy", 0.50, 5);

    /** Cold weather with too little clothing is a documented way to ruin a party. */
    val needsClothing: Boolean get() = this == COLD || this == VERY_COLD || this == SNOWY
}

/**
 * Picks each day's weather from a month-appropriate distribution.
 *
 * The 1985 version computed weather from month *and* location, which matters because
 * the back half of the trail is mountainous and a late party can be caught by snow in
 * the Blue Mountains. That is modelled here as a cold shift once the party is deep
 * enough into the journey, rather than by tracking altitude properly.
 */
object WeatherModel {

    private val SPRING = listOf(
        Weather.COOL, Weather.RAINY, Weather.PLEASANT, Weather.COLD, Weather.WARM,
    )
    private val SUMMER = listOf(
        Weather.WARM, Weather.HOT, Weather.PLEASANT, Weather.HOT, Weather.RAINY,
    )
    private val AUTUMN = listOf(
        Weather.COOL, Weather.RAINY, Weather.COLD, Weather.PLEASANT, Weather.COLD,
    )
    private val WINTER = listOf(
        Weather.COLD, Weather.VERY_COLD, Weather.SNOWY, Weather.VERY_COLD, Weather.SNOWY,
    )

    /** Day of the journey past which the trail is high enough to shift the weather cold. */
    private const val MOUNTAINS_FROM_DAY = 110

    fun roll(startMonth: Int, dayOfJourney: Int, rng: Rng): Weather {
        val month = TrailDate.of(startMonth, dayOfJourney).month
        val table = when (month) {
            3, 4, 5 -> SPRING
            6, 7, 8 -> SUMMER
            9, 10 -> AUTUMN
            else -> WINTER
        }
        val weather = rng.pick(table)
        return if (dayOfJourney >= MOUNTAINS_FROM_DAY) colder(weather) else weather
    }

    /** Shifts weather one step colder, for the mountainous back half of the trail. */
    private fun colder(weather: Weather): Weather = when (weather) {
        Weather.HOT -> Weather.WARM
        Weather.WARM -> Weather.PLEASANT
        Weather.PLEASANT -> Weather.COOL
        Weather.COOL -> Weather.COLD
        Weather.RAINY -> Weather.COLD
        Weather.COLD -> Weather.VERY_COLD
        Weather.VERY_COLD -> Weather.SNOWY
        Weather.SNOWY -> Weather.SNOWY
    }
}

/** A date in 1848, the year the journey is set. */
@Serializable
data class TrailDate(val month: Int, val day: Int) {
    val monthName: String get() = MONTH_NAMES[month - 1]
    override fun toString(): String = "$monthName $day, $YEAR"

    companion object {
        /** The year the journey is set in, named so the screens can print it too. */
        const val YEAR = 1848

        val MONTH_NAMES = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        )

        // 1848 was a leap year.
        private val DAYS_IN_MONTH = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

        /** Months the player may depart in — the historically viable window. */
        val DEPARTURE_MONTHS = 3..7

        /**
         * The date [dayOfJourney] days after departing on the 1st of [startMonth].
         * Day 0 is the departure date itself.
         */
        fun of(startMonth: Int, dayOfJourney: Int): TrailDate {
            require(startMonth in 1..12) { "month out of range: $startMonth" }
            require(dayOfJourney >= 0) { "dayOfJourney must not be negative" }
            var month = startMonth
            var day = 1 + dayOfJourney
            while (day > DAYS_IN_MONTH[month - 1]) {
                day -= DAYS_IN_MONTH[month - 1]
                month++
                // A journey running past December means the party is long dead, but
                // wrapping keeps the date function total rather than throwing.
                if (month > 12) month = 1
            }
            return TrailDate(month, day)
        }
    }
}
