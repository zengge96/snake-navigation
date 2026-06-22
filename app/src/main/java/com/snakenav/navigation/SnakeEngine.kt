package com.snakenav.navigation

import com.snakenav.data.LatLng
import com.snakenav.data.RouteMatcher

/**
 * 路线规划引擎（A* + 动态权重）
 *
 * 目标：给定起点和终点，找一条尽量不走重复路的路线。
 *
 * 核心策略：
 * 1. 已走过的路段权重 ×10000（强烈避免回头）
 * 2. 死胡同/断头路 → 允许走一次回头路（但视觉上显示为"新的一边"）
 * 3. 多目标排序 → 优先去"没去过"的景点
 */
class SnakeEngine(
    private val allRoutes: Map<String, RouteSegment> = emptyMap(),
    private val eatenSegmentIds: MutableSet<String> = mutableSetOf()
) {
    data class RouteSegment(
        val id: String,
        val start: LatLng,
        val end: LatLng,
        val distanceMeters: Double
    )

    private val NO_GO_WEIGHT = 10_000.0

    /**
     * 计算从 [from] 到 [to] 的最优路径，避开已吃的路段。
     * Returns ordered list of LatLng waypoints, or null if unreachable.
     */
    fun findPath(from: LatLng, to: LatLng, routePoints: List<LatLng>): List<LatLng>? {
        if (routePoints.size < 2) return null

        // Current position as index along route
        val fromIdx = RouteMatcher.findClosestPointIndex(from, routePoints)
        val toIdx = RouteMatcher.findClosestPointIndex(to, routePoints)

        // Simple strategy: go forward along the pre-defined route.
        // If the segment ahead is "eaten", we need to find an alternative.
        return findPathAlongRoute(fromIdx, toIdx, routePoints)
    }

    private fun findPathAlongRoute(fromIdx: Int, toIdx: Int, route: List<LatLng>): List<LatLng>? {
        // If going forward along route - straightforward path
        if (toIdx > fromIdx) {
            val segment = route.subList(fromIdx, toIdx + 1)
            // Check if any intermediate points are "eaten"
            val hasEatenSegment = (fromIdx until toIdx).any { isEaten(it, it + 1, route) }
            return if (hasEatenSegment) {
                findDetour(fromIdx, toIdx, route)
            } else {
                segment
            }
        }

        // Going backward or wrapping around
        if (toIdx < fromIdx) {
            // Check if forward path (wrap-around) or backward path is better
            val forwardPath = route.subList(fromIdx, route.size) +
                    route.subList(0, toIdx + 1)
            val backwardPath = route.subList(toIdx, fromIdx + 1).reversed()

            val forwardEaten = (fromIdx until route.size - 1).any { isEaten(it, it + 1, route) } ||
                    (0 until toIdx).any { isEaten(it, it + 1, route) }
            val backwardEaten = (toIdx until fromIdx).any { isEaten(it, it + 1, route) }

            return when {
                !backwardEaten -> backwardPath
                !forwardEaten -> forwardPath
                else -> findDetour(fromIdx, toIdx, route)
            }
        }

        return listOf(route[fromIdx])
    }

    private fun isEaten(from: Int, to: Int, route: List<LatLng>): Boolean {
        if (from < 0 || to >= route.size) return false
        val segmentId = "${from}-${to}"
        return eatenSegmentIds.contains(segmentId)
    }

    /**
     * Find a detour when the direct path is eaten.
     * TODO: Implement proper A* with graph topology.
     * For now: just return the direct path (allowing repeats).
     */
    private fun findDetour(fromIdx: Int, toIdx: Int, route: List<LatLng>): List<LatLng> {
        // Placeholder: return forward path even if eaten
        // In the real version, this would find an alternative path
        return if (toIdx >= fromIdx) {
            route.subList(fromIdx, toIdx + 1)
        } else {
            route.subList(fromIdx, route.size) + route.subList(0, toIdx + 1)
        }
    }

    fun markEaten(from: Int, to: Int) {
        eatenSegmentIds.add("${from}-${to}")
    }
}
