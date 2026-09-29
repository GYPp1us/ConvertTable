# 紫晶浮雕 / 灰纹沉底 · 2026-09-28

灰色菱形纹由凸起改成与矿岩基底齐平的低对比底纹，配合浅刻色差与 PBR 微凹。紫色晶纹向外凸出一个体素，根部延伸进基底，保持三层结构：四角包边 1、横边与水晶浮雕 2、基底与灰纹 3。

- Blockbench、Blender、贴图和 Minecraft 方块 / 物品资源已同步。
- 239 个可编辑 cuboid，2229 个连通整数体素，669 个运行时面，16 套原生 16×16 材质。
- 重新打开保存的 Blender 模型，检查无重叠、无悬浮和 16×16×16 边界；正面、侧面、斜视及暗光渲染通过。
- `validate_assets.py`：50 套材质、6441 个面通过。
- 使用本机缓存 Gradle 9.6.0 完成离线 `build` 和隔离 Vulkan `runClientGameTest`；Minecraft 26.3 模型解析及晶体生长测试通过，日志见 `build-and-game-test.log`。
- JAR 中 50 个紫水晶资源文件逐字节匹配当前源文件，SHA-256 见 `package-check.json`。

改前模型在 `before/`，对比渲染在 `comparison.png`，实际游戏截图在 `game-preview.png`。
当前模组为 `../../build/libs/convert-table-1.4.0.jar`。
