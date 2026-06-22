package com.snakenav

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.snakenav.map.SnakeMapScreen
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(applicationContext, null, WellKnownTileServer.MapLibre)
        setContent {
            Surface(modifier = Modifier.fillMaxSize()) {
                SnakeMapScreen()
            }
        }
    }
}
