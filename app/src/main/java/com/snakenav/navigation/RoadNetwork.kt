package com.snakenav.navigation

import kotlin.math.*

/**
 * 道路网络 — 图数据 + A* 寻路
 *
 * 节点是路口/道路点，边是可通行路段。每条边记录长度（米）。
 */
class RoadNetwork(
    val nodes: List<RoadNode>
) {
    companion object {
        private const val R = 6_371_000.0
    }

    init {
        // 确保双向边
        for (i in nodes.indices) {
            for (j in nodes[i].adj) {
                if (j in nodes.indices && i !in nodes[j].adj) {
                    nodes[j].adj.add(i)
                }
            }
        }
    }

    /** 坐标附近最近的节点 ID */
    fun nearest(lat: Double, lng: Double): Int =
        nodes.indices.minByOrNull { distSq(lat, lng, nodes[it].lat, nodes[it].lng) } ?: 0

    /** 两点 Haversine 距离（米） */
    fun distMeters(a: Int, b: Int): Double {
        val (na, nb) = nodes[a] to nodes[b]
        return haversine(na.lat, na.lng, nb.lat, nb.lng)
    }

    /**
     * A* 寻路
     * @return 节点 ID 路径（含起终点），不可达则 null
     */
    fun findPath(from: Int, to: Int, eatenWeight: Double = 10000.0): List<Int>? {
        if (from == to) return listOf(from)

        val open = mutableSetOf(from)
        val came = mutableMapOf<Int, Int>()
        val g = mutableMapOf(from to 0.0)
        val f = mutableMapOf(from to h(from, to))

        while (open.isNotEmpty()) {
            val cur = open.minByOrNull { f[it] ?: Double.MAX_VALUE } ?: break
            if (cur == to) return unwind(came, cur)

            open.remove(cur)
            val gCur = g[cur] ?: continue

            for (nb in nodes[cur].adj) {
                val baseCost = distMeters(cur, nb)
                val cost = if (nodes[nb].eaten) baseCost * eatenWeight else baseCost
                val tg = gCur + cost
                if (tg < (g[nb] ?: Double.MAX_VALUE)) {
                    came[nb] = cur
                    g[nb] = tg
                    f[nb] = tg + h(nb, to)
                    open.add(nb)
                }
            }
        }
        return null
    }

    /** 直线距离启发式 */
    private fun h(a: Int, b: Int) = distMeters(a, b)

    /** 标记已吃 */
    fun markEaten(id: Int) { nodes[id].eaten = true }

    private fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * R * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun unwind(came: Map<Int, Int>, cur: Int): List<Int> {
        val path = mutableListOf(cur)
        var c = cur
        while (came.containsKey(c)) { c = came[c]!!; path.add(c) }
        return path.reversed()
    }

    /** 经纬度平方距离（快速排序用） */
    private fun distSq(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double =
        (lat1 - lat2).pow(2) + (lng1 - lng2).pow(2)
}

/** 道路节点 */
data class RoadNode(
    val id: Int,
    val lat: Double,
    val lng: Double,
    val adj: MutableList<Int> = mutableListOf(),
    var eaten: Boolean = false
)
