package com.oregontrail.wear.core

/**
 * The trail from Independence to Oregon City, as a directed graph.
 *
 * A plain list won't do: there are three branch points (South Pass, the Blue Mountains,
 * and The Dalles) where the player picks between routes of different length and risk.
 *
 * Distances are sourced — see docs/reference/game-mechanics-1985.md. The Green River +
 * Barlow Road route totals 1,971 miles, which matches the designer's stated ~2,000-mile
 * target; `TrailTest` asserts this, since it's the cross-check that validates the whole
 * table.
 */
enum class LandmarkId {
    INDEPENDENCE,
    KANSAS_RIVER,
    BIG_BLUE_RIVER,
    FORT_KEARNEY,
    CHIMNEY_ROCK,
    FORT_LARAMIE,
    INDEPENDENCE_ROCK,
    SOUTH_PASS,
    GREEN_RIVER,
    FORT_BRIDGER,
    SODA_SPRINGS,
    FORT_HALL,
    SNAKE_RIVER,
    FORT_BOISE,
    BLUE_MOUNTAINS,
    FORT_WALLA_WALLA,
    THE_DALLES,
    COLUMBIA_RIVER,
    OREGON_CITY,
}

sealed interface LandmarkKind {
    /** Where the journey begins; has the only unmarked-up store. */
    data object Departure : LandmarkKind

    /** Sells supplies at a markup over Matt's General Store. */
    data class Fort(val markupPercent: Int) : LandmarkKind

    /**
     * Must be crossed. [typicalDepthFeet] is the centre of the distribution the actual
     * depth is drawn from at arrival; weather shifts it. Fording a river deeper than
     * [Rivers.DANGEROUS_DEPTH_FEET] is the documented way to lose a wagon.
     */
    data class River(
        val widthFeet: Int,
        val typicalDepthFeet: Double,
        val ferryAvailable: Boolean,
    ) : LandmarkKind

    /** Somewhere to stop, look, and talk to people. No store, no obstacle. */
    data object Waypoint : LandmarkKind

    /** The end of the trail. */
    data object Destination : LandmarkKind
}

/**
 * One outbound route from a landmark. [label] is shown to the player at branch points
 * and is ignored where a landmark has only one route out.
 */
data class Route(
    val to: LandmarkId,
    val miles: Int,
    val label: String = "",
)

data class Landmark(
    val id: LandmarkId,
    val name: String,
    val kind: LandmarkKind,
    val routes: List<Route>,
) {
    val isBranch: Boolean get() = routes.size > 1
    val isEnd: Boolean get() = routes.isEmpty()
}

object Rivers {
    /**
     * Fording deeper than this risks swamping the wagon. This threshold is sourced;
     * the per-river depths it's compared against are plausible values chosen to make
     * the decision interesting, and are tuning knobs rather than facts.
     */
    const val DANGEROUS_DEPTH_FEET = 3.0
}

object Trail {

