from pathlib import Path
from PIL import Image,ImageDraw,ImageFont

HERE=Path(__file__).resolve().parent
font=lambda n:ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',n)
for variant,label,caption in [
    ('black_gold','黑金转换台','浅金包角 · 黑石猪鼻 · 1px 顶面高差 · 菱形坑'),
    ('sculk','幽匿转换台','柔和灰阶包边 · 单组下层骨纹 · 清理蓝点 · 悬浮晶柱'),
]:
    root=HERE/variant/'renders'
    sheet=Image.new('RGB',(2160,835),(25,28,36));draw=ImageDraw.Draw(sheet)
    for i,(view,title) in enumerate([('front','正面'),('side','侧面'),('top','顶部')]):
        sheet.paste(Image.open(root/f'{variant}_{view}.png').convert('RGB'),(720*i,65))
        draw.text((720*i+30,20),title,font=font(28),fill=(220,218,224))
    draw.text((30,794),label+' · 16×16×16 · '+caption,font=font(23),fill=(189,194,203))
    sheet.save(root/f'{variant}_three_views.png')
comparison=Image.new('RGB',(2400,1320),(25,28,36));d=ImageDraw.Draw(comparison)
for i,(variant,label,caption) in enumerate([
    ('black_gold','黑金 / BLACK GOLD','浅金包角 · 黑石猪鼻 · 菱形雕坑'),
    ('sculk','幽匿 / SCULK','柔和灰阶包边 · 单组骨纹 · 悬浮晶柱'),
]):
    a=Image.open(HERE/variant/'renders'/f'{variant}_hero.png').convert('RGB').resize((1200,1200),Image.Resampling.LANCZOS)
    comparison.paste(a,(1200*i,70))
    d.text((45+1200*i,22),label,font=font(32),fill=(224,220,224))
    d.text((45+1200*i,1276),caption,font=font(24),fill=(179,188,199))
comparison.save(HERE/'black_gold_and_sculk.png')
print('Saved both orthographic sheets and comparison render.')
