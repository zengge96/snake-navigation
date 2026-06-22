package com.snakenav.navigation

import com.snakenav.data.LatLng
import com.snakenav.data.RouteMatcher
import kotlin.math.sqrt

/**
 * 路线规划引擎（A* + 动态权重）
 *
 * 核心逻辑：
 * 1. 景区路线被建模为一个有向图（GraphNode + GraphEdge）
 * 2. 已吃路段权重 ×10000，A* 自动避开
 * 3. 若所有路径都吃过（死胡同），允许走回头路（但降速以示惩罚）
 * 4. 欧拉回路检测：如果所有边都已吃，行程完成
 */
class SnakeEngine {

    /** 单条有向边 */
    data class GraphEdge(
        val from: Int,     // node index
        val to: Int,       // node index
        val distanceKm: Double,
        var isEaten: Boolean = false
    )

    /** 图节点 = 路线上的一个关键点 */
    data class GraphNode(
        val latLng: LatLng,
        val name: String = ""
    )

    /** 完整路线图 */
    class RouteGraph(
        val nodes: List<GraphNode>,
        edges: List<GraphEdge>
    ) {
        val edges: MutableList<GraphEdge> = edges.toMutableList()

        /** 邻接表: nodeIndex → list of (edgeIndex, neighborIndex) */
        val adjacency: Map<Int, List<Pair<Int, Int>>>

        init {
            val adj = mutableMapOf<Int, MutableList<Pair<Int, Int>>>()
            for ((ei, e) in edges.withIndex()) {
                adj.getOrPut(e.from) { mutableListOf() }.add(ei to e.to)
                // Undirected graph: also add reverse
                adj.getOrPut(e.to) { mutableListOf() }.add(ei to e.from)
            }
            adjacency = adj
        }

        /** 标记一条边为已吃 */
        fun eatEdge(from: Int, to: Int) {
            edges.forEach { e ->
                if ((e.from == from && e.to == to) || (e.from == to && e.to == from)) {
                    e.isEaten = true
                }
            }
        }

        /** 未吃的边数 */
        val uneatenCount: Int get() = edges.count { !it.isEaten }

        /** 是否全部吃完 */
        val isFullyEaten: Boolean get() = edges.all { it.isEaten }

        /** 获取边权重（已吃=10000，未吃=原始距离） */
        fun edgeCost(ei: Int): Double {
            val e = edges[ei]
            return if (e.isEaten) e.distanceKm * 10000.0 else e.distanceKm
        }
    }

    companion object {
        private const val EATEN_PENALTY = 10_000.0
    }

    /**
     * A* 寻路：从 [fromIdx] 到 [toIdx]，避开已吃边。
     * Returns 路径的 node index 列表，或 null（不可达）。
     */
    fun findPath(graph: RouteGraph, fromIdx: Int, toIdx: Int): List<Int>? {
        if (fromIdx == toIdx) return listOf(fromIdx)
        if (fromIdx < 0 || toIdx < 0 || fromIdx >= graph.nodes.size || toIdx >= graph.nodes.size) return null

        val openSet = mutableSetOf(fromIdx)
        val cameFrom = mutableMapOf<Int, Int>()
        val gScore = mutableMapOf<Int, Double>().withDefault { Double.MAX_VALUE }
        val fScore = mutableMapOf<Int, Double>().withDefault { Double.MAX_VALUE }

        gScore[fromIdx] = 0.0
        fScore[fromIdx] = heuristic(graph, fromIdx, toIdx)

        while (openSet.isNotEmpty()) {
            // Find node in openSet with lowest fScore
            val current = openSet.minByOrNull { fScore.getValue(it) } ?: break
            if (current == toIdx) {
                return reconstructPath(cameFrom, current)
            }

            openSet.remove(current)
            val neighbors = graph.adjacency[current] ?: continue

            for ((edgeIdx, neighbor) in neighbors) {
                val tentativeG = gScore.getValue(current) + graph.edgeCost(edgeIdx)
                if (tentativeG < gScore.getValue(neighbor)) {
                    cameFrom[neighbor] = current
                    gScore[neighbor] = tentativeG
                    fScore[neighbor] = tentativeG + heuristic(graph, neighbor, toIdx)
                    openSet.add(neighbor)
                }
            }
        }
        return null // 不可达
    }

    /**
     * 贪吃蛇": 找下一个"最推荐"的目标节点。
     * 策略：优先去还没吃过的边较多的区域。
     */
    fun findNextTarget(graph: RouteGraph, currentIdx: Int): Int? {
        val visited = mutableSetOf<Int>()

        // BFS 找最近的未吃边
        val queue = ArrayDeque<Pair<Int, Int>>() // (node, depth)
        queue.add(currentIdx to 0)
        visited.add(currentIdx)

        while (queue.isNotEmpty()) {
            val (node, depth) = queue.removeFirst()
            val edges = graph.adjacency[node] ?: continue

            for ((edgeIdx, neighbor) in edges) {
                if (neighbor in visited) continue
                visited.add(neighbor)

                val edge = graph.edges[edgeIdx]
                if (!edge.isEaten) {
                    // 这条边还没吃过 → 走向 neighbor
                    return neighbor
                }
                if (depth < 20) { // 限制深度，防止全吃过后还继续搜
                    queue.addLast(neighbor to depth + 1)
                }
            }
        }
        return null // 全吃完了
    }

    /** 从线性路线构建图（相邻点之间连边） */
    fun buildChainGraph(route: ScenicRouteData): RouteGraph {
        val coords = route.coordinates
        val nodes = coords.mapIndexed { i, c ->
            GraphNode(c, if (i < route.waypointNames.size) route.waypointNames[i] else "")
        }
        val edges = mutableListOf<GraphEdge>()
        for (i in 0 until coords.size - 1) {
            val dist = RouteMatcher.haversine(coords[i], coords[i + 1]) / 1000.0
            edges.add(GraphEdge(i, i + 1, dist))
        }
        // 额外分岔边
        for ((from, to) in route.connections) {
            if (from in coords.indices && to in coords.indices) {
                val dist = RouteMatcher.haversine(coords[from], coords[to]) / 1000.0
                edges.add(GraphEdge(from, to, dist))
            }
        }
        // 如果是环形路线，首尾相连
        if (route.isLoop && coords.size >= 3) {
            val dist = RouteMatcher.haversine(coords.last(), coords.first()) / 1000.0
            edges.add(GraphEdge(coords.size - 1, 0, dist))
        }
        return RouteGraph(nodes, edges)
    }

    /** 欧几里得距离启发函数 */
    private fun heuristic(graph: RouteGraph, a: Int, b: Int): Double {
        val p1 = graph.nodes[a].latLng
        val p2 = graph.nodes[b].latLng
        return RouteMatcher.haversine(p1, p2)
    }

    private fun reconstructPath(cameFrom: Map<Int, Int>, current: Int): List<Int> {
        val path = mutableListOf(current)
        var node = current
        while (cameFrom.containsKey(node)) {
            node = cameFrom[node]!!
            path.add(node)
        }
        return path.reversed()
    }
}

/** 路线数据（供构建图和编辑器使用） */
data class ScenicRouteData(
    val id: String,
    val name: String,
    val coordinates: List<LatLng>,
    val waypointNames: List<String> = emptyList(),
    val isLoop: Boolean = false,
    /** 用户自定义的额外边（超出链式连接的部分），用于分岔路线 */
    val connections: List<Pair<Int, Int>> = emptyList()
)
