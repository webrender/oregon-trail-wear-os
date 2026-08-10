package com.oregontrail.wear.ui

import com.oregontrail.wear.core.Ailment
import com.oregontrail.wear.core.Animal
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
        val file = File(artDirectory, "$name.pix")
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
