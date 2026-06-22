package com.snakenav.map

import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.snakenav.data.*
import com.snakenav.gamification.SnakeGauge
import com.snakenav.navigation.ScenicRouteData
import com.snakenav.navigation.SnakeEngine
import kotlinx.coroutines.delay
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng as MLatLng

@Composable
fun SnakeMapScreen() {
    val context = LocalContext.current
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }

    val route = remember { SampleRoute.createSummerPalace() }
    val routeState = remember { RouteState(route) }
    var gpsIndex by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }

    // Route editor state
    var editMode by remember { mutableStateOf(false) }
    var waypoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }

    val engine = remember { SnakeEngine() }

    // GPS simulation
    LaunchedEffect(paused, editMode) {
        if (editMode) return@LaunchedEffect
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
                    mapViewInstance = mv
                    mv.getMapAsync { map ->
                        map.setStyle("https://tiles.openfreemap.org/styles/liberty") {
                            mapInstance = map
                            map.moveCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    route.coordinates.first().toMaplibre(), 15.0
                                )
                            )
                            drawSnakeRoute(map, routeState)

                            // Map click → add waypoint in edit mode
                            map.addOnMapClickListener { point ->
                                if (editMode) {
                                    val wp = LatLng(point.latitude, point.longitude)
                                    waypoints = waypoints + wp
                                    redrawEditor(map, waypoints)
                                    true
                                } else false
                            }
                        }
                    }
                }
            }
        )

        // Edit mode instructions
        if (editMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ) {
                Text(
                    "📍 点击地图添加航点，已添加 ${waypoints.size} 个点",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Edit/Cancel button (top-right, below gauge)
        if (editMode) {
            FilledTonalButton(
                onClick = {
                    editMode = false
                    waypoints = emptyList()
                    redrawEditor(mapInstance, emptyList())
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 72.dp, end = 16.dp)
            ) { Text("取消") }
        } else {
            FilledTonalButton(
                onClick = { editMode = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 72.dp, end = 16.dp)
            ) { Text("✏️ 编辑路线") }
        }

        // Save button (when has waypoints)
        if (editMode && waypoints.size >= 2) {
            Button(
                onClick = {
                    // Save waypoints as route
                    val newRoute = ScenicRouteData(
                        id = "custom",
                        name = "自定义路线 (${waypoints.size}个点)",
                        coordinates = waypoints,
                        waypointNames = waypoints.indices.map { "点${it + 1}" },
                        isLoop = false
                    )
                    editMode = false
                    waypoints = emptyList()
                    redrawEditor(mapInstance, emptyList())
                    // TODO: switch to new route
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
            ) { Text("✅ 保存路线 (${waypoints.size}个点)") }
        }

        // Progress + Play/Pause
        if (!editMode) {
            SnakeGauge(
                progress = routeState.progress,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            )

            FilledTonalButton(
                onClick = { paused = !paused },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) { Text(if (paused) "▶ 继续" else "⏸ 暂停") }

            if (routeState.isFullyEaten) {
                Text("🎉 路线已吃完！",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp))
            }
        }
    }
}

// ===== GeoJSON helpers =====
private fun geoJsonLineString(coords: List<LatLng>): String {
    val pts = coords.joinToString(",") { "[${it.lng},${it.lat}]" }
    return """{"type":"LineString","coordinates":[$pts]}"""
}
private fun geoJsonPoint(lat: Double, lng: Double): String {
    return """{"type":"Point","coordinates":[$lng,$lat]}"""
}
private fun geoJsonMultiPoint(coords: List<LatLng>): String {
    val pts = coords.joinToString(",") { "[${it.lng},${it.lat}]" }
    return """{"type":"GeometryCollection","geometries":[${coords.mapIndexed { i, c ->
        """{"type":"Point","coordinates":[${c.lng},${c.lat}]}"""
    }.joinToString(",")}]}"""
}

// ===== Route rendering =====
private fun drawSnakeRoute(map: MapLibreMap, state: RouteState) {
    val style = map.style ?: return
    listOf("eaten-line", "remaining-line", "snake-head").forEach { n ->
        try { style.removeLayer(n) } catch (_: Exception) {} }
    listOf("eaten-source", "remaining-source", "head-source").forEach { n ->
        try { style.removeSource(n) } catch (_: Exception) {} }

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
                PropertyFactory.lineWidth(8f), PropertyFactory.lineOpacity(0.9f)
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

// ===== Route Editor rendering =====
private fun redrawEditor(map: MapLibreMap?, waypoints: List<LatLng>) {
    val style = map?.style ?: return
    listOf("wp-line", "wp-marker").forEach { n ->
        try { style.removeLayer(n) } catch (_: Exception) {} }
    listOf("wp-source").forEach { n ->
        try { style.removeSource(n) } catch (_: Exception) {} }

    if (waypoints.isEmpty()) return

    // Connecting line
    style.addSource(GeoJsonSource("wp-source", geoJsonLineString(waypoints)))
    style.addLayer(LineLayer("wp-line", "wp-source").apply {
        setProperties(
            PropertyFactory.lineColor(Color.parseColor("#FF9800")),
            PropertyFactory.lineWidth(6f), PropertyFactory.lineOpacity(0.8f)
        )
    })

    // Waypoint markers (numbered circles)
    waypoints.forEachIndexed { i, wp ->
        val srcId = "wp-marker-$i"
        try { style.addSource(GeoJsonSource(srcId, geoJsonPoint(wp.lat, wp.lng))) } catch (_: Exception) {}
        try {
            style.addLayer(CircleLayer("wp-marker-$i", srcId).apply {
                setProperties(
                    PropertyFactory.circleColor(Color.parseColor("#FF5722")),
                    PropertyFactory.circleRadius(12f),
                    PropertyFactory.circleStrokeColor(Color.WHITE),
                    PropertyFactory.circleStrokeWidth(3f)
                )
            })
        } catch (_: Exception) {}
    }
}
