package com.snakenav.data

import com.snakenav.navigation.ScenicRouteData

/**
 * Built-in sample scenic routes for development & demo.
 */
object SampleRoute {

    /** Summer Palace loop */
    fun createSummerPalace(): ScenicRoute {
        val coords = listOf(
            LatLng(39.9998, 116.2755),  // 0 东宫门
            LatLng(40.0008, 116.2750),  // 1 仁寿殿
            LatLng(40.0015, 116.2740),  // 2 长廊东
            LatLng(40.0025, 116.2725),  // 3 长廊中
            LatLng(40.0030, 116.2710),  // 4 排云殿
            LatLng(40.0025, 116.2690),  // 5 石舫
            LatLng(40.0010, 116.2670),  // 6 西堤北
            LatLng(39.9985, 116.2660),  // 7 西堤中
            LatLng(39.9960, 116.2675),  // 8 西堤南
            LatLng(39.9940, 116.2690),  // 9 南湖岛西
            LatLng(39.9930, 116.2710),  // 10 南湖岛
            LatLng(39.9935, 116.2730),  // 11 十七孔桥
            LatLng(39.9950, 116.2745),  // 12 新建宫门
            LatLng(39.9975, 116.2755),  // 13 文昌阁
            LatLng(39.9998, 116.2755),  // 14 东宫门
        )
        return ScenicRoute(
            id = "summer-palace", name = "颐和园经典游览路线",
            coordinates = coords, totalDistanceKm = 3.5
        )
    }

    fun summerPalaceData(): ScenicRouteData = ScenicRouteData(
        id = "summer-palace", name = "颐和园经典游览路线",
        coordinates = createSummerPalace().coordinates,
        waypointNames = listOf(
            "东宫门", "仁寿殿", "长廊东", "长廊中", "排云殿",
            "石舫", "西堤北", "西堤中", "西堤南", "南湖岛西",
            "南湖岛", "十七孔桥", "新建宫门", "文昌阁", "东宫门"
        ),
        isLoop = true
    )

    /** Olympic Park loop */
    fun createOlympicPark(): ScenicRoute {
        val coords = listOf(
            LatLng(40.0110, 116.3890),  // 0 南门
            LatLng(40.0130, 116.3895),  // 1 仰山
            LatLng(40.0155, 116.3900),  // 2 天境
            LatLng(40.0170, 116.3885),  // 3 北侧
            LatLng(40.0165, 116.3850),  // 4 西侧湿地
            LatLng(40.0145, 116.3840),  // 5 生态廊道
            LatLng(40.0120, 116.3850),  // 6 南侧湿地
            LatLng(40.0110, 116.3870),  // 7 奥海
            LatLng(40.0110, 116.3890),  // 8 回到南门
        )
        return ScenicRoute(
            id = "olympic-park", name = "奥林匹克森林公园南园",
            coordinates = coords, totalDistanceKm = 5.0
        )
    }

    fun olympicParkData(): ScenicRouteData = ScenicRouteData(
        id = "olympic-park", name = "奥林匹克森林公园南园",
        coordinates = createOlympicPark().coordinates,
        waypointNames = listOf(
            "南门", "仰山", "天境", "北侧", "西侧湿地",
            "生态廊道", "南侧湿地", "奥海", "南门"
        ),
        isLoop = true
    )

    /**
     * 分岔路线：颐和园带分支（测试 A* 避重）
     * 主路 + 两条分支支路。
     */
    fun branchedSummerPalace(): ScenicRouteData {
        val coords = listOf(
            LatLng(39.9998, 116.2755),  // 0 东宫门
            LatLng(40.0008, 116.2750),  // 1 仁寿殿
            LatLng(40.0015, 116.2740),  // 2 长廊东
            LatLng(40.0025, 116.2725),  // 3 长廊中
            LatLng(40.0030, 116.2710),  // 4 排云殿（分岔点）
            // 支路 A: 排云殿 → 佛香阁 → 智慧海 → 排云殿（回到分岔点）
            // 支路 B: 排云殿 → 石舫 → 西堤北 → 西堤中 → 西堤南（继续前行）
        )
        // 这个路线需要在 SnakeEngine.buildChainGraph 之后手动添加分支边
        return ScenicRouteData(
            id = "branch-test", name = "分岔路线测试",
            coordinates = coords,
            waypointNames = listOf("东宫门", "仁寿殿", "长廊东", "长廊中", "排云殿"),
            isLoop = false
        )
    }
}
