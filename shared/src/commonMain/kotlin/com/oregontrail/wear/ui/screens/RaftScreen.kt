package com.oregontrail.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.oregontrail.wear.ui.components.rotaryInput
import com.oregontrail.wear.ui.art.displayWidthPx
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.oregontrail.wear.core.LandmarkId
import com.oregontrail.wear.core.RaftDamage
import com.oregontrail.wear.core.RaftImpact
import com.oregontrail.wear.core.Rafting
import com.oregontrail.wear.ui.GameController
import com.oregontrail.wear.ui.art.ArtLoader
import com.oregontrail.wear.ui.art.ArtNames
import com.oregontrail.wear.ui.art.SCENE_WIDTH
import com.oregontrail.wear.ui.art.Scene
import com.oregontrail.wear.ui.art.Sprite
import com.oregontrail.wear.ui.art.spriteEdgePx
import com.oregontrail.wear.ui.components.Gap
import com.oregontrail.wear.ui.components.MenuChip
import com.oregontrail.wear.ui.components.SceneScrollColumn
import com.oregontrail.wear.ui.components.ScreenText
import com.oregontrail.wear.ui.components.ScreenTitle
import com.oregontrail.wear.ui.counted
import com.oregontrail.wear.ui.theme.AppleII
import com.oregontrail.wear.ui.theme.AppleIIChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * The field is square, in the same scene units [SCENE_WIDTH] measures across.
 *
 * Every other scene in the game is a 2:1 band, because a landscape seen side-on wants
 * to be wide. This one is looking straight *down* at the river, so it takes the whole
 * display — and a Wear display's framebuffer is square, which makes the vertical extent
 * of the grid equal to its horizontal one.
 */
private const val FIELD = SCENE_WIDTH

/**
 * The navigable channel, in scene units. The rest of the width is bank.
 *
 * Measured off `river_bank.png` rather than specified to it. The painted banks are
 * deliberately ragged — water starts between 14 and 19 on the left and ends between 109
 * and 116 on the right — and because the backdrop scrolls, every one of those rows passes
 * the raft in turn. So these are the *innermost* extents over the whole image rather than
 * an average: nowhere in the art does rock reach past them.
 *
 * Erring inward is deliberate. A bank hit is expensive and comes with a haptic thump, and
 * one delivered while the raft is visibly still in clear water reads as a bug — whereas
 * stopping a hair short of the rocks just reads as the pilot being careful.
 */
private const val CHANNEL_LEFT = 19
private const val CHANNEL_RIGHT = 109

/**
 * The raft's footprint, arrived at the way every other box on this screen is.
 *
 * The width is the tuned number — it is what a rock has to be dodged by, and what
 * [STEER_GAIN] is expressed against — so the width is kept and the height derived from
 * the art's own proportions. The raft is drawn *fitted* inside this box
 * without distortion (see [Sprite]), so a box shaped unlike the art is not a bigger
 * raft, it is a raft with dead space down both sides that still collects collisions:
 * the authored raft is 214x267, and in the 22x18 box this started as it drew 14 units
 * wide against a 22-unit hit box. That is four units of clear water either side of a
 * visibly untouched raft in which a rock still counts as a hit.
 */
private const val RAFT_WIDTH = 22
private const val RAFT_HEIGHT = 27

/**
 * Where the raft sits, and why it is not lower.
 *
 * The display is a circle of radius 64 in these units, centred at (64, 64). At the
 * raft's bottom edge the circle is only about 100 units wide, so a raft any further
 * down would have the far edges of its travel clipped by the bezel exactly when the
 * player was steering into them.
 *
 * So it is the *bottom* edge that is pinned, at 104: this is [RAFT_HEIGHT] short of it,
 * and moved up when the raft grew to the height its art wanted rather than letting the
 * corners of its travel drift under the bezel.
 */
private const val RAFT_Y = 104 - RAFT_HEIGHT

private const val TICK_MILLIS = 40L

/** Scene units the current carries everything downstream per tick. */
private const val CURRENT_PER_TICK = 1.15f

/**
 * Scene units of raft movement per device pixel of crown scroll.
 *
 * A feel knob, and the most important one on this screen. A detent of the Pixel Watch 2's
 * crown reports on the order of 50 pixels, which at the 3 device pixels a scene unit takes
 * puts a detent at about ten units — a little under half the raft's width, so one click
 * sidesteps a small rock and six take the raft right across the channel.
 *
 * It was 0.35, which made that twelve clicks across and felt like poling a barge.
 */
