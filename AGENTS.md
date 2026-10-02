# ConvertTable 维护约定

## README 面向玩家

- `README.md` 是中文玩家指南，`README.en.md` 是同内容英文版。只介绍安装必要条件、方块用途、如何摆放/投入/选择/开启/收取、工作台合成和实际故障排查。
- 不向玩家解释 Java 类、JSON 字段、资源路径、算法、材质通道、构建命令或未实装策划。技术说明写在 `config/convert_table/README.md`、`assets/README.md` 或本文件。
- 先查当前 Java、配方和语言资源再改说明。不要把 `规划/` 或旧 `outputs/*/verification.md` 中的计划/历史测试当作当前功能。中文、英文、Excel、配置说明的操作限制必须一致。
- 当前基线为 v0.2.0 / Minecraft 26.3 / Java 25。版本以 `gradle.properties` 为准，运行要求以 `fabric.mod.json` 为准。
- 转换台单次操作时长由代码固定：黑金 4 秒、末地 2 秒、幽匿 1 秒；界面显示进度动画。开启连续后，投入槽或连接输入中的材料和费用足够时会自动开始下一项，不需要逐项点击；输出堵塞时进度仍会完成并等待出料，交付时才扣材料和费用。晶脉增殖界面显示当前产速和下一件进度。
- 黑金随机转换；三种转换台都支持由连接棒绑定输入和输出。末地/幽匿可指定目标并处理潜影盒，连接输入精确匹配用投入槽样本，潜影盒精确匹配锁定首次成功输入。容器链接范围为曼哈顿距离 16、每台输入和输出共 8 个，不自动扫描；连接容器点击的面决定漏斗侧面规则。触媒基座只支持输出链接。
- 幽匿普通转换每批消耗 `sculk.ordinary_souls_per_batch` 个可用灵魂，默认 1；进阶按现有 `advanced[].deaths` 配方值消耗可用灵魂。两者均不消耗相位。`sculk.advanced_charge_per_operation` 是兼容字段，零和旧非零值都可读，运行时不扣相位；旧配置缺少 `ordinary_souls_per_batch` 时按 1 读取。JSON 配方字段 `deaths`、设置值 `advanced_cost = "death_count"` 和存档 NBT 键 `Deaths` 均保留原名；玩家界面称为“可用灵魂”。升级旧世界时幽匿台旧燃料槽物品会掉落。
- 幽匿可用灵魂按合格生物原版经验奖励 × 固定代码倍率 32 计算（1 XP=32 灵魂），倍率不配置化；尊重 `shouldDropExperience`，不掉经验的生物提供 0，默认无需玩家击杀。优先复用死亡过程中原版首次计算的奖励，避免随机经验重掷；自然死亡若未计算则使用同一原版接口取值一次。旧设置 `death_count_per_mob` 保留并校验但运行时忽略；`death_count_capacity` 仍为每台 4096；自定义 `requires_player_kill` 仍有效。原版经验球、掉落物和幽匿催发体行为不拦截。
- 进阶配方可有 `outputs` 随机产物池；保留 `output` 为主产物展示值，唯一性校验须覆盖所有候选结果。JEI 显示增殖配方，REI 当前仍只显示转换四类。
- 晶脉固定为各轴距离合计 8、128 个节点（含源台）、8 块母岩、每网一座增殖台。容器链接和幽匿网络各用自己的 16 格距离规则，不能与晶脉范围混用。
- README 方块图使用 `assets/readme/`，从实际资源/JAR 重渲染。不使用旧 `assets/previews/`、概念图或 AI 猜测游戏外观。

## 配方：先定位，只改唯一源，运行同步

Excel 用于向用户展示配方，不是游戏输入或第二套手写配方源。禁止手改 Excel 或只改导出的 JSON。