    val landmarks: List<Landmark> = listOf(
        Landmark(
            LandmarkId.INDEPENDENCE, "Independence, Missouri", LandmarkKind.Departure,
            listOf(Route(LandmarkId.KANSAS_RIVER, 102)),
        ),
        Landmark(
            LandmarkId.KANSAS_RIVER, "Kansas River Crossing",
            LandmarkKind.River(widthFeet = 620, typicalDepthFeet = 2.4, ferryAvailable = true),
            listOf(Route(LandmarkId.BIG_BLUE_RIVER, 83)),
        ),
        Landmark(
            LandmarkId.BIG_BLUE_RIVER, "Big Blue River Crossing",
            LandmarkKind.River(widthFeet = 400, typicalDepthFeet = 2.8, ferryAvailable = false),
            listOf(Route(LandmarkId.FORT_KEARNEY, 119)),
        ),
        Landmark(
            LandmarkId.FORT_KEARNEY, "Fort Kearney", LandmarkKind.Fort(markupPercent = 25),
            listOf(Route(LandmarkId.CHIMNEY_ROCK, 250)),
        ),
        Landmark(
            LandmarkId.CHIMNEY_ROCK, "Chimney Rock", LandmarkKind.Waypoint,
            listOf(Route(LandmarkId.FORT_LARAMIE, 86)),
        ),
        Landmark(
            LandmarkId.FORT_LARAMIE, "Fort Laramie", LandmarkKind.Fort(markupPercent = 50),
            listOf(Route(LandmarkId.INDEPENDENCE_ROCK, 190)),
        ),
        Landmark(
            LandmarkId.INDEPENDENCE_ROCK, "Independence Rock", LandmarkKind.Waypoint,
            listOf(Route(LandmarkId.SOUTH_PASS, 102)),
        ),
        Landmark(
            LandmarkId.SOUTH_PASS, "South Pass", LandmarkKind.Waypoint,
            listOf(
                Route(LandmarkId.GREEN_RIVER, 57, "Short route, ford the Green River"),
                Route(LandmarkId.FORT_BRIDGER, 125, "Longer route, resupply at Fort Bridger"),
            ),
        ),
        Landmark(
            LandmarkId.GREEN_RIVER, "Green River Crossing",
            LandmarkKind.River(widthFeet = 400, typicalDepthFeet = 5.2, ferryAvailable = true),
            listOf(Route(LandmarkId.SODA_SPRINGS, 144)),
        ),
        Landmark(
            LandmarkId.FORT_BRIDGER, "Fort Bridger", LandmarkKind.Fort(markupPercent = 75),
            listOf(Route(LandmarkId.SODA_SPRINGS, 162)),
        ),
        Landmark(
            LandmarkId.SODA_SPRINGS, "Soda Springs", LandmarkKind.Waypoint,
            listOf(Route(LandmarkId.FORT_HALL, 57)),
        ),
        Landmark(
            LandmarkId.FORT_HALL, "Fort Hall", LandmarkKind.Fort(markupPercent = 100),
            listOf(Route(LandmarkId.SNAKE_RIVER, 182)),
        ),
        Landmark(
            LandmarkId.SNAKE_RIVER, "Snake River Crossing",
            LandmarkKind.River(widthFeet = 1000, typicalDepthFeet = 3.6, ferryAvailable = false),
            listOf(Route(LandmarkId.FORT_BOISE, 114)),
        ),
        Landmark(
            LandmarkId.FORT_BOISE, "Fort Boise", LandmarkKind.Fort(markupPercent = 125),
            listOf(Route(LandmarkId.BLUE_MOUNTAINS, 160)),
        ),
        Landmark(
            LandmarkId.BLUE_MOUNTAINS, "Blue Mountains", LandmarkKind.Waypoint,
            listOf(
                Route(LandmarkId.THE_DALLES, 125, "Press on to The Dalles"),
                Route(LandmarkId.FORT_WALLA_WALLA, 55, "Detour to Fort Walla Walla"),
            ),
        ),
        Landmark(
            LandmarkId.FORT_WALLA_WALLA, "Fort Walla Walla", LandmarkKind.Fort(markupPercent = 150),
            listOf(Route(LandmarkId.THE_DALLES, 120)),
        ),
        Landmark(
            LandmarkId.THE_DALLES, "The Dalles", LandmarkKind.Waypoint,
            listOf(
                Route(LandmarkId.OREGON_CITY, 200, "Take the Barlow Toll Road"),
                Route(LandmarkId.COLUMBIA_RIVER, 50, "Raft down the Columbia River"),
            ),
        ),
        Landmark(
            LandmarkId.COLUMBIA_RIVER, "The Columbia River",
            LandmarkKind.River(widthFeet = 2000, typicalDepthFeet = 8.0, ferryAvailable = false),
            listOf(Route(LandmarkId.OREGON_CITY, 20)),
        ),
        Landmark(
            LandmarkId.OREGON_CITY, "Oregon City", LandmarkKind.Destination,
            emptyList(),
        ),
    )

    private val byId: Map<LandmarkId, Landmark> = landmarks.associateBy { it.id }

    operator fun get(id: LandmarkId): Landmark =
        byId[id] ?: error("No landmark defined for $id")

    val start: Landmark get() = this[LandmarkId.INDEPENDENCE]

    /** Every fort, in trail order — the set of places with a marked-up store. */
    val forts: List<Landmark> get() = landmarks.filter { it.kind is LandmarkKind.Fort }
}