private const val STEER_GAIN = 0.6f

/**
 * The gap between rocks, and the number this screen is most sensitive to.
 *
 * A rock takes about three seconds to travel from the top of the field to the raft, and
 * roughly half of them spawn on a collision course with a raft that never moves. At the
 * 550-1100ms this started at, that was a hit every second or so and a raft that broke up
 * in seven seconds without the player doing anything wrong — the descent has to be
 * survivable by steering, not merely by luck. At this spacing a run puts about a dozen
 * rocks in the water, one every couple of seconds, which leaves each one a decision
 * rather than a reflex.
 */
private const val SPAWN_GAP_MIN_MILLIS = 1_400L
private const val SPAWN_GAP_MAX_MILLIS = 2_400L

/** Grace before the first rock, so the player can find the raft. */
private const val FIRST_ROCK_MILLIS = 1_800L

/** When each of the three direction signs comes past. Bouchard's pacing device. */
private val SIGN_TIMES = listOf(8_000L, 16_000L, 24_000L)

/** When the landing appears upstream. Rocks stop spawning here — the run is aiming now. */
private const val LANDING_MILLIS = 26_500L

private const val LANDING_WIDTH = 28
private const val LANDING_HEIGHT = 34

/**
 * Where the landing's beach meets the water.
 *
 * The landing is a break in the cliff, not an island, and it is placed by this edge rather
 * than by its far side so that it stays welded to the bank: the art is drawn with its
 * gravel at the right-hand edge of the file and green scrub filling the rest, so pinning
 * that edge to the water line puts the scrub over the painted bank where it belongs.
 * Placing it by its left edge instead — as a fixed [LANDING_X] of 10 did — left the far
 * side of a 28-unit landing 19 units out into the channel with open water showing between
 * it and the cliff behind it, which reads as a raft-sized meadow floating downstream.
 *
 * The five units past [CHANNEL_LEFT] are the beach itself, jutting into the river the way
 * a real one does. They are also what keeps the landing legible: the display is a circle,
 * so down at the height the landing is judged the bezel has already eaten everything left
 * of about unit 10, and a landing pinned flush to the water line would be showing the
 * player a sliver of its own edge.
 */
private const val LANDING_EDGE = CHANNEL_LEFT + 5

private const val LANDING_X = LANDING_EDGE - LANDING_WIDTH

/**
 * How far off the beach the raft can be and still put ashore: the gap from the raft's left
 * edge to [LANDING_EDGE], in scene units.
 *
 * Measured edge-to-edge rather than centre-to-centre, because the landing is no longer a
 * thing in the channel to be hit — it is on the bank, and putting ashore means being
 * alongside it. Half a raft's width of slack, which works out within a couple of units of
 * the reach the old centre-to-centre test allowed, so the descent is neither easier nor
 * harder to finish than it was.
 */
private const val LANDING_TOLERANCE = RAFT_WIDTH / 2

/**
 * The sign's footprint: tall and narrow, because the art is a post with the board
 * cantilevered off one side rather than a billboard. It was a 16x16 square, which the
 * post fitted by height and left half empty across.
 */
private const val SIGN_WIDTH = 10
private const val SIGN_HEIGHT = 17

/**
 * Where a sign is staked: post on the gravel, board overhanging the water.
 *
 * Derived from [CHANNEL_RIGHT] rather than written out, so that re-measuring the banks
 * against a new backdrop cannot leave the signs stranded midstream.
 */
private const val SIGN_X = CHANNEL_RIGHT - 3

/** How long the raft is immune after an impact, so one rock cannot bill twice. */
private const val IMPACT_COOLDOWN_MILLIS = 500L

private const val BOB_FRAME_MILLIS = 200L

/**
 * Foam specks on the water.
 *
 * They no longer have to sell the current on their own — the backdrop scrolls now — but
 * they still earn their place: the river repeats every two tiles, so its painted foam
 * comes round again, and a scatter of specks arriving at random breaks up a pattern the
 * eye would otherwise start to recognise.
 */
private const val FOAM_WIDTH = 10
private const val FOAM_HEIGHT = 3
private const val MAX_FOAM = 8

