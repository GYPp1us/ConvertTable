"""Rebuild the 16x16 Life Seed item from its approved RGBA texels.

The sprite was generated with the built-in ImageGen tool, then cropped to the
visible subject and nearest-neighbor sampled onto the 14x14 inner item area.
The generated alpha is retained on every visible texel. The native RGBA rows
below are the authoring source, so regeneration needs no network or source image.
"""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "src/main/resources/assets/convert_table/textures/item/life_seed.png"

SOURCE_PROMPT = """Use case: stylized-concept. Asset type: a single production Minecraft 16x16 inventory item sprite for the ConvertTable mod, called Life Seed (life_seed). Subject: a small translucent pale cyan / mint-green living gel pearl, with a warm pale golden embryo dot inside, recognizable as a natural magical material acquired from water. Shape: squat organic irregular rounded droplet, slightly asymmetric, visibly not an egg and not a pointed amethyst crystal. Strong readable dark teal lower outline, medium sea-green shaded lower-right shell, pale mint upper-left highlight, soft pale cyan inner gel, a tiny warm ivory/gold core slightly off-center. Style: authentic disciplined 16x16 Minecraft pixel art, exactly sixteen logical square pixels across and down with one flat color per pixel, around 8-10 colors, 1 logical pixel transparent margin. Output true 16x16 if possible, otherwise present the 16x16 logical pixel sprite as an exact nearest-neighbor enlargement on a 1024x1024 canvas, aligned to a grid of 64x64 square cells. Clear silhouette on real transparent alpha background; no background texture or checkerboard. No text, no labels, no border, no environment, no drop shadow, no external glow, no smoothing, no tiny subpixel details, no decorative particles, no leaves or stems. Preserve genuine partial transparency inside the gel while keeping the silhouette readable; golden embryo dot stays opaque. Only one sprite."""

# Sixteen rows of sixteen RGBA texels, eight hexadecimal digits per texel.
RGBA_ROWS = (
    "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
    "00000000000000000000000000000000000000001a6a68fd1c6b68fe1b6a68fd023c44fd00000000000000000000000000000000000000000000000000000000",
    "000000000000000000000000000000001a6967fed0f7eafed1f7ebfdb9f1e1fe80cdbffe023c44fe000000000000000000000000000000000000000000000000",
    "0000000000000000000000001a6966fed2f8ecfef9fbf6fe9be2d6fd97cbcbbcbef2e2fd83d1c4fd033c43fe0000000000000000000000000000000000000000",
    "0000000000000000196866fdd3f8ecfef9fbf5fecaf5e8fe8fd9d4fe94c7cabe93c6cabe89d3d5e34a9fa2fe033c43fd00000000000000000000000000000000",
    "0000000000000000186766fed1f7ebfda3e7d9fe88d4cefebef5f8fea6d7dbc28fc2c7be89c2c6bf91d9c0fd50a7a7fe033b44fe000000000000000000000000",
    "000000001b6b68fde5fbf4feb4f0defe86d0cefda4d8ddc8a7d8e0c7a4dee3dc92c6bfd188bfc4c17ec0c4c4ade9ddfd6db3abfd033a41fd0000000000000000",
    "000000001b6a68fde3faf3fe86d1cdfd92c6ccb59dced4b8bbf1f3fd85c0bbc8acd1c0dec6d5b7f2a6d3c3e375bbc0c892d5b9fe33867efd023b44fe00000000",
    "00000000023b42fdacecd7fd9adedffe8aced1e284c1c7c68ac6ced688c0bcc8cdd5b5f6fcf4ccfdced7b4f98bc8c2df55b2b1e679bca8fe1f6e6bfd00000000",
    "00000000033d43fd368b8dfdbef3e3fe9bdfe1fd84cacfd784c0c5ccabd0c1d7e3d59dfbfdecb2fee4c47afdaed1bbf53b9c96fe76bba6fd1f6f6cfe00000000",
    "0000000000000000033b42fa6bc1b4fea8ebd8fec2f6f7fd8ec7ccce8fc5c9d1b2d1bbe4e9bd6ffcf9e1a3fcabcfbaeb338b8dfe2d7c75fe023c45fe00000000",
    "000000000000000000000000013c43fe287a7afd96e0c8fdb4f0defdbef2f1fca1e3e5fa77bbc0db42a0a2fa8ac6b1fc378a7dfd053c45fd0000000000000000",
    "00000000000000000000000000000000033c43fd318586fe74bcb7fd84d4cafe6fc3b3fd3ea896fc35897cfd36897dfe023c44fd000000000000000000000000",
    "0000000000000000000000000000000000000000013c44fe1e6e6bfd1f716dfe206e6bfd1f706cfd023c45fe033d45fe00000000000000000000000000000000",
    "000000000000000000000000000000000000000000000000023c43fd033c43fd033d45fe00000000000000000000000000000000000000000000000000000000",
    "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
)


def image() -> Image.Image:
    assert len(RGBA_ROWS) == 16 and all(len(row) == 128 for row in RGBA_ROWS)
    return Image.frombytes("RGBA", (16, 16), bytes.fromhex("".join(RGBA_ROWS)))


def preview(sprite: Image.Image, path: Path) -> None:
    sheet = Image.new("RGB", (656, 396), (26, 31, 37))
    draw = ImageDraw.Draw(sheet)
    draw.text((10, 10), "Life Seed: native 16 x 16 RGBA / nearest-neighbor x18", fill=(236, 243, 242))
    enlarged = sprite.resize((288, 288), Image.Resampling.NEAREST)
    for x, background in ((8, (31, 39, 43)), (336, (207, 209, 208))):
        sheet.paste(background, (x, 32, x + 312, 344))
        sheet.paste(enlarged, (x + 12, 44), enlarged)
        sheet.paste(sprite, (x + 144, 366), sprite)
    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(path)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--preview", type=Path, help="optional enlarged dark/light background review sheet")
    args = parser.parse_args()
    sprite = image()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    sprite.save(args.output, optimize=True)
    if args.preview:
        preview(sprite, args.preview)
    print(f"{args.output}: 16x16 RGBA, sha256 {hashlib.sha256(args.output.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    main()
