package com.oregontrail.wear.core

import kotlinx.serialization.Serializable

/**
 * Everything the wagon is carrying.
 *
 * Money is held in **cents** so the arithmetic is exact — food at $0.20/lb and the
 * quarter-step fort markups would both accumulate rounding error in floating point.
 *
 * Ammunition is held in **individual bullets** even though it is *sold* in boxes of 20,
 * because scoring counts it per 50. Keeping one unit internally and converting only at
 * the point of purchase avoids a whole class of off-by-a-box bugs.
 */
@Serializable
data class Inventory(
    val oxen: Int = 0,
    val foodPounds: Int = 0,
    val clothingSets: Int = 0,
    val bullets: Int = 0,
    val spareWheels: Int = 0,
    val spareAxles: Int = 0,
    val spareTongues: Int = 0,
    val cashCents: Int = 0,
) {
    val spareParts: Int get() = spareWheels + spareAxles + spareTongues

    fun spend(cents: Int): Inventory {
        require(cents <= cashCents) { "cannot spend $cents cents with only $cashCents" }
        return copy(cashCents = cashCents - cents)
    }

    fun consumeFood(pounds: Int): Inventory =
        copy(foodPounds = (foodPounds - pounds).coerceAtLeast(0))

    /** How much of [good] the wagon carries, in that good's internal unit. */
    fun amountOf(good: Good): Int = when (good) {
        Good.OXEN -> oxen
        Good.FOOD -> foodPounds
        Good.CLOTHING -> clothingSets
        Good.BULLETS -> bullets
        Good.SPARE_WHEEL -> spareWheels
        Good.SPARE_AXLE -> spareAxles
        Good.SPARE_TONGUE -> spareTongues
    }

    /**
     * Adds (or, with a negative [delta], removes) some amount of [good], floored at zero.
     *
     * For adjustments that aren't a purchase — [Encounters] trades goods without cash
     * changing hands, so [Store.buy]'s price-and-purchase-unit machinery doesn't apply.
     */
    fun adjust(good: Good, delta: Int): Inventory = when (good) {
        Good.OXEN -> copy(oxen = (oxen + delta).coerceAtLeast(0))
        Good.FOOD -> copy(foodPounds = (foodPounds + delta).coerceAtLeast(0))
        Good.CLOTHING -> copy(clothingSets = (clothingSets + delta).coerceAtLeast(0))
        Good.BULLETS -> copy(bullets = (bullets + delta).coerceAtLeast(0))
        Good.SPARE_WHEEL -> copy(spareWheels = (spareWheels + delta).coerceAtLeast(0))
        Good.SPARE_AXLE -> copy(spareAxles = (spareAxles + delta).coerceAtLeast(0))
        Good.SPARE_TONGUE -> copy(spareTongues = (spareTongues + delta).coerceAtLeast(0))
    }
}

/** The goods a store sells. Prices are per unit of [purchaseUnit]. */
enum class Good(val displayName: String, val purchaseUnit: String, val baseCents: Int) {
    OXEN("Oxen", "yoke of 2", 20_00),
    FOOD("Food", "pound", 20),
    CLOTHING("Clothing", "set", 10_00),
    // "Bullets", not "Ammunition": a store row's label gets 204 pixels on the physical
    // watch and "Ammunition" needs 234, so it broke mid-word as "Ammuniti / on" — the one
    // label here with no space to wrap at. "Bullets" fits on one line and is the word the
    // rest of the game already uses (see Format.kt's describeQuantity).
    BULLETS("Bullets", "box of 20", 2_00),
    SPARE_WHEEL("Wagon wheel", "each", 10_00),
    SPARE_AXLE("Wagon axle", "each", 10_00),
    SPARE_TONGUE("Wagon tongue", "each", 10_00);

    /** How many inventory units one purchase unit yields. */
    val unitsPerPurchase: Int
        get() = when (this) {
            OXEN -> 2
            BULLETS -> 20
            else -> 1
        }
}

/**
 * A store at a particular place on the trail.
 *
 * Matt's General Store in Independence sells at base price; each fort further west adds
 * 25 percentage points of markup, reaching +150% at Fort Walla Walla. Every base price
 * divides evenly at those steps, so integer cents stay exact.
 */
@Serializable
data class Store(val name: String, val markupPercent: Int) {

    fun priceCents(good: Good): Int = good.baseCents * (100 + markupPercent) / 100

    /** Cost of [quantity] purchase-units of [good] — e.g. 3 yokes, 50 boxes. */
    fun costCents(good: Good, quantity: Int): Int = priceCents(good) * quantity

    /** How many purchase-units of [good] can be afforded with [cashCents]. */
    fun affordable(good: Good, cashCents: Int): Int = cashCents / priceCents(good)

    fun buy(inventory: Inventory, good: Good, quantity: Int): Inventory {
        require(quantity >= 0) { "quantity must not be negative" }
        val cost = costCents(good, quantity)
        val paid = inventory.spend(cost)
        val units = quantity * good.unitsPerPurchase
        return when (good) {
            Good.OXEN -> paid.copy(oxen = paid.oxen + units)
            Good.FOOD -> paid.copy(foodPounds = paid.foodPounds + units)
            Good.CLOTHING -> paid.copy(clothingSets = paid.clothingSets + units)
            Good.BULLETS -> paid.copy(bullets = paid.bullets + units)
            Good.SPARE_WHEEL -> paid.copy(spareWheels = paid.spareWheels + units)
            Good.SPARE_AXLE -> paid.copy(spareAxles = paid.spareAxles + units)
            Good.SPARE_TONGUE -> paid.copy(spareTongues = paid.spareTongues + units)
        }
    }

    companion object {
        val matts = Store("Matt's General Store", markupPercent = 0)

        /** The store at [landmark], or null if it isn't a fort or the departure point. */
        fun at(landmark: Landmark): Store? = when (val kind = landmark.kind) {
            is LandmarkKind.Departure -> matts
            is LandmarkKind.Fort -> Store(landmark.name, kind.markupPercent)
            else -> null
        }
    }
}