/**
 * A rock in the channel. Two sizes, so the player has something to judge.
 *
 * [width] and [height] are the footprint the art is drawn in; [sprayInsetX] and
 * [sprayInsetY] are how far inside that the rock *itself* is, and everything about a
 * collision is measured from the inset box.
 *
 * The two differ because both rocks are drawn standing in a collar of white broken water
 * that reaches the edges of the file on every side — it is what stops them reading as
 * boulders floating on the surface, so it cannot be cropped away. But it is spray. The
 * grey rock occupies the middle 8-91% of the large file and 13-80% of the small one, and
 * a hit box drawn round the spray costs the player the run for water they steered through
 * cleanly. The insets are those measurements rounded inward, so what remains is a shade
 * smaller than the rock rather than a shade larger — the same way [CHANNEL_LEFT] errs.
 */
private enum class RockSize(
    val art: String,
    val width: Int,
    val height: Int,
    val sprayInsetX: Int,
    val sprayInsetY: Int,
) {
    SMALL(ArtNames.ROCK_SMALL, width = 14, height = 15, sprayInsetX = 2, sprayInsetY = 2),
    LARGE(ArtNames.ROCK_LARGE, width = 22, height = 26, sprayInsetX = 2, sprayInsetY = 3);

    /** The width a collision is judged against: rock, no spray. */
    val hitWidth: Int get() = width - 2 * sprayInsetX
}

private data class Rock(val size: RockSize, val x: Int, val y: Float)

/**
 * Every asset the descent can show, each with the box edge [Scene] will ask for it at.
 *
 * Derived from the footprints above rather than listed, because the art cache is keyed by
 * the sample size a request resolves to: a prewarm at an edge the screen never asks for is
 * not merely wasted but invisible, and the decode it was meant to hoist off the tick loop
 * happens in the tick anyway. Assets used to be grouped under one shared edge, which held
 * only for as long as every footprint in a group rounded to the same power-of-two bucket —
 * true at 384 pixels, and nothing said it stayed true at another display size.
 */
private val PREWARM: List<Pair<String, Int>> = buildList {
    for (frame in ArtNames.raftBob) add(frame to max(RAFT_WIDTH, RAFT_HEIGHT))
    for (size in RockSize.entries) add(size.art to max(size.width, size.height))
    add(ArtNames.RAFT_LANDING to max(LANDING_WIDTH, LANDING_HEIGHT))
    add(ArtNames.RAFT_SIGN to max(SIGN_WIDTH, SIGN_HEIGHT))
    add(ArtNames.RAFT_FOAM to max(FOAM_WIDTH, FOAM_HEIGHT))
}

private data class Drifter(val x: Int, val y: Float)

/**
 * Rafting the Columbia: the last leg, and the only minigame the player cannot decline.
 *
 * The current carries the raft down the screen on its own and the player only steers,
 * which is the shape of the 1985 original — see docs/reference/game-mechanics-1985.md,
 * where the rest of that design and the two faults worth fixing are written up. The
 * crown steers rather than a d-pad, per ADR 0004: a finger on this screen covers the
 * rocks it is trying to miss. It is also the only steering there is — a horizontal drag
 * used to work as a fallback, but steering right is the system dismiss gesture, so the
 * fallback's own direction could end the run, and an interrupted descent is a run that
 * never happened.
 *
 * Collisions are *graded* here, which is the fix for the original's flattest edge: how
 * far the raft's box overlaps the rock's decides whether the river calls it a graze or a
 * solid hit, and putting the raft into the bank is worse than either. The screen keeps a
 * running list of [RaftImpact]s and hands it to [Rafting.finish] exactly once, when the
 * landing has been made or missed — same contract as [HuntingScreen], and for the same
 * reason: nothing about a descent in progress is saved, so a run interrupted by the OS
 * is a run that never happened.
 */
@Composable
fun RaftScreen(controller: GameController) {
    var pushedOff by remember { mutableStateOf(false) }

    if (pushedOff) {
        RaftRun(controller)
    } else {
        RaftBriefing(onPushOff = { pushedOff = true })
    }
}

/**
 * The instructions, which the original gave two full screens to.
 *
 * Not optional and not skippable-by-default: this is the one screen in the game with a
 * control scheme of its own, and a player who works that out by wrecking has already
 * paid for the lesson.
 */
