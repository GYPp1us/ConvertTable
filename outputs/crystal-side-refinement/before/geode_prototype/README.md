# 母岩水晶台 · 模型与 PBR

保留第四阶参考中的方解石外壳、玄武岩支撑、内凹晶洞和阶梯晶簇。晶体提高紫色饱和度，按根部、晶面、晶尖分别绘制发光；石材不发光。侧面去掉遮挡晶脉的额外内竖条。

- `mother_rock_geode_16px.bbmodel`：831 个可编辑 cuboid、12 组、12 张原生 16×16 贴图。
- `mother_rock_geode_16px.blend`：相同模型、贴图、PBR 和摄影棚。
- `textures/`：颜色、法线/AO/高度、labPBR specular、独立发光及可编辑预览通道。
- `material_channels.png`：贴图通道检查图。
- `geode_front.png`、`geode_side.png`、`geode_three_quarter.png`：重新打开保存的 Blender 文件后渲染。
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

验证：2601 个整数体素、单一连通体、无重叠与悬浮、边界严格 16×16×16。模型中间轮检查发现晶尖过曝，已降低发光并重新渲染；最终保留可辨识的淡紫晶尖和白石边缘。PBR 参考见 `../labpbr/REFERENCE.md`。
