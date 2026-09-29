# 母岩水晶台 · 模型与 PBR

保留方解石外壳、玄武岩支撑、内凹晶洞和阶梯晶簇。四个侧面由方框改为三层矿石雕刻：按最外圈为第一层，四角包边位于深度 0，上下横边与晶簇形石质浅浮雕位于深度 1，矿岩基底位于深度 2。紫晶纹路嵌入石雕之间的凹槽，与基底同深；没有围合侧面的白色竖边。

雕刻采用断开的对称晶簇纹，石面为灰紫矿岩；深紫晶面、淡紫过渡和中心两个银紫亮点分别控制发光。方解石与基底保持不发光，侧面石材法线起伏低于晶体，明暗处都保留材质和切面层次。所有纹理原生 16×16，所有实体仍在整数体素网格上。

- `mother_rock_geode_16px.bbmodel`：242 个可编辑 cuboid、13 组、16 张原生 16×16 贴图。
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

验证：2229 个整数体素、单一连通体、无重叠与悬浮、边界严格 16×16×16；导出 733 个运行时面。新侧面降低了石材法线强度，维持曝光 -1.65，保留可辨识的淡紫晶尖和白石边缘。改前备份及对比预览位于 `../../outputs/crystal-side-refinement/`。PBR 参考见 `../labpbr/REFERENCE.md`。
