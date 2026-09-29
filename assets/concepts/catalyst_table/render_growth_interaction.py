"""Compose actual game captures of the catalyst UI and crosshair hint."""
from pathlib import Path
import shutil
from PIL import Image, ImageDraw, ImageFont, ImageOps

ROOT=Path(__file__).resolve().parents[3]
HERE=Path(__file__).resolve().parent
CAPTURE=ROOT/'build/run/clientGameTest/screenshots'
files={
 'world':('0000_growth-crystal-and-pedestal.png','growth_world_game.png'),
 'crystal':('0001_growth-crystal-ui.png','growth_crystal_ui_game.png'),
 'pedestal':('0002_growth-catalyst-ui.png','growth_catalyst_ui_game.png'),
 'hint':('0003_growth-placement-hint.png','growth_placement_hint_game.png'),
}
for source,target in files.values():shutil.copy2(CAPTURE/source,HERE/target)
pictures={k:Image.open(HERE/v[1]).convert('RGB') for k,v in files.items()}
canvas=Image.new('RGB',(1920,1220),'#191c25')
draw=ImageDraw.Draw(canvas)
fontpath='C:/Windows/Fonts/msyh.ttc'
def label(x,y,text,size=24,color='#e5e1eb'):
 draw.text((x,y),text,font=ImageFont.truetype(fontpath,size),fill=color)
def panel(box):
 draw.rounded_rectangle(box,radius=12,fill='#272b37',outline='#685679',width=2)
def paste_contained(pic,box,nearest=False):
 x,y,w,h=box
 p=ImageOps.contain(pic,(w,h),method=Image.Resampling.NEAREST if nearest else Image.Resampling.LANCZOS)
 canvas.paste(p,(x+(w-p.width)//2,y+(h-p.height)//2))
label(44,20,'母岩增殖 · 晶脉状态 / 触媒目标 / 世界连接',38,'#f7efff')
label(46,76,'Minecraft 26.3 实拍 · 不消耗催化剂 · 多基座共享导出额度',22,'#baadc9')
panel((36,118,896,804));panel((920,118,1884,804))
label(58,130,'母岩增殖台：晶芽数量与实际导出',26)
label(944,130,'触媒基座：目标选择与六方向容器',26)
paste_contained(pictures['crystal'].crop((372,122,908,598)),(57,178,817,603),True)
paste_contained(pictures['pedestal'].crop((320,122,960,598)),(944,178,914,603),True)
panel((36,828,896,1166));panel((920,828,1884,1166))
label(58,841,'世界：选定产物以完整方块尺寸悬浮',25)
label(944,841,'放置：导体可用时，指针旁出现扁平像素符号 + 连接标记',25)
paste_contained(pictures['world'].crop((340,60,940,440)),(55,884,821,261))
paste_contained(pictures['hint'].crop((465,280,840,500)),(944,884,398,261),True)
label(1364,898,'基座 / 方解石 / 平滑玄武岩',21,'#e3c6f3')
label(1364,940,'连通晶脉且放置处空闲',21)
label(1364,980,'多台基座分配同一份产能',21)
label(1364,1020,'树苗触媒 → 选择原木或木板',21)
label(1364,1060,'触媒保留；产物自动进入容器',21)
label(44,1182,'内部 24 势 ≠ 每秒 24 势 · 普通每芽 1 势/秒 · 方解石每芽 2 势/秒',24,'#cab7da')
out=HERE/'growth_interaction_ui_v3.png'
canvas.save(out)
print(out)