@Composable
private fun RaftBriefing(onPushOff: () -> Unit) {
    SceneScrollColumn(background = ArtNames.landmark(LandmarkId.COLUMBIA_RIVER)) {
        Gap(4)
        ScreenTitle("The Columbia")
        // Short lines on purpose. This font is sized in device pixels and does not
        // shrink to fit, so anything longer wraps to two lines and pushes the chip below
        // the fold — a briefing the player has to scroll is a briefing they skip.
        ScreenText("Fifty miles of white water.", color = AppleII.White, small = true)
        Gap(8)
        ScreenText("Crown steers.", color = AppleII.Green, small = true)
        ScreenText("Miss the rocks.", color = AppleII.Green, small = true)
        ScreenText("Land at sign three.", color = AppleII.Green, small = true)
        Gap(10)
        MenuChip(label = "Push off", primary = true, onClick = onPushOff)
    }
}

@Composable
private fun RaftRun(controller: GameController) {
    val haptics = LocalHapticFeedback.current

    val displayWidthPx = displayWidthPx()
    val pixelsPerUnit = displayWidthPx.toFloat() / FIELD

    // Decode everything the descent can show before the first tick, off the main thread —
    // the same precaution [HuntingScreen] takes, and it matters more here: the first rock
    // of the run is the one the player has least warning of, and a PNG decode inside its
    // spawn tick is a visible stutter at exactly the wrong moment.
    LaunchedEffect(displayWidthPx) {
        withContext(Dispatchers.Default) {
            for ((name, boxEdge) in PREWARM) {
                ArtLoader.prewarm(listOf(name), spriteEdgePx(boxEdge, displayWidthPx))
            }
        }
    }

    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var raftX by remember { mutableFloatStateOf(((FIELD - RAFT_WIDTH) / 2).toFloat()) }
    var rocks by remember { mutableStateOf(emptyList<Rock>()) }
    var foam by remember { mutableStateOf(emptyList<Drifter>()) }
    var signs by remember { mutableStateOf(emptyList<Drifter>()) }
    var landing by remember { mutableStateOf<Drifter?>(null) }
    var spray by remember { mutableStateOf(emptyList<Drifter>()) }
    var impacts by remember { mutableStateOf(emptyList<RaftImpact>()) }
    var signsPassed by remember { mutableIntStateOf(0) }
    var lastImpactAt by remember { mutableLongStateOf(-IMPACT_COOLDOWN_MILLIS) }
    // Whether the raft is currently up against a bank, so that a grounding is billed on
    // the way in rather than once per cooldown for as long as it is held.
    var aground by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        var nextRockAt = FIRST_ROCK_MILLIS
        var signsSpawned = 0
        var landed = false
        var running = true

        while (running) {
            delay(TICK_MILLIS)
            elapsedMillis += TICK_MILLIS

            // Everything on the water moves at the current's speed; only the raft has a
            // will of its own, and even that is only sideways.
            rocks = rocks.map { it.copy(y = it.y + CURRENT_PER_TICK) }.filter { it.y < FIELD }
            foam = foam.map { it.copy(y = it.y + CURRENT_PER_TICK) }.filter { it.y < FIELD }
            signs = signs.map { it.copy(y = it.y + CURRENT_PER_TICK) }.filter { it.y < FIELD }
            spray = spray.map { it.copy(y = it.y + CURRENT_PER_TICK) }.filter { it.y < FIELD }

            if (foam.size < MAX_FOAM && Random.nextInt(3) == 0) {
                foam = foam + Drifter(
                    x = Random.nextInt(CHANNEL_LEFT, CHANNEL_RIGHT - FOAM_WIDTH),
                    y = -FOAM_HEIGHT.toFloat(),
                )
            }

            if (signsSpawned < SIGN_TIMES.size && elapsedMillis >= SIGN_TIMES[signsSpawned]) {
                signsSpawned++
                signs = signs + Drifter(SIGN_X, -SIGN_HEIGHT.toFloat())
            }
            // Signs already off the bottom of the field have been dropped from the
            // list, so anything not still upstream of the raft counts as passed.
            signsPassed = signsSpawned - signs.count { it.y <= RAFT_Y }

            // Rocks stop coming once the landing is on the water. The last stretch is
            // about lining up, and a rock arriving mid-approach would make the landing a
            // coin toss rather than a decision.
            if (elapsedMillis >= LANDING_MILLIS) {
                if (landing == null) landing = Drifter(LANDING_X, -LANDING_HEIGHT.toFloat())
            } else if (elapsedMillis >= nextRockAt) {
                val size = if (Random.nextInt(3) == 0) RockSize.LARGE else RockSize.SMALL
                rocks = rocks + Rock(
                    size = size,
                    x = Random.nextInt(CHANNEL_LEFT, CHANNEL_RIGHT - size.width),
                    y = -size.height.toFloat(),
                )
                nextRockAt = elapsedMillis +
                    Random.nextLong(SPAWN_GAP_MIN_MILLIS, SPAWN_GAP_MAX_MILLIS)
            }

            val approach = landing
            if (approach != null) {
                val moved = approach.copy(y = approach.y + CURRENT_PER_TICK)
                landing = moved
                // The landing is made or missed the moment it draws level with the raft.
                if (moved.y >= RAFT_Y) {
                    landed = raftX - LANDING_EDGE <= LANDING_TOLERANCE
                    running = false
                }
            }

            // The banks. Hitting one is the worst thing on the river, so the raft is
            // stopped dead at the edge rather than being allowed to grind along it.
            //
            // A grounding is billed once, when the raft first touches, and not again
            // until it has come off. Charging per cooldown instead — as this used to —
            // put two BANK impacts a second against a wreck threshold of six, so a single
            // second of oversteer was a total loss: the player leaning on the crown to
            // dodge a rock reached the bank, kept leaning for the moment it takes to
            // notice, and lost the run to that rather than to the rock. Held contact
            // costs nothing extra because it is already costing the only thing that
            // matters here — the raft is pinned and not being steered anywhere.
            val struckBank = raftX < CHANNEL_LEFT || raftX + RAFT_WIDTH > CHANNEL_RIGHT
            // Except on the last stretch, where the left bank stops being something to
            // keep off and becomes the thing the player is steering *at*: the landing is a
            // beach on that bank, so running up against it there is putting ashore, not a
            // grounding. Billing it was a trap of the cruellest kind — the run asks for a
            // move it then charges three of the six points a raft has for making, so a
            // player who had touched a bank once anywhere in the previous half minute
            // could not land at all without breaking up in sight of the beach. Play-tested
            // 2026-08-14: that is exactly how the run ended.
            val beaching = landing != null && raftX < CHANNEL_LEFT
            raftX = raftX.coerceIn(
                CHANNEL_LEFT.toFloat(),
                (CHANNEL_RIGHT - RAFT_WIDTH).toFloat(),
            )
            if (struckBank && !beaching && !aground &&
                elapsedMillis - lastImpactAt >= IMPACT_COOLDOWN_MILLIS
            ) {
                impacts = impacts + RaftImpact.BANK
                lastImpactAt = elapsedMillis
                spray = spray + sprayAt(raftX.toInt(), RAFT_Y)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            aground = struckBank

            if (elapsedMillis - lastImpactAt >= IMPACT_COOLDOWN_MILLIS) {
                val hit = rocks.firstOrNull { overlapsRaft(it, raftX) }
                if (hit != null) {
                    // Grading the hit by how deep the overlap ran is the whole point —
                    // the 1985 game charged the same for a scrape as for a broadside,
                    // and its designer says so himself.
                    val overlap = overlapWidth(hit, raftX)
                    impacts = impacts + if (overlap <= hit.size.hitWidth * GRAZE_FRACTION) {
                        RaftImpact.GRAZE
                    } else {
                        RaftImpact.SOLID
                    }
                    lastImpactAt = elapsedMillis
                    rocks = rocks - hit
                    spray = spray + sprayAt(hit.x, hit.y.toInt())
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }

            // A raft that has taken this much is not going to be steered anywhere. It
            // breaks up here rather than limping to a landing it could never make.
            if (impacts.sumOf { it.severity } >= Rafting.WRECK_THRESHOLD) running = false
        }

        controller.finishRaft(impacts, landed)
    }

    val bobFrame = if ((elapsedMillis / BOB_FRAME_MILLIS) % 2L == 0L) 0 else 1

    // The river itself moves, at exactly the speed everything floating on it does. Derived
    // from the clock rather than accumulated in the tick loop so there is no second copy of
    // the current's speed to drift out of step with the first.
    val riverScroll = elapsedMillis / TICK_MILLIS.toFloat() * CURRENT_PER_TICK

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleII.Black)
            .rotaryInput { pixels ->
                raftX += pixels / pixelsPerUnit * STEER_GAIN
            },
    ) {
        Scene(
            background = ArtNames.RAFT_RIVER,
            modifier = Modifier.fillMaxSize(),
            backdropScroll = riverScroll,
            sprites = buildList {
                for (speck in foam) {
                    add(Sprite(ArtNames.RAFT_FOAM, speck.x, speck.y.toInt(), FOAM_WIDTH, FOAM_HEIGHT))
                }
                landing?.let {
                    add(
                        Sprite(
                            ArtNames.RAFT_LANDING,
                            it.x,
                            it.y.toInt(),
                            LANDING_WIDTH,
                            LANDING_HEIGHT,
                        )
                    )
                }
                for (sign in signs) {
                    add(Sprite(ArtNames.RAFT_SIGN, sign.x, sign.y.toInt(), SIGN_WIDTH, SIGN_HEIGHT))
                }
                for (rock in rocks) {
                    add(
                        Sprite(
                            rock.size.art,
                            rock.x,
                            rock.y.toInt(),
                            rock.size.width,
                            rock.size.height,
                        )
                    )
                }
                add(
                    Sprite(
                        ArtNames.raftBob[bobFrame],
                        raftX.toInt(),
                        RAFT_Y,
                        RAFT_WIDTH,
                        RAFT_HEIGHT,
                    )
                )
                for (drop in spray) {
                    add(Sprite(ArtNames.RAFT_FOAM, drop.x, drop.y.toInt(), FOAM_WIDTH, FOAM_HEIGHT))
                }
            },
        )

        // How many of the three signs have gone past, so the landing is never a surprise —
        // and, on the last stretch, whether the raft is far enough over to make it.
        //
        // That second job is why the colour has three states rather than two. The landing
        // is judged against a tolerance the player cannot see, so a raft that was already
        // going to make it looks exactly like one that is about to miss, and the only way
        // to be sure is to keep steering — into the bank the beach is part of. Going green
        // the moment the run is winnable says stop steering, which is the whole decision
        // the last four seconds are asking about.
        val linedUp = landing != null && raftX - LANDING_EDGE <= LANDING_TOLERANCE
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScreenText(
                signMarkers(signsPassed),
                color = when {
                    linedUp -> AppleII.Green
                    signsPassed >= SIGN_TIMES.size -> AppleII.Orange
                    else -> AppleIIChrome.MutedGreen
                },
                small = true,
            )
        }

        // What the river has taken out of the raft so far. Below the raft rather than up
        // with the signs: the two answer different questions — one is how far there is to
        // go, the other how much more the raft will take — and a player who has just been
        // hit should not have to find the answer among the pacing.
        RaftCondition(
            damage = impacts.sumOf { it.severity },
            // 18dp, not more: the blocks have to clear the raft's box above them and stay
            // inside the round display below, and the circle is only about 210 pixels
            // across down here.
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
        )
    }
}

