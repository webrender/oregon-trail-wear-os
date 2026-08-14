package com.oregontrail.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Text
import com.oregontrail.wear.core.Animal
import com.oregontrail.wear.ui.GameController
import com.oregontrail.wear.ui.art.ArtNames
import com.oregontrail.wear.ui.art.SCENE_HEIGHT
import com.oregontrail.wear.ui.art.SCENE_WIDTH
import com.oregontrail.wear.ui.art.Scene
import com.oregontrail.wear.ui.art.Sprite
import com.oregontrail.wear.ui.components.Gap
import com.oregontrail.wear.ui.components.ScreenText
import com.oregontrail.wear.ui.components.ScreenTitle
import com.oregontrail.wear.ui.theme.AppleII
import com.oregontrail.wear.ui.theme.AppleIIChrome
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 * nowhere near centred, since the barrel is held straight out to the right.
 *
 * Measured off `hunter_shoot.png` rather than guessed. The art is trimmed to its visible
 * pixels, so the muzzle is the rightmost thing in the file and lands exactly on the right
 * edge of the box; the barrel sits a little over a fifth of the way down.
 */
private const val MUZZLE_X = HUNTER_X + HUNTER_WIDTH
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

    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var bulletsUsed by remember { mutableIntStateOf(0) }
    var meatShot by remember { mutableIntStateOf(0) }
    var bearsEscaped by remember { mutableIntStateOf(0) }
    var animalsShot by remember { mutableIntStateOf(0) }
    var active by remember { mutableStateOf<ActiveAnimal?>(null) }
    var carcass by remember { mutableStateOf<Carcass?>(null) }
    var bullet by remember { mutableStateOf<Bullet?>(null) }
    var shotFlash by remember { mutableStateOf(false) }
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
                        animalsShot++
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

    val secondsLeft = ((SESSION_MILLIS - elapsedMillis) / 1000L).coerceAtLeast(0L)
    val bulletsLeft = (startingBullets - bulletsUsed).coerceAtLeast(0)

    Box(
        modifier = Modifier.fillMaxSize().background(AppleII.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            // Scene first, title after — the same order every other landmark-style
            // screen uses, and not just for consistency: the round bezel narrows the
            // safe width sharply near the top, so a full-width title placed there (as
            // this screen originally had it) gets its edges eaten by the bezel. Putting
            // it below the scene, in the middle of the display where the circle is
            // widest, is what makes it safe to run full width. The 48dp top padding
            // matches [com.oregontrail.wear.ui.components.RotaryScrollColumn]'s, for the
            // same reason: it clears the scene's corners of the bezel above.
            modifier = Modifier.fillMaxSize().padding(top = 48.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
                    add(
                        Sprite(
                            if (shotFlash) ArtNames.HUNTER_SHOOT else ArtNames.HUNTER_STAND,
                            HUNTER_X,
                            HUNTER_Y,
                            HUNTER_WIDTH,
                            HUNTER_HEIGHT,
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
                        val dx = (x - MUZZLE_X).toFloat()
                        val dy = (y - MUZZLE_Y).toFloat()
                        val distance = sqrt(dx * dx + dy * dy)
                        if (distance >= 1f) {
                            bullet = Bullet(
                                x = MUZZLE_X.toFloat(),
                                y = MUZZLE_Y.toFloat(),
                                vx = dx / distance * BULLET_SPEED_PER_TICK,
                                vy = dy / distance * BULLET_SPEED_PER_TICK,
                            )
                        }
                    }
                },
            )
            Gap(4)
            ScreenTitle("Hunting ${ground.displayName}")
            ScreenText(
                "$animalsShot down, $meatShot lb · $bulletsLeft bullets · ${secondsLeft}s",
                color = AppleIIChrome.MutedGreen,
                small = true,
            )
            Gap(10)
            CompactChip(
                onClick = { leaving = true },
                label = { Text("End hunt") },
            )
        }
    }
}
