# ConvertTable 配方环境核对（2026-10-02）

## 结论

缺少的黏液球、岩浆膏和农夫乐事转换配方来自旧的 `recipes.json`：测试实例与服务器配置仍只有 99 组普通配方和 52 条进阶配方，而 1.6.0 内置默认是 107 组和 83 条。1.6.0 测试期间幽匿进阶目录仍启用，不能据此归因于总开关关闭。

JEI 增殖目录在 1.6.0 测试日志中成功注册 105 条。对应启动记录没有 ConvertTable/JEI 缺类、插件失败或隐藏类别异常。因此现有证据能确认旧配置导致普通与进阶配方缺项，但不能确认 1.6.0 测试时增殖条目在界面不可见的原因。用户已说明目前主动回退到 1.5.2；当前活动版本不能代表最初的 1.6.0 测试状态。

## 实例与日志证据

客户端实例为 `D:\Desktop\Document\MC\MC\.minecraft\versions\26.3-CRAFT`。2026-10-01 的启动归档显示：

- `logs/2026-10-01-4.log.gz` 在 14:13 启动 `convert_table 1.6.0`、JEI `31.7.0.47`、FarmersDelight `26.3-3.6.27+refabricated`；Fabric Loader 记录 `Loading 96 mods`。
- 该次启动记录显示 `Conversion catalogue: 99 groups, 52 advanced entries; execution enabled: true`、`Loaded 105 growth recipes across 33 catalysts`、`JEI growth recipes registered: 105` 和 `JEI conversion recipes registered: 27700`。
- `logs/2026-10-01-5.log.gz` 在 23:02 的第二次 1.6.0 启动也报告 99/52、已加载 105 条增殖配方，并向 JEI 注册 105 条。
- `logs/2026-10-01-6.log.gz` 在 23:07 已记录回到 `convert_table 1.5.2`。当前 `mods/convert-table-1.5.2.jar` 是活动文件，`mods/convert-table-1.6.0.jar.disabled` 是回退后保留的禁用副本。

禁用副本 SHA-256 为 `2B2804E89430A98DBC5E08431243D6675FD2DB50EF0944FEEF64F601AD69532F`，与仓库 `build/libs/convert-table-1.6.0.jar` 及已记录的 1.6.0 构建哈希相同；其元数据版本为 1.6.0。它内置默认配方为 107 组/83 条进阶，增殖配方 JSON 有 105 条，并包含 `GrowthJeiCategory`、`GrowthViewerRecipe` 类。

## 配置差异

客户端配置：`D:\Desktop\Document\MC\MC\.minecraft\versions\26.3-CRAFT\config\convert_table\recipes.json`

服务器配置：`E:\MC\server_26.3_island\config\convert_table\recipes.json`

两份文件都与活动 1.5.2 JAR 内置默认 `convert_table/default_recipes.json` 完全一致，文件里没有自定义配方、禁用条目或用户删除记录。两份配置均为 `execution_enabled=true`，99/99 普通组和 52/52 进阶配方启用；`sculk.ordinary_souls_per_batch` 缺失，按兼容规则读取为 1。对应 `unavailable-items.txt` 均为 0 字节，没有记录无法解析的物品 ID。

当前 1.6.0 默认中比这两份旧配置新增的普通组 ID 为 `FD-01` 至 `FD-08`：蔬菜、种子、生/熟肉片、生/熟鱼片、饼干、面团与生意面。这些条目使用 `farmersdelight:` 物品 ID，且启动模组列表确认农夫乐事已加载。新增进阶 ID 为：

`fd-draw-01`, `mat-001`, `mat-002`, `tree-oak-log`, `tree-oak-wood`, `tree-spruce-log`, `tree-spruce-wood`, `tree-birch-log`, `tree-birch-wood`, `tree-jungle-log`, `tree-jungle-wood`, `tree-acacia-log`, `tree-acacia-wood`, `tree-dark-oak-log`, `tree-dark-oak-wood`, `tree-mangrove-log`, `tree-mangrove-wood`, `tree-cherry-log`, `tree-cherry-wood`, `tree-pale-oak-log`, `tree-pale-oak-wood`, `tree-poplar-log`, `tree-poplar-wood`, `tree-crimson-stem`, `tree-crimson-hyphae`, `tree-warped-stem`, `tree-warped-hyphae`, `life-cod`, `life-salmon`, `life-tropical-fish`, `life-pufferfish`.

