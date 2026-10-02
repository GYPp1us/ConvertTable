"""Rebuild the 16px low catalyst pedestal from the ivory/basalt concept."""
from __future__ import annotations

import json
from pathlib import Path
import sys

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "src/main/resources/assets/convert_table/models/block/catalyst_pedestal.json"
sys.path.insert(0, str(ROOT / "assets/tools"))
from export_refined_assets import FACE_CORNERS, clipped_faces, model_element

# Use the current geode stone set, including its native color clusters and PBR
# channels; the former unprefixed maps are archived authoring materials.
materials = {
    key: "convert_table:block/crystal_table/" + (
        "geode_" + key if key.startswith(("basalt", "calcite", "lining")) else key)
    for key in ("basalt_dark", "basalt_light", "calcite", "calcite_light",
                "calcite_shadow", "vein", "bud_lilac", "bud_pink", "lining_light")
}
elements = []
maps = {}
for key, texture in materials.items():
    name = texture.rsplit("/", 1)[-1]
    folder = ROOT / "src/main/resources/assets/convert_table/textures/block/crystal_table"
    maps[key] = tuple(np.array(Image.open(folder / f"{name}{suffix}.png").convert("RGBA"))
                      for suffix in ("", "_s"))
cube_count = 0


def cube(frm, to, texture):
    global cube_count
    cube_count += 1
    for side, corners in FACE_CORNERS.items():
        points = [tuple(to[i] if bit else frm[i] for i, bit in enumerate(corner))
                  for corner in corners]
        # Same block-space, one-texel-per-voxel mapping as publish_geode.py.
        uv = []
        for x, y, z in points:
            if side in ("up", "down"):
                u, v = x, z
            elif side in ("north", "south"):
                u, v = (x if side == "north" else 16 - x), 16 - y
            else:
                u, v = (z if side == "east" else 16 - z), 16 - y
            uv.append((u, v))
        face = {"vertices": points, "uv": uv, "texture": texture}
        # Crystal accents receive the same per-texel vanilla emission as the
        # parent table, in addition to their shared shader material channels.
        elements.extend(model_element(q) for q in clipped_faces(face, *maps[texture]))


# A two-pixel basalt foot and a recessed mineral well keep the body low.
cube((0, 0, 0), (16, 2, 16), "basalt_dark")
cube((1, 2, 1), (15, 3, 15), "basalt_light")
cube((3, 3, 3), (13, 4, 13), "calcite_shadow")
cube((4, 4, 4), (12, 5, 12), "vein")

# Four calcite rim stones with an inlaid purple line on the inside wall.
for frm, to in (
    ((3, 3, 1), (13, 5, 3)), ((3, 3, 13), (13, 5, 15)),
    ((1, 3, 3), (3, 5, 13)), ((13, 3, 3), (15, 5, 13)),
):
    cube(frm, to, "calcite_light")
for frm, to in (
    ((4, 4, 3), (12, 5, 4)), ((4, 4, 12), (12, 5, 13)),
    ((3, 4, 4), (4, 5, 12)), ((12, 4, 4), (13, 5, 12)),
):
    cube(frm, to, "lining_light")

# Each corner steps from dark anchor to pale stone and a small glowing crystal.
for x in (0, 13):
    for z in (0, 13):
        cube((x, 2, z), (x + 3, 5, z + 3), "basalt_dark")
        cx, cz = (x + 1, z + 1)
        cube((cx, 5, cz), (cx + 2, 6, cz + 2), "calcite")
        cube((cx, 6, cz), (cx + 2, 7, cz + 2), "bud_lilac")
        # Rotate the asymmetric tip around this existing 2x2 bud's own center.
        # Pedestal roots keep their authored positions, including the outer
        # eastern/southern footprint, while the four tips face NW/NE/SE/SW.
        turns = {(0, 0): 0, (13, 0): 1, (13, 13): 2, (0, 13): 3}[x, z]
        tip_x, tip_z = 0, 0
        for _ in range(turns):
            tip_x, tip_z = 1 - tip_z, tip_x
        cube((cx + tip_x, 7, cz + tip_z), (cx + tip_x + 1, 8, cz + tip_z + 1), "bud_pink")

model = {
    "parent": "minecraft:block/block",
    "ambientocclusion": True,
    "textures": {**materials, "particle": materials["calcite"]},
    "elements": elements,
}
OUT.write_text(json.dumps(model, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"{OUT}: {cube_count} cuboids, {len(elements)} material faces")