| 要改什么 | 唯一编辑入口 | 派生文件 |
| --- | --- | --- |
| 普通组、幽匿进阶、燃料/灵魂费用 | `规划/conversion_tables_26.3_v1.4.toml` | 默认 JSON、仓库配置样例、Excel |
| 触媒增殖 | `src/main/resources/data/convert_table/growth_recipes.json` | Excel |
| 工作台合成 | `src/main/resources/data/convert_table/recipe/*.json` | Excel；同时维护生成该配方的美术脚本 |
| 新物品的中文展示名 | `assets/tools/recipe_item_names.json` | Excel；名称从当前游戏语言文件核实 |

固定展示文件：`outputs/01a0f354-50a1-7850-9a3e-c4f75b90728b/ConvertTable配方.xlsx`。覆盖这一份，不创建“最终版2”等平行文件。五张表为配方总览、普通转换、幽匿进阶、触媒增殖、方块合成，支持筛选，数量/费用/时长存成数字。进阶表 J 列保持可用灵魂费用、K 列保持相位，新增展示列只能追加，不能移动现有列。

### 每次维护的最短流程

1. 用 `python assets/tools/sync_recipes.py --find WOOD-01` 定位稳定 ID，也可以传物品 ID。再用 `rg -n 'WOOD-01' 规划/conversion_tables_26.3_v1.4.toml` 找唯一源的小片段。不要先读取整个 Excel、整个大 JSON 或全部旧规划。
2. 只修改所需条目，保留其他 ID、顺序、配方和参数。同一普通组是严格 1:1 等价类，禁止为一个定向配方合并不等价材料。
3. 运行 `python assets/tools/sync_recipes.py`：校验全部数据，导出两份转换 JSON，从默认配方、增殖配方和工作台合成自动生成 Excel。
4. 运行 `python assets/tools/sync_recipes.py --check`。只读检查唯一源与两份 JSON 一致、ID/整数/名称/重复配方合法、Excel 的来源哈希和文件哈希一致。
5. 检查差异和 Excel 受影响的行，报告“哪个 ID，材料/产物/数量/费用/自动限制如何变化”。功能变更同步中英文 README 和配置说明。涉及游戏配方或逻辑时，再运行适用的 Gradle 检查。

同步脚本用 Python 3.11+ 标准库和 Codex 内置 `@oai/artifact-tool`，自动查找内置运行时，无须 npm 安装。找不到时先调用 `load_workspace_dependencies`，再传 `--runtime <dependencies目录>`。缺少运行时应保留原 Excel、报告未同步，不改用手工表格。

### 普通转换：一个 `[[group]]` 就是一组

```toml
[[group]]
id = "WOOD-01"                 # 保留现有稳定 ID
cat = "木材"
name = "木板"
tier = "piglin"                # piglin：三台；end：末地+幽匿；sculk：仅幽匿
batch = 64                     # 每批上限，1..4096
items = ["oak_planks|橡木木板", "spruce_planks|云杉木木板"]
# enabled = false              # 如需停用，在源设置；默认 true
```

至少两个不同的有效物品，不能转成自身。黑金随机，其他台定向。金粒、相位和可用灵魂费用须按实际台型/组级分别展示，不能把继承组的三台费用误写成相同。幽匿普通转换从 `sculk.ordinary_souls_per_batch` 读取每批费用，必须是 1..4096 整数，旧配置缺失时默认 1。

### 幽匿进阶：一个 `[[recipe]]` 是一次操作

- 按现有条目复制结构，只改所需字段：`id/name/input/input_n/output/output_n/deaths/auto`。`catalyst/catalyst_n` 是可选、会消耗的材料；没有触媒时 `catalyst_n` 可为 0 或省略。
- `auto = false` 表示仅投入槽手动，不能连续或处理容器。`unlock` 是说明文字，没有独立进度锁。
- 输入、输出及存在触媒时的数量是物品数量，必须为整数 1..4096；无触媒时 `catalyst_n` 可为 0 或省略。JSON `deaths` 是兼容字段，数值仍表示单次操作消耗的可用灵魂，独立为整数 1..4096；设置兼容值 `advanced_cost = "death_count"` 与存档 NBT 键 `Deaths` 也保留原名。幽匿进阶相位兼容字段在 `[sculk].advanced_charge_per_operation`，允许 0..4096 且运行时忽略。
- `ADV-009/010/011` 是珊瑚模板，导出为五个品种，每次返还一个空桶。改模板后核对五个具体结果。新增模板规则或返还类型须同时改 `export_recipe_config.py`，不能只写它不处理的字段。
- 同一主材/产物不能重复为多个启用的进阶配方，否则运行时无法唯一选取。

