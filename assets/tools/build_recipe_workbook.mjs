// Called by sync_recipes.py with a validated JSON snapshot and output directory.
import fs from 'node:fs/promises';
import path from 'node:path';
import { Workbook, SpreadsheetFile } from '@oai/artifact-tool';

const [input, outputDir] = process.argv.slice(2);
const data = JSON.parse(await fs.readFile(input, 'utf8'));
const { conversion, growth, crafting, names, fishing } = data;
const workbook = Workbook.create();
const sheets = ['配方总览', '普通转换', '幽匿进阶', '触媒增殖', '方块合成'];
for (const name of sheets) workbook.worksheets.add(name);
const colors = ['#374151', '#805E23', '#246D68', '#725494', '#465C76'];
const nameOf = id => id ? names[id] : '无';
const operationSeconds = { piglin: 4, end: 2, sculk: 1 };
const ordinarySculkSouls = conversion.settings.sculk.ordinary_souls_per_batch ?? 1;
const columns = n => {
  let out = '';
  for (; n > 0; n = Math.floor((n - 1) / 26)) out = String.fromCharCode(65 + (n - 1) % 26) + out;
  return out;
};

function setup(index, title, subtitle, source) {
  const sheet = workbook.worksheets.getItemAt(index);
  sheet.showGridLines = false;
  sheet.tabColor = colors[index];
  sheet.getRange('A2').values = [[title]];
  sheet.getRange('A2').format.font = { name: 'Arial', size: 16, bold: true, color: colors[index] };
  sheet.getRange('A3').values = [[subtitle]];
  sheet.getRange('A3').format.font = { name: 'Arial', size: 10, color: '#374151' };
  if (source) {
    sheet.getRange('A4').values = [[`数据源：${source}`]];
    sheet.getRange('A4').format.font = { name: 'Arial', size: 10, italic: true, color: '#667085' };
  }
  sheet.getRange('A2:Q2').format.borders = { bottom: { style: 'thin', color: '#D5D9E0' } };
  return sheet;
}

function table(sheet, index, headers, rows, widths, heights = 28) {
  const last = columns(headers.length), end = rows.length + 6;
  const range = sheet.getRange(`A6:${last}${end}`);
  range.values = [headers, ...rows];
  range.format.font = { name: 'Arial', size: 11, color: '#1F2937' };
  range.format.verticalAlignment = 'center';
  range.format.rowHeight = heights;
  range.format.wrapText = true;
  sheet.getRange(`A6:${last}6`).format.fill = colors[index];
  sheet.getRange(`A6:${last}6`).format.font = { name: 'Arial', size: 11, color: '#FFFFFF', bold: true };
  sheet.getRange(`A6:${last}6`).format.horizontalAlignment = 'center';
  sheet.getRange(`A6:${last}6`).format.rowHeight = 34;
  widths.forEach((width, col) => sheet.getRange(`${columns(col + 1)}6:${columns(col + 1)}${end}`).format.columnWidth = width);
  for (let row = 7; row <= end; row++) {
    if (row % 2 === 0) sheet.getRange(`A${row}:${last}${row}`).format.fill = '#F2F4F7';
  }
  const excelTable = sheet.tables.add(`A6:${last}${end}`, true, `Recipes${index}`);
  excelTable.style = 'TableStyleLight1';
  excelTable.showFilterButton = true;
  sheet.freezePanes.freezeRows(6);
  sheet.freezePanes.freezeColumns(index === 2 ? 2 : 1);
  return { end, last };
}

