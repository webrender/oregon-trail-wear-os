package com.oregontrail.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.oregontrail.wear.data.SaveRepository
import com.oregontrail.wear.ui.GameController
import com.oregontrail.wear.ui.OregonTrailApp
import java.io.File

class MainActivity : ComponentActivity() {

    /**
     * Built here rather than inside the composition so the run survives recomposition,
     * and so the save file's location is decided in the one place that knows about
     * Android at all. Everything below [GameController] is plain Kotlin.
     */
    private val controller by lazy {
        GameController(SaveRepository(File(filesDir, "run.json")))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OregonTrailApp(controller) }
    }
}
