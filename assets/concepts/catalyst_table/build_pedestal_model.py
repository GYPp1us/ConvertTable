"""Rebuild the 16px low catalyst pedestal from the ivory/basalt concept."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "src/main/resources/assets/convert_table/models/block/catalyst_pedestal.json"
materials = {
    key: f"convert_table:block/crystal_table/{key}"
    for key in ("basalt_dark", "basalt_light", "calcite", "calcite_light",
                "calcite_shadow", "vein", "bud_lilac", "bud_pink", "lining_light")
}
elements = []


def cube(frm, to, texture):
    elements.append({
        "from": list(frm), "to": list(to),
        "faces": {side: {"texture": "#" + texture}
                  for side in ("down", "up", "north", "south", "east", "west")},
    })


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
        cube((cx, 7, cz), (cx + 1, 8, cz + 1), "bud_pink")

model = {
    "parent": "minecraft:block/block",
    "ambientocclusion": True,
    "textures": {**materials, "particle": materials["calcite"]},
    "elements": elements,
}
OUT.write_text(json.dumps(model, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"{OUT}: {len(elements)} cuboids")