其中 `mat-001` 是黏液球转岩浆膏，`mat-002` 是岩浆膏转黏液球。

## JEI 检查边界

1.6.0 两次启动的相关记录都确认增殖目录向 JEI 注册了 105 条配方。日志中没有 `GrowthJeiCategory`、`GrowthViewerRecipe` 缺类、JEI 插件失败、隐藏类别或相关异常。日志里有两条缺类警告，分别指向 MiniHUD 的 `EntitiesDataManager` 和 EMF 的 `EMFModelPart`，与 ConvertTable/JEI 无关。

1.5.2 JAR 不含 `GrowthJeiCategory` 和 `GrowthViewerRecipe`，所以目前回退后的活动版本不能提供 1.6.0 新增的 JEI 增殖分类。这只说明当前回退状态；1.6.0 测试日志则证明当时 105 条已注册。日志没有记录用户在 JEI 搜索框中的结果，故如果当时界面仍缺少可见条目，单凭启动日志无法进一步定位。

本核对只读取客户端、服务器和构建产物；没有修改实例配置、模组 JAR、存档，也没有生成安装候选文件。

## 1.6.1 修复与验收

配置升级已补入 1.6.1：仅当完整配置匹配已发布的 1.5.2 默认值时升级，对象键顺序和空白差异不影响识别。先校验新版配方，在同一配置目录保存 `recipes-1.5.2-<唯一编号>.json.bak` 原始字节备份，再原子替换为新版默认值。任何设置、自定义、禁用、删除或配方变更均使配置保持原样，不自动补回玩家有意删除的条目。

使用已发布旧配置直接启动仓库隔离世界，日志记录自动升级到 1.6.1、备份路径与来源哈希，并加载 107 组/83 条。逐条执行覆盖 4762 个普通转换场景、335 个进阶场景、105 条增殖，全部零失败。临时文件测试覆盖原始/重排键默认、重复启动、旧备份保留、自定义/禁用/删除/编辑不改动、新默认校验失败与并发编辑检测。

JEI 另以客户端实际 21 个兼容模组和 JEI 配置做隔离验证：105 条配方、触媒 U、产物 R、两种工作站及台子物品 U 查询的分类/数量断言全部通过。台子 U 查询同时使用 JEI 的 INPUT 与 CRAFTING_STATION 角色，不能把仅 INPUT 的查询当作实际 U 快捷键行为。当前未复现玩家所报的“完全没有增殖分类”，没有据此改动 JEI 的生产注册逻辑。复测时可在触媒或两种台子物品上按 U、在产物上按 R。

测试日志：`../../build/recipes-1.6.0-full-audit.log`、`../../build/recipes-1.6.0-full-mods.log`、`../../build/recipes-1.6.1-final.log`、`../../build/recipes-1.6.1-upgrade-startup.log`。完整模组组合的一次 1.6.1 测试在全部断言通过后卡在 Fabric gametest 关闭同步，线程栈保存在 `../../build/recipes-test-exit-threads.txt`；已仅终止该仓库隔离进程。旧配置启动、全部断言及最终构建以最后一个日志的 BUILD SUCCESSFUL 为准。

所有后台测试设置主音量为 0，仅修改仓库隔离实例。客户端与 E 盘服务器的原始配置文件 SHA-256 均仍为 `bc3cb66500d47413f71a3cdeadfc72cb73c00a344be7d257931f35ae811e2c15`，没有修改玩家正在使用的实例。

修复版 JAR：`../../build/libs/convert-table-1.6.1.jar`；SHA-256：`942bc7df8b9f343ab4ba5a04cec97b0353ff7c3e8a80cf22307765020b18caf2`。

实际 JEI 增殖分类截图：

![JEI 晶脉增殖配方](../../build/recipes-1.6.1-screenshots/0009_jei-growth-recipes.png)
