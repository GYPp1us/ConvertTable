import {execFileSync} from 'node:child_process';
import fs from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {FileBlob,SpreadsheetFile} from '@oai/artifact-tool';
const root=fileURLToPath(new URL('.',import.meta.url));
const output=root+'outputs/01a09e81-7dc7-7742-a282-bfc6b07daf6b/';
await fs.mkdir(output,{recursive:true});
const wb=await SpreadsheetFile.importXlsx(await FileBlob.load('D:/Desktop/Minecraft_26.3_转换台配方策划_v1.3.xlsx'));
const overview=wb.worksheets.getItem('总览');
overview.getRange('A1').values=[['Minecraft 26.3 转换台配方策划 · v1.4']];
overview.getRange('E7').values=[['普通：相位充能\n高级：相位＋死亡次数']];
overview.getRange('E7').format.wrapText=true;
overview.getRange('A7:K7').format.rowHeight=40;
const advanced=wb.worksheets.getItem('幽匿高级配方');
advanced.getRange('A2').values=[['高级配方可声明方向、催化物、同种类映射。原 XP 改为死亡次数，每次有效生物死亡 +1；费用数字暂沿用原值，待玩法平衡。高级每次另消耗 1 点相位（建议）。规则详见 UI 与幽匿计数策划 v1.4。']];
advanced.getRange('A2').format.wrapText=true;
advanced.getRange('A2:N2').format.rowHeight=42;
advanced.getRange('J4').values=[['死亡次数']];
advanced.getRange('J4:J44').format.columnWidth=13;
advanced.getRange('M21').values=[['鸡蛋 + 生物样本 + 死亡计数。']];
const rules=wb.worksheets.getItem('规则与排除');
rules.getRange('A17:E25').values=[
 ['幽匿计数 v1.4','条件','建议值','费用／归属','说明'],
 ['获得计数','生物死亡时脚下支撑为与台相连的幽匿顶面',1,'每次有效死亡','玩家、盔甲架、非生物实体除外；同次死亡只结算一次'],
 ['计数库存','保存在转换台',4096,'容量上限（草案）','满额不再新增；不消耗玩家经验，不拦截经验球或原版死亡效果'],
 ['高级费用','40 条配方原数值暂作死亡次数',1,'每次另耗相位（建议）','死亡次数与 XP 不等价，后续独立测试平衡；auto/unlock 保留'],
 ['连接范围','幽匿系方块按相邻面连通',16,'曼哈顿距离上限（草案）','脉络只沿真实附着面连通；有效死亡地面只认幽匿顶面'],
 ['搜索上限','仅已加载区块',1024,'节点上限（草案）','超限／未加载显示未知；与容器连接半径 4、总数 8 分开'],
 ['共享区域','同一死亡只归属一台',1,'网络路径最近的台','距离相同按坐标稳定归属；同事件不得被多个节点／台重复计数'],
 ['提交转换','材料、相位与计数统一结算',null,'成功产出才扣除','输出满／连接中断则暂停；未提交不扣；已有库存不因地面断开而消失'],
 ['范围显示','节点、有效地面、归属与断点',null,'层高地图＋逐格世界投影','青色已连接，暗金已断开，灰色未知；保留三维高度']
];
rules.getRange('A17:E17').copyFrom(rules.getRange('A3:E3'),'all');
rules.getRange('A17:E17').values=[['幽匿计数 v1.4','条件','建议值','费用／归属','说明']];
rules.getRange('A18:E25').format.font={name:'Microsoft YaHei',size:10};
rules.getRange('A18:E25').format.wrapText=true;
rules.getRange('A18:E25').format.verticalAlignment='center';
rules.getRange('A18:E25').format.rowHeight=50;
for(const t of advanced.tables.items) console.log('TABLE_HEADER',t.name,t.getHeaderRowRange().values);
wb.recalculate();
console.log((await wb.inspect({kind:'table',range:'幽匿高级配方!I4:M8',include:'values,formulas',tableMaxRows:5,tableMaxCols:5,maxChars:2000})).ndjson);
console.log((await wb.inspect({kind:'match',searchTerm:'#REF!|#DIV/0!|#VALUE!|#NAME\\?|#NUM!|#SPILL!|#CALC!',options:{useRegex:true,maxResults:30},maxChars:1000})).ndjson);
for(const [sheetName,range,file] of [['总览','A4:H7','after-overview.png'],['幽匿高级配方','I4:M10','after-sculk.png'],['规则与排除','A17:E25','after-rules.png']]){
 const blob=await wb.render({sheetName,range,scale:1.5,format:'png'});
 await fs.writeFile(root+file,new Uint8Array(await blob.arrayBuffer()));
}
await (await SpreadsheetFile.exportXlsx(wb)).save(output+'Minecraft_26.3_转换台配方策划_v1.4.xlsx');
console.log('SAVED',output+'Minecraft_26.3_转换台配方策划_v1.4.xlsx');

execFileSync('C:/Users/GYP/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe',['-X','utf8',root+'fix_table_header.py',output+'Minecraft_26.3_转换台配方策划_v1.4.xlsx'],{stdio:'inherit'});

