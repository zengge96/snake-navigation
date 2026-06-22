package com.snakenav.map

import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.snakenav.data.*
import com.snakenav.gamification.SnakeGauge
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.geometry.LatLng as MLatLng

/**
 * Main map screen with the snake-navigation game.
 */
@Composable
fun SnakeMapScreen() {
    val context = LocalContext.current
    var mapReady by remember { mutableStateOf(false) }
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }

    // 🗺️ Load/manage a sample scenic route
    val route = remember { SampleRoute.createSummerPalace() }
    val routeState = remember { RouteState(route) }

    // 📍 Simulated GPS position (moves along route for demo)
    var gpsIndex by remember { mutableIntStateOf(0) }

    // Animate GPS along the route
    LaunchedEffect(Unit) {
        while (true) {
            delay(200) // 200ms per step = smooth animation
            if (gpsIndex < route.coordinates.size - 1) {
                gpsIndex++
                routeState.eatUpTo(gpsIndex)
                // Re-draw route lines
                mapInstance?.let { drawRoute(it, routeState) }
            }
        }
    }

    // Initialize MapLibre
    LaunchedEffect(Unit) {
        MapLibre.getInstance(context)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).also { mv ->
                    mv.getMapAsync { map ->
                        map.setStyle(Style.getPredefinedStyle("Streets")) {
                            mapInstance = map
                            mapReady = true

                            // Initial route draw
                            map.moveCamera(
                                org.maplibre.android.camera.CameraUpdateFactory
                                    .newLatLngZoom(
                                        route.coordinates.first().toMaplibre(),
                                        15.0
                                    )
                            )
                            drawRoute(map, routeState)
                        }
                    }
                }
            }
        )

        // 📊 Progress gauge (top-right)
        SnakeGauge(
            progress = routeState.progress,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )

        // Status text
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

/**
 * Draw the route on MapLibre: eaten = dashed, remaining = solid glow.
 */
private fun drawRoute(map: MapLibreMap, state: RouteState) {
    // Remove old layers
    try {
        map.style?.removeLayer("eaten-route")
        map.style?.removeLayer("remaining-route")
        map.style?.removeSource("route-source")
    } catch (_: Exception) {}

    if (state.route.coordinates.size < 2) return

    // Build a single LineString with all coordinates
    val allLatLngs = state.route.coordinates.map { it.toMaplibre() }
    val lineString = org.maplibre.android.geometry.LineString.fromLngLats(allLatLngs)

    val source = org.maplibre.android.style.sources.GeoJsonSource("route-source", lineString)
    map.style?.addSource(source)

    // Eaten part (dashed, faded)
    val eatenLine = org.maplibre.android.style.layers.LineLayer("eaten-route", "route-source")
    eatenLine.setSourceLayer("route-source")
    // Only draw points up to eatenUpToIndex
    val eatenCount = state.eatenCoordinates.size
    eatenLine.setFilter(
        org.maplibre.android.style.expressions.Expression.lt(
            org.maplibre.android.style.expressions.Expression.index(),
            org.maplibre.android.style.expressions.Expression.literal(eatenCount)
        )
    )
    // Actually, GeoJSON source doesn't support per-vertex filtering easily.
    // Better approach: draw two separate GeoJSON sources for eaten/remaining.

    // Simpler approach: just redraw using two separate sources
    drawSimpleRoute(map, state)
}

/**
 * Simpler approach: two GeoJSON sources (eaten + remaining) = two separate lines.
 */
private fun drawSimpleRoute(map: MapLibreMap, state: RouteState) {
    val style = map.style ?: return

    // Remove old
    listOf("eaten-line", "remaining-line", "snake-head").forEach { name ->
        try { style.removeLayer(name) } catch (_: Exception) {}
    }
    listOf("eaten-source", "remaining-source", "head-source").forEach { name ->
        try { style.removeSource(name) } catch (_: Exception) {}
    }

    // --- EATEN segment (dashed, translucent) ---
    if (state.eatenCoordinates.size >= 2) {
        val eatenLatLngs = state.eatenCoordinates.map { it.toMaplibre() }
        val eatenGeo = org.maplibre.android.geometry.LineString.fromLngLats(eatenLatLngs)
        style.addSource(org.maplibre.android.style.sources.GeoJsonSource("eaten-source", eatenGeo))

        val eatenLayer = org.maplibre.android.style.layers.LineLayer("eaten-line", "eaten-source")
        eatenLayer.setProperties(
            org.maplibre.android.style.layers.PropertyFactory.lineColor(Color.LTGRAY),
            org.maplibre.android.style.layers.PropertyFactory.lineWidth(6f),
            org.maplibre.android.style.layers.PropertyFactory.lineDasharray(floatArrayOf(2f, 4f)),
            org.maplibre.android.style.layers.PropertyFactory.lineOpacity(0.5f)
        )
        style.addLayer(eatenLayer)
    }

    // --- REMAINING segment (solid glow, neon green) ---
    if (state.remainingCoordinates.size >= 2) {
        val remLatLngs = state.remainingCoordinates.map { it.toMaplibre() }
        val remGeo = org.maplibre.android.geometry.LineString.fromLngLats(remLatLngs)
        style.addSource(org.maplibre.android.style.sources.GeoJsonSource("remaining-source", remGeo))

        val remLayer = org.maplibre.android.style.layers.LineLayer("remaining-line", "remaining-source")
        remLayer.setProperties(
            org.maplibre.android.style.layers.PropertyFactory.lineColor(Color.parseColor("#00E676")),
            org.maplibre.android.style.layers.PropertyFactory.lineWidth(8f),
            org.maplibre.android.style.layers.PropertyFactory.lineOpacity(0.9f)
        )
        style.addLayer(remLayer)
    }

    // --- SNAKE HEAD (current GPS position) ---
    if (state.eatenCoordinates.isNotEmpty()) {
        val head = state.eatenCoordinates.last().toMaplibre()
        val headGeo = org.maplibre.android.geometry.Point.fromLngLat(head.longitude, head.latitude)
        style.addSource(org.maplibre.android.style.sources.GeoJsonSource("head-source", headGeo))

        val headLayer = org.maplibre.android.style.layers.CircleLayer("snake-head", "head-source")
        headLayer.setProperties(
            org.maplibre.android.style.layers.PropertyFactory.circleColor(Color.parseColor("#FF1744")),
            org.maplibre.android.style.layers.PropertyFactory.circleRadius(10f),
            org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor(Color.WHITE),
            org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth(3f)
        )
        style.addLayer(headLayer)
    }
}
