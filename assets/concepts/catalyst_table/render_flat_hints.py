"""Show the three flat HUD symbols from actual Minecraft screenshots."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[3]
HERE=Path(__file__).resolve().parent
CAPTURE=ROOT/'build/run/clientGameTest/screenshots'
entries=[('触媒基座','0003_growth-placement-hint.png'),
         ('方解石 · 导出','0004_growth-calcite-flat-hint.png'),
         ('平滑玄武岩 · 生长','0005_growth-basalt-flat-hint.png')]
board=Image.new('RGB',(840,220),'#191c25')
draw=ImageDraw.Draw(board)
font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',22)
for i,(title,name) in enumerate(entries):
 x=10+i*280
 draw.rounded_rectangle((x,10,x+270,210),radius=8,fill='#292635',outline='#786488',width=2)
 frame=Image.open(CAPTURE/name).convert('RGB')
 glyph=frame.crop((656,364,714,412)).resize((174,144),Image.Resampling.NEAREST)
 board.paste(glyph,(x+48,20))
 box=draw.textbbox((0,0),title,font=font)
 draw.text((x+(270-box[2])//2,174),title,font=font,fill='#ece4f4')
out=HERE/'growth_flat_pixel_hints_game.png'
board.save(out)
print(out)
