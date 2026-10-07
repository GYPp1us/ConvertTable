"""Author ConvertTable's original, hard-edged pixel advancement and tooltip art.

Run from any directory with Python and Pillow. The small integer-grid drawings
below are the editable source, with no network, extracted art, or image models.
Only this script's item models, item definitions, textures and review sheet are
written. Existing gameplay items are not registered or modified.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "src/main/resources/assets/convert_table"
REVIEW = ROOT / "assets/advancement_art"


def canvas():
    image = Image.new("RGBA", (32, 32))
    return image, ImageDraw.Draw(image)


def black_gold():
    """A revolving gold seal around a warm, irregular blackstone ember."""
    im, d = canvas()
    # Four substantial cut-metal segments, with open joints for the wheel.
    d.polygon([(9, 3), (21, 3), (26, 8), (26, 14), (22, 14),
               (22, 10), (19, 7), (10, 7), (10, 10), (4, 10), (4, 5)], fill="#362921")
    d.polygon([(10, 4), (20, 4), (25, 9), (25, 12), (23, 12),
               (23, 9), (20, 6), (9, 6), (9, 9), (5, 9), (5, 5)], fill="#B8892D")
    d.line([(10, 4), (20, 4), (24, 8)], fill="#FFE791", width=1)
    d.line([(10, 5), (20, 5), (23, 8)], fill="#E8BE54", width=1)
    d.rectangle((5, 5, 7, 7), fill="#FFE791")
    d.polygon([(5, 17), (9, 17), (9, 21), (12, 24), (22, 24),
               (22, 21), (28, 21), (28, 26), (23, 29), (11, 29), (5, 23)], fill="#362921")
    d.polygon([(6, 18), (8, 18), (8, 22), (12, 26), (23, 26),
               (23, 22), (27, 22), (27, 25), (23, 28), (12, 28), (6, 22)], fill="#AD7928")
    d.line([(7, 18), (7, 22), (12, 27), (22, 27)], fill="#E4B74B", width=1)
    d.line([(23, 22), (27, 22)], fill="#FFF0AD", width=1)
    d.rectangle((24, 23, 26, 24), fill="#E8BE54")
    # Carved charcoal medallion; warm face and cold lower/right bevel.
    d.polygon([(13, 9), (20, 9), (24, 13), (24, 19), (20, 23),
               (12, 23), (9, 20), (9, 13)], fill="#292529")
    d.polygon([(13, 10), (19, 10), (22, 13), (18, 17), (10, 17), (10, 13)], fill="#716357")
    d.polygon([(10, 18), (18, 18), (18, 22), (12, 22), (10, 20)], fill="#443F43")
    d.polygon([(19, 17), (23, 13), (23, 19), (20, 22), (19, 22)], fill="#4F4640")
    d.line([(13, 10), (19, 10), (21, 12)], fill="#9B8A6D", width=1)
    d.polygon([(15, 12), (19, 13), (21, 16), (17, 20), (12, 17)], fill="#9C401B")
    d.polygon([(15, 12), (18, 13), (20, 16), (17, 18), (13, 16)], fill="#F69431")
    d.polygon([(15, 13), (18, 14), (18, 16), (15, 16), (14, 15)], fill="#FFE498")
    d.rectangle((12, 20, 13, 20), fill="#8B735A")
    d.rectangle((3, 13, 4, 14), fill="#E8BE54")
    d.point((27, 17), fill="#FFE791")
    return im


def end():
    """An endstone orbit gathers three separate sparks into one violet core."""
    im, d = canvas()
    d.polygon([(7, 6), (11, 3), (18, 3), (18, 5), (12, 5),
               (8, 8), (8, 14), (5, 14), (5, 9)], fill="#453A5B")
    d.line([(6, 13), (6, 9), (9, 6), (12, 4), (17, 4)], fill="#EEE5AC", width=1)
    d.line([(7, 12), (7, 9), (10, 6), (12, 5), (17, 5)], fill="#A7A381", width=1)
    d.polygon([(23, 17), (26, 17), (26, 22), (22, 26), (16, 29),
               (10, 27), (10, 24), (16, 26), (21, 23), (23, 21)], fill="#453A5B")
    d.line([(24, 18), (24, 21), (21, 24), (16, 27), (11, 25)], fill="#D8D5A0", width=1)
    d.line([(25, 18), (25, 22), (22, 25), (16, 28), (11, 26)], fill="#9C9983", width=1)
    # Floating core with strongly separated facets and a black-purple outline.
    d.polygon([(15, 7), (18, 7), (23, 13), (23, 18), (17, 25),
               (15, 25), (9, 18), (9, 14)], fill="#342341")
    d.polygon([(15, 8), (17, 8), (21, 13), (16, 15), (11, 14)], fill="#D9A0F5")
    d.polygon([(11, 15), (15, 16), (15, 23), (10, 18)], fill="#A56BDD")
    d.polygon([(16, 16), (22, 14), (22, 18), (17, 23), (16, 24)], fill="#753BAA")
    d.line([(16, 9), (16, 14)], fill="#F4D1FF", width=1)
    d.line([(11, 16), (11, 18), (14, 21)], fill="#BD8CEB", width=1)
    d.line([(18, 17), (20, 16)], fill="#CA86EF", width=1)
    d.rectangle((16, 18, 17, 20), fill="#EBC0FF")
    # Three crystal motes deliberately point toward the shared center.
    for x, y, light in ((25, 6, "#EBD2FF"), (4, 20, "#D8D5A0"), (25, 27, "#C998F1")):
        d.polygon([(x, y - 2), (x + 2, y), (x, y + 2), (x - 2, y)], fill="#49335E")
        d.point((x, y - 1), fill=light)
        d.rectangle((x - 1, y, x, y), fill=light)
    d.point((22, 9), fill="#A66FCE")
    d.point((7, 18), fill="#BE98D5")
    return im


def sculk():
    """A still soul-light held within two bone-lined echo horns."""
    im, d = canvas()
    # Horns give a recognizably sculk silhouette without depicting the table.
    d.polygon([(3, 7), (6, 7), (6, 12), (9, 15), (9, 22),
               (12, 25), (20, 25), (23, 22), (23, 15), (26, 12),
               (26, 7), (29, 7), (29, 14), (26, 18), (26, 24),
               (22, 29), (10, 29), (6, 25), (6, 18), (3, 14)], fill="#14292E")
    d.line([(4, 8), (4, 13), (7, 17), (7, 24), (11, 27), (21, 27),
            (24, 23), (24, 17), (27, 13), (27, 8)], fill="#46656C", width=2)
    d.line([(5, 8), (5, 12), (8, 16), (8, 22)], fill="#B7C9C0", width=1)
    d.line([(26, 8), (26, 12), (23, 16), (23, 21)], fill="#88A49F", width=1)
    d.rectangle((11, 26, 13, 27), fill="#668C8B")
    d.rectangle((19, 26, 21, 27), fill="#405C66")
    # Flame-shaped crystal contains small dark breaks as visible facet seams.
    d.polygon([(16, 2), (20, 7), (20, 11), (23, 14), (22, 20),
               (18, 24), (14, 24), (10, 20), (10, 15), (13, 11),
               (13, 7)], fill="#143B43")
    d.polygon([(16, 3), (18, 7), (17, 12), (14, 15), (12, 17),
               (11, 16), (14, 11), (14, 7)], fill="#66E2DC")
    d.polygon([(18, 8), (19, 9), (19, 12), (22, 15), (21, 20),
               (18, 22), (17, 18), (18, 14)], fill="#25AAA9")
    d.polygon([(12, 18), (15, 15), (16, 17), (17, 22),
               (14, 22), (11, 19)], fill="#16828B")
    d.line([(16, 5), (15, 8), (15, 11)], fill="#C4FFFF", width=1)
    d.polygon([(16, 14), (18, 16), (17, 20), (15, 20), (14, 18)], fill="#A1F9EB")
    d.rectangle((15, 16, 16, 18), fill="#E1FFEA")
    d.point((21, 17), fill="#64D5D3")
    d.point((4, 22), fill="#2BABAB")
    d.point((28, 20), fill="#5EDACD")
    return im


def geode():
    """Calcite-lined mother rock carries a young, asymmetrical crystal spray."""
    im, d = canvas()
    d.polygon([(3, 16), (7, 15), (10, 19), (21, 19), (25, 15),
               (29, 17), (28, 25), (24, 29), (9, 30), (4, 26)], fill="#272B36")
    d.polygon([(4, 17), (7, 16), (11, 20), (21, 20), (25, 16),
               (28, 18), (25, 24), (10, 26), (5, 23)], fill="#7D8190")
    d.polygon([(5, 18), (7, 18), (10, 21), (22, 21), (26, 18),
               (25, 22), (22, 25), (10, 27), (6, 23)], fill="#CCC9CC")
    d.line([(6, 19), (7, 19), (10, 22), (13, 22)], fill="#F1EAE4", width=1)
    d.line([(23, 22), (25, 20)], fill="#F1EAE4", width=1)
    d.polygon([(5, 24), (10, 28), (22, 27), (26, 24), (24, 28),
               (9, 29), (5, 26)], fill="#535564")
    d.rectangle((11, 28, 15, 28), fill="#82818C")
    d.rectangle((22, 27, 24, 27), fill="#343B49")
    # Crystals overlap as minerals rooted in the same rock, not a bouquet.
    d.polygon([(7, 9), (10, 8), (14, 12), (16, 22), (12, 24),
               (8, 18)], fill="#48315F")
    d.polygon([(8, 10), (10, 9), (12, 12), (14, 21), (12, 22),
               (10, 17)], fill="#9D73C6")
    d.line([(9, 11), (10, 16), (12, 20)], fill="#DFC0F9", width=1)
    d.polygon([(16, 2), (20, 6), (20, 17), (17, 24), (14, 22),
               (12, 15), (13, 7)], fill="#493064")
    d.polygon([(16, 3), (18, 6), (16, 8), (14, 7)], fill="#F4D9FF")
    d.polygon([(14, 8), (16, 9), (16, 21), (15, 21), (13, 15)], fill="#BA8DE0")
    d.polygon([(17, 9), (19, 7), (19, 17), (17, 22)], fill="#8554B5")
    d.line([(14, 9), (14, 15)], fill="#E6C2FF", width=1)
    d.polygon([(25, 10), (27, 13), (25, 19), (20, 24), (17, 22),
               (20, 16)], fill="#4D3368")
    d.polygon([(25, 11), (26, 13), (23, 19), (19, 22), (18, 21),
               (21, 16)], fill="#AC7CD4")
    d.line([(24, 13), (21, 18), (19, 20)], fill="#E7BDFF", width=1)
    d.line([(25, 16), (23, 20), (21, 22)], fill="#7955A9", width=1)
    d.point((25, 6), fill="#CAA7E4")
    d.point((5, 5), fill="#E9D6F8")
    return im


ICONS = {"black_gold": black_gold, "end": end, "sculk": sculk, "crystal": geode}

# Original compositions. The reference only informed the dark, quiet surface
# and category-colored edging, never the pixels or implementation:
# https://github.com/Aizistral-Studios/Enigmatic-Legacy/blob/1.20.X/
# src/main/java/com/aizistral/enigmaticlegacy/handlers/EnigmaticEventHandler.java
# Native Minecraft 26.3 tooltip sprites use a 12px text inset, a 9px background
# slice, and a 10px frame slice. All corner ornament stays inside that 10px band.
TOOLTIPS = {
    "black_gold": {"shadow": "#38291D", "low": "#745126", "rim": "#C49A4B", "light": "#E7C784", "gem": "#E8AD44", "shine": "#FFF0B8", "inner": "#5D472D"},
    "end": {"shadow": "#2A203F", "low": "#51416D", "rim": "#9980B5", "light": "#C2A7D9", "gem": "#AC74D6", "shine": "#EBD0FF", "inner": "#493758"},
    "sculk": {"shadow": "#152E32", "low": "#31535B", "rim": "#609F9F", "light": "#95C9C1", "gem": "#43C7C5", "shine": "#C2FFF0", "inner": "#2B494F"},
    "crystal": {"shadow": "#2B293A", "low": "#5C566A", "rim": "#AAA3B9", "light": "#D8D0DF", "gem": "#AB82CF", "shine": "#F0D6FF", "inner": "#504458"},
    "arcane": {"shadow": "#25273A", "low": "#4D516C", "rim": "#8B94B6", "light": "#B9C5D6", "gem": "#91C1CB", "shine": "#DAEEFF", "inner": "#424B61"},
}


def tooltip_background():
    image = Image.new("RGBA", (100, 100))
    draw = ImageDraw.Draw(image)
    # Only a few discrete bands. The center is uniform, so any amount of
    # stretching preserves contrast and cannot introduce noisy text backing.
    draw.rectangle((5, 2, 94, 97), fill=(6, 8, 13, 65))
    draw.rectangle((2, 5, 97, 94), fill=(6, 8, 13, 65))
    draw.rectangle((4, 4, 95, 95), fill=(7, 10, 16, 160))
    draw.rectangle((6, 3, 93, 96), fill=(10, 13, 21, 245))
    draw.rectangle((3, 6, 96, 93), fill=(10, 13, 21, 245))
    draw.rectangle((5, 5, 94, 94), fill=(12, 15, 24, 249))
    draw.rectangle((8, 8, 91, 91), fill=(15, 17, 27, 249))
    return image


def tooltip_frame(colors):
    image = Image.new("RGBA", (100, 100))
    draw = ImageDraw.Draw(image)
    # Layered mineral rails, all kept outside the 12px text inset. The long
    # sections are constant strips; Minecraft can stretch them without seams.
    for inset, color in ((3, colors["shadow"]), (4, colors["rim"]),
                         (5, colors["low"]), (8, colors["inner"])):
        draw.line((10, inset, 89, inset), fill=color)
        draw.line((inset, 10, inset, 89), fill=color)
        draw.line((10, 99 - inset, 89, 99 - inset), fill=color)
        draw.line((99 - inset, 10, 99 - inset, 89), fill=color)
    # A tiny socket and a mineral facet at each corner. This ten-pixel corner
    # is the complete decoration; it never overlaps text, even on short tips.
    corner = Image.new("RGBA", (10, 10))
    cd = ImageDraw.Draw(corner)
    cd.line([(1, 9), (1, 6), (6, 1), (9, 1)], fill=colors["shadow"])
    cd.line([(2, 9), (2, 6), (6, 2), (9, 2)], fill=colors["low"])
    cd.line([(3, 9), (3, 6), (6, 3), (9, 3)], fill=colors["light"])
    cd.line([(4, 9), (4, 7), (7, 4), (9, 4)], fill=colors["rim"])
    cd.line([(5, 9), (5, 8), (8, 5), (9, 5)], fill=colors["low"])
    cd.polygon([(6, 4), (8, 6), (6, 8), (4, 6)], fill=colors["gem"])
    cd.point((6, 4), fill=colors["shine"])
    cd.line((5, 5, 5, 6), fill=colors["light"])
    cd.point((7, 7), fill=colors["low"])
    cd.line([(8, 9), (8, 8), (9, 8)], fill=colors["inner"])
    image.alpha_composite(corner, (0, 0))
    image.alpha_composite(corner.transpose(Image.Transpose.FLIP_LEFT_RIGHT), (90, 0))
    image.alpha_composite(corner.transpose(Image.Transpose.FLIP_TOP_BOTTOM), (0, 90))
    image.alpha_composite(corner.transpose(Image.Transpose.ROTATE_180), (90, 90))
    # The entire text rectangle remains transparent by construction.
    assert image.getchannel("A").crop((10, 10, 90, 90)).getbbox() is None
    return image


def save_tooltips():
    folder = RES / "textures/gui/sprites/tooltip"
    folder.mkdir(parents=True, exist_ok=True)
    sprites = {}
    for name, colors in TOOLTIPS.items():
        background, frame = tooltip_background(), tooltip_frame(colors)
        sprites[name] = (background, frame)
        for kind, image, border in (("background", background, 9), ("frame", frame, 10)):
            path = folder / f"{name}_{kind}.png"
            image.save(path, optimize=True)
            scaling = {"type": "nine_slice", "width": 100, "height": 100, "border": border}
            if kind == "frame":
                scaling["stretch_inner"] = True
            write_json(Path(str(path) + ".mcmeta"), {"gui": {"scaling": scaling}})
    return sprites


def nine_slice(sprite, size, border):
    """Preview the nine patches with the same fixed edge widths as Minecraft."""
    width, height = size
    result = Image.new("RGBA", size)
    src = (0, border, sprite.width - border, sprite.width)
    dst_x = (0, border, width - border, width)
    dst_y = (0, border, height - border, height)
    for row in range(3):
        for col in range(3):
            tile = sprite.crop((src[col], src[row], src[col + 1], src[row + 1]))
            target_size = (dst_x[col + 1] - dst_x[col], dst_y[row + 1] - dst_y[row])
            if tile.size != target_size:
                tile = tile.resize(target_size, Image.Resampling.NEAREST)
            result.alpha_composite(tile, (dst_x[col], dst_y[row]))
    return result


def tooltip_preview(sprites):
    # Text is just a legibility specimen, not a replacement language source.
    samples = [
        ("black_gold", "Black Gold Conversion Table", "Offer gold. Let the wheel decide.", (224, 54)),
        ("end", "End Conversion Table", "Choose a target and begin conversion.", (244, 64)),
        ("sculk", "Sculk Conversion Table", "Usable souls: 4,096", (184, 76)),
        ("crystal", "Mother Rock Geode", "A living crystal grows within the stone.", (272, 70)),
        ("arcane", "Boughbound Reverie", "A fragment of a quiet memory.", (218, 60)),
    ]
    sheet = Image.new("RGB", (640, 468), "#1B2027")
    d = ImageDraw.Draw(sheet)
    d.text((16, 12), "CONVERTTABLE / NATIVE TOOLTIP SKINS", fill="#ECE7DF")
    d.text((16, 28), "100px nine-slice sources / actual-size samples, then 2x corner detail", fill="#93A0AE")
    font = ImageFont.load_default(size=10)
    for index, (name, title, body, size) in enumerate(samples):
        y = 55 + index * 78
        background, frame = sprites[name]
        tip = nine_slice(background, size, 9)
        tip.alpha_composite(nine_slice(frame, size, 10))
        td = ImageDraw.Draw(tip)
        td.text((12, 11), title, font=font, fill=TOOLTIPS[name]["light"])
        td.text((12, 27), body, font=font, fill="#BFC1CB")
        if size[1] > 64:
            td.text((12, 43), "ConvertTable", font=font, fill="#757E91")
        sheet.paste(tip, (16, y), tip)
        detail = tip.crop((0, 0, 48, 24)).resize((96, 48), Image.Resampling.NEAREST)
        sheet.paste(detail, (328, y + 4), detail)
        d.text((440, y + 10), name.upper().replace("_", " "), fill=TOOLTIPS[name]["light"])
        d.text((440, y + 27), f"{size[0]} x {size[1]}", fill="#93A0AE")
    REVIEW.mkdir(parents=True, exist_ok=True)
    sheet.save(REVIEW / "tooltip_samples.png", optimize=True)


def write_json(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(content, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def save_icon(name, im):
    path = RES / f"textures/item/advancement/{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path, optimize=True)
    write_json(RES / f"models/item/advancement/{name}.json", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"convert_table:item/advancement/{name}"},
    })
    write_json(RES / f"items/advancement/{name}.json", {
        "model": {"type": "minecraft:model", "model": f"convert_table:item/advancement/{name}"},
    })
    assert im.size == (32, 32) and im.mode == "RGBA"
    assert set(im.getchannel("A").getdata()) == {0, 255}
    box = im.getbbox()
    assert box and min(box[:2]) > 0 and max(box[2:]) < 32
    return {"path": str(path.relative_to(ROOT)).replace("\\", "/"),
            "size": list(im.size), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}


def preview(icons):
    im = Image.new("RGB", (640, 272), "#1B2027")
    d = ImageDraw.Draw(im)
    d.text((16, 12), "CONVERTTABLE / ADVANCEMENT ICONS", fill="#ECE7DF")
    d.text((16, 29), "Original 32px sprites / 4x and native inventory sizes", fill="#93A0AE")
    for index, (name, icon) in enumerate(icons.items()):
        x = 16 + index * 156
        d.rectangle((x, 52, x + 139, 191), fill="#252C35")
        im.paste(icon.resize((128, 128), Image.Resampling.NEAREST), (x + 6, 58), icon.resize((128, 128), Image.Resampling.NEAREST))
        d.text((x, 203), name.upper().replace("_", " "), fill="#DCDCE6")
        # Minecraft 16px presentation uses nearest neighbor to expose any weak silhouettes.
        im.paste(icon, (x + 2, 228), icon)
        small = icon.resize((16, 16), Image.Resampling.NEAREST)
        im.paste(small, (x + 52, 236), small)
        d.rectangle((x + 91, 223, x + 137, 267), fill="#C0BAB0")
        im.paste(icon, (x + 99, 229), icon)
    REVIEW.mkdir(parents=True, exist_ok=True)
    im.save(REVIEW / "advancement_icons.png", optimize=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--no-preview", action="store_true")
    args = parser.parse_args()
    icons = {name: draw() for name, draw in ICONS.items()}
    manifest = [save_icon(name, im) for name, im in icons.items()]
    sprites = save_tooltips()
    if not args.no_preview:
        preview(icons)
        tooltip_preview(sprites)
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()
