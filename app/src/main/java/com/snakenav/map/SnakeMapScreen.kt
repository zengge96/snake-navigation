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
    var gpsIndex by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }

    // Route editor state
    var editMode by remember { mutableStateOf(false) }
    var waypoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var connections by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
    var connectionMode by remember { mutableStateOf(false) }
    var selectedWpIdx by remember { mutableIntStateOf(-1) }

    // Active route (custom or default)
    var customRoute by remember { mutableStateOf<ScenicRoute?>(null) }
    fun currentRoute(): ScenicRoute = customRoute ?: route

    // Force redraw trigger
    var tick by remember { mutableIntStateOf(0) }

    val engine = remember { SnakeEngine() }

    // GPS simulation (restarts on customRoute change)
    LaunchedEffect(paused, editMode, customRoute, tick) {
        if (editMode) return@LaunchedEffect
        val active = currentRoute()
        gpsIndex = 0

        // Build route graph for A* pathfinding
        val routeData = ScenicRouteData(
            id = active.id,
            name = active.name,
            coordinates = active.coordinates,
            isLoop = false
        )
        val graph = engine.buildChainGraph(routeData)

        mapInstance?.let { drawSnakeRoute(it, RouteState(active)) }
        while (true) {
            if (!paused && gpsIndex < active.coordinates.size - 1) {
                delay(800)
                // A*: find next target avoiding eaten edges
                val nextTarget = engine.findNextTarget(graph, gpsIndex)
                if (nextTarget != null && nextTarget != gpsIndex) {
                    // Follow path to target
                    val path = engine.findPath(graph, gpsIndex, nextTarget)
                    val nextStep = if (path != null && path.size > 1) path[1] else (gpsIndex + 1)
                    // Mark edge as eaten
                    graph.eatEdge(gpsIndex, nextStep)
                    gpsIndex = nextStep
                } else {
                    gpsIndex++
                    if (gpsIndex < graph.nodes.size) {
                        graph.eatEdge(gpsIndex - 1, gpsIndex)
                    }
                }
                mapInstance?.let { drawSnakeRoute(it, RouteState(active).also { it.eatUpTo(gpsIndex) }) }
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
                                    currentRoute().coordinates.first().toMaplibre(), 15.0
                                )
                            )
                            drawSnakeRoute(map, RouteState(currentRoute()).also { s -> (0..gpsIndex).forEach { s.eatUpTo(it) } })

                            // Map click → add waypoint OR create connection
                            map.addOnMapClickListener { point ->
                                if (!editMode) return@addOnMapClickListener false
                                val wp = LatLng(point.latitude, point.longitude)

                                if (connectionMode && waypoints.isNotEmpty()) {
                                    // Find nearest waypoint (screen distance)
                                    val proj = map.projection
                                    val tapScreen = proj.toScreenLocation(
                                        org.maplibre.android.geometry.LatLng(point.latitude, point.longitude)
                                    )
                                    var nearestIdx = -1
                                    var nearestDist = Float.MAX_VALUE
                                    for ((i, wpt) in waypoints.withIndex()) {
                                        val ws = proj.toScreenLocation(wpt.toMaplibre())
                                        val dx = tapScreen.x - ws.x
                                        val dy = tapScreen.y - ws.y
                                        val d = kotlin.math.sqrt(dx * dx + dy * dy)
                                        if (d < nearestDist) { nearestDist = d; nearestIdx = i }
                                    }

                                    if (nearestDist < 80f && nearestIdx >= 0) {
                                        if (selectedWpIdx < 0) {
                                            // First selection
                                            selectedWpIdx = nearestIdx
                                        } else if (nearestIdx == selectedWpIdx) {
                                            // Deselect
                                            selectedWpIdx = -1
                                        } else if (nearestIdx != selectedWpIdx) {
                                            // Create connection
                                            val edge = if (selectedWpIdx < nearestIdx)
                                                (selectedWpIdx to nearestIdx)
                                            else (nearestIdx to selectedWpIdx)
                                            if (edge !in connections) {
                                                connections = connections + edge
                                            }
                                            selectedWpIdx = -1
                                        }
                                        redrawEditor(map, waypoints, connections, selectedWpIdx)
                                        return@addOnMapClickListener true
                                    }
                                    // Tap not near any waypoint: add as new waypoint
                                    waypoints = waypoints + wp
                                    redrawEditor(map, waypoints, connections, selectedWpIdx)
                                    true
                                } else if (editMode) {
                                    val wp = LatLng(point.latitude, point.longitude)
                                    waypoints = waypoints + wp
                                    redrawEditor(map, waypoints, connections, selectedWpIdx)
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
                    connectionMode = false
                    selectedWpIdx = -1
                    waypoints = emptyList()
                    connections = emptyList()
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

        // Connection mode toggle (when editing with 2+ waypoints)
        if (editMode && waypoints.size >= 2) {
            FilledTonalButton(
                onClick = {
                    connectionMode = !connectionMode
                    selectedWpIdx = -1
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 72.dp, start = 16.dp)
            ) {
                Text(if (connectionMode) "连线中..." else "连线")
            }
            if (connectionMode && selectedWpIdx >= 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 120.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ) {
                    Text(
                        "已选航点 ${selectedWpIdx + 1}，点击另一个航点创建连接",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Save button (when has waypoints)
        if (editMode && waypoints.size >= 2) {
            Button(
                onClick = {
                    // Save waypoints as route
                    val scenicRoute = ScenicRoute(
                        id = "custom-${System.currentTimeMillis()}",
                        name = "自定义路线 (${waypoints.size}个点)",
                        coordinates = waypoints.toList(),
                        totalDistanceKm = -1.0
                    )
                    // Save connections for graph building
                    val savedConnections = connections.toList()
                    customRoute = scenicRoute
                    editMode = false
                    connectionMode = false
                    selectedWpIdx = -1
                    waypoints = emptyList()
                    connections = emptyList()
                    redrawEditor(mapInstance, emptyList(), savedConnections)
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
            ) { Text("✅ 保存路线 (${waypoints.size}个点)") }
        }

        // Progress + Play/Pause
        if (!editMode) {
            SnakeGauge(
                progress = if (currentRoute().coordinates.size <= 1) 1f
                    else gpsIndex.toFloat() / (currentRoute().coordinates.size - 1).toFloat(),
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            )

            FilledTonalButton(
                onClick = { paused = !paused },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) { Text(if (paused) "▶ 继续" else "⏸ 暂停") }

            if (gpsIndex >= currentRoute().coordinates.size - 1) {
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
private fun redrawEditor(map: MapLibreMap?, waypoints: List<LatLng>,
                          connections: List<Pair<Int, Int>> = emptyList(),
                          selectedIdx: Int = -1) {
    val style = map?.style ?: return

    // Clean up ALL old markers, line, connection layers
    for (i in 0..50) {
        try { style.removeLayer("wp-marker-$i") } catch (_: Exception) { break }
    }
    for (i in 0..50) {
        try { style.removeSource("wp-source-$i") } catch (_: Exception) { break }
    }
    for (i in 0..50) {
        try { style.removeLayer("wp-conn-line-$i") } catch (_: Exception) { break }
    }
    for (i in 0..50) {
        try { style.removeSource("wp-conn-source-$i") } catch (_: Exception) { break }
    }
    try { style.removeLayer("wp-line") } catch (_: Exception) {}
    try { style.removeSource("wp-editor-source") } catch (_: Exception) {}
    try { style.removeLayer("wp-connections") } catch (_: Exception) {}
    try { style.removeSource("wp-connections-source") } catch (_: Exception) {}

    if (waypoints.size < 1) return

    // Default chain line (orange)
    if (waypoints.size >= 2) {
        style.addSource(GeoJsonSource("wp-editor-source", geoJsonLineString(waypoints)))
        style.addLayer(LineLayer("wp-line", "wp-editor-source").apply {
            setProperties(
                PropertyFactory.lineColor(Color.parseColor("#FF9800")),
                PropertyFactory.lineWidth(6f), PropertyFactory.lineOpacity(0.8f)
            )
        })
    }

    // Custom connection lines (cyan/blue)
    if (connections.isNotEmpty()) {
        val connCoords = connections.mapNotNull { (a, b) ->
            if (a in waypoints.indices && b in waypoints.indices)
                listOf(waypoints[a], waypoints[b]) else null
        }.flatten()
        if (connCoords.size >= 2) {
            style.addSource(GeoJsonSource("wp-connections-source", geoJsonLineString(connCoords)))
            // This single line won't work for multiple disjoint edges - use individual lines instead
        }
        // Individual connection lines
        connections.forEachIndexed { ci, (a, b) ->
            if (a in waypoints.indices && b in waypoints.indices) {
                val coords = listOf(waypoints[a], waypoints[b])
                val srcId = "wp-conn-source-$ci"
                val layerId = "wp-conn-line-$ci"
                try {
                    style.addSource(GeoJsonSource(srcId, geoJsonLineString(coords)))
                    style.addLayer(LineLayer(layerId, srcId).apply {
                        setProperties(
                            PropertyFactory.lineColor(Color.parseColor("#00BCD4")),
                            PropertyFactory.lineWidth(4f),
                            PropertyFactory.lineOpacity(0.7f)
                        )
                    })
                } catch (_: Exception) {}
            }
        }
    }

    // Individual waypoint circles
    waypoints.forEachIndexed { i, wp ->
        val srcId = "wp-source-$i"
        val layerId = "wp-marker-$i"
        try {
            style.addSource(GeoJsonSource(srcId, geoJsonPoint(wp.lat, wp.lng)))
            val isSelected = i == selectedIdx
            style.addLayer(CircleLayer(layerId, srcId).apply {
                setProperties(
                    PropertyFactory.circleColor(if (isSelected) Color.parseColor("#FFEB3B") else Color.parseColor("#FF5722")),
                    PropertyFactory.circleRadius(if (isSelected) 16f else 12f),
                    PropertyFactory.circleStrokeColor(Color.WHITE),
                    PropertyFactory.circleStrokeWidth(3f)
                )
            })
        } catch (_: Exception) {}
    }
}