/**
 * The raft's remaining integrity: one block per point of damage it can still take before
 * [Rafting.WRECK_THRESHOLD] breaks it up, going dark from the right as they are spent.
 *
 * Blocks rather than the continuous bar the hunt uses for its session clock, because this
 * quantity is discrete and very small. The question a player asks of it mid-descent is
 * "can I take another one of those", and six timbers with two left answers that at a
 * glance, where a bar seven-tenths full has to be interpreted. It goes orange at two, the
 * same warning the hunt's carry readout gives at its cap.
 *
 * There is no equivalent in the 1985 original, which told the player nothing until the
 * raft broke up — see docs/reference/game-mechanics-1985.md. Graded impacts are only
 * fairer than its flat ones if the player can see the grading land.
 */
@Composable
private fun RaftCondition(damage: Int, modifier: Modifier = Modifier) {
    val left = (Rafting.WRECK_THRESHOLD - damage).coerceAtLeast(0)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(Rafting.WRECK_THRESHOLD) { index ->
            Box(
                modifier = Modifier
                    .size(width = 12.dp, height = 7.dp)
                    .background(
                        when {
                            index >= left -> AppleIIChrome.DimGreen
                            left <= 2 -> AppleII.Orange
                            else -> AppleII.Green
                        }
                    ),
            )
        }
    }
}