const ordinaryRows = conversion.groups.map(group => {
  const tier = { piglin: '黑金、末地、幽匿', end: '末地、幽匿', sculk: '幽匿' }[group.tier];
  const fee = group.tier === 'piglin'
    ? `黑金：${conversion.settings.piglin.cost_n} ${nameOf(conversion.settings.piglin.cost)}\n末地：${conversion.settings.end.charge_per_batch} 相位\n幽匿：${ordinarySculkSouls} 可用灵魂`
    : group.tier === 'end'
      ? `末地：${conversion.settings.end.charge_per_batch} 相位\n幽匿：${ordinarySculkSouls} 可用灵魂`
      : `幽匿：${ordinarySculkSouls} 可用灵魂`;
  const members = group.items.map(nameOf);
  const lines = [];
  let currentLine = [], currentWidth = 0;
  for (const member of members) {
    // Budget full-width Chinese glyphs rather than just item count, so long
    // pressure-plate names do not wrap into an unaccounted extra row.
    const width = [...member].reduce((sum, char) => sum + (char.codePointAt(0) > 127 ? 1 : 0.55), 0);
    if (currentLine.length && (currentLine.length >= 4 || currentWidth + width + 1 > 32)) {
      lines.push(currentLine.join('、'));
      currentLine = [];
      currentWidth = 0;
    }
    currentLine.push(member);
    currentWidth += width + (currentLine.length > 1 ? 1 : 0);
  }
  if (currentLine.length) lines.push(currentLine.join('、'));
  return [group.id, group.cat, group.name, tier, group.batch, '1 → 1', fee,
    group.items.length, lines.join('\n'), group.enabled ? '启用' : '停用',
    group.tier === 'piglin' ? operationSeconds.piglin : null,
    group.tier !== 'sculk' ? operationSeconds.end : null,
    operationSeconds.sculk];
});
const ordinary = setup(1, '普通转换', '同组物品按 1:1 互转，不能转成自身。黑金随机抽取，其余两台可选目标。费用按每批收取。',
  'src/main/resources/convert_table/default_recipes.json · groups（由规划 TOML 导出）');
table(ordinary, 1, ['配方组 ID', '分类', '配方组', '可用转换台', '每批上限', '数量比例', '每批费用', '成员数', '组内物品', '状态', '黑金秒/次', '末地秒/次', '幽匿秒/次'],
  ordinaryRows, [18, 12, 20, 22, 12, 12, 25, 10, 76, 10, 12, 12, 12], 64);
ordinaryRows.forEach((row, i) => {
  ordinary.getRange(`A${i + 7}:M${i + 7}`).format.rowHeight = Math.max(64, row[8].split('\n').length * 18 + 8);
});
ordinary.getRange(`E7:E${ordinaryRows.length + 6}`).setNumberFormat('0');
ordinary.getRange(`H7:H${ordinaryRows.length + 6}`).setNumberFormat('0');
for (const col of ['E', 'H', 'K', 'L', 'M']) {
  ordinary.getRange(`${col}7:${col}${ordinaryRows.length + 6}`).setNumberFormat('0');
  ordinary.getRange(`${col}7:${col}${ordinaryRows.length + 6}`).format.horizontalAlignment = 'center';
}

const advancedRows = conversion.advanced.map(recipe => [recipe.id, recipe.name,
  nameOf(recipe.input), recipe.input_n, nameOf(recipe.catalyst), recipe.catalyst_n ?? 0,
  nameOf(recipe.output), recipe.output_n,
  recipe.outputs ? recipe.outputs.map(nameOf).join('、') : '无（定向）', recipe.deaths, 0,
  recipe.returns.map(r => `${nameOf(r.item)} × ${r.count}`).join('、') || '无',
  recipe.auto ? '允许连续/容器' : '仅投入槽手动', recipe.enabled ? '启用' : '停用',
  recipe.input, recipe.catalyst ?? '', recipe.output, operationSeconds.sculk]);
const advanced = setup(2, '幽匿进阶', '催化材料和可用灵魂按次消耗；相位费用为 0。存在候选池时，产物列保留主产物并列出全部候选。仅手动配方请使用投入槽。',
  'src/main/resources/convert_table/default_recipes.json · advanced（珊瑚已展开为具体品种）');
table(advanced, 2, ['配方 ID', '配方', '主材料', '数量', '催化材料', '数量', '主产物', '数量', '随机产物（全量）', '可用灵魂', '相位', '返还', '操作方式', '状态', '主材料 ID', '催化材料 ID', '主产物 ID', '用时（秒/操作）'],
  advancedRows, [21, 26, 23, 8, 20, 8, 24, 8, 56, 12, 8, 19, 22, 10, 43, 43, 43, 14], 48);
for (const col of ['D', 'F', 'H', 'J', 'K', 'R']) {
  advanced.getRange(`${col}7:${col}${advancedRows.length + 6}`).setNumberFormat('0');
  advanced.getRange(`${col}7:${col}${advancedRows.length + 6}`).format.horizontalAlignment = 'center';
}

