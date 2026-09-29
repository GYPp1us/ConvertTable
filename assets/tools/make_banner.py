"""Generate the repository banner (assets/previews/banner.png) from the mod's own art.

Reproducible: run `python assets/tools/make_banner.py` from the project root.

The four subjects are the mod's own renders, which sit on near-black backdrops.
They are screen-blended, so that backdrop contributes nothing and only the lit
geometry lands on the banner gradient. Nothing is drawn that is not in the repo.
"""

from __future__ import annotations

import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "assets", "previews", "banner.png")

W, H = 1500, 420
BG_TOP = (12, 14, 18)
BG_BOTTOM = (24, 28, 35)

GOLD = (216, 167, 60)
TEAL = (94, 205, 205)
TITLE = (244, 246, 249)
MUTED = (152, 162, 178)

# (path relative to project root, glow tint)
SUBJECTS = [
    ("assets/previews/black_gold.png", (255, 190, 92)),
    ("assets/previews/end.png", (176, 132, 255)),
    ("assets/previews/sculk.png", (94, 214, 202)),
    ("assets/geode_prototype/geode_dark.png", (208, 168, 255)),
]

FONT_BOLD = r"C:\Windows\Fonts\seguisb.ttf"
FONT_CN = r"C:\Windows\Fonts\msyhbd.ttc"
FONT_LIGHT = r"C:\Windows\Fonts\segoeui.ttf"
FONT_FALLBACK = r"C:\Windows\Fonts\arialbd.ttf"


def load_font(path: str, size: int) -> ImageFont.FreeTypeFont:
    for candidate in (path, FONT_FALLBACK):
        try:
            return ImageFont.truetype(candidate, size)
        except OSError:
            continue
    return ImageFont.load_default(size)


def gradient(w: int, h: int, top, bottom) -> np.ndarray:
    ramp = np.linspace(0.0, 1.0, h, dtype=np.float32)[:, None]
    rows = np.array(top, np.float32)[None, :] * (1 - ramp) + np.array(bottom, np.float32)[None, :] * ramp
    return np.repeat(rows[:, None, :], w, axis=1) / 255.0


def subject_alpha(rgb: np.ndarray, floor: float = 6.0, feather: float = 34.0) -> np.ndarray:
    """Alpha for a render shot on a flat, near-black backdrop.

    `rgb` is 0..1; `floor`/`feather` are expressed in 0..255 colour units.
    """
    border = np.concatenate([rgb[0:4].reshape(-1, 3), rgb[-4:].reshape(-1, 3),
                             rgb[:, 0:4].reshape(-1, 3), rgb[:, -4:].reshape(-1, 3)])
    bg = np.median(border, axis=0)
    dist = np.linalg.norm(rgb - bg[None, None, :], axis=2) * 255.0
    alpha = np.clip((dist - floor) / feather, 0.0, 1.0)

    # Fill each row's span so the object's own dark voxels stay solid.
    solid = alpha > 0.35
    for y in range(alpha.shape[0]):
        xs = np.flatnonzero(solid[y])
        if xs.size > 2:
            alpha[y, xs.min():xs.max() + 1] = np.maximum(alpha[y, xs.min():xs.max() + 1], 1.0)
    return alpha


