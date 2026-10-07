#!/usr/bin/env python3
"""Render README block art directly from the packaged Minecraft model resources.

Run from the repository root, optionally pointing at the freshly built mod jar:

    python assets/tools/render_readme.py --jar build/libs/convert-table-0.3.1.jar

The small orthographic rasterizer handles vanilla block-model faces, alpha cutouts,
the format-2 animated quads and their position/rotation tracks. It intentionally
reads packaged models and textures instead of authoring scenes or preview fixtures.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import math
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

import numpy as np
from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
RESOURCE_ROOT = ROOT / "src" / "main" / "resources"
ASSET_PREFIX = "assets/convert_table/"
OUT_DIR = ROOT / "assets" / "readme"
BLOCKS = ("black_gold", "end", "sculk", "crystal_table", "catalyst_pedestal")
TABLES = ("black_gold", "end", "sculk")

# The corner order is Minecraft's FaceInfo order; see export_refined_assets.py.
FACE_CORNERS = {
    "down": ((0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)),
    "up": ((0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)),
    "north": ((1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)),
    "south": ((0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)),
    "west": ((0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)),
    "east": ((1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)),
}
FACE_NORMALS = {
    "down": (0, -1, 0), "up": (0, 1, 0), "north": (0, 0, -1),
    "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0),
}


class ResourceReader:
    def __init__(self, jar_path: Path | None):
        self.jar_path = jar_path
        self.archive = zipfile.ZipFile(jar_path) if jar_path else None
        self.hashes: dict[str, str] = {}
        self._json: dict[str, dict] = {}
        self._images: dict[str, Image.Image] = {}

    def read(self, rel: str) -> bytes:
        rel = rel.replace("\\", "/").lstrip("/")
        if self.archive is not None:
            data = self.archive.read(rel)
        else:
            data = (RESOURCE_ROOT / Path(*rel.split("/"))).read_bytes()
        self.hashes[rel] = hashlib.sha256(data).hexdigest()
        return data

    def json(self, rel: str) -> dict:
        rel = rel.replace("\\", "/")
        if rel not in self._json:
            self._json[rel] = json.loads(self.read(rel).decode("utf-8"))
        return self._json[rel]

    def image(self, rel: str) -> Image.Image:
        rel = rel.replace("\\", "/")
        if rel not in self._images:
            self._images[rel] = Image.open(io.BytesIO(self.read(rel))).convert("RGBA")
        return self._images[rel]

    def close(self) -> None:
        if self.archive is not None:
            self.archive.close()


@dataclass
class Quad:
    vertices: np.ndarray
    uv: np.ndarray
    texture: str
    normal: np.ndarray
    emission: int = 0


def texture_resource(texture_id: str) -> str:
    if texture_id.startswith("#"):
        raise ValueError(f"unresolved texture reference: {texture_id}")
    if texture_id.startswith("minecraft:"):
        namespace, rel = texture_id.split(":", 1)
        return f"assets/{namespace}/textures/{rel}.png"
    namespace, rel = texture_id.split(":", 1) if ":" in texture_id else ("convert_table", texture_id)
    return f"assets/{namespace}/textures/{rel}.png"


def static_quads(model: dict) -> list[Quad]:
    textures = model.get("textures", {})

    def resolve(name: str) -> str:
        seen: set[str] = set()
        while name.startswith("#"):
            key = name[1:]
            if key in seen or key not in textures:
                raise ValueError(f"cannot resolve model texture {name}")
            seen.add(key)
            name = textures[key]
        return name

    quads: list[Quad] = []
    for element in model.get("elements", []):
        lo, hi = np.asarray(element["from"], float), np.asarray(element["to"], float)
        for direction, face in element.get("faces", {}).items():
            corners = np.asarray(FACE_CORNERS[direction], float)
            vertices = np.where(corners > 0, hi, lo)
            uv_rect = face.get("uv")
            if uv_rect is None:
                uv_rect = default_uv(direction, lo, hi)
            u, v, U, V = map(float, uv_rect)
            base = np.asarray(((u, v), (u, V), (U, V), (U, v)), float)
            quarter = (int(face.get("rotation", 0)) // 90) % 4
            uv = base[(np.arange(4) + quarter) % 4]
            quads.append(Quad(vertices, uv, resolve(face["texture"]),
                              np.asarray(FACE_NORMALS[direction], float),
                              int(element.get("light_emission", 0))))
    return quads


def default_uv(direction: str, lo: np.ndarray, hi: np.ndarray) -> tuple[float, float, float, float]:
    # Vanilla FaceBakery defaults. Current ConvertTable models carry explicit UVs,
    # but this keeps the renderer useful if a future model omits them.
    if direction in ("up", "down"):
        return lo[0], 16 - hi[2], hi[0], 16 - lo[2]
    if direction == "north":
        return 16 - hi[0], 16 - hi[1], 16 - lo[0], 16 - lo[1]
    if direction == "south":
        return lo[0], 16 - hi[1], hi[0], 16 - lo[1]
    if direction == "west":
        return lo[2], 16 - hi[1], hi[2], 16 - lo[1]
    return 16 - hi[2], 16 - hi[1], 16 - lo[2], 16 - lo[1]


def animated_quads(animation: dict) -> list[Quad]:
    result = []
    for group in animation.get("groups", []):
        for quad in group.get("quads", []):
            # Format 2 UVs are normalized, unlike static model UVs in texels.
            result.append(Quad(np.asarray(quad["vertices"], float),
                               np.asarray(quad["uv"], float) * 16,
                               quad["texture"], np.asarray(quad["normal"], float),
                               int(quad.get("light_emission", 0))))
    return result


def track_value(keys: list[list[float]], tick: float, duration: float) -> float:
    t = tick % duration if duration else tick
    for index in range(len(keys) - 1):
        t0, v0 = keys[index]
        t1, v1 = keys[index + 1]
        if t0 <= t <= t1:
            return float(v0 + (v1 - v0) * ((t - t0) / max(t1 - t0, 1e-9)))
    return float(keys[-1][1]) if keys else 0.0


def animation_period_seconds(animation: dict) -> float:
    """Return a shared, seamless group period in the renderer's seconds unit."""
    groups = animation.get("groups", [])
    if not groups:
        raise ValueError("animated table has no groups")
    periods = [float(group.get("duration", 0)) for group in groups]
    if min(periods) <= 0 or any(abs(period - periods[0]) > 1e-6 for period in periods[1:]):
        raise ValueError(f"groups do not share one positive animation period: {periods}")
    for group, duration in zip(groups, periods):
        keys = group.get("keyframes", [])
        if not keys or abs(float(keys[0][0])) > 1e-6 or abs(float(keys[-1][0]) - duration) > 1e-6:
            raise ValueError(f"{group.get('name')}: track does not span its full period")
        start, end = float(keys[0][1]), float(keys[-1][1])
        if group.get("kind") == "rotation":
            turns = (end - start) / 360.0
            if abs(turns - round(turns)) > 1e-6:
                raise ValueError(f"{group.get('name')}: rotation track does not close at loop")
        elif group.get("kind") == "position":
            if abs(end - start) > 1e-6:
                raise ValueError(f"{group.get('name')}: position track does not close at loop")
        else:
            raise ValueError(f"unsupported animation kind {group.get('kind')!r}")
    return periods[0]


