# 🐍 Snake Navigation (蛇形导航)

**贪吃蛇导航** — 景区游览车趣味导航 App。
走过的路线变虚线，尽量不走重复路。

## Core Concept

将景区路径规划变成贪吃蛇游戏：
- 游览车开过的路段变成灰色虚线（"吃掉"）
- 未走过的路段保持高亮霓虹色
- 系统自动规划不走回头路的路线
- GPS 实时追踪，车头是蛇头图标

## Architecture

```
app/src/main/java/com/snakenav/
├── MainActivity.kt          # Compose 入口
├── map/
│   └── SnakeMapScreen.kt    # 主地图界面（MapLibre GL + route rendering）
├── data/
│   ├── GeoJsonRoute.kt      # 路线数据模型 + RouteState + RouteMatcher
│   └── SampleRoute.kt       # 内置示范路线（颐和园/奥林匹克公园）
├── navigation/
│   └── SnakeEngine.kt       # 路径规划引擎（A* + 动态权重）
└── gamification/
    ├── SnakeGauge.kt        # 进度环形指示器
    └── SnakeRenderer.kt     # 贪吃蛇粒子/音效（待实现）
```

## Tech Stack

- **Kotlin + Jetpack Compose** — 现代 Android UI
- **MapLibre GL Native** — 开源地图渲染（离线可用）
- **mapLibre-navigation-android** — Turn-by-turn 导航（后续接入）
- **GeoJSON** — 路线数据标准格式

## Phases

| Phase | Feature | Status |
|-------|---------|--------|
| P0 | MapLibre 地图 + 路线显示 + GPS 动画 | ✅ Done |
| P1 | 路线切割（走过变虚线）+ 贪吃蛇逻辑 | 🔨 In progress |
| P2 | 真实 GPS 追踪 + 自动吸附绑路 | ⏳ Next |
| P3 | A* 路径重规划 + 动态权重 | 📅 Planned |
| P4 | 战争迷雾 + 积分榜 + 语音彩蛋 | 🎯 Future |

## Build

```bash
./gradlew assembleDebug
```

Requires Android SDK 34+ and JDK 17.

## License

MIT
