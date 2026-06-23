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
import com.snakenav.navigation.RoadNetwork
import com.snakenav.navigation.RoadNetworkLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng as MLatLng

private enum class NavMode { SET_START, ADD_POINTS, NAVIGATING, DONE }

@Composable
fun SnakeMapScreen() {
    val context = LocalContext.current
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }

    var navMode by remember { mutableStateOf(NavMode.SET_START) }
    var startPoint by remember { mutableStateOf<LatLng?>(null) }
    var startNodeId by remember { mutableIntStateOf(-1) }
    var visitPoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var visitNodeIds by remember { mutableStateOf<List<Int>>(emptyList()) }

    var plannedRoute by remember { mutableStateOf<ScenicRoute?>(null) }
    var routeNodePath by remember { mutableStateOf<List<Int>>(emptyList()) }
    var calculating by remember { mutableStateOf(false) }
    var gpsIndex by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }

    val roadNetwork = remember { RoadNetworkLoader.load(context) }
    val engine = remember { SnakeEngine() }
    val scope = rememberCoroutineScope()

    fun currentRoute(): ScenicRoute = plannedRoute
        ?: SampleRoute.createSummerPalace()

    // ===== 地图点击 =====
    fun onMapTap(lat: Double, lng: Double) {
        when (navMode) {
            NavMode.SET_START -> {
                startPoint = LatLng(lat, lng)
                startNodeId = roadNetwork.nearest(lat, lng)
                navMode = NavMode.ADD_POINTS
                redrawMarkers(mapInstance, startPoint, visitPoints)
            }
            NavMode.ADD_POINTS -> {
                visitPoints = visitPoints + LatLng(lat, lng)
                visitNodeIds = visitNodeIds + roadNetwork.nearest(lat, lng)
                redrawMarkers(mapInstance, startPoint, visitPoints)
            }
            else -> {}
        }
    }

    // ===== TSP 最优路径规划 =====
    fun planOptimalRoute() {
        if (startNodeId < 0) return
        calculating = true
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                val allNodes = listOf(startNodeId) + visitNodeIds
                val n = allNodes.size
                if (n < 2) return@withContext emptyList<Int>()

                // 1) 预计算所有点对之间的 A* 路径与距离
                val pairCache = mutableMapOf<Pair<Int, Int>, Pair<List<Int>, Double>>()
                for (i in 0 until n) {
                    for (j in i + 1 until n) {
                        val path = roadNetwork.findPath(allNodes[i], allNodes[j])
                        if (path != null) {
                            var dist = 0.0
                            for (pi in 0 until path.size - 1) {
                                dist += roadNetwork.distMeters(path[pi], path[pi + 1])
                            }
                            pairCache[allNodes[i] to allNodes[j]] = path to dist
                            pairCache[allNodes[j] to allNodes[i]] = path.reversed() to dist
                        }
                    }
                }

                // 2) TSP: 找遍历所有途经点的最短路径
                val waypointIndices = (1 until n).toList()
                if (waypointIndices.isEmpty()) return@withContext emptyList<Int>()

                var bestOrder = waypointIndices
                var bestCost = Double.MAX_VALUE

                // ≤7 个途经点 => 全排列穷举
                if (waypointIndices.size <= 7) {
                    for (perm in permutations(waypointIndices)) {
                        var cost = 0.0
                        var prev = 0
                        for (wpIdx in perm) {
                            val cached = pairCache[allNodes[prev] to allNodes[wpIdx]] ?: break
                            cost += cached.second
                            prev = wpIdx
                        }
                        if (cost < bestCost) { bestCost = cost; bestOrder = perm }
                    }
                } else {
                    // >7 => 贪心最近邻
                    val remaining = waypointIndices.toMutableList()
                    var prev = 0
                    while (remaining.isNotEmpty()) {
                        var bestNext = remaining[0]
                        var bestNextCost = Double.MAX_VALUE
                        for (ri in remaining) {
                            val c = pairCache[allNodes[prev] to allNodes[ri]]?.second
                                ?: Double.MAX_VALUE
                            if (c < bestNextCost) { bestNextCost = c; bestNext = ri }
                        }
                        remaining.remove(bestNext)
                        prev = bestNext
                    }
                }

                // 3) 按最优顺序拼接路径
                val merged = mutableListOf<Int>()
                var prev = 0
                for (wpIdx in bestOrder) {
                    val cached = pairCache[allNodes[prev] to allNodes[wpIdx]] ?: continue
                    val seg = cached.first
                    merged.addAll(if (merged.isEmpty()) seg else seg.drop(1))
                    prev = wpIdx
                }
                merged
            }

            calculating = false
            if (result.isEmpty()) return@launch

            routeNodePath = result
            val coords = result.map { id ->
                val n = roadNetwork.nodes[id]; LatLng(n.lat, n.lng)
            }
            plannedRoute = ScenicRoute(
                id = "nav-${System.currentTimeMillis()}",
                name = "导航路线 (${result.size}段)",
                coordinates = coords, totalDistanceKm = -1.0
            )
            gpsIndex = 0
            paused = false
            navMode = NavMode.NAVIGATING
        }
    }

    // ===== 模拟循环 =====
    LaunchedEffect(plannedRoute, paused, navMode) {
        val route = plannedRoute ?: return@LaunchedEffect
        if (navMode != NavMode.NAVIGATING) return@LaunchedEffect
        if (route.coordinates.size < 2) return@LaunchedEffect

        val routeData = ScenicRouteData(
            id = route.id, name = route.name,
            coordinates = route.coordinates, isLoop = false
        )
        val graph = engine.buildChainGraph(routeData)
        gpsIndex = 0
        mapInstance?.let { drawSnakeRoute(it, RouteState(route)) }

        while (navMode == NavMode.NAVIGATING && gpsIndex < route.coordinates.size - 1) {
            if (!paused) {
                delay(800)
                val nextTarget = engine.findNextTarget(graph, gpsIndex)
                if (nextTarget != null && nextTarget != gpsIndex) {
                    val path = engine.findPath(graph, gpsIndex, nextTarget)
                    val nextStep = if (path != null && path.size > 1) path[1] else (gpsIndex + 1)
                    graph.eatEdge(gpsIndex, nextStep)
                    gpsIndex = nextStep
                    if (nextStep < routeNodePath.size)
                        roadNetwork.markEaten(routeNodePath[nextStep])
                } else {
                    gpsIndex++
                    if (gpsIndex < graph.nodes.size) {
                        graph.eatEdge(gpsIndex - 1, gpsIndex)
                        if (gpsIndex < routeNodePath.size)
                            roadNetwork.markEaten(routeNodePath[gpsIndex])
                    }
                }
                mapInstance?.let {
                    drawSnakeRoute(it, RouteState(route).also { s -> s.eatUpTo(gpsIndex) })
                }
            } else delay(100)
        }
        if (gpsIndex >= route.coordinates.size - 1) navMode = NavMode.DONE
    }

    // ===== UI =====
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
                                CameraUpdateFactory.newLatLngZoom(MLatLng(40.0, 116.275), 15.0)
                            )
                            map.addOnMapClickListener { point ->
                                if (navMode == NavMode.NAVIGATING || navMode == NavMode.DONE)
                                    return@addOnMapClickListener false
                                onMapTap(point.latitude, point.longitude)
                                true
                            }
                        }
                    }
                }
            }
        )

        // 顶部提示
        val hint = when (navMode) {
            NavMode.SET_START -> "点击地图设置起点"
            NavMode.ADD_POINTS -> "继续点击添加途经点 (${visitPoints.size}个)，点底部规划路线"
            NavMode.NAVIGATING -> "导航中... ${gpsIndex}/${currentRoute().coordinates.size}"
            NavMode.DONE -> "全部吃完!"
        }
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ) { Text(hint, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }

        when (navMode) {
            NavMode.ADD_POINTS -> {
                if (visitPoints.isNotEmpty()) {
                    Button(
                        onClick = { planOptimalRoute() },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
                    ) { Text("规划路线 (${visitPoints.size}个点)") }
                }
                FilledTonalButton(
                    onClick = {
                        startPoint = null; startNodeId = -1; visitPoints = emptyList()
                        visitNodeIds = emptyList(); plannedRoute = null; navMode = NavMode.SET_START
                        mapInstance?.let { clearAllLayers(it) }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
                ) { Text("重置") }
            }
            NavMode.NAVIGATING -> {
                SnakeGauge(
                    progress = if (currentRoute().coordinates.size <= 1) 1f
                        else gpsIndex.toFloat() / (currentRoute().coordinates.size - 1).toFloat(),
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                )
                FilledTonalButton(
                    onClick = { paused = !paused },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                ) { Text(if (paused) "继续" else "暂停") }
                FilledTonalButton(
                    onClick = {
                        startPoint = null; startNodeId = -1; visitPoints = emptyList()
                        visitNodeIds = emptyList(); plannedRoute = null; navMode = NavMode.SET_START
                        mapInstance?.let { clearAllLayers(it) }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
                ) { Text("重新开始") }
            }
            NavMode.DONE -> {
                SnakeGauge(progress = 1f,
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))
                Text("全部吃完!",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp))
                FilledTonalButton(
                    onClick = {
                        startPoint = null; startNodeId = -1; visitPoints = emptyList()
                        visitNodeIds = emptyList(); plannedRoute = null; navMode = NavMode.SET_START
                        mapInstance?.let { clearAllLayers(it) }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
                ) { Text("重新开始") }
            }
            else -> {}
        }

        if (calculating) {
            Surface(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ) { Text("正在计算最优路线...",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) }
        }
    }
}