def fit_subject(path: str, size: int) -> np.ndarray | None:
    """Load a render, crop to the subject, and return RGBA scaled into `size`."""
    full = os.path.join(ROOT, path)
    if not os.path.exists(full):
        print(f"  ! missing render, skipped: {path}")
        return None
    img = Image.open(full).convert("RGB")
    rgb = np.asarray(img, np.float32) / 255.0
    alpha = subject_alpha(rgb)

    ys, xs = np.where(alpha > 0.5)
    if xs.size == 0:
        print(f"  ! no subject found, skipped: {path}")
        return None

    pad = 12
    x0, x1 = max(xs.min() - pad, 0), min(xs.max() + pad + 1, rgb.shape[1])
    y0, y1 = max(ys.min() - pad, 0), min(ys.max() + pad + 1, rgb.shape[0])
    crop_rgb = rgb[y0:y1, x0:x1]
    crop_a = alpha[y0:y1, x0:x1]

    rgb_img = Image.fromarray((crop_rgb * 255).astype(np.uint8), "RGB")
    a_img = Image.fromarray((crop_a * 255).astype(np.uint8), "L")

    scale = size / max(rgb_img.size)
    new = (max(int(rgb_img.width * scale), 1), max(int(rgb_img.height * scale), 1))
    rgb_img = rgb_img.resize(new, Image.LANCZOS)
    a_img = a_img.resize(new, Image.LANCZOS)

    tile = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    tile.paste(rgb_img, ((size - new[0]) // 2, (size - new[1]) // 2), a_img)
    return np.asarray(tile, np.float32) / 255.0


def screen_blend(base: np.ndarray, layer_rgb: np.ndarray, alpha: np.ndarray) -> np.ndarray:
    screened = 1.0 - (1.0 - base) * (1.0 - layer_rgb)
    a = alpha[:, :, None]
    return base * (1.0 - a) + screened * a


def add_glow(base: np.ndarray, box, color, strength: float) -> np.ndarray:
    x, y, size = box
    glow = Image.new("L", (W, H), 0)
    ImageDraw.Draw(glow).ellipse(
        [x + size * 0.06, y + size * 0.06, x + size * 0.94, y + size * 0.94], fill=255
    )
    glow = np.asarray(glow.filter(ImageFilter.GaussianBlur(size * 0.26)), np.float32) / 255.0
    tint = np.array(color, np.float32) / 255.0
    return np.clip(base + glow[:, :, None] * tint[None, None, :] * strength, 0.0, 1.0)


def main() -> None:
    base = gradient(W, H, BG_TOP, BG_BOTTOM)

    slot = 220
    x_start, y_slot = 556, 100

    boxes = [(x_start + i * (slot + 8), y_slot, slot) for i in range(len(SUBJECTS))]

    for (x, y, s), (_, tint) in zip(boxes, SUBJECTS):
        base = add_glow(base, (x, y, s), tint, strength=0.30)

    placed = 0
    for (path, _), (x, y, s) in zip(SUBJECTS, boxes):
        tile = fit_subject(path, s)
        if tile is None:
            continue
        region = base[y : y + s, x : x + s, :]
        base[y : y + s, x : x + s, :] = screen_blend(region, tile[:, :, :3], tile[:, :, 3])
        placed += 1

    canvas = Image.fromarray((np.clip(base, 0, 1) * 255).astype(np.uint8), "RGB")

    # Warm wash behind the title block.
    wash = Image.new("L", (W, H), 0)
    ImageDraw.Draw(wash).ellipse([-140, -120, 600, 540], fill=70)
    wash = np.asarray(wash.filter(ImageFilter.GaussianBlur(80)), np.float32) / 255.0
    arr = np.asarray(canvas, np.float32) / 255.0
    arr = np.clip(arr + wash[:, :, None] * (np.array(GOLD, np.float32) / 255.0)[None, None, :], 0, 1)
    canvas = Image.fromarray((arr * 255).astype(np.uint8), "RGB")

    draw = ImageDraw.Draw(canvas)

    x0, y0 = 62, 100
    draw.text((x0, y0), "ConvertTable", font=load_font(FONT_BOLD, 66), fill=TITLE)

    rule_y = y0 + 82
    draw.rectangle([x0, rule_y, x0 + 150, rule_y + 5], fill=GOLD)
    draw.rectangle([x0 + 158, rule_y, x0 + 232, rule_y + 5], fill=TEAL)

    draw.text((x0 + 1, rule_y + 20),
              "Minecraft 26.3   ·   Fabric 0.19.5+   ·   Java 25",
              font=load_font(FONT_LIGHT, 21), fill=MUTED)
    draw.text((x0, rule_y + 52),
              "三张动画转换台 · 母岩晶洞生长 · 触媒增殖",
              font=load_font(FONT_CN, 22), fill=GOLD)

    canvas.save(OUT)
    print(f"wrote {os.path.relpath(OUT, ROOT)}  ({W}x{H}, {placed}/{len(SUBJECTS)} subjects placed)")


if __name__ == "__main__":
    main()