def pose_animation(animation: dict, tick: float) -> list[Quad]:
    posed: list[Quad] = []
    for group in animation.get("groups", []):
        duration = float(group.get("duration", 12))
        value = track_value(group.get("keyframes", []), tick, duration)
        pivot = np.asarray(group.get("pivot", (8, 8, 8)), float)
        axis = np.asarray(group.get("axis", (0, 1, 0)), float)
        axis_norm = np.linalg.norm(axis)
        axis = axis / axis_norm if axis_norm else np.asarray((0, 1, 0), float)
        if group.get("kind") == "rotation":
            angle = math.radians(value)
            x, y, z = axis
            c, s = math.cos(angle), math.sin(angle)
            rot = np.asarray(((c + x*x*(1-c), x*y*(1-c)-z*s, x*z*(1-c)+y*s),
                              (y*x*(1-c)+z*s, c+y*y*(1-c), y*z*(1-c)-x*s),
                              (z*x*(1-c)-y*s, z*y*(1-c)+x*s, c+z*z*(1-c))))
            transform = lambda p: (p - pivot) @ rot.T + pivot
            normal_transform = lambda n: n @ rot.T
        elif group.get("kind") == "position":
            offset = axis * value
            transform = lambda p: p + offset
            normal_transform = lambda n: n
        else:
            raise ValueError(f"unsupported animation kind {group.get('kind')!r}")
        for quad in group.get("quads", []):
            posed.append(Quad(transform(np.asarray(quad["vertices"], float)),
                              np.asarray(quad["uv"], float) * 16,
                              quad["texture"],
                              normal_transform(np.asarray(quad["normal"], float)),
                              int(quad.get("light_emission", 0))))
    return posed


