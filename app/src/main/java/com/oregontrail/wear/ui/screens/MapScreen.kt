package com.oregontrail.wear.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.oregontrail.wear.core.GameState
import com.oregontrail.wear.core.LandmarkId
import com.oregontrail.wear.core.Trail
import com.oregontrail.wear.ui.GameController
import com.oregontrail.wear.ui.Screen
import com.oregontrail.wear.ui.art.ArtLoader
import com.oregontrail.wear.ui.art.ArtNames
import com.oregontrail.wear.ui.art.MapPoint
import com.oregontrail.wear.ui.art.TrailMap
import com.oregontrail.wear.ui.components.Gap
import com.oregontrail.wear.ui.components.StaticScreen
import com.oregontrail.wear.ui.counted
import com.oregontrail.wear.ui.theme.AppleII
import com.oregontrail.wear.ui.theme.AppleIIChrome
import kotlin.math.roundToInt

/**
 * The shape of the map band: its width over its height.
 *
 * Tiles are square and drawn as tall as the band, so this is also how many of them fit
 * across it — a shade under one and a half, or about 500 miles of country at the eastern
 * end. Wide enough that a landmark is seen in the context of its neighbours, tall enough
 * to leave room above and below for the two lines of text.
 */
private const val MAP_ASPECT = 1.5f

/** Half the band's width, in tiles. */
private const val HALF_VIEW = MAP_ASPECT / 2f

/**
 * How far past the end of the strip the view may scroll, in tiles.
 *
 * Independence sits 0.08 tiles from the eastern edge of the art, so centring it exactly
 * would leave two thirds of the display black. Clamping the scroll to the strip instead
 * would push it out under the bezel. This is the compromise: the last landmark at either
 * end comes to rest off-centre, with a strip of black beyond it that reads as the edge of
 * the map, which is what it is.
 */
private const val EDGE_SLACK = 0.35f

/**
 * How much crown travel steps the selection on by one landmark.
 *
 * The Pixel Watch 2's crown turns continuously rather than clicking, so the detents the
 * map scrubs by are made here rather than felt in the hardware. A feel constant: too small
 * and the whole trail flies past in a flick, too large and crossing the continent is a
 * chore.
 */
private val DETENT = 48.dp

/**
 * The road, and the black it is outlined in so that it reads over any terrain.
 *
 * The outline is only a pixel and a half wider than the road either side. Wider was tried
 * and the black swallowed the colour: at watch size a green line inside a heavy black one
 * reads as a black line, which loses the distinction between the road walked and the road
 * ahead — the one thing the colour is carrying.
 */
private val ROAD_WIDTH = 3.dp
private val ROAD_OUTLINE = 6.dp

/** A landmark, and the party's own marker. */
private val MARKER = 7.dp
private val SELECTION = 15.dp
private val WAGON = 20.dp

