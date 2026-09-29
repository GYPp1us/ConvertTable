"""Render the exact, buildable top-down array diagram."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


OUT = Path(__file__).with_name("geode_array_topdown_exact.png")
FONT = Path(r"C:\Windows\Fonts\msyh.ttc")
FONT_BOLD = Path(r"C:\Windows\Fonts\msyhbd.ttc")
W, H = 1280, 740
CELL = 76
LEFT, TOP = 83, 125


def font(size: int, bold: bool = False):
    return ImageFont.truetype(str(FONT_BOLD if bold else FONT), size)


def centered(draw, box, label, face, fill):
    x0, y0, x1, y1 = box
    bounds = draw.multiline_textbbox((0, 0), label, font=face, align="center", spacing=0)
    tw, th = bounds[2] - bounds[0], bounds[3] - bounds[1]
    x = (x0 + x1 - tw) / 2 - bounds[0]
    y = (y0 + y1 - th) / 2 - bounds[1]
    draw.multiline_text((x, y), label, font=face, fill=fill, align="center", spacing=0)


im = Image.new("RGB", (W, H), "#17182b")
d = ImageDraw.Draw(im)
d.rounded_rectangle((37, 26, 1243, 713), radius=25, fill="#22233a", outline="#72658d", width=2)
d.text((65, 50), "晶洞阵法 · 可照摆的俯视图", font=font(35, True), fill="#f6f1ff")
d.text((65, 89), "同一高度的一层。每格代表 Minecraft 的一个方块位置。", font=font(18), fill="#c9bfd7")
d.rounded_rectangle((65, 113, 633, 681), radius=18, fill="#2e3145", outline="#82769c", width=2)

tiles = {
    (-2, 0): ("水晶台", "#e5dfd8", "#352d43"),
    (-1, 0): ("紫水\n晶块", "#ad7cd7", "#251235"),
    (0, 0): ("母岩", "#7842ad", "#ffffff"),
    (0, -1): ("留空", "#343044", "#f1e5ff"),
    (0, -2): ("方解石", "#ece9df", "#36343a"),
    (0, -3): ("平滑\n玄武岩", "#464654", "#ffffff"),
    (1, 0): ("留空", "#343044", "#f1e5ff"),
    (2, 0): ("方解石", "#ece9df", "#36343a"),
    (3, 0): ("平滑\n玄武岩", "#464654", "#ffffff"),
    (0, 1): ("留空", "#343044", "#f1e5ff"),
    (0, 2): ("方解石", "#ece9df", "#36343a"),
    (0, 3): ("平滑\n玄武岩", "#464654", "#ffffff"),
}
for z in range(-3, 4):
    for x in range(-3, 4):
        px, py = LEFT + (x + 3) * CELL, TOP + (z + 3) * CELL
        box = (px, py, px + CELL, py + CELL)
        if (x, z) in tiles:
            label, bg, fg = tiles[(x, z)]
            d.rectangle(box, fill=bg, outline="#8a809e", width=2)
            if label == "留空":
                d.polygon(
                    [(px + 38, py + 12), (px + 49, py + 35), (px + 38, py + 56), (px + 27, py + 35)],
                    fill="#b77ee5",
                    outline="#ead6ff",
                )
                centered(d, (px, py + 53, px + CELL, py + CELL), label, font(14), fg)
            else:
                centered(d, box, label, font(17 if "\n" not in label else 16, True), fg)
        else:
            d.rectangle(box, fill="#333649", outline="#77748c", width=1)

d.text((286, 686), "← 西         东 →", font=font(19), fill="#d0c6e3")
d.rounded_rectangle((696, 129, 1219, 659), radius=20, fill="#2e293e", outline="#8f7caa", width=2)
d.text((727, 150), "摆放顺序", font=font(29, True), fill="#f4ebff")
steps = [
    ("① 连线", "水晶台 → 紫水晶块 → 母岩"),
    ("② 每条生长臂", "母岩 → 留空 → 方解石 → 平滑玄武岩"),
    ("③ 留空格", "不放方块；晶芽由母岩自然长出来"),
    ("④ 本例贡献", "北、东、南各 4；本层可见贡献共 12"),
]
for i, (title, detail) in enumerate(steps):
    y = 210 + i * 94
    d.text((727, y), title, font=font(22, True), fill="#dab6ff")
    d.text((727, y + 35), detail, font=font(19), fill="#f2eef5")
d.line((724, 591, 1190, 591), fill="#827190", width=1)
d.text((727, 603), "上下两个生长面未在俯视图中显示。", font=font(16), fill="#c9bfd7")
d.text((727, 632), "这是机制策划示例，尚未加入游戏。", font=font(16), fill="#c9bfd7")
d.text((643, 387), "北 ↑", font=font(16), fill="#d0c6e3")
d.text((643, 419), "南 ↓", font=font(16), fill="#d0c6e3")
im.save(OUT)
print(OUT)
