package com.snakenav.navigation

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 真实路网加载器
 *
 * 从 assets 或 res/raw 中的 OSM 道路数据 JSON 构建路线图。
 * JSON 格式：{ "nodes": [[lat,lng], ...], "edges": [[from,to], ...] }
 */
object RoadNetworkLoader {

    private var cachedNetwork: RoadNetwork? = null

    /**
     * 加载路网（带缓存）
     * @param context Android 上下文（用于读取资源文件）
     * @return RoadNetwork 实例
     */
    fun load(context: Context): RoadNetwork {
        cachedNetwork?.let { return it }

        val nodes = mutableListOf<RoadNode>()
        val json: JSONObject

        try {
            // Try res/raw first
            val resId = context.resources.getIdentifier(
                "summer_palace_roads", "raw", context.packageName)
            if (resId != 0) {
                val `is` = context.resources.openRawResource(resId)
                val reader = BufferedReader(InputStreamReader(`is`))
                val text = reader.readText()
                reader.close()
                json = JSONObject(text)
            } else {
                // Fallback: generate a simple grid
                return generateFallback()
            }
        } catch (e: Exception) {
            android.util.Log.w("RoadNetLoader", "Failed to load road data: ${e.message}")
            return generateFallback()
        }

        // Parse nodes
        val nodesArr = json.optJSONArray("nodes") ?: return generateFallback()
        for (i in 0 until nodesArr.length()) {
            val pt = nodesArr.getJSONArray(i)
            nodes.add(RoadNode(id = i, lat = pt.getDouble(0), lng = pt.getDouble(1)))
        }

        // Parse edges
        val edgesArr = json.optJSONArray("edges") ?: return generateFallback()
        for (i in 0 until edgesArr.length()) {
            val e = edgesArr.getJSONArray(i)
            val from = e.getInt(0)
            val to = e.getInt(1)
            if (from in nodes.indices && to in nodes.indices) {
                nodes[from].adj.add(to)
                // bidirectional
                if (from !in nodes[to].adj) {
                    nodes[to].adj.add(from)
                }
            }
        }

        cachedNetwork = RoadNetwork(nodes)
        return cachedNetwork!!
    }

    /** 回退：小规模网格 */
    private fun generateFallback(): RoadNetwork {
        android.util.Log.w("RoadNetLoader", "Using fallback grid network")
        val nodes = mutableListOf<RoadNode>()
        val cols = 10; val rows = 10
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                nodes.add(RoadNode(
                    id = r * cols + c,
                    lat = 39.990 + r * 0.0022,
                    lng = 116.260 + c * 0.0033
                ))
            }
        }
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val id = r * cols + c
                if (c + 1 < cols) {
                    nodes[id].adj.add(r * cols + (c + 1))
                }
                if (r + 1 < rows) {
                    nodes[id].adj.add((r + 1) * cols + c)
                }
            }
        }
        return RoadNetwork(nodes)
    }
}
