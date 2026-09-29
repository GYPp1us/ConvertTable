import fs from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {FileBlob,SpreadsheetFile} from '@oai/artifact-tool';
const root=fileURLToPath(new URL('.',import.meta.url));
const wb=await SpreadsheetFile.importXlsx(await FileBlob.load('D:/Desktop/Minecraft_26.3_转换台配方策划_v1.3.xlsx'));
console.log((await wb.inspect({kind:'sheet',include:'id,name',maxChars:2000})).ndjson);
for(const [sheetName,range,file] of [['总览','A4:H7','before-overview.png'],['幽匿高级配方','I4:M10','before-sculk.png']]) {
const blob=await wb.render({sheetName,range,scale:1.5,format:'png'});
await fs.writeFile(root+file,new Uint8Array(await blob.arrayBuffer()));
}