const growthRows = growth.recipes.map(recipe => {
  const segment = recipe.id.split('/')[1];
  const category = { wood: '木材', stone: '石材', nether: '下界', sand: '沙类', snow: '冰雪', soil: '泥土', color: '彩色方块', plant: '植物', flower: '花卉', mushroom: '蘑菇' }[segment] ?? segment;
  return [category, nameOf(recipe.catalyst), nameOf(recipe.output), recipe.cost,
    recipe.id, recipe.catalyst, recipe.output, nameOf(recipe.source), 1, recipe.source];
});
const growing = setup(3, '触媒增殖', '放入触媒和一件目标物品作源本，两者都不消耗；每次产出 1 件。小/中/大/成熟晶芽自然内涵量为 16/1000、8/1000、4/1000、1/1000；每个未成熟晶芽基础供给 1/1000 因子/秒；每块方解石再加 1/1000，可叠加。产速 = 分配的生长因子/秒 ÷ 单件成本。',
  'src/main/resources/data/convert_table/growth_recipes.json');
table(growing, 3, ['分类', '触媒（不消耗）', '产物', '生长因子/件', '配方 ID', '触媒 ID', '产物 ID', '源本（不消耗）', '源本数量', '源本 ID'],
  growthRows, [14, 24, 26, 16, 63, 38, 46, 26, 12, 46], 28);
growing.getRange(`I7:I${growthRows.length + 6}`).setNumberFormat('0');
growing.getRange(`D7:D${growthRows.length + 6}`).setNumberFormat('0');
growing.getRange(`D7:D${growthRows.length + 6}`).format.horizontalAlignment = 'center';

const craftingRows = Object.entries(crafting).map(([filename, recipe]) => {
  let layout, ingredients;
  if (recipe.type === 'minecraft:crafting_shaped') {
    const counts = new Map();
    for (const letter of recipe.pattern.join('')) if (letter !== ' ')
      counts.set(recipe.key[letter], (counts.get(recipe.key[letter]) ?? 0) + 1);
    layout = recipe.pattern.map(row => row.replaceAll(' ', '·')).join('\n');
    ingredients = Object.entries(recipe.key).map(([letter, id]) => `${letter} = ${nameOf(id)} × ${counts.get(id)}`);
  } else {
    const counts = new Map();
    for (const id of recipe.ingredients) counts.set(id, (counts.get(id) ?? 0) + 1);
    layout = '无序合成\n任意工作台格';
    ingredients = [...counts].map(([id, count]) => `${nameOf(id)} × ${count}`);
  }
  return [nameOf(recipe.result.id), layout, ingredients.join('\n'), recipe.result.count, filename, recipe.result.id];
});
const crafts = setup(4, '方块合成', '在工作台按图案摆放；“·”表示空格。材料列给出每次合成的总用量。',
  'src/main/resources/data/convert_table/recipe/*.json');
table(crafts, 4, ['方块', '工作台图案', '材料及总数量', '产出数量', '配方文件', '方块 ID'],
  craftingRows, [27, 20, 59, 13, 38, 45], 118);
crafts.getRange(`B7:B${craftingRows.length + 6}`).format.horizontalAlignment = 'center';
crafts.getRange(`D7:D${craftingRows.length + 6}`).setNumberFormat('0');
crafts.getRange(`D7:D${craftingRows.length + 6}`).format.horizontalAlignment = 'center';

