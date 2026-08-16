package com.oregontrail.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.oregontrail.wear.ui.art.displayWidthPx
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.oregontrail.wear.ui.components.Text
import com.oregontrail.wear.core.Animal
import com.oregontrail.wear.core.Good
import com.oregontrail.wear.core.Hunting
import com.oregontrail.wear.ui.GameController
import com.oregontrail.wear.ui.art.ArtLoader
import com.oregontrail.wear.ui.art.ArtNames
import com.oregontrail.wear.ui.art.SCENE_HEIGHT
import com.oregontrail.wear.ui.art.SCENE_WIDTH
import com.oregontrail.wear.ui.art.Scene
import com.oregontrail.wear.ui.art.Sprite
import com.oregontrail.wear.ui.art.spriteEdgePx
import com.oregontrail.wear.ui.components.CompactActionChip
import com.oregontrail.wear.ui.components.Gap
import com.oregontrail.wear.ui.components.IconValue
import com.oregontrail.wear.ui.theme.AppleII
import com.oregontrail.wear.ui.theme.AppleIIChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/** How long a hunt lasts if the player doesn't run out of ammunition or leave first. */
private const val SESSION_MILLIS = 20_000L

/** The movement/spawn tick. 40ms is 25 updates a second — smooth enough for a tap target
 *  this size without redrawing the scene faster than the watch needs to. */
private const val TICK_MILLIS = 40L

private const val SPAWN_GAP_MIN_MILLIS = 350L
private const val SPAWN_GAP_MAX_MILLIS = 950L

/** How long a downed animal's carcass sprite stays on screen before it's cleared. */
private const val CARCASS_MILLIS = 450L

/** How long the hunter's shooting frame holds after a tap, win or miss. */
private const val SHOT_FLASH_MILLIS = 150L

private const val WALK_FRAME_MILLIS = 160L

private const val HUNTER_WIDTH = 16
private const val HUNTER_HEIGHT = 20
private const val HUNTER_X = (SCENE_WIDTH - HUNTER_WIDTH) / 2
private const val HUNTER_Y = SCENE_HEIGHT - HUNTER_HEIGHT

/** The carcass a downed animal leaves behind, in scene units. */
private const val CARCASS_WIDTH = 16
private const val CARCASS_HEIGHT = 10

/**
 * Where the rifle's muzzle sits in the hunter's own box — the one point on him that is
 * nowhere near centred, since the barrel is held straight out ahead of him.
 *
 * Measured off `hunter_shoot.png` rather than guessed. The art is trimmed to its visible
 * pixels, so the muzzle is the rightmost thing in the file and lands exactly on the right
 * edge of the box; the barrel sits a little over a fifth of the way down. Mirroring the
 * sprite to face left mirrors the muzzle with it, onto the left edge of the same box.
 */
private fun muzzleX(facingRight: Boolean): Int =
    if (facingRight) HUNTER_X + HUNTER_WIDTH else HUNTER_X

private const val MUZZLE_Y = HUNTER_Y + 6

/**
 * Scene-pixels the bullet covers per tick. Faster than any animal on purpose — the
 * bullet has to close a gap the animal is also crossing — but slow enough to cover in a
 * visible handful of frames rather than arrive in one tick. That travel time is the
 * whole mechanic: aim at where an animal *is* and by the time the bullet gets there it
 * has moved on, so hitting anything means leading the shot.
 */
private const val BULLET_SPEED_PER_TICK = 3.5f

/**
 * How much of the scene an animal takes up, and so also how big a target it is — see
 * [Sprite] and docs/art-brief.md. A game-balance number rather than an art one: the
 * artwork is fitted into this box, and [ActiveAnimal.contains] aims at the same box, so a
 * bear is easier to hit than a squirrel by exactly as much as it looks.
 */
private fun Animal.width(): Int = when (this) {
    Animal.SQUIRREL -> 10
    Animal.RABBIT -> 12
    Animal.DEER -> 24
    Animal.BEAR -> 28
    Animal.BISON -> 32
}

private fun Animal.height(): Int = when (this) {
    Animal.SQUIRREL -> 8
    Animal.RABBIT -> 10
    Animal.DEER -> 20
    Animal.BEAR -> 22
    Animal.BISON -> 24
}

