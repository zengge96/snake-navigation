package com.snakenav.data

/**
 * Built-in sample scenic routes for development & demo.
 */
object SampleRoute {

    /**
     * 颐和园模拟游览路线 (Summer Palace scenic tour)
     * A circular-ish route around Kunming Lake.
     */
    fun createSummerPalace(): ScenicRoute {
        // Approximate coordinates around Summer Palace, Beijing
        // Start: East Palace Gate (东宫门)
        // Follow: 仁寿殿 → 长廊 → 排云殿 → 石舫 → 西堤 → 南湖岛 → 十七孔桥 → 返回东门
        val coords = listOf(
            LatLng(39.9998, 116.2755),  // 东宫门
            LatLng(40.0008, 116.2750),  // 仁寿殿
            LatLng(40.0015, 116.2740),  // 长廊东
            LatLng(40.0025, 116.2725),  // 长廊中
            LatLng(40.0030, 116.2710),  // 排云殿
            LatLng(40.0025, 116.2690),  // 石舫
            LatLng(40.0010, 116.2670),  // 西堤北
            LatLng(39.9985, 116.2660),  // 西堤中
            LatLng(39.9960, 116.2675),  // 西堤南
            LatLng(39.9940, 116.2690),  // 南湖岛西
            LatLng(39.9930, 116.2710),  // 南湖岛
            LatLng(39.9935, 116.2730),  // 十七孔桥
            LatLng(39.9950, 116.2745),  // 新建宫门
            LatLng(39.9975, 116.2755),  // 文昌阁
            LatLng(39.9998, 116.2755),  // 东宫门（回到起点）
        )
        return ScenicRoute(
            id = "summer-palace",
            name = "颐和园经典游览路线",
            coordinates = coords,
            totalDistanceKm = 3.5
        )
    }

    /**
     * 北京奥林匹克森林公园南园环线
     */
    fun createOlympicPark(): ScenicRoute {
        val coords = listOf(
            LatLng(40.0110, 116.3890),  // 南门
            LatLng(40.0130, 116.3895),  // 仰山
            LatLng(40.0155, 116.3900),  // 天境
            LatLng(40.0170, 116.3885),  // 北侧
            LatLng(40.0165, 116.3850),  // 西侧湿地
            LatLng(40.0145, 116.3840),  // 生态廊道
            LatLng(40.0120, 116.3850),  // 南侧湿地
            LatLng(40.0110, 116.3870),  // 奥海
            LatLng(40.0110, 116.3890),  // 回到南门
        )
        return ScenicRoute(
            id = "olympic-park",
            name = "奥林匹克森林公园南园",
            coordinates = coords,
            totalDistanceKm = 5.0
        )
    }
}