/** Overlap deeper than this fraction of the rock's width is a hit, not a graze. */
private const val GRAZE_FRACTION = 0.35f

/**
 * How far the raft and the rock proper overlap across the channel — see [RockSize] for why
 * the rock's spray is discounted and the raft's is not. The raft's own white water is a
 * thin fringe on art that fills its box to within a unit; the rock's is a collar wider
 * than the boulder inside it.
 */
private fun overlapWidth(rock: Rock, raftX: Float): Float {
    val left = max((rock.x + rock.size.sprayInsetX).toFloat(), raftX)
    val right = min((rock.x + rock.size.width - rock.size.sprayInsetX).toFloat(), raftX + RAFT_WIDTH)
    return (right - left).coerceAtLeast(0f)
}

private fun overlapsRaft(rock: Rock, raftX: Float): Boolean {
    val rockTop = rock.y + rock.size.sprayInsetY
    val rockBottom = rock.y + rock.size.height - rock.size.sprayInsetY
    val verticallyClear = rockBottom <= RAFT_Y || rockTop >= RAFT_Y + RAFT_HEIGHT
    return !verticallyClear && overlapWidth(rock, raftX) > 0f
}

/** Three specks of water thrown up where something was struck. */
private fun sprayAt(x: Int, y: Int): List<Drifter> = listOf(
    Drifter(x, y.toFloat()),
    Drifter(x + FOAM_WIDTH, (y - FOAM_HEIGHT).toFloat()),
    Drifter(x - FOAM_WIDTH, (y - FOAM_HEIGHT / 2).toFloat()),
)