// ===== 全排列生成器 =====
private fun permutations(list: List<Int>): Sequence<List<Int>> = sequence {
    if (list.size <= 1) { yield(list); return@sequence }
    for (i in list.indices) {
        val elem = list[i]
        val rest = list.filterIndexed { idx, _ -> idx != i }
        for (perm in permutations(rest)) {
            yield(listOf(elem) + perm)
        }
    }
}

// ===== GeoJSON =====
private fun geoJsonLineString(coords: List<LatLng>): String {
    val pts = coords.joinToString { "[${it.lng},${it.lat}]" }
    return """{"type":"LineString","coordinates":[$pts]}"""
}
private fun geoJsonPoint(lat: Double, lng: Double): String {
    return """{"type":"Point","coordinates":[$lng,$lat]}"""
}

// ===== 路线渲染 =====
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
                PropertyFactory.lineDasharray(
                    org.maplibre.android.style.expressions.Expression.raw("[\"literal\", [2.0, 4.0]]")),
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

// ===== 标记渲染 =====
private fun redrawMarkers(map: MapLibreMap?, start: LatLng?, visits: List<LatLng>) {
    val style = map?.style ?: return
    listOf("start-marker", "visit-markers").forEach { n ->
        try { style.removeLayer(n) } catch (_: Exception) {} }
    listOf("start-source", "visit-source").forEach { n ->
        try { style.removeSource(n) } catch (_: Exception) {} }

    if (start != null) {
        style.addSource(GeoJsonSource("start-source", geoJsonPoint(start.lat, start.lng)))
        style.addLayer(CircleLayer("start-marker", "start-source").apply {
            setProperties(
                PropertyFactory.circleColor(Color.parseColor("#00C853")),
                PropertyFactory.circleRadius(14f),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleStrokeWidth(3f)
            )
        })
    }
    if (visits.isNotEmpty()) {
        val points = visits.joinToString { p -> """{"type":"Point","coordinates":[${p.lng},${p.lat}]}""" }
        val geoJson = """{"type":"GeometryCollection","geometries":[$points]}"""
        style.addSource(GeoJsonSource("visit-source", geoJson))
        style.addLayer(CircleLayer("visit-markers", "visit-source").apply {
            setProperties(
                PropertyFactory.circleColor(Color.parseColor("#FF5722")),
                PropertyFactory.circleRadius(10f),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleStrokeWidth(2f)
            )
        })
    }
}

private fun clearAllLayers(map: MapLibreMap) {
    listOf("eaten-line", "remaining-line", "snake-head",
           "start-marker", "visit-markers").forEach { n ->
        try { map.style?.removeLayer(n) } catch (_: Exception) {} }
    listOf("eaten-source", "remaining-source", "head-source",
           "start-source", "visit-source").forEach { n ->
        try { map.style?.removeSource(n) } catch (_: Exception) {} }
}
