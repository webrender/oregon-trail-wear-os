package com.oregontrail.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.oregontrail.wear.ui.theme.AppleII

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TitleScreen() }
    }
}

/**
 * Placeholder title screen. Exists so the stripped, native-code-free build can be
 * verified end to end on device before any game logic lands.
 */
@Composable
private fun TitleScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleII.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "THE OREGON TRAIL",
            color = AppleII.Green,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.title3,
        )
    }
}