def camera_basis() -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    # North/east/top view, matching the familiar Minecraft inventory angle.
    view = np.asarray((-0.55, -0.58, 0.60), float)
    view /= np.linalg.norm(view)
    world_up = np.asarray((0, 1, 0), float)
    right = np.cross(view, world_up)
    right /= np.linalg.norm(right)
    up = np.cross(right, view)
    up /= np.linalg.norm(up)
    return right, up, view


class RasterView:
    def __init__(self, quads: Iterable[Quad], size: int):
        right, up, view = camera_basis()
        points = np.concatenate([q.vertices for q in quads], axis=0)
        center = (points.min(axis=0) + points.max(axis=0)) / 2
        projected = np.column_stack(((points - center) @ right, (points - center) @ up))
        span = projected.max(axis=0) - projected.min(axis=0)
        self.center = center
        self.right, self.up, self.view = right, up, view
        self.size = size
        self.scale = size * 0.80 / max(float(span.max()), 1.0)
        self.project_center = (projected.min(axis=0) + projected.max(axis=0)) / 2

    def project(self, vertices: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
        rel = vertices - self.center
        xy = np.column_stack((rel @ self.right, rel @ self.up))
        xy = (xy - self.project_center) * self.scale + self.size / 2
        xy[:, 1] = self.size - xy[:, 1]
        depth = rel @ self.view
        return xy, depth


def sample_texture(texture: Image.Image, uv: np.ndarray) -> np.ndarray:
    pixels = np.asarray(texture, dtype=np.uint8)
    h, w = pixels.shape[:2]
    u = np.floor(uv[:, 0]).astype(np.int64) % w
    v = np.floor(uv[:, 1]).astype(np.int64) % h
    return pixels[v, u]


def draw_triangle(canvas: np.ndarray, zbuf: np.ndarray, view: RasterView,
                  coords: np.ndarray, depths: np.ndarray, uvs: np.ndarray,
                  normal: np.ndarray, emission: int, texture: Image.Image,
                  light: np.ndarray) -> None:
    h, w, _ = canvas.shape
    min_x = max(int(math.floor(coords[:, 0].min())), 0)
    max_x = min(int(math.ceil(coords[:, 0].max())), w - 1)
    min_y = max(int(math.floor(coords[:, 1].min())), 0)
    max_y = min(int(math.ceil(coords[:, 1].max())), h - 1)
    if max_x < min_x or max_y < min_y:
        return
    x0, y0 = coords[0]
    x1, y1 = coords[1]
    x2, y2 = coords[2]
    denom = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
    if abs(denom) < 1e-9:
        return
    xs = np.arange(min_x, max_x + 1, dtype=np.float32) + 0.5
    ys = np.arange(min_y, max_y + 1, dtype=np.float32) + 0.5
    xx, yy = np.meshgrid(xs, ys)
    a = ((y1-y2)*(xx-x2) + (x2-x1)*(yy-y2)) / denom
    b = ((y2-y0)*(xx-x2) + (x0-x2)*(yy-y2)) / denom
    c = 1 - a - b
    inside = (a >= -1e-6) & (b >= -1e-6) & (c >= -1e-6)
    if not np.any(inside):
        return
    interp_depth = a * depths[0] + b * depths[1] + c * depths[2]
    depth_region = zbuf[min_y:max_y+1, min_x:max_x+1]
    inside &= interp_depth < depth_region - 1e-6
    if not np.any(inside):
        return
    uv = a[:, :, None] * uvs[0] + b[:, :, None] * uvs[1] + c[:, :, None] * uvs[2]
    rgba = sample_texture(texture, uv.reshape(-1, 2)).reshape(uv.shape[:2] + (4,))
    inside &= rgba[:, :, 3] >= 128
    if not np.any(inside):
        return
    n = normal / max(np.linalg.norm(normal), 1e-9)
    shade = 0.54 + 0.46 * max(float(np.dot(n, light)), 0.0)
    e = min(max(emission, 0), 15) / 15.0
    shade = shade * (1.0 - e) + 1.28 * e
    shaded = np.clip(rgba[:, :, :3].astype(np.float32) * shade, 0, 255).astype(np.uint8)
    out_region = canvas[min_y:max_y+1, min_x:max_x+1]
    out_region[inside, :3] = shaded[inside]
    out_region[inside, 3] = 255
    depth_region[inside] = interp_depth[inside]


def render(quads: list[Quad], reader: ResourceReader, size: int,
           view: RasterView | None = None) -> Image.Image:
    if not quads:
        raise ValueError("no geometry to render")
    view = view or RasterView(quads, size)
    canvas = np.zeros((size, size, 4), dtype=np.uint8)
    zbuf = np.full((size, size), np.inf, dtype=np.float32)
    light = np.asarray((-0.45, 0.88, -0.58), float)
    light /= np.linalg.norm(light)
    for quad in quads:
        coords, depths = view.project(quad.vertices)
        texture = reader.image(texture_resource(quad.texture))
        for i, j, k in ((0, 1, 2), (0, 2, 3)):
            normal = np.cross(quad.vertices[j] - quad.vertices[i],
                              quad.vertices[k] - quad.vertices[i])
            if np.linalg.norm(normal) < 1e-8:
                normal = quad.normal
            draw_triangle(canvas, zbuf, view, coords[[i, j, k]], depths[[i, j, k]],
                          quad.uv[[i, j, k]], normal, quad.emission, texture, light)
    return Image.fromarray(canvas, "RGBA")


def save_png(quads: list[Quad], reader: ResourceReader, path: Path,
             size: int) -> None:
    # 2x supersampling smooths the silhouette while preserving nearest-texel UVs.
    image = render(quads, reader, size * 2).resize((size, size), Image.Resampling.LANCZOS)
    image.save(path, optimize=True)


def gif_frame_delays_ms(period_seconds: float, frame_count: int) -> list[int]:
    """Split the period into equal samples using GIF's 10ms delay granularity."""
    period_centiseconds = round(period_seconds * 100)
    if period_centiseconds < frame_count:
        raise ValueError("animation period is too short for nonzero 10ms GIF frame delays")
    # Error diffusion keeps cumulative timing within one centisecond and the total
    # exactly equal to the nearest GIF-representable period.
    delays_cs = [((i + 1) * period_centiseconds) // frame_count
                 - (i * period_centiseconds) // frame_count for i in range(frame_count)]
    return [delay * 10 for delay in delays_cs]


def save_gif(frames: list[Image.Image], path: Path, frame_delays_ms: list[int]) -> None:
    if not frames:
        raise ValueError("cannot encode an empty animation")
    if len(frame_delays_ms) != len(frames) or any(delay <= 0 or delay % 10 for delay in frame_delays_ms):
        raise ValueError("each GIF frame needs a positive delay in 10ms increments")
    # Derive one shared palette from all visible pixels. Reserve palette index 255
    # for transparency; cutout pixels keep a clean edge against README backgrounds.
    visible_samples = []
    for frame in frames:
        rgba = np.asarray(frame, dtype=np.uint8)
        visible = rgba[:, :, 3] >= 112
        if np.any(visible):
            visible_samples.append(rgba[visible, :3])
    sample = np.concatenate(visible_samples, axis=0)
    palette_source = Image.fromarray(sample.reshape(-1, 1, 3), "RGB")
    palette_source = palette_source.quantize(colors=255, method=Image.Quantize.MEDIANCUT)
    palette = palette_source.getpalette() or [0] * 768
    palette.extend([0] * (768 - len(palette)))
    palette[255 * 3:255 * 3 + 3] = [255, 0, 255]
    encoded = []
    for frame in frames:
        # Give Pillow an image carrying the exact shared palette.
        pal_img = Image.new("P", (1, 1))
        pal_img.putpalette(palette)
        pal_img = frame.convert("RGB").quantize(palette=pal_img, dither=Image.Dither.NONE)
        indices = np.asarray(pal_img, dtype=np.uint8).copy()
        indices[np.asarray(frame, dtype=np.uint8)[:, :, 3] < 112] = 255
        pal_img = Image.fromarray(indices, "P")
        pal_img.putpalette(palette)
        encoded.append(pal_img)
    encoded[0].save(path, save_all=True, append_images=encoded[1:], duration=frame_delays_ms,
                     loop=0, disposal=2, transparency=255, optimize=False)


def block_resources(reader: ResourceReader, name: str) -> tuple[list[Quad], dict | None]:
    model = reader.json(f"{ASSET_PREFIX}models/block/{name}.json")
    base = static_quads(model)
    if name not in TABLES:
        return base, None
    animation = reader.json(f"{ASSET_PREFIX}conversion_table/{name}.json")
    if animation.get("format") != 2:
        raise ValueError(f"{name}: expected conversion-table format 2")
    return base, animation


def image_hash(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, help="read final packaged resources from this mod JAR")
    parser.add_argument("--size", type=int, default=512, help="PNG/GIF frame edge in pixels (default: 512)")
    parser.add_argument("--frames", type=int, default=24, help="frames per animated table GIF (default: 24)")
    parser.add_argument("--animations-only", action="store_true",
                        help="refresh GIFs and manifest while reusing verified static renders")
    args = parser.parse_args()
    if args.size < 128 or args.frames < 4:
        parser.error("--size must be >=128 and --frames must be >=4")
    jar_path = args.jar.resolve() if args.jar else None
    if jar_path is not None and not jar_path.is_file():
        parser.error(f"JAR not found: {jar_path}")
    if args.animations_only and jar_path is None:
        parser.error("--animations-only requires --jar so existing static renders can be verified")

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    reader = ResourceReader(jar_path)
    output_records = {}
    old_manifest = None
    jar_sha256 = hashlib.sha256(jar_path.read_bytes()).hexdigest() if jar_path else None
    try:
        if args.animations_only:
            manifest_path = OUT_DIR / "manifest.json"
            if not manifest_path.is_file():
                raise ValueError("--animations-only requires an existing render manifest")
            old_manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            old_settings = old_manifest.get("render_settings", {})
            if old_settings.get("size") != args.size or old_settings.get("frames") != args.frames:
                raise ValueError("--animations-only size/frames must match the existing render manifest")
            old_inputs = old_manifest.get("inputs", {})
            if not old_inputs:
                raise ValueError("existing manifest has no resource hashes to verify")
            for rel, expected in old_inputs.items():
                actual = hashlib.sha256(reader.read(rel)).hexdigest()
                if actual != expected:
                    raise ValueError(f"packaged input changed since static render: {rel}")
            old_outputs = old_manifest.get("outputs", {})
            for name in (*[f"{block}.png" for block in BLOCKS], "banner.png"):
                record = old_outputs.get(name)
                path = OUT_DIR / name
                if not record or not path.is_file() or image_hash(path) != record.get("sha256"):
                    raise ValueError(f"cannot reuse missing or changed static output: {name}")
                output_records[name] = record

        for name in BLOCKS:
            base, animation = block_resources(reader, name)
            if args.animations_only and name not in TABLES:
                continue
            if animation is None:
                quads = base
                save_png(quads, reader, OUT_DIR / f"{name}.png", args.size)
            else:
                if not args.animations_only:
                    at_zero = base + pose_animation(animation, 0)
                    save_png(at_zero, reader, OUT_DIR / f"{name}.png", args.size)
                duration = animation_period_seconds(animation)
                times = [duration * i / args.frames for i in range(args.frames)]
                posed_frames = [base + pose_animation(animation, tick) for tick in times]
                bounds_view = RasterView([q for group in posed_frames for q in group], args.size * 2)
                rendered = [render(quads, reader, args.size * 2, bounds_view)
                            .resize((args.size, args.size), Image.Resampling.LANCZOS)
                            for quads in posed_frames]
                delays_ms = gif_frame_delays_ms(duration, args.frames)
                save_gif(rendered, OUT_DIR / f"{name}.gif", delays_ms)
                output_records[f"{name}.gif"] = {
                    "frames": args.frames, "frame_size": [args.size, args.size],
                    "cycle_seconds": duration, "cycle_ms": sum(delays_ms),
                    "frame_durations_ms": delays_ms,
                    "animation_groups": [g["name"] for g in animation.get("groups", [])],
                    "sha256": image_hash(OUT_DIR / f"{name}.gif"),
                }
            if not args.animations_only:
                output_records[f"{name}.png"] = {
                    "size": [args.size, args.size], "sha256": image_hash(OUT_DIR / f"{name}.png"),
                }
            print(f"rendered {name}")

        # The banner is composed only after all five fresh block renders exist.
        if not args.animations_only:
            sys.path.insert(0, str(ROOT / "assets" / "tools"))
            import make_banner
            make_banner.main()
            banner = OUT_DIR / "banner.png"
            output_records["banner.png"] = {
                "size": list(Image.open(banner).size), "sha256": image_hash(banner),
            }
        manifest = {
            "renderer": "assets/tools/render_readme.py",
            "source": "jar" if jar_path else "src/main/resources",
            "jar": str(jar_path.relative_to(ROOT)) if jar_path and jar_path.is_relative_to(ROOT) else str(jar_path) if jar_path else None,
            "jar_sha256": jar_sha256,
            "render_settings": {"size": args.size, "frames": args.frames,
                                "gif_delay_resolution_ms": 10,
                                "gif_time_unit": "seconds, matching the Minecraft client renderer",
                                "camera": "orthographic north/east/top"},
            "inputs": dict(sorted(reader.hashes.items())),
            "outputs": output_records,
        }
        (OUT_DIR / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        print(f"wrote {OUT_DIR.relative_to(ROOT)} with {len(reader.hashes)} hashed resource files")
    finally:
        reader.close()


if __name__ == "__main__":
    main()
