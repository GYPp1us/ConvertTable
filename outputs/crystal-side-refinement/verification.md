# 紫水晶侧面美术验证 · 2026-09-28

侧面从完整方框改为三层结构：四角包边为第 1 层，横边及石质浅浮雕为第 2 层，矿岩基底为第 3 层。晶簇纹中的紫晶嵌槽与基底同深，比石雕表面低一个体素。新材质使用较平缓的石面法线、分级紫晶发光和少量银紫亮点。

- 可编辑模型：242 个 cuboid、13 组，整体为 2229 个连通整数体素，无重叠、无悬浮，边界为 16×16×16。
- 运行时：16 套原生 16×16 材质、733 个面；Blockbench、Blender、资源 JSON 与 PNG 均已同步。
- `python assets/tools/validate_assets.py`：通过，检查 50 套材质和 6505 个面。
- 保存后的 Blender 模型重新打开并渲染正面、侧面、斜视和暗光；暗光图无辉光后处理。
- 离线 `build`：通过，Minecraft 26.3 原生解析器读取全部 10 个模型，UV 与动画检查通过。
- 隔离 Vulkan 客户端 `runClientGameTest`（`growthTest`）：通过；实际场景截图见 `game-preview.png`。
- 模组中 50 个紫水晶资源文件逐字节匹配源文件，校验记录及 SHA-256 见 `package-check.json`。
- 其他三个转换台与触媒底座模型的 SHA-256 与修改前一致。

产物：`../../build/libs/convert-table-1.4.0.jar`。未安装到玩家实例。

本次使用缓存中的 Gradle 9.6.0、项目原有 Loom 1.17-SNAPSHOT（解析到 1.17.21）和 `C:/Users/GYP/.gradle`，避开缺少发行包的 9.5.1 wrapper。构建与测试日志分别为 `gradle-build-final.log` 和 `game-test.log`；测试使用启动器现有资源缓存与索引 34，并跳过 `downloadAssets`。

美术对比：`comparison.png`；改前可编辑资源与运行时材质保存在 `before/`。
