package com.oregontrail.wear.ui.art

import com.oregontrail.wear.core.Animal
import com.oregontrail.wear.core.GameEvent
import com.oregontrail.wear.core.Good
import com.oregontrail.wear.core.LandmarkId
import com.oregontrail.wear.core.TerrainRegion
import com.oregontrail.wear.core.Weather

/**
 * Which `.pix` asset stands for what.
 *
 * Kept pure and free of Android imports so the mapping can be tested against the files
 * on disk under plain JUnit. That test matters more than it looks: a wrong name here
 * fails silently at runtime — [ArtLoader.loadOrNull] swallows the miss and
 * [PixelArtImage] draws nothing — so a typo would show up as a mysteriously empty screen
 * rather than as a crash.
 */
object ArtNames {

    /** The scene for a landmark. Every [LandmarkId] has one. */
    fun landmark(id: LandmarkId): String = "lm_" + id.name.lowercase()

    /**
     * The backdrop to travel across on the way to [heading].
     *
     * Terrain is chosen by where the party is on the trail rather than by anything in
     * the game model, because the model has no notion of biome — the trail is a graph of
     * named places, and the landscape between them is presentation only. Snow overrides
     * everything: a snowed-in prairie and a snowed-in mountain pass both read as "you
     * left too late", which is the point the art is making.
     */
    fun terrain(heading: LandmarkId, weather: Weather): String {
        if (weather == Weather.SNOWY) return "terrain_snow"
        return when (TerrainRegion.of(heading)) {
            TerrainRegion.PRAIRIE -> "terrain_prairie"
            TerrainRegion.PLAINS -> "terrain_plains"
            TerrainRegion.ROCKIES -> "terrain_rockies"
            TerrainRegion.DESERT -> "terrain_desert"
            TerrainRegion.FOREST -> "terrain_forest"
        }
    }

    fun weather(weather: Weather): String = when (weather) {
        Weather.HOT, Weather.WARM -> "weather_sun"
        Weather.PLEASANT, Weather.COOL -> "weather_cloud"
        Weather.RAINY -> "weather_rain"
        Weather.SNOWY -> "weather_snow"
        Weather.COLD, Weather.VERY_COLD -> "weather_cold"
    }

    fun good(good: Good): String = when (good) {
        Good.OXEN -> "icon_ox"
        Good.FOOD -> "icon_food"
        Good.CLOTHING -> "icon_clothing"
        Good.BULLETS -> "icon_bullets"
        Good.SPARE_WHEEL -> "icon_wheel"
        Good.SPARE_AXLE -> "icon_axle"
        Good.SPARE_TONGUE -> "icon_tongue"
    }

    /**
     * A picture to accompany an event, or null if words alone will do.
     *
     * Deliberately sparse. An image on every event would turn the travel screen into a
     * slideshow and rob the deaths of their weight.
     */
    fun forEvent(event: GameEvent): String? = when (event) {
        is GameEvent.Died -> "tombstone"
        is GameEvent.OxDied -> "ox_dead"
        is GameEvent.WagonBroke -> if (event.hadSpare) null else "wagon_ox_broken"
        else -> null
    }

    /** The two frames of the wagon's walk cycle. */
    val wagonWalk: List<String> = listOf("wagon_ox_1", "wagon_ox_2")

    const val WAGON_STOPPED: String = "wagon_ox_broken"

    /** One frame (0 or 1) of [animal]'s run cycle. */
    fun animal(animal: Animal, frame: Int): String =
        "animal_${animal.name.lowercase()}_${frame + 1}"

    const val HUNT_TERRAIN: String = "hunt_terrain"
    const val HUNT_CARCASS: String = "hunt_carcass"
    const val HUNT_BULLET: String = "hunt_bullet"
    const val HUNTER_STAND: String = "hunter_stand"
    const val HUNTER_SHOOT: String = "hunter_shoot"
}
