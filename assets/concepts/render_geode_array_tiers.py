"""Render the contribution of one budding-amethyst growth face."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


OUT = Path(__file__).with_name("geode_array_contribution_tiers.png")
FONT = Path(r"C:\Windows\Fonts\msyh.ttc")
FONT_BOLD = Path(r"C:\Windows\Fonts\msyhbd.ttc")


def face(size, bold=False):
    return ImageFont.truetype(str(FONT_BOLD if bold else FONT), size)


im = Image.new("RGB", (1280, 610), "#181a2c")
d = ImageDraw.Draw(im)
d.rounded_rectangle((35, 25, 1245, 585), radius=26, fill="#23243b", outline="#7c6a9a", width=2)
d.text((66, 50), "一条生长臂，贡献怎么算？", font=face(35, True), fill="#faf5ff")
d.text((66, 100), "从母岩往晶芽伸出的方向，看后面有没有方解石和平滑玄武岩。", font=face(20), fill="#d0c5df")

rows = [
    ("基础", 1, 16, False, False),
    ("加方解石", 2, 32, True, False),
    ("两层齐全", 4, 64, True, True),
]
for i, (name, score, output, calcite, basalt) in enumerate(rows):
    y = 155 + i * 140
    d.rounded_rectangle((63, y - 10, 1214, y + 112), radius=15, fill="#303047", outline="#6e6384", width=1)
    d.text((87, y + 14), name, font=face(25, True), fill="#ebd7ff")
    x0, side, gap = 326, 86, 22
    boxes = [
        (x0, "#7742ac", "母岩", "#ffffff"),
        (x0 + side + gap, "#373047", "", "#ffffff"),
    ]
    if calcite:
        boxes.append((x0 + 2 * (side + gap), "#ece9df", "方解石", "#302d37"))
    if basalt:
        boxes.append((x0 + 3 * (side + gap), "#494953", "平滑\n玄武岩", "#ffffff"))
    for xx, bg, label, fg in boxes:
        d.rectangle((xx, y, xx + side, y + side), fill=bg, outline="#998ba9", width=2)
        if label:
            bb = d.multiline_textbbox((0, 0), label, font=face(17, True), align="center")
            tw, th = bb[2] - bb[0], bb[3] - bb[1]
            d.multiline_text((xx + (side - tw) / 2 - bb[0], y + (side - th) / 2 - bb[1]), label, font=face(17, True), fill=fg, align="center")
    xx = x0 + side + gap
    d.polygon([(xx + 43, y + 10), (xx + 57, y + 45), (xx + 43, y + 72), (xx + 29, y + 45)], fill="#ba81e7", outline="#efdbff")
    d.text((xx + 28, y + 75), "留空", font=face(13), fill="#f4e8ff")
    for j in range(1 + int(calcite) + int(basalt)):
        ax = x0 + side + j * (side + gap)
        d.text((ax + 2, y + 27), "→", font=face(25, True), fill="#dfc9f4")
    d.text((900, y + 5), f"贡献 {score}", font=face(28, True), fill="#f2dbff")
    d.text((900, y + 47), f"8 材料＋成熟晶簇 → {output}", font=face(18), fill="#dbd4e5")

d.text((67, 558), "每面独立计算；紫水晶块只负责连接，不增加贡献。", font=face(17), fill="#c5bbd6")
im.save(OUT)
print(OUT)