const overview = setup(0, `ConvertTable v${data.version} 配方`, '默认配方展示。服务器修改配方后，以游戏内实际目录为准。Excel 由源文件生成。');
overview.getRange('A6:C11').values = [
  ['配方类型', '数量', '查看方式'],
  ['普通转换组', null, '普通转换：按分类、可用台、物品筛选'],
  ['幽匿进阶配方', null, '幽匿进阶：主材、催化、可用灵魂、相位、返还'],
  ['触媒增殖配方', null, '触媒增殖：触媒、产物、生长因子/件'],
  ['触媒种类', data.counts.catalysts, '同一触媒可提供多个目标'],
  ['方块合成', null, '方块合成：工作台图案和材料总数'],
];
overview.getRange('B7').formulas = [[`=COUNTA('普通转换'!A7:A${ordinaryRows.length + 6})`]];
overview.getRange('B8').formulas = [[`=COUNTA('幽匿进阶'!A7:A${advancedRows.length + 6})`]];
overview.getRange('B9').formulas = [[`=COUNTA('触媒增殖'!E7:E${growthRows.length + 6})`]];
overview.getRange('B11').formulas = [[`=COUNTA('方块合成'!A7:A${craftingRows.length + 6})`]];
overview.getRange('A6:C11').format.font = { name: 'Arial', size: 11, color: '#1F2937' };
overview.getRange('A6:C11').format.rowHeight = 32;
overview.getRange('A6:C11').format.verticalAlignment = 'center';
overview.getRange('A6:C6').format.fill = colors[0];
overview.getRange('A6:C6').format.font = { name: 'Arial', size: 11, color: '#FFFFFF', bold: true };
overview.getRange('A6:A20').format.columnWidth = 25;
overview.getRange('B6:B20').format.columnWidth = 12;
overview.getRange('C6:C20').format.columnWidth = 74;
overview.getRange('B7:B11').setNumberFormat('0');
overview.getRange('A14:C19').values = [
  ['使用要点', '黑金', '同组随机转换；支持投入槽与连接输入容器，每批花金粒。'],
  ['使用要点', '连续', '开启后材料与费用足够时自动开始下一项；输出堵塞时会保留完成进度并等待出料，交付时才扣费。'],
  ['使用要点', '进度', '单次操作固定用时：黑金 4 秒、末地 2 秒、幽匿 1 秒；界面显示进度动画。'],
  ['使用要点', '幽匿', `普通每批消耗 ${ordinarySculkSouls} 个可用灵魂；进阶按配方消耗可用灵魂，均不消耗相位。`],
  ['使用要点', '增殖', '触媒＋一件目标物品作源本，两者保留。晶脉没有范围、节点、母岩或总产速上限；多个增殖台共用供给。'],
  ['使用要点', '灵魂来源', '合格死亡的 1 XP = 32 灵魂；0 XP 不提供；默认无需玩家击杀；每台上限 4096。'],
];
overview.getRange('A14:C19').format.font = { name: 'Arial', size: 11, color: '#1F2937' };
overview.getRange('A14:C19').format.rowHeight = 30;
overview.getRange('A14:C19').format.verticalAlignment = 'center';
overview.getRange('C14:C19').format.wrapText = true;
overview.getRange('A15:C15').format.rowHeight = 54;
overview.getRange('A19:C19').format.rowHeight = 54;
overview.getRange('A18:C18').format.rowHeight = 54;

const fishingUses = {
  'convert_table:boughbound_reverie': '陆栖、飞行及自然生命；远古种子抽卡',
  'convert_table:stillwater_palimpsest': '水栖生命塑形',
  'convert_table:unbroken_cognizance': '村民与流浪商人塑形',
  'convert_table:unwrought_facet': '灵异、亡灵与构造生命塑形',
  'minecraft:amethyst_shard': '自然材料增殖的触媒；其他紫水晶用途',
};
overview.getRange('A21').values = [['钓鱼材料']];
overview.getRange('A21').format.font = {name:'Arial',size:14,bold:true,color:colors[0]};
overview.getRange('A22:G27').values = [
  ['材料','获取池','用途','追加权重','参考钓获概率','最少数量','最多数量'],
  ...fishing.entries.map(entry => [nameOf(entry.item),entry.table==='fish'?'鱼类':'宝藏',fishingUses[entry.item],entry.weight,null,entry.min,entry.max]),
];
overview.getRange('A22:G27').format.font = {name:'Arial',size:11,color:'#1F2937'};
overview.getRange('A22:G27').format.verticalAlignment = 'center';
overview.getRange('A22:G27').format.rowHeight = 48;
overview.getRange('A22:C27').format.wrapText = true;
overview.getRange('A22:G22').format.fill = colors[0];
overview.getRange('A22:G22').format.font = {name:'Arial',size:11,bold:true,color:'#FFFFFF'};
overview.getRange('D22:G22').format.wrapText = true;
for (const [col,width] of [['D',14],['E',18],['F',12],['G',12]]) overview.getRange(`${col}22:${col}28`).format.columnWidth = width;
overview.getRange('D23:D27').setNumberFormat('0');
overview.getRange('E23:E27').setNumberFormat('0.00%');
overview.getRange('F23:G27').setNumberFormat('0');
overview.getRange('D23:G27').format.horizontalAlignment = 'right';
overview.getRange('I22:L24').values = [
  ['原版参考','分类概率','原分类权重','追加后总权重'],
  ['鱼类',fishing.vanilla_reference.fish_chance,fishing.vanilla_reference.fish_weight,null],
  ['宝藏',fishing.vanilla_reference.treasure_chance,fishing.vanilla_reference.treasure_weight,null],
];
overview.getRange('I22:L24').format.font = {name:'Arial',size:10,color:'#374151'};
overview.getRange('I22:L24').format.columnWidth = 20;
overview.getRange('I22:L24').format.rowHeight = 30;
overview.getRange('J23:J24').setNumberFormat('0%');
overview.getRange('K23:L24').setNumberFormat('0');
overview.getRange('L23').formulas = [['=K23+SUMIFS(D23:D27,B23:B27,I23)']];
overview.getRange('L24').formulas = [['=K24+SUMIFS(D23:D27,B23:B27,I24)']];
for(let index=0;index<fishing.entries.length;index++) {
  const row=index+23, reference=fishing.entries[index].table==='fish'?23:24;
  overview.getRange(`E${row}`).formulas = [[`=$J$${reference}*D${row}/$L$${reference}`]];
}
overview.getRange('A29').values = [['概率按原版开放水域、无海眷计算；附魔和其他模组可改变实际钓获比例。宝藏仍受开放水域条件约束。']];
overview.getRange('A29').format.font = {name:'Arial',size:10,italic:true,color:'#667085'};

