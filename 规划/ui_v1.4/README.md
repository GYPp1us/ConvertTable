# 转换台 UI 概念与 v1.4 策划

本轮只修改策划，未实装配方、GUI 或 26.3 迁移。原 v1.3 文件保留。

## UI 概念图

使用内置 imagegen 生成，材质方向为原版像素 GUI 配主题边框。图稿用于评审布局；槽位网格与部分图标是生图示意，实际布局规格以策划文稿为准（玩家背包固定 9×3＋9 快捷栏）。

- [猪灵转换台](<D:/Desktop/Document/Coding/FULL STACK/ConvertTable/规划/ui_v1.4/01_猪灵_UI概念.png>)：两个输入、一个随机产物；无目标选择。
- [末地转换台](<D:/Desktop/Document/Coding/FULL STACK/ConvertTable/规划/ui_v1.4/02_末地_UI概念.png>)：容器输入／输出、相位燃料、批次进度和目标抽屉。
- [幽匿转换台](<D:/Desktop/Document/Coding/FULL STACK/ConvertTable/规划/ui_v1.4/03_幽匿_UI概念.png>)：高级配方、相位与死亡计数、分层连接地图及世界投影。

[完整 UI 与幽匿规则策划](<D:/Desktop/Document/Coding/FULL STACK/ConvertTable/规划/ui_v1.4/UI与幽匿计数策划_v1.4.md>) · [最终提示词集](<D:/Desktop/Document/Coding/FULL STACK/ConvertTable/规划/ui_v1.4/image_prompts.json>)

## 配方修订

- [v1.4 工作簿](<D:/Desktop/Document/Coding/FULL STACK/ConvertTable/规划/ui_v1.4/outputs/01a09e81-7dc7-7742-a282-bfc6b07daf6b/Minecraft_26.3_转换台配方策划_v1.4.xlsx>)
- [v1.4 TOML](<D:/Desktop/Document/Coding/FULL STACK/ConvertTable/规划/conversion_tables_26.3_v1.4.toml>)

高级配方费用由 XP 改为死亡次数。每次生物在连通幽匿顶面上的有效死亡记 1 次，费用数字暂沿用原稿，后续再做玩法平衡。计数上限、网络边界、共享归属和高级相位消耗解释均在文稿中明确标为建议／草案。

已核对：99 个普通组保留；40 条高级配方的材料、数量、自动化和门槛保留；表格与 TOML 的死亡费用一致。工作簿沿用原布局，新增规则位于“规则与排除”第 17—25 行。

表格由 artifact-tool 编辑及预览；该库仅改可见表头时会保留旧的内部表列名，已对导出文件的该项元数据作最小修正，其他 ZIP 部件不变。

