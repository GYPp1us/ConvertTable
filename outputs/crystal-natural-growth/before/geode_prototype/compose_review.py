"""Compose the actual Blender renders into a before / after / emission review."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
OUT = HERE.parents[1] / 'outputs/crystal-relief-refinement'
BEFORE = OUT / 'before/geode_prototype'
OUT.mkdir(parents=True, exist_ok=True)
font_path = 'C:/Windows/Fonts/msyh.ttc'
title = ImageFont.truetype(font_path, 34)
label = ImageFont.truetype(font_path, 25)
small = ImageFont.truetype(font_path, 21)
sheet = Image.new('RGB', (1800, 864), '#181a23')
draw = ImageDraw.Draw(sheet)
draw.text((28, 22), '紫水晶 · 灰纹沉底，晶纹凸起', font=title, fill='#eee9fa')
draw.text((29, 72), '四角包边 ①  /  上下横边与紫晶浮雕 ②  /  矿岩基底与灰色底纹 ③',
          font=small, fill='#b7abc9')
panels = [
    ('修改前 · 灰色石雕凸起', BEFORE / 'geode_three_quarter.png'),
    ('修改后 · 紫晶浮雕', HERE / 'geode_three_quarter.png'),
    ('暗光检查 · 无辉光后处理', HERE / 'geode_dark.png'),
]
for index, (caption, path) in enumerate(panels):
    x = index * 600
    draw.text((x + 28, 122), caption, font=label, fill='#e0d8ed')
    with Image.open(path) as source:
        sheet.paste(source.convert('RGB').resize((580, 580), Image.Resampling.LANCZOS), (x + 10, 165))
draw.line((28, 771, 1772, 771), fill='#454052', width=1)
draw.text((28, 793), '原生 16×16 纹理  ·  灰色浅刻底纹 + 一体凸起晶脉  ·  深紫晶面 / 银紫亮点',
          font=small, fill='#c9bfd6')
sheet.save(OUT / 'comparison.png')
print(OUT / 'comparison.png')