workbook.recalculate();
const summary = await workbook.inspect({ kind: 'table', range: '配方总览!A6:C11', include: 'values,formulas', tableMaxRows: 6, tableMaxCols: 3, maxChars: 2500 });
console.log(summary.ndjson);
const errorScan = await workbook.inspect({ kind: 'match', searchTerm: '#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A|#NUM!|#NULL!|#SPILL!|#CALC!',
  options: { useRegex: true, maxResults: 30 }, maxChars: 1800 });
console.log(errorScan.ndjson);
const expected = [data.counts.groups, data.counts.advanced, data.counts.growth, data.counts.catalysts, data.counts.crafting];
const actual = overview.getRange('B7:B11').values.flat();
if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new Error(`Summary mismatch: ${JSON.stringify(actual)} != ${JSON.stringify(expected)}`);
await fs.mkdir(outputDir, { recursive: true });
// Preview full-width representative rows on every sheet; no extra workbook variants.
const previews = [['A1:G29', 'overview'], ['A1:M11', 'ordinary'], ['A1:R15', 'advanced'], ['A1:J13', 'growth'], ['A1:F13', 'crafting']];
for (let i = 0; i < sheets.length; i++) {
  const [range, label] = previews[i];
  const preview = await workbook.render({ sheetName: sheets[i], range, scale: 1.25, format: 'png' });
  await fs.writeFile(path.join(outputDir, `preview-${label}.png`), new Uint8Array(await preview.arrayBuffer()));
}
const outputPoolIndex = advancedRows.findIndex(row => row[8].includes('、'));
if (outputPoolIndex >= 0) {
  const poolRow = outputPoolIndex + 7;
  const preview = await workbook.render({ sheetName: '幽匿进阶', range: `A${poolRow - 2}:R${poolRow + 2}`, scale: 1.25, format: 'png' });
  await fs.writeFile(path.join(outputDir, 'preview-advanced-output-pool.png'), new Uint8Array(await preview.arrayBuffer()));
}
const longNames = await workbook.render({ sheetName: '普通转换', range: 'A15:M18', scale: 1.25, format: 'png' });
await fs.writeFile(path.join(outputDir, 'preview-ordinary-long-names.png'), new Uint8Array(await longNames.arrayBuffer()));
const magicRow = growthRows.findIndex(row => row[5] === 'minecraft:amethyst_shard') + 7;
const magicPreview = await workbook.render({ sheetName: '触媒增殖', range: `A${magicRow - 1}:G${magicRow + 11}`, scale: 1.25, format: 'png' });
await fs.writeFile(path.join(outputDir, 'preview-growth-magical-catalysts.png'), new Uint8Array(await magicPreview.arrayBuffer()));
const file = await SpreadsheetFile.exportXlsx(workbook);
await file.save(path.join(outputDir, 'ConvertTable配方.xlsx'));
console.log('Exported all five recipe sheets.');