### 触媒增殖：一条 JSON 对应一个目标

```json
{"id":"convert_table:growth/wood/oak/log","catalyst":"minecraft:oak_sapling","output":"minecraft:oak_log","cost":2}
```

`id` 唯一，`catalyst/output` 是完整物品 ID，`cost` 是正整数生长因子/件，每次产出一件。同一触媒可有多个不同目标，不能重复触媒/目标对。目标只选原版低阶易得材料；不要把加工二级品或矿石、矿物、稀有战利品加入目录。目标 ID 必须是实际注册的 Item，仅有语言文件里的 Block 名称不够。触媒不消耗，与幽匿催化材料不同。木材系列优先使用对应的低阶树苗作触媒（红树使用胎生苗）；其他配方优先选自然材料或高级魔法材料。今后避免工具、船和加工物作触媒，不要求玩家使用工具才能启动增殖；浮冰和滴水石块作为已保留的自然主题触媒例外。

### 同步边界和验收

- 同步脚本只更新仓库，不修改玩家启动器实例或服务器。模组仅对完整语义等于已发布 0.1.1（原 1.5.2） 默认值的配置做自动升级：识别对象键排序后的固定来源指纹，先保存原始字节备份，再原子替换为已校验的新内置默认值；不支持原子替换时保留原配置。定制、禁用、删除条目或修改设置的配置不自动覆盖，需管理员另行同步并重启。固定旧默认指纹用于迁移识别，不能作为第二套配方编辑入口。
- 配方验收必须覆盖生产计时路径和菜单选目标，不能仅调用即时事务测试后宣称全部可用。`RecipeCoverageGameTest` 逐输入/台型、首尾定向目标、连续批量验证普通组，逐条验证进阶及随机目标/容器限制，逐条用两种增殖 ticker 验证全部增殖；JEI 同时检查触媒 U、产物 R、台子 U 的分类和索引。
- 用户报告配方缺失时，先只读核对实际客户端和服务端的模组版本、配方目录数量/稳定 ID、执行开关及可选模组。构建成功、隔离测试通过不能当作实例已更新；报告必须区分仓库产物和实际安装。更新候选按旧 JAR 默认、实例配置、新默认做三方比较，保留自定义、禁用和明确删除，先提供差异与备份方案。
- 增殖配方由 JAR 读取，改 JSON 源需重新构建并更换模组。JEI 显示晶脉增殖目录；REI 当前仍只显示转换四分类。
- `ui`、`rules`、网络说明等草案元数据不是可执行配置。改变扫描范围必须改 Java，不能只改 TOML 描述并宣称实装。
- 生成器是 `sync_recipes.py`、`export_recipe_config.py`、`build_recipe_workbook.mjs`。校验失败必须修复，不能手改清单哈希让旧 Excel 通过。
- `recipes.manifest.json` 随 Excel 提交，用于识别过期来源。PNG 预览和工具检查中间文件不必提交。
- 构建用 `.\gradlew.bat build`；已有离线查看器依赖可加 `--offline -PviewerDeps=build/viewer-deps`。图像用 `python assets/tools/render_readme.py --jar build/libs/convert-table-0.2.0.jar`，再运行 `python assets/tools/make_banner.py`。版本参数随构建更新。
- 美术导出会重新生成部分合成。修改合成时同步 `export_refined_assets.py` 的 `recipes()` 或对应晶洞/基座生成器，防止下次导出恢复旧配方。

当前数量为 107 组普通转换、83 条进阶、105 条增殖/19 种触媒、6 条合成，仅为基线，不要把这些数字写成永远不变的校验上限。