/**
 * The trail map: the whole continent as one strip, scrubbed along with the crown.
 *
 * There is no panning by touch. A right swipe is never delivered to this app — it hits the
 * Wear OS dismiss gesture and ends the run — so horizontal drag is the one gesture that
 * cannot be claimed, and a pannable map claims exactly it. The trail is a path rather than
 * a plane anyway, so one axis is all there is to scrub: the crown steps from landmark to
 * landmark and the country slides past underneath. Tapping anywhere goes back.
 *
 * See docs/adr/0006-map-screen.md.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MapScreen(controller: GameController) {
    val state = controller.game
    val stops = TrailMap.stops

    var selected by remember { mutableIntStateOf(stops.indexOf(state.at).coerceAtLeast(0)) }
    var turned by remember { mutableFloatStateOf(0f) }

    val density = LocalDensity.current
    val detentPx = with(density) { DETENT.toPx() }

    // The crown only reaches a composable that holds focus, and nothing here would ask for
    // it otherwise — the screen has no list and no text field. Forgetting this leaves a map
    // that responds to nothing at all, which reads as a broken screen rather than a missing
    // modifier.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val focus by animateFloatAsState(
        targetValue = TrailMap[stops[selected]].x
            .coerceIn(HALF_VIEW - EDGE_SLACK, TrailMap.TILES - HALF_VIEW + EDGE_SLACK),
        label = "map focus",
    )

    val context = LocalContext.current
    val displayWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }

    // A tile is drawn as tall as the band, which is the display width over MAP_ASPECT.
    //
    // All six are decoded on the way in rather than as they scroll into view. Six tiles at
    // this size cost a couple of megabytes against ArtLoader's 16MB budget, and paying for
    // them up front means the one moment of decoding lands on a screen that is not yet
    // animating anything — where a hitch is invisible — instead of on every flick of the
    // crown, where it would not be.
    val tileEdgePx = (displayWidthPx / MAP_ASPECT).roundToInt()
    val tiles = remember(tileEdgePx) {
        ArtNames.mapTiles.map { ArtLoader.loadOrNull(context, it, tileEdgePx) }
    }
    val wagonEdgePx = with(density) { WAGON.roundToPx() }
    val wagon = remember(wagonEdgePx) {
        ArtLoader.loadOrNull(context, ArtNames.MAP_WAGON, wagonEdgePx)
    }

    StaticScreen(
        modifier = Modifier
            .onRotaryScrollEvent { event ->
                turned += event.verticalScrollPixels
                while (turned >= detentPx && selected < stops.lastIndex) {
                    selected++
                    turned -= detentPx
                }
                while (turned <= -detentPx && selected > 0) {
                    selected--
                    turned += detentPx
                }
                // Both ends of the trail refuse to step further, and without this the
                // winding would bank up against them — a dozen turns past Oregon City
                // would then take a dozen back before the map moved at all.
                turned = turned.coerceIn(-detentPx, detentPx)
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { controller.go(Screen.TrailMenu) }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = Trail[stops[selected]].name,
                color = AppleII.Green,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.caption1,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(0.8f),
            )
            Gap(4)
            Canvas(
                // Full bleed rather than the 0.85 the travel scene uses. A map is supposed
                // to continue past the edge of what is shown, and the round bezel cropping
                // its corners costs nothing, since a strip map has nothing in them.
                modifier = Modifier.fillMaxWidth().aspectRatio(MAP_ASPECT).clipToBounds(),
            ) {
                drawTiles(tiles, focus)
                drawRoads(state, focus)
                drawLandmarks(state, focus)
                drawParty(state, wagon, focus)
                // Last, so that it is never hidden — which it otherwise is exactly when it
                // matters most, with the wagon parked on the landmark being looked at.
                drawSelection(at(TrailMap[stops[selected]], focus))
            }
            Gap(6)
            Text(
                text = describe(state, stops[selected]),
                color = AppleIIChrome.MutedGreen,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.caption3,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(0.8f),
            )
        }
    }
}

/**
 * Where the selected landmark stands in relation to the party.
 *
 * The distance ahead is measured from [GameState.heading] rather than from
 * [GameState.at], which is what makes the arm of a branch the party did not take come back
 * as "Not on your road" instead of as a mileage they will never travel.
 */
private fun describe(state: GameState, selected: LandmarkId): String {
    // Only while the party is actually standing there. `at` is the last landmark reached,
    // not where the wagon is, and it keeps that value for the several days it takes to
    // reach the next one — during which the marker on the map is visibly somewhere else.
    if (selected == state.at && state.milesIntoSegment == 0) return "You are here"

    val journey = state.journey
    val reachedAt = journey.indexOf(selected)
    if (reachedAt >= 0) {
        val behind = Trail.milesAlong(journey) - Trail.milesAlong(journey.take(reachedAt + 1)) +
            state.milesIntoSegment
        return counted(behind, "mile") + " back"
    }

    val ahead = state.heading?.let { Trail.milesBetween(it, selected) }
        ?: return "Not on your road"
    return counted(ahead + state.milesToNextLandmark, "mile") + " on"
}

/** The horizontal centre of the band, in tiles, given the tile size in pixels. */
private fun DrawScope.originX(focus: Float): Float = size.width / 2f - focus * size.height

private fun DrawScope.at(point: MapPoint, focus: Float): Offset =
    Offset(originX(focus) + point.x * size.height, point.y * size.height)

/**
 * Draws the strip, whichever part of it is on screen.
 *
 * Each tile's right edge is taken from the next tile's rounded left edge rather than from
 * its own width, so that a fractional tile size cannot round independently at both ends
 * and leave a one-pixel seam of background crawling down the display as the map scrolls.
 */
private fun DrawScope.drawTiles(tiles: List<ImageBitmap?>, focus: Float) {
    val origin = originX(focus)
    val tile = size.height
    for ((index, image) in tiles.withIndex()) {
        val left = (origin + index * tile).roundToInt()
        val right = (origin + (index + 1) * tile).roundToInt()
        if (image == null || left > size.width || right < 0) continue
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(left, 0),
            dstSize = IntSize(right - left, tile.roundToInt()),
            // Bilinear, as everywhere else: the tiles arrive far larger than the band and
            // have already been decoded down close to it, so nearest-neighbour would alias
            // the remaining fraction into edges that crawl as the map scrolls.
            filterQuality = FilterQuality.Low,
        )
    }
}

