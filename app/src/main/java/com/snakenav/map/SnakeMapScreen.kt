package com.snakenav.map

import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.snakenav.data.*
import com.snakenav.gamification.SnakeGauge
import kotlinx.coroutines.delay
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.camera.CameraUpdateFactory

@Composable
fun SnakeMapScreen() {
    val context = LocalContext.current
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }

    val route = remember { SampleRoute.createSummerPalace() }
    val routeState = remember { RouteState(route) }
    var gpsIndex by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }

    // GPS simulation
    LaunchedEffect(paused) {
        while (true) {
            if (!paused && gpsIndex < route.coordinates.size - 1) {
                delay(800)
                gpsIndex++
                routeState.eatUpTo(gpsIndex)
                mapInstance?.let { drawSnakeRoute(it, routeState) }
            } else {
                delay(100)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).also { mv ->
                    mv.getMapAsync { map ->
                        map.setStyle("https://tiles.openfreemap.org/styles/liberty") {
                            mapInstance = map
                            map.moveCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    route.coordinates.first().toMaplibre(),
                                    15.0
                                )
                            )
                            drawSnakeRoute(map, routeState)
                        }
                    }
                }
            }
        )

        // Progress gauge
        SnakeGauge(
            progress = routeState.progress,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )

        // Route completed text
        if (routeState.isFullyEaten) {
            Text(
                "🎉 路线已吃完！",
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
            )
        }
    }
}

private fun geoJsonLineString(coords: List<LatLng>): String {
    val pts = coords.joinToString(",") { "[${it.lng},${it.lat}]" }
    return """{"type":"LineString","coordinates":[$pts]}"""
}

private fun geoJsonPoint(lat: Double, lng: Double): String {
    return """{"type":"Point","coordinates":[$lng,$lat]}"""
}

private fun drawSnakeRoute(map: MapLibreMap, state: RouteState) {
    val style = map.style ?: return
    listOf("eaten-line", "remaining-line", "snake-head").forEach { name ->
        try { style.removeLayer(name) } catch (_: Exception) {}
    }
    listOf("eaten-source", "remaining-source", "head-source").forEach { name ->
        try { style.removeSource(name) } catch (_: Exception) {}
    }

    if (state.eatenCoordinates.size >= 2) {
        style.addSource(GeoJsonSource("eaten-source", geoJsonLineString(state.eatenCoordinates)))
        style.addLayer(LineLayer("eaten-line", "eaten-source").apply {
            setProperties(
                PropertyFactory.lineColor(Color.parseColor("#777777")),
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineDasharray(Expression.raw("[\"literal\", [2.0, 4.0]]")),
                PropertyFactory.lineOpacity(0.4f)
            )
        })
    }

    if (state.remainingCoordinates.size >= 2) {
        style.addSource(GeoJsonSource("remaining-source", geoJsonLineString(state.remainingCoordinates)))
        style.addLayer(LineLayer("remaining-line", "remaining-source").apply {
            setProperties(
                PropertyFactory.lineColor(Color.parseColor("#00E676")),
                PropertyFactory.lineWidth(8f),
                PropertyFactory.lineOpacity(0.9f)
            )
        })
    }

    if (state.eatenCoordinates.isNotEmpty()) {
        val head = state.eatenCoordinates.last()
        style.addSource(GeoJsonSource("head-source", geoJsonPoint(head.lat, head.lng)))
        style.addLayer(CircleLayer("snake-head", "head-source").apply {
            setProperties(
                PropertyFactory.circleColor(Color.parseColor("#FF1744")),
                PropertyFactory.circleRadius(10f),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleStrokeWidth(3f)
            )
        })
    }
}
