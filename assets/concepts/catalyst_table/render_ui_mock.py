"""Deterministic UI layout concept for the mother-rock catalyst table."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


OUT = Path(__file__).with_name("catalyst_table_ui_mock_v1.png")
FONT = Path(r"C:\Windows\Fonts\msyh.ttc")
FONT_BOLD = Path(r"C:\Windows\Fonts\msyhbd.ttc")
W, H = 1280, 820


def face(size, bold=False):
    return ImageFont.truetype(str(FONT_BOLD if bold else FONT), size)


im = Image.new("RGB", (W, H), "#181b25")
d = ImageDraw.Draw(im)


def box(x0, y0, x1, y1, fill, border="#85828d", width=2):
    d.rectangle((x0, y0, x1, y1), fill=fill, outline=border, width=width)


def txt(x, y, label, size=18, color="#eeeaf4", bold=False):
    d.text((x, y), label, font=face(size, bold), fill=color)


def button(x0, y0, x1, y1, label, selected=False):
    box(x0, y0, x1, y1, "#604877" if selected else "#454651", "#d1b4e4" if selected else "#aaa8af")
    bb = d.textbbox((0, 0), label, font=face(18, selected))
    txt(x0 + (x1 - x0 - bb[2] + bb[0]) / 2, y0 + 7, label, 18, "#fffaff", selected)


def item_slot(x, y, sample=False, ghost=False):
    box(x, y, x + 64, y + 64, "#24232d" if not ghost else "#302a39", "#cbb7d8")
    if sample:
        d.rectangle((x + 12, y + 12, x + 52, y + 52), fill="#865936", outline="#c99b58", width=3)
        d.rectangle((x + 21, y + 21, x + 43, y + 43), outline="#dfbf7d", width=2)
        d.rectangle((x + 27, y + 27, x + 37, y + 37), outline="#5d3b27", width=2)
    if ghost:
        d.rectangle((x + 13, y + 13, x + 51, y + 51), outline="#8b7b93", width=2)


# Existing-family gray Minecraft inventory panels with mineral accents.
box(48, 45, 817, 791, "#c6c3c9", "#efe9ef", 4)
box(51, 48, 814, 788, "#54535d", "#302f38", 4)
box(839, 45, 1231, 791, "#c6c3c9", "#efe9ef", 4)
box(842, 48, 1228, 788, "#41424e", "#302f38", 4)
txt(76, 67, "母岩触媒台", 28, "#fff9ff", True)
txt(673, 75, "运行中", 17, "#d7f1c2")
box(76, 112, 789, 239, "#34343d", "#77717f")
txt(91, 121, "触媒样本", 16, "#d6cadb")
item_slot(91, 151, sample=True)
txt(168, 151, "橡木原木", 24, "#fff8ff", True)
txt(168, 188, "保留样本 · 自动识别配方", 16, "#c6c0cb")
txt(445, 147, "→", 38, "#e2c2ef", True)
txt(520, 121, "产物预览", 16, "#d6cadb")
item_slot(520, 151, sample=True)
txt(595, 164, "橡木原木", 19, "#fff8ff")
txt(595, 193, "每个消耗 1 点", 16, "#c6c0cb")

box(76, 253, 789, 350, "#302b3c", "#826b99")
txt(92, 267, "可用生长元素", 16, "#d9c1e8")
txt(92, 292, "12 / 秒", 29, "#f3d5ff", True)
txt(334, 267, "当前复制速度", 16, "#d9c1e8")
txt(334, 292, "12 个 / 秒", 29, "#f4f0f7", True)
txt(628, 267, "配方费用", 16, "#d9c1e8")
txt(628, 300, "1 / 个", 20, "#f4f0f7", True)

box(76, 363, 789, 427, "#34343d", "#77717f")
txt(91, 373, "输出至", 16, "#d6cadb")
txt(91, 397, "北侧箱子", 20, "#fff9ff", True)
txt(300, 397, "剩余空间约 512 个", 16, "#c6c0cb")
button(643, 376, 774, 415, "更换容器")

button(76, 440, 264, 488, "暂停", True)
box(284, 444, 602, 484, "#393741", "#77717f")
txt(300, 450, "持续复制", 18, "#eeeaf4")
box(518, 449, 583, 478, "#766092", "#d7b8ef")
txt(526, 449, "开启", 16, "#ffffff", True)
txt(636, 450, "已产出 1843", 17, "#d6cadb")

txt(89, 511, "物品栏", 17, "#fff9ff")
inventory_x, cell, step = 202, 48, 51
for row in range(3):
    for col in range(9):
        x, y = inventory_x + col * step, 540 + row * step
        box(x, y, x + cell, y + cell, "#282831", "#8b8791", 1)
txt(89, 720, "快捷栏", 17, "#fff9ff")
for col in range(9):
    x = inventory_x + col * step
    box(x, 716, x + cell, 764, "#282831", "#8b8791", 1)

# Optional drawer. It is closed by default in the implementation concept.
txt(861, 67, "晶洞状态", 25, "#fff9ff", True)
button(1143, 66, 1209, 101, "收起")
box(861, 119, 1209, 225, "#2b2d39", "#796d8d")
txt(876, 130, "已连接母岩", 16, "#d4c8dd")
txt(876, 159, "3", 30, "#f1d4ff", True)
txt(1038, 130, "活跃晶芽", 16, "#d4c8dd")
txt(1038, 159, "12", 30, "#f1d4ff", True)
txt(875, 198, "普通紫水晶块连接 · 区块已加载", 14, "#bdb8c7")
box(861, 239, 1209, 368, "#2b2d39", "#796d8d")
txt(876, 250, "晶芽阶段", 17, "#eeeaf4", True)
for i, (stage, count, tone) in enumerate((("小", "6", "#bb8ce6"), ("中", "4", "#9c75c9"), ("大", "2", "#7859a9"), ("簇", "0", "#5b4c70"))):
    x = 876 + i * 81
    box(x, 291, x + 69, 347, "#343141", tone)
    txt(x + 10, 298, stage, 16, "#eeeaf4")
    txt(x + 40, 300, count, 18, "#ffffff", True)
box(861, 382, 1209, 531, "#2b2d39", "#796d8d")
txt(876, 391, "石层影响", 17, "#eeeaf4", True)
txt(876, 429, "方解石：2 块母岩", 17, "#f0edf2")
txt(876, 458, "平滑玄武岩：1 块母岩", 17, "#f0edf2")
txt(876, 493, "悬停可看每块母岩的贡献", 14, "#bdb8c7")
box(861, 545, 1209, 700, "#2b2d39", "#796d8d")
txt(876, 555, "连接提示", 17, "#eeeaf4", True)
txt(876, 596, "另有 1 块母岩未接通", 17, "#f0d8a9")
txt(876, 628, "查看阵法可定位断点", 15, "#c6c0cb")
button(877, 711, 1194, 757, "查看阵法")
im.save(OUT)
print(OUT)