/**
 * Draws every road on the trail, then the part of it the party has actually walked.
 *
 * Both arms of each branch are drawn, including the one that was not taken. A map that
 * hid the road not travelled would be no use for the decision it is most often opened to
 * make.
 */
private fun DrawScope.drawRoads(state: GameState, focus: Float) {
    for (landmark in Trail.landmarks) {
        for (route in landmark.routes) {
            road(
                at(TrailMap[landmark.id], focus),
                at(TrailMap[route.to], focus),
                AppleIIChrome.MutedGreen,
            )
        }
    }
    val journey = state.journey
    for ((from, to) in journey.zipWithNext()) {
        road(at(TrailMap[from], focus), at(TrailMap[to], focus), AppleII.Green)
    }
    // The segment in progress, drawn only as far as the party has got along it.
    road(at(TrailMap[state.at], focus), at(partyPoint(state), focus), AppleII.Green)
}

/**
 * One stretch of road: a black line with the colour laid over it.
 *
 * The outline is not decoration. A two-pixel green line over a forest of green trees or a
 * blue line of river is invisible, and the art is dense everywhere the trail goes. The
 * black underneath is what the eye follows.
 */
private fun DrawScope.road(from: Offset, to: Offset, color: Color) {
    drawLine(AppleII.Black, from, to, strokeWidth = ROAD_OUTLINE.toPx(), cap = StrokeCap.Round)
    drawLine(color, from, to, strokeWidth = ROAD_WIDTH.toPx(), cap = StrokeCap.Round)
}

/** A marker at every landmark, brighter for the ones already behind the party. */
private fun DrawScope.drawLandmarks(state: GameState, focus: Float) {
    val reached = state.journey.toSet()
    for (id in TrailMap.stops) {
        val centre = at(TrailMap[id], focus)
        square(centre, MARKER.toPx() + ROAD_OUTLINE.toPx() - ROAD_WIDTH.toPx(), AppleII.Black)
        square(
            centre,
            MARKER.toPx(),
            if (id in reached) AppleII.Green else AppleIIChrome.MutedGreen,
        )
    }
}

/** The ring around whichever landmark the crown has stopped on. */
private fun DrawScope.drawSelection(centre: Offset) {
    square(centre, SELECTION.toPx() + 2f, AppleII.Black, stroke = 4f)
    square(centre, SELECTION.toPx(), AppleII.White, stroke = 2f)
}

private fun DrawScope.square(centre: Offset, edge: Float, color: Color, stroke: Float? = null) {
    val topLeft = Offset(centre.x - edge / 2f, centre.y - edge / 2f)
    val box = Size(edge, edge)
    if (stroke == null) drawRect(color, topLeft, box)
    else drawRect(color, topLeft, box, style = Stroke(width = stroke))
}

/** The wagon, wherever along the current segment the party has got to. */
private fun DrawScope.drawParty(state: GameState, wagon: ImageBitmap?, focus: Float) {
    if (wagon == null) return
    val centre = at(partyPoint(state), focus)
    val edge = WAGON.toPx()
    drawImage(
        image = wagon,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(wagon.width, wagon.height),
        dstOffset = IntOffset(
            (centre.x - edge / 2f).roundToInt(),
            (centre.y - edge / 2f).roundToInt(),
        ),
        dstSize = IntSize(edge.roundToInt(), edge.roundToInt()),
        filterQuality = FilterQuality.Low,
    )
}

/**
 * Where the party is, interpolated along the segment they are on.
 *
 * Falls back to the last landmark whenever there is no segment to be part-way along — at
 * the end of the trail, and at a river or a branch, where [GameState.heading] is null
 * until the player resolves it.
 */
private fun partyPoint(state: GameState): MapPoint {
    val here = TrailMap[state.at]
    val next = state.heading?.let { TrailMap[it] } ?: return here
    val miles = state.currentRoute?.miles ?: return here
    if (miles <= 0) return here
    val travelled = (state.milesIntoSegment.toFloat() / miles).coerceIn(0f, 1f)
    return MapPoint(
        here.x + (next.x - here.x) * travelled,
        here.y + (next.y - here.y) * travelled,
    )
}