/** Scene-pixels covered per tick. Bigger game is a slower, easier target. */
private fun Animal.speedPerTick(): Float = when (this) {
    Animal.SQUIRREL -> 2.4f
    Animal.RABBIT -> 2.0f
    Animal.DEER -> 1.4f
    Animal.BEAR -> 1.1f
    Animal.BISON -> 1.0f
}

/** The y-lane it runs along, clear of the hunter standing at the bottom of the scene. */
private fun Animal.lane(): Int = when (this) {
    Animal.SQUIRREL -> 10
    Animal.RABBIT -> 28
    Animal.DEER -> 16
    Animal.BEAR -> 14
    Animal.BISON -> 22
}

private data class ActiveAnimal(
    val animal: Animal,
    val x: Float,
    val y: Int,
    val rightward: Boolean,
    val frame: Int,
) {
    fun contains(tapX: Int, tapY: Int): Boolean {
        val left = x.toInt()
        return tapX in left until (left + animal.width()) && tapY in y until (y + animal.height())
    }
}

private data class Carcass(val x: Int, val y: Int, val untilMillis: Long)

/**
 * A shot in flight: a point moving at a constant velocity from the muzzle toward
 * wherever the player tapped. It keeps travelling in that direction past the tapped
 * point until it leaves the scene, rather than stopping there — a tap slightly short of
 * a fast animal can still connect if the animal is still crossing the bullet's path when
 * it arrives.
 */
private data class Bullet(val x: Float, val y: Float, val vx: Float, val vy: Float) {
    fun advance(): Bullet = copy(x = x + vx, y = y + vy)

    val offScene: Boolean
        get() = x < 0f || x > SCENE_WIDTH || y < 0f || y > SCENE_HEIGHT
}

/**
 * Hunting: a real-time, tap-to-shoot minigame, per the 1985 design's own description of
 * it (see docs/reference/game-mechanics-1985.md). Animals cross the scene one at a time
 * from a species list weighted by [com.oregontrail.wear.core.HuntingGround].
 *
 * A tap doesn't hit instantly — it fires a [Bullet] from the rifle's muzzle toward the
 * tapped point, and the bullet takes several ticks to get there while the animal keeps
 * moving. Tap directly on an animal and the bullet almost always arrives after it has
 * already moved on; hitting it means leading the shot, tapping into the gap the animal
 * is about to cross rather than where it currently stands. Every tap costs a bullet
 * whether or not it ends up landing, and only one shot is in flight at a time. The
 * session ends on its own — out of time, out of ammunition, or the player taps away —
 * and reports exactly once to [GameController.finishHunt], which is where the shot
 * tally turns into food, spent ammunition, and a day gone from the calendar.
 *
 * Everything here is disposable UI state. Nothing about an in-progress hunt is saved, so
 * a hunt interrupted by the OS killing the app is simply a hunt that never happened —
 * the same as backing out of any other menu without having committed to it yet.
 */
