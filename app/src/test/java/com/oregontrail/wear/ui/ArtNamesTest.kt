package com.oregontrail.wear.ui

import com.oregontrail.wear.core.Ailment
import com.oregontrail.wear.core.Animal
import com.oregontrail.wear.core.Encounter
import com.oregontrail.wear.core.GameEvent
import com.oregontrail.wear.core.Good
import com.oregontrail.wear.core.LandmarkId
import com.oregontrail.wear.core.Weather
import com.oregontrail.wear.ui.art.ArtNames
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks that every art name the UI can ask for is a file that actually exists.
 *
 * This is the test that earns its keep. A wrong name fails *silently* at runtime —
 * `ArtLoader.loadOrNull` swallows the missing asset and the canvas draws nothing — so
 * without this the symptom would be a blank patch on a watch screen, with no exception
 * and no log line to work back from.
 */
class ArtNamesTest {

    private val artDirectory = File("src/main/assets/art")

    private fun assertArtExists(name: String, context: String) {
        val file = File(artDirectory, "$name.png")
        assertTrue("$context refers to missing art '$name' (${file.absolutePath})", file.isFile)
    }

    @Test
    fun `every landmark has a scene`() {
        for (id in LandmarkId.entries) {
            assertArtExists(ArtNames.landmark(id), "landmark $id")
        }
    }

    @Test
    fun `every stretch of trail has terrain in every weather`() {
        for (id in LandmarkId.entries) {
            for (weather in Weather.entries) {
                assertArtExists(ArtNames.terrain(id, weather), "terrain heading to $id in $weather")
            }
        }
    }

    @Test
    fun `every weather has an icon`() {
        for (weather in Weather.entries) {
            assertArtExists(ArtNames.weather(weather), "weather $weather")
        }
    }

    @Test
    fun `every good has an icon`() {
        for (good in Good.entries) {
            assertArtExists(ArtNames.good(good), "good $good")
        }
    }

    @Test
    fun `the wagon has both walk frames and a broken one`() {
        for (frame in ArtNames.wagonWalk) {
            assertArtExists(frame, "wagon walk cycle")
        }
        assertArtExists(ArtNames.WAGON_STOPPED, "stopped wagon")
    }

    @Test
    fun `the trail marker exists`() {
        assertArtExists(ArtNames.TRAIL_MARKER, "trail marker")
    }

    @Test
    fun `the map has all six tiles and a marker for the party`() {
        for (tile in ArtNames.mapTiles) {
            assertArtExists(tile, "map tile")
        }
        assertArtExists(ArtNames.MAP_WAGON, "the party's marker on the map")
    }

    @Test
    fun `the store interior exists`() {
        assertArtExists(ArtNames.STORE_INTERIOR, "store interior")
    }

    @Test
    fun `every animal has both frames of a run cycle`() {
        for (animal in Animal.entries) {
            for (frame in 0..1) {
                assertArtExists(ArtNames.animal(animal, frame), "$animal frame $frame")
            }
        }
    }

    @Test
    fun `the hunt has its terrain, carcass, and hunter poses`() {
        assertArtExists(ArtNames.HUNT_TERRAIN, "hunt terrain")
        assertArtExists(ArtNames.HUNT_CARCASS, "hunt carcass")
        assertArtExists(ArtNames.HUNT_BULLET, "hunt bullet")
        assertArtExists(ArtNames.HUNTER_STAND, "hunter standing")
        assertArtExists(ArtNames.HUNTER_SHOOT, "hunter shooting")
    }

    @Test
    fun `the rafting minigame has its river, raft, hazards and landing`() {
        for (frame in ArtNames.raftBob) {
            assertArtExists(frame, "raft bob cycle")
        }
        assertArtExists(ArtNames.RAFT_RIVER, "raft river")
        assertArtExists(ArtNames.ROCK_SMALL, "small rock")
        assertArtExists(ArtNames.ROCK_LARGE, "large rock")
        assertArtExists(ArtNames.RAFT_FOAM, "raft foam")
        assertArtExists(ArtNames.RAFT_SIGN, "raft sign")
        assertArtExists(ArtNames.RAFT_LANDING, "raft landing")
        assertArtExists(ArtNames.RAFT_WRECK, "raft wreck")
    }

    /**
     * The one backdrop in the game that may not be wider than it is tall.
     *
     * It is drawn down the whole round display looking straight at the channel, scrolling
     * as a strip of itself alternating with a vertically flipped copy. That tiling makes
     * its width the only exact fit, so it is never cropped sideways — which matters,
     * because [com.oregontrail.wear.ui.screens.RaftScreen] places its banks by scene
     * units rather than by anything it can see in the art.
     *
     * The height is what this pins. One tile has to reach at least a full screen, or the
     * river repeats within a single view and the player watches the same stretch of bank
     * go by twice at once. The art is square, which clears the bar comfortably; a 16:9
     * file dropped in by mistake would not.
     */
    @Test
    fun `the raft river backdrop is at least as tall as the display is`() {
        val (width, height) = pngSize(File(artDirectory, "${ArtNames.RAFT_RIVER}.png"))
        assertTrue(
            "${ArtNames.RAFT_RIVER} must be no wider than it is tall, was ${width}x$height",
            height >= width,
        )
    }

