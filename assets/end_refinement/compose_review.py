from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

HERE=Path(__file__).resolve().parent
sheet=Image.new('RGB',(2160,820),(24,26,34))
draw=ImageDraw.Draw(sheet)
try:
    title=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',28)
    small=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',20)
except OSError:
    title=small=ImageFont.load_default()
for i,(view,label) in enumerate([('front','正面'),('side','侧面'),('top','顶部')]):
    sheet.paste(Image.open(HERE/'renders'/f'end_{view}.png').convert('RGB'),(i*720,65))
    draw.text((i*720+30,20),label,font=title,fill=(219,211,231))
draw.text((30,785),'末地转换台 · 16× 原版像素密度 · 实模渲染',font=small,fill=(178,169,193))
sheet.save(HERE/'renders/end_three_views.png')
print('Review sheet saved.')