@Composable
fun HuntingScreen(controller: GameController) {
    val ground = controller.huntGround ?: return
    val startingBullets = remember { controller.game.inventory.bullets }
    val scope = rememberCoroutineScope()

    // Decode every sprite this hunt can possibly show before the first tick, off the main
    // thread. Without it the first crossing by each species pays for two PNG decodes
    // inside a 40ms tick, and the animal visibly stutters on exactly the frame the player
    // is trying to lead. The sizes have to match what Scene will ask for or the warmed
    // entries are never found again — hence spriteEdgePx rather than a guess.
    val displayWidthPx = displayWidthPx()
    LaunchedEffect(ground, displayWidthPx) {
        withContext(Dispatchers.Default) {
            for (species in ground.animals.distinct()) {
                val edge = spriteEdgePx(max(species.width(), species.height()), displayWidthPx)
                ArtLoader.prewarm((0..1).map { ArtNames.animal(species, it) }, edge)
            }
            ArtLoader.prewarm(
                listOf(ArtNames.HUNTER_STAND, ArtNames.HUNTER_SHOOT),
                spriteEdgePx(max(HUNTER_WIDTH, HUNTER_HEIGHT), displayWidthPx),
            )
            ArtLoader.prewarm(
                listOf(ArtNames.HUNT_CARCASS),
                spriteEdgePx(max(CARCASS_WIDTH, CARCASS_HEIGHT), displayWidthPx),
            )
        }
    }

    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var bulletsUsed by remember { mutableIntStateOf(0) }
    var meatShot by remember { mutableIntStateOf(0) }
    var bearsEscaped by remember { mutableIntStateOf(0) }
    var active by remember { mutableStateOf<ActiveAnimal?>(null) }
    var carcass by remember { mutableStateOf<Carcass?>(null) }
    var bullet by remember { mutableStateOf<Bullet?>(null) }
    var shotFlash by remember { mutableStateOf(false) }
    // Which way the hunter is turned. Set by the last shot he took and held there, so he
    // stands facing wherever he last aimed rather than snapping back to the right.
    var facingRight by remember { mutableStateOf(true) }
    var leaving by remember { mutableStateOf(false) }

    LaunchedEffect(ground) {
        var nextSpawnAt = 0L
        while (!leaving && elapsedMillis < SESSION_MILLIS && bulletsUsed < startingBullets) {
            delay(TICK_MILLIS)
            elapsedMillis += TICK_MILLIS

            carcass?.let { if (elapsedMillis >= it.untilMillis) carcass = null }

            bullet?.let { inFlight ->
                val moved = inFlight.advance()
                val target = active
                when {
                    target != null && target.contains(moved.x.toInt(), moved.y.toInt()) -> {
                        meatShot += controller.huntMeatRoll(target.animal)
                        carcass = Carcass(target.x.toInt(), target.y, elapsedMillis + CARCASS_MILLIS)
                        active = null
                        bullet = null
                    }
                    moved.offScene -> bullet = null
                    else -> bullet = moved
                }
            }

            val current = active
            active = if (current == null) {
                if (elapsedMillis >= nextSpawnAt) {
                    val species = ground.animals.random()
                    val rightward = Random.nextBoolean()
                    val startX = if (rightward) {
                        -species.width().toFloat()
                    } else {
                        SCENE_WIDTH.toFloat()
                    }
                    ActiveAnimal(species, startX, species.lane(), rightward, frame = 0)
                } else {
                    null
                }
            } else {
                val dx = current.animal.speedPerTick() * if (current.rightward) 1f else -1f
                val nx = current.x + dx
                val exited = if (current.rightward) {
                    nx > SCENE_WIDTH
                } else {
                    nx < -current.animal.width().toFloat()
                }
                if (exited) {
                    if (current.animal == Animal.BEAR) bearsEscaped++
                    nextSpawnAt = elapsedMillis +
                        Random.nextLong(SPAWN_GAP_MIN_MILLIS, SPAWN_GAP_MAX_MILLIS)
                    null
                } else {
                    val frame = if ((elapsedMillis / WALK_FRAME_MILLIS) % 2L == 0L) 0 else 1
                    current.copy(x = nx, frame = frame)
                }
            }
        }
        controller.finishHunt(bulletsUsed, meatShot, bearsEscaped)
    }

    val bulletsLeft = (startingBullets - bulletsUsed).coerceAtLeast(0)

    // What the wagon would actually leave with, not what has been shot. Everything past
    // the carry limit is left on the ground (see [Hunting.CARRY_LIMIT_POUNDS]), so a
    // running total of meat shot is a number that does not become food — showing it was
    // read, reasonably, as a promise the supplies screen then broke.
    val carried = meatShot.coerceAtMost(Hunting.CARRY_LIMIT_POUNDS)
    val atCarryLimit = meatShot >= Hunting.CARRY_LIMIT_POUNDS

    Box(
        modifier = Modifier.fillMaxSize().background(AppleII.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            // The readout goes *above* the scene, and this screen carries no heading.
            //
            // Both fall out of the same measurement: on the 384x384 watch the visible
            // circle leaves only ~76 px below a full-width scene before the curve closes
            // in, and the "End hunt" chip alone needs 64 of it. The layout this replaced
            // stacked a heading and a stats line under the scene, each of which wrapped
            // to two lines at this width; together they pushed the chip clean off the
            // bottom of the display and left the wrapped tail of the stats line outside
            // the bezel. It fit on the 454 emulator, which is where it was checked.
            //
            // The band above the scene was empty padding and is the only space left, so
            // the live numbers go there and the heading goes away — during a 20-second
            // reflex game the terrain art already says where you are, and meat and
            // ammunition are what the player is actually watching.
            // 28dp, not the 48dp every scrolling screen uses: the readout is narrower
            // than a scene, so it can sit higher in the circle, and those 40 px are what
            // the chip at the bottom needs.
            modifier = Modifier.fillMaxSize().padding(top = 28.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                // Shown as a fraction of the limit rather than a bare total, so the
                // ceiling is visible before it is hit, and in orange once it is — at
                // which point every further shot is a wasted bullet.
                IconValue(
                    iconArt = ArtNames.good(Good.FOOD),
                    value = "$carried/${Hunting.CARRY_LIMIT_POUNDS}",
                    color = if (atCarryLimit) AppleII.Orange else AppleII.Green,
                )
                Spacer(Modifier.width(8.dp))
                IconValue(
                    iconArt = ArtNames.good(Good.BULLETS),
                    value = "$bulletsLeft",
                )
            }
            Gap(2)
            Scene(
                background = ArtNames.HUNT_TERRAIN,
                modifier = Modifier.fillMaxWidth(0.85f).aspectRatio(2f),
                sprites = buildList {
                    active?.let {
                        // Every animal is drawn facing right — head and horns on the
                        // right edge in all ten frames — so it is the leftward run that
                        // needs mirroring, or the animal walks backwards.
                        add(
                            Sprite(
                                ArtNames.animal(it.animal, it.frame),
                                it.x.toInt(),
                                it.y,
                                it.animal.width(),
                                it.animal.height(),
                                flip = !it.rightward,
                            )
                        )
                    }
                    carcass?.let {
                        add(Sprite(ArtNames.HUNT_CARCASS, it.x, it.y, CARCASS_WIDTH, CARCASS_HEIGHT))
                    }
                    bullet?.let {
                        add(Sprite(ArtNames.HUNT_BULLET, it.x.toInt(), it.y.toInt(), 1, 1))
                    }
                    // Both hunter frames are authored facing right, so facing left is the
                    // mirrored draw — the same trick the animals use above.
                    add(
                        Sprite(
                            if (shotFlash) ArtNames.HUNTER_SHOOT else ArtNames.HUNTER_STAND,
                            HUNTER_X,
                            HUNTER_Y,
                            HUNTER_WIDTH,
                            HUNTER_HEIGHT,
                            flip = !facingRight,
                        )
                    )
                },
                onTap = { x, y ->
                    // One shot in flight at a time — a bolt-action rifle, not a spray,
                    // and it's what makes leading a real decision rather than something
                    // spammable away.
                    if (bullet == null && bulletsUsed < startingBullets) {
                        bulletsUsed++
                        shotFlash = true
                        scope.launch {
                            delay(SHOT_FLASH_MILLIS)
                            shotFlash = false
                        }
                        // He turns to the shot before taking it: a tap left of where he
                        // stands swings him left, one to the right swings him back. The
                        // muzzle moves with him, so the bullet always leaves the barrel
                        // and never crosses through his own body to get there.
                        facingRight = x >= HUNTER_X + HUNTER_WIDTH / 2
                        val muzzle = muzzleX(facingRight)
                        val dx = (x - muzzle).toFloat()
                        val dy = (y - MUZZLE_Y).toFloat()
                        val distance = sqrt(dx * dx + dy * dy)
                        if (distance >= 1f) {
                            bullet = Bullet(
                                x = muzzle.toFloat(),
                                y = MUZZLE_Y.toFloat(),
                                vx = dx / distance * BULLET_SPEED_PER_TICK,
                                vy = dy / distance * BULLET_SPEED_PER_TICK,
                            )
                        }
                    }
                },
            )
            Gap(2)
            // The session clock, as a bar rather than a digit count. There is no room
            // left for a third number on this display, and a draining bar is the easier
            // read anyway while the player is tracking a moving target.
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(4.dp)
                    .background(AppleIIChrome.DimGreen),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(
                            ((SESSION_MILLIS - elapsedMillis).toFloat() / SESSION_MILLIS)
                                .coerceIn(0f, 1f)
                        )
                        .fillMaxHeight()
                        .background(AppleIIChrome.MutedGreen),
                )
            }
            Gap(2)
            CompactActionChip(label = "End hunt", onClick = { leaving = true })
        }
    }
}
