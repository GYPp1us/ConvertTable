# 母岩水晶台 · 模型与 PBR

保留方解石横边、矿岩基底、内凹晶洞和阶梯晶簇。四个侧面使用各不相同的晶体生长构图：长短不同的晶柱、偏心晶尖、错落小簇和空隙，避免对称倒三角。紫晶从基底凸出一个体素，根部深入矿岩；灰色矿物暗纹留在基底上。

上、下四角角套统一为黑石，四条竖向棱柱由两面环抱的黑石包边连接角套。层级仍为：外层角套 1、中层横边 / 包边 / 紫晶 2、内层矿岩与暗纹 3。

所有白、灰、黑石材按原版方解石、平滑玄武岩和深板岩的 16px 色块重绘，明显提高颗粒明暗对比，石材保持哑光且不发光。水晶保留深紫晶面与分散的银紫晶尖。新增 `geode_*` 石材 ID，以保留触媒底座使用的旧共享材质；`side_relief` 仍是齐平的灰色暗纹。原版参考来源见 `references/README.md`。

- `mother_rock_geode_16px.bbmodel`：191 个可编辑 cuboid、14 组、16 张原生 16×16 贴图。
- `mother_rock_geode_16px.blend`：相同模型、贴图、PBR 和摄影棚。
- `textures/`：颜色、法线/AO/高度、labPBR specular、独立发光及可编辑预览通道。
- `material_channels.png`：贴图通道检查图。
- `geode_front.png`、`geode_side.png`、`geode_three_quarter.png`、`geode_dark.png`：重新打开保存的 Blender 文件后渲染；暗光图不加辉光后处理。
- `scene.json` / `runtime_faces.json`：合并的外表面及运行时面数据。

## 复现

```powershell
python assets/geode_prototype/author_materials.py
& 'D:\Blender\blender.exe' --background --factory-startup --python assets/geode_prototype/build_geode.py
python assets/geode_prototype/publish_geode.py
& 'D:\Blender\blender.exe' --background assets/geode_prototype/mother_rock_geode_16px.blend --python assets/geode_prototype/verify_render.py
python assets/tools/validate_assets.py
```

导出器仅写 `models/block/crystal_table.json`、`models/item/crystal_table.json` 和 `textures/block/crystal_table/`，不修改注册、语言、配方或战利品。

验证：2323 个整数体素、单一连通体、无重叠与悬浮、边界严格 16×16×16；导出 648 个运行时面。曝光保持 -1.65，保留可辨识的淡紫晶尖、黑石护角和白石边缘。本轮改前备份及对比预览位于 `../../outputs/crystal-natural-growth/`，前两轮分别保留在 `../../outputs/crystal-relief-refinement/` 和 `../../outputs/crystal-side-refinement/`。PBR 参考见 `../labpbr/REFERENCE.md`。
