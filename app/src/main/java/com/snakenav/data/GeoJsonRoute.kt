package com.snakenav.data

import com.google.gson.annotations.SerializedName

/**
 * GeoJSON FeatureCollection representing a scenic route.
 * Parsed from GeoJSON files bundled with the app or downloaded.
 */
data class GeoJsonFeatureCollection(
    val type: String = "FeatureCollection",
    val features: List<GeoJsonFeature> = emptyList()
)

data class GeoJsonFeature(
    val type: String = "Feature",
    val geometry: GeoJsonGeometry? = null,
    val properties: Map<String, Any>? = null
)

data class GeoJsonGeometry(
    val type: String = "LineString",
    val coordinates: List<List<Double>> = emptyList()
)

/**
 * A scenic route with metadata.
 * Parsed from GeoJSON and enhanced with runtime state.
 */
data class ScenicRoute(
    val id: String,
    val name: String,
    val coordinates: List<LatLng>,       // all points in order
    val waypoints: List<LatLng> = emptyList(), // named POI waypoints
    val totalDistanceKm: Double = 0.0
)

data class LatLng(
    val lat: Double,
    val lng: Double
) {
    fun toMaplibre(): org.maplibre.android.geometry.LatLng =
        org.maplibre.android.geometry.LatLng(lat, lng)
}

/**
 * Route state: tracks which segments have been "eaten".
 */
class RouteState(val route: ScenicRoute) {
    /** Index of the last eaten point (0 = none eaten). */
    var eatenUpToIndex: Int = 0
        private set

    val isFullyEaten: Boolean get() = eatenUpToIndex >= route.coordinates.size - 1
    val progress: Float get() = if (route.coordinates.size <= 1) 1f
        else eatenUpToIndex.toFloat() / (route.coordinates.size - 1).toFloat()

    fun eatUpTo(index: Int) {
        if (index > eatenUpToIndex) eatenUpToIndex = index
    }

    /** Points that have been traversed (eaten) — will be drawn as dashed. */
    val eatenCoordinates: List<LatLng>
        get() = route.coordinates.subList(0, (eatenUpToIndex + 1).coerceAtMost(route.coordinates.size))

    /** Points ahead (not yet eaten) — drawn as solid glow line. */
    val remainingCoordinates: List<LatLng>
        get() = route.coordinates.subList(
            (eatenUpToIndex + 1).coerceAtMost(route.coordinates.size),
            route.coordinates.size
        )
}

/**
 * Utility to find the closest point on the route to a given GPS position.
 */
object RouteMatcher {
    /**
     * Find the index of the closest point on the route to [pos].
     * Searches forward from [startIndex] to avoid snapping backwards.
     */
    fun findClosestPointIndex(
        pos: LatLng,
        route: List<LatLng>,
        startIndex: Int = 0
    ): Int {
        var bestIdx = startIndex
        var bestDist = Double.MAX_VALUE
        for (i in startIndex until route.size) {
            val d = haversine(pos, route[i])
            if (d < bestDist) {
                bestDist = d
                bestIdx = i
            }
        }
        return bestIdx
    }

    /** Haversine distance in meters between two LatLng points. */
    fun haversine(a: LatLng, b: LatLng): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val sinDLat = kotlin.math.sin(dLat / 2)
        val sinDLng = kotlin.math.sin(dLng / 2)
        val h = sinDLat * sinDLat +
                kotlin.math.cos(Math.toRadians(a.lat)) *
                kotlin.math.cos(Math.toRadians(b.lat)) *
                sinDLng * sinDLng
        return R * 2 * kotlin.math.atan2(kotlin.math.sqrt(h), kotlin.math.sqrt(1 - h))
    }
}