    /**
     * A PNG's dimensions, read straight out of its IHDR chunk.
     *
     * `javax.imageio` is not on the Android unit-test classpath, and pulling in an image
     * library to read two integers would be absurd. The header is fixed-layout: an
     * 8-byte signature, a 4-byte length, the 4-byte chunk type, then width and height as
     * big-endian 32-bit integers.
     */
    private fun pngSize(file: File): Pair<Int, Int> {
        val header = file.readBytes().copyOfRange(0, 24)
        fun intAt(offset: Int): Int = (0..3).fold(0) { acc, i ->
            (acc shl 8) or (header[offset + i].toInt() and 0xFF)
        }
        return intAt(16) to intAt(20)
    }

    @Test
    fun `every kind of encounter has a portrait`() {
        val sample = listOf(
            Encounter.Traveler("test"),
            Encounter.Trade(Good.FOOD, 1, Good.BULLETS, 1),
            Encounter.Guide(costCents = 100, healthBenefit = 5),
        )
        for (encounter in sample) {
            assertArtExists(ArtNames.forEncounter(encounter), "encounter $encounter")
        }
    }

    @Test
    fun `event art exists wherever an event supplies one`() {
        for (event in everyKindOfEvent) {
            val art = ArtNames.forEvent(event) ?: continue
            assertArtExists(art, "event $event")
        }
    }

    /**
     * Snow overrides the region, and everything else follows the region.
     *
     * Pinned because the override is easy to lose in a refactor and the loss would be
     * invisible — a snowy day in the Rockies would simply show the ordinary Rockies.
     */
    @Test
    fun `snow overrides regional terrain everywhere`() {
        for (id in LandmarkId.entries) {
            assertTrue(
                "snow should override terrain heading to $id",
                ArtNames.terrain(id, Weather.SNOWY) == "terrain_snow",
            )
        }
    }

    @Test
    fun `every event except distance travelled has something to say`() {
        for (event in everyKindOfEvent) {
            val text = EventText.describe(event)
            if (event is GameEvent.Traveled) continue
            assertNotNull("no text for $event", text)
            assertTrue("empty text for $event", !text.isNullOrBlank())
        }
    }

    @Test
    fun `travelling a distance is not worth stopping for`() {
        assertTrue(!EventText.pausesTravel(GameEvent.Traveled(14)))
        assertTrue(EventText.pausesTravel(GameEvent.Died("Amanda", "typhoid")))
        assertTrue(EventText.pausesTravel(GameEvent.ArrivedAt(LandmarkId.CHIMNEY_ROCK)))
    }

    /**
     * A wagon part that broke with a spare on board is a footnote; one without is the
     * end of the day. The distinction is the whole reason spares are worth buying, so it
     * is pinned rather than left to the reader of the `when`.
     */
    @Test
    fun `a breakage only stops the wagon when there is no spare`() {
        assertTrue(!EventText.pausesTravel(GameEvent.WagonBroke("wheel", hadSpare = true)))
        assertTrue(EventText.pausesTravel(GameEvent.WagonBroke("wheel", hadSpare = false)))
    }

    /**
     * One instance of every [GameEvent] the engine can emit.
     *
     * Written out by hand rather than generated, so that adding an event to the engine
     * without adding it here is a compile error in the `when` above rather than a gap
     * that quietly goes untested.
     */
    private val everyKindOfEvent: List<GameEvent> = listOf(
        GameEvent.Traveled(14),
        GameEvent.ArrivedAt(LandmarkId.FORT_LARAMIE),
        GameEvent.FellIll("Eliza", Ailment.CHOLERA),
        GameEvent.Recovered("Eliza"),
        GameEvent.Died("Samuel", "dysentery"),
        GameEvent.OxDied(remaining = 0),
        GameEvent.OxDied(remaining = 1),
        GameEvent.OxDied(remaining = 4),
        GameEvent.WagonBroke("axle", hadSpare = true),
        GameEvent.WagonBroke("axle", hadSpare = false),
        GameEvent.SuppliesStolen("food", 30),
        GameEvent.SuppliesFound("wild fruit", 20),
        GameEvent.LostTime("heavy fog", 9),
        GameEvent.OutOfFood,
        GameEvent.BadWater,
        GameEvent.PartyLost,
    )
}
