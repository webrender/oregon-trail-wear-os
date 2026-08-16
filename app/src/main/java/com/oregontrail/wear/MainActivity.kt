package com.oregontrail.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.oregontrail.wear.data.FileStorage
import com.oregontrail.wear.data.HighScoreRepository
import com.oregontrail.wear.data.SaveRepository
import com.oregontrail.wear.platform.BuildInfo
import com.oregontrail.wear.ui.GameController
import com.oregontrail.wear.ui.OregonTrailApp
import com.oregontrail.wear.ui.art.ArtLoader
import java.io.File

/**
 * The whole of the Wear OS app.
 *
 * Everything the player sees is in `:shared`, which builds for the browser too. What is
 * left here is the three things only an Android app can answer: where the save file goes,
 * where the art is read from, and whether this is a debug build.
 */
class MainActivity : ComponentActivity() {

    /**
     * Built here rather than inside the composition so the run survives recomposition,
     * and so the save file's location is decided in the one place that knows about
     * Android at all. Everything below [GameController] is plain Kotlin.
     */
    private val controller by lazy {
        GameController(
            repository = SaveRepository(FileStorage(File(filesDir, "run.json"))),
            // A file of its own, so that clearing a run — abandoning it, or finishing one
            // and starting the next — cannot take the high score table with it.
            scoreboard = HighScoreRepository(FileStorage(File(filesDir, "scores.json"))),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Both before the first composition, because both are read during it: the title
        // screen asks whether to show the debug menu, and draws a banner.
        BuildInfo.isDebug = BuildConfig.DEBUG
        ArtLoader.install(applicationContext)
        setContent { OregonTrailApp(controller) }
    }

    /**
     * The run's saves are written on a background worker so they stay out of the frame
     * that has to draw their result, which leaves a window — small, but real — where the
     * newest state is queued rather than on disk. `onStop` is the last callback Wear OS
     * reliably delivers before it may kill the process, so the queue is drained here.
     */
    override fun onStop() {
        controller.flushSave()
        super.onStop()
    }
}
