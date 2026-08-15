package com.oregontrail.wear.ui.art

import com.oregontrail.wear.core.LandmarkId

/**
 * Where a landmark sits on the map strip.
 *
 * [x] is measured in tiles from the western edge, so it runs 0 at the Pacific to
 * [TrailMap.TILES] at the Missouri. [y] is a fraction of one tile's height, 0 at the top
 * edge and 1 at the bottom. Tiles are square, so one unit of [x] and one unit of [y] are
 * the same distance on screen.
 */
data class MapPoint(val x: Float, val y: Float)

/**
 * The trail drawn on top of the six map tiles.
 *
 * Nothing about a landmark's position is in the art: the tiles are plain landscape, and
 * every road, marker and name is drawn live over them. This table is the whole of the
 * correspondence between the two, which is what lets the art be re-sliced or replaced
 * without touching the game — see docs/adr/0006-map-screen.md.
 *
 * **The positions are not true geography, and could not be.** Six square tiles make a 6:1
 * strip, against real proportions of about 3:1 for the country the trail crosses, so the
 * map is roughly twice as wide as the ground is; the trail's real north-south wander of
 * nearly 500 miles cannot be drawn at true scale inside it. Compressing it is the
 * strip-map convention, and is what the emigrant guidebooks themselves did.
 *
 * **[x] follows the art rather than longitude.** The generated tiles put the Rockies, the
 * desert and the prairie where the composition wanted them, not where they are, so a
 * landmark is placed on the terrain it belongs to instead of at its true longitude. Every
 * river crossing sits on a drawn river, Chimney Rock sits on the drawn spire, the forts
 * sit on open ground. The cost is that the map's scale varies along its length — the
 * mountains are drawn about twice as spread out per mile as the plains. There is no scale
 * bar and the screen quotes distances in words, so nothing depends on the difference.
 *
 * **[y] is a diagram.** It keeps the relations that carry meaning — the trail climbs
 * north-west, Fort Bridger is the southern detour, Fort Walla Walla is the northern one,
 * the last leg comes down to the Columbia — and exaggerates the two detours so that a
 * branch is legible on a 1.2" display. It is not latitude.
 */
object TrailMap {

    /** How many tiles the strip is, and so how wide it is in [MapPoint.x] units. */
    val TILES: Int = ArtNames.mapTiles.size

    private val positions: Map<LandmarkId, MapPoint> = mapOf(
        // The eastern prairie. The two rivers the party fords are the two the art draws
        // running top to bottom; Independence is the open grass east of both.
        LandmarkId.INDEPENDENCE to MapPoint(5.92f, 0.73f),
        LandmarkId.KANSAS_RIVER to MapPoint(5.71f, 0.70f),
        LandmarkId.BIG_BLUE_RIVER to MapPoint(5.55f, 0.66f),
        LandmarkId.FORT_KEARNEY to MapPoint(5.30f, 0.62f),

        // The dry plains. Chimney Rock is the lone spire the art draws there, and Fort
        // Laramie sits on the north bank of the river running left to right beside it.
        LandmarkId.CHIMNEY_ROCK to MapPoint(4.88f, 0.50f),
        LandmarkId.FORT_LARAMIE to MapPoint(4.62f, 0.44f),

        // The Rockies. South Pass is in the range itself, and the Green River crossing on
        // the river in the sagebrush gap that parts it. Fort Bridger is pushed further
        // south than it really lies, so that the two arms of the branch separate.
        LandmarkId.INDEPENDENCE_ROCK to MapPoint(4.10f, 0.42f),
        LandmarkId.SOUTH_PASS to MapPoint(3.70f, 0.44f),
        LandmarkId.GREEN_RIVER to MapPoint(3.51f, 0.50f),
        LandmarkId.FORT_BRIDGER to MapPoint(3.44f, 0.64f),

        // The desert west of the divide. The Snake crossing is on the river the art draws
        // in a canyon; Fort Boise is at the western edge of the sage, under the mountains.
        LandmarkId.SODA_SPRINGS to MapPoint(3.10f, 0.42f),
        LandmarkId.FORT_HALL to MapPoint(2.96f, 0.40f),
        LandmarkId.SNAKE_RIVER to MapPoint(2.57f, 0.46f),
        LandmarkId.FORT_BOISE to MapPoint(2.38f, 0.40f),

        // The Blue Mountains and the Columbia. Fort Walla Walla is exaggerated northward
        // for the same reason Fort Bridger is exaggerated south. The Dalles and the rapids
        // below it sit on the river; Oregon City is in the valley south of the estuary,
        // which is what makes the Barlow Road read as the long swing inland that it was.
        LandmarkId.BLUE_MOUNTAINS to MapPoint(2.00f, 0.38f),
        LandmarkId.FORT_WALLA_WALLA to MapPoint(1.80f, 0.28f),
        LandmarkId.THE_DALLES to MapPoint(1.45f, 0.57f),
        LandmarkId.COLUMBIA_RIVER to MapPoint(0.95f, 0.65f),
        LandmarkId.OREGON_CITY to MapPoint(0.62f, 0.82f),
    )

    operator fun get(id: LandmarkId): MapPoint =
        positions[id] ?: error("No map position for $id")

    /**
     * Every landmark in the order the crown scrubs through them, east to west.
     *
     * This is [LandmarkId.entries] unchanged, and `TrailMapTest` pins that it can be:
     * every route on the trail runs from a larger [MapPoint.x] to a smaller one, and the
     * enum is declared in trail order, so declaration order and west-to-east order are the
     * same list. Sorting here instead would hide a table that had drifted out of order
     * rather than failing on it.
     */
    val stops: List<LandmarkId> = LandmarkId.entries
}