/**
 * A bullet for each sign already passed, a middle dot for each still to come.
 *
 * Not the filled and hollow circles this used to draw. Shaston has neither — see
 * `FontCoverageTest` — so Android was borrowing both from the system font and the browser,
 * having nothing to borrow from, drew an empty line where the readout should be. These two
 * are in the font, and being the same shape at two sizes they read as a count rather than
 * as two different things.
 */
private fun signMarkers(passed: Int): String {
    val total = SIGN_TIMES.size
    val seen = passed.coerceIn(0, total)
    return buildString {
        repeat(seen) { append("• ") }
        repeat(total - seen) { append("· ") }
        if (seen >= total) append(" LAND")
    }.trim()
}

/**
 * What the river did, shown once and dismissed by a tap — the counterpart to
 * [CrossingResultScreen], and deliberately built the same way.
 */
@Composable
fun RaftResultScreen(controller: GameController) {
    val outcome = controller.raftOutcome ?: return
    val damage = outcome.damage

    SceneScrollColumn(
        background = if (outcome.wrecked) {
            ArtNames.RAFT_WRECK
        } else {
            ArtNames.landmark(LandmarkId.COLUMBIA_RIVER)
        }
    ) {
        Gap(4)
        // "A rough run" is reserved for a descent that cost an ox or a life. A few pounds
        // of flour off the back of the raft is a scrape, and titling it as a rough run
        // both overstates it and leaves nothing to say about the run that went badly.
        // Either way the losses are itemised below, so nothing is hidden by the softer
        // heading — it is the heading's job to say how much they mattered.
        ScreenTitle(
            when {
                outcome.wrecked -> "The raft broke up"
                !outcome.landed -> "Swept past"
                damage.costLifeOrLivestock -> "A rough run"
                else -> "Ashore"
            }
        )
        ScreenText(raftDetail(outcome.landed, outcome.extraDays, outcome.wrecked), color = AppleII.White)

        if (damage.isAnything) {
            Gap(6)
            for (line in damageLines(damage)) {
                ScreenText(line, color = AppleIIChrome.MutedGreen, small = true)
            }
        }

        Gap(12)
        MenuChip(label = "Continue", primary = true, onClick = { controller.dismissRaft() })
    }
}

private fun raftDetail(landed: Boolean, extraDays: Int, wrecked: Boolean): String = when {
    wrecked && !landed -> "What is left of the raft washed up short of the landing."
    wrecked -> "You reached the landing on a raft coming apart under you."
    !landed -> "The landing went by. It cost ${counted(extraDays, "day", "days")} to walk back up."
    else -> "The raft is beached at the path, and Oregon City is twenty miles off."
}

private fun damageLines(damage: RaftDamage): List<String> = buildList {
    if (damage.foodPounds > 0) add("${damage.foodPounds} lb of food lost")
    if (damage.oxenLost > 0) add("${counted(damage.oxenLost, "ox", "oxen")} lost")
    if (damage.clothingSets > 0) {
        add("${counted(damage.clothingSets, "set", "sets")} of clothing lost")
    }
    for (name in damage.drowned) add("$name drowned")
}
