"""Validate recipe sources and regenerate runtime JSON and the player workbook.

python assets/tools/sync_recipes.py          # validate, export, rebuild Excel
python assets/tools/sync_recipes.py --check  # read-only consistency check
python assets/tools/sync_recipes.py --find WOOD-01
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import tomllib
import zipfile
from xml.etree import ElementTree as ET

from export_recipe_config import export

ROOT = Path(__file__).resolve().parents[2]
PLAN = ROOT / "规划/conversion_tables_26.3_v1.4.toml"
DEFAULT = ROOT / "src/main/resources/convert_table/default_recipes.json"
EXAMPLE = ROOT / "config/convert_table/recipes.json"
GROWTH = ROOT / "src/main/resources/data/convert_table/growth_recipes.json"
FISHING = ROOT / "src/main/resources/data/convert_table/fishing_materials.json"
CRAFTING = ROOT / "src/main/resources/data/convert_table/recipe"
NAMES = ROOT / "assets/tools/recipe_item_names.json"
OUT = ROOT / "outputs/01a0f354-50a1-7850-9a3e-c4f75b90728b"
BOOK = OUT / "ConvertTable配方.xlsx"
MANIFEST = OUT / "recipes.manifest.json"
BUILDER = ROOT / "assets/tools/build_recipe_workbook.mjs"
ID = re.compile(r"[a-z0-9_.-]+:[a-z0-9_./-]+")


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def require(ok, message):
    if not ok:
        raise ValueError(message)


def integer(value, low, high, label):
    require(type(value) is int and low <= value <= high,
            f"{label}: expected integer {low}..{high}, got {value!r}")


def item(value, names, label):
    require(isinstance(value, str) and ID.fullmatch(value) and value != "minecraft:air",
            f"{label}: invalid item ID {value!r}")
    require(value in names and isinstance(names[value], str) and names[value].strip(),
            f"{label}: add verified Chinese name for {value} to {NAMES.relative_to(ROOT)}")


def validate(conversion, growth, crafts, names):
    require(conversion.get("schema_version") == 1, "conversion schema_version must be 1")
    require(type(conversion.get("execution_enabled")) is bool, "execution_enabled must be boolean")
    settings = conversion["settings"]
    for section, key, low, high in [("piglin", "cost_n", 1, 64),
                                   ("end", "fuel_charge", 1, 4096),
                                   ("end", "charge_per_batch", 1, 4096),
                                   ("sculk", "advanced_charge_per_operation", 0, 4096),
                                   ("sculk", "ordinary_souls_per_batch", 1, 4096),
                                   ("sculk", "death_count_per_mob", 1, 4096),
                                   ("sculk", "death_count_capacity", 1, 4096)]:
        integer(settings[section][key], low, high, f"settings.{section}.{key}")
    require(settings["sculk"]["advanced_cost"] == "death_count"
            and settings["sculk"]["consume_player_xp"] is False, "sculk must use death_count, not player XP")
    require(type(settings["sculk"].get("requires_player_kill")) is bool,
            "sculk.requires_player_kill must be boolean")
    item(settings["piglin"]["cost"], names, "piglin.cost")
    item(settings["end"]["fuel"], names, "end.fuel")
    require(len(conversion["groups"]) <= 256 and len(conversion["advanced"]) <= 1024,
            "conversion catalogue exceeds runtime limits")
    seen = set()
    advanced_pairs = set()
    expanded = 0
    for collection in ("groups", "advanced"):
        for entry in conversion[collection]:
            key = entry["id"].lower()
            require(re.fullmatch(r"[a-z0-9_./-]+", key) and key not in seen,
                    f"invalid/duplicate conversion ID: {entry['id']}")
            seen.add(key)
            require(isinstance(entry["name"], str) and entry["name"], f"{key}: missing name")
            require(type(entry.get("enabled", True)) is bool, f"{key}: enabled must be boolean")
            if collection == "groups":
                require(entry["tier"] in ("piglin", "end", "sculk"), f"{key}: invalid tier")
                integer(entry["batch"], 1, 4096, f"{key}.batch")
                values = entry["items"]
                require(2 <= len(values) <= 128 and len(values) == len(set(values)),
                        f"{key}: need 2..128 distinct items")
                for value in values:
                    item(value, names, key)
                if entry.get("enabled", True):
                    expanded += len(values) * (len(values) - 1) * (2 if entry["tier"] != "sculk" else 1)
            else:
                for field in ("input", "output", "catalyst"):
                    if field in entry:
                        item(entry[field], names, f"{key}.{field}")
                        integer(entry[field + "_n"], 1, 4096, f"{key}.{field}_n")
                outputs = entry["outputs"] if "outputs" in entry else [entry["output"]]
                minimum = 2 if "outputs" in entry else 1
                require(isinstance(outputs, list) and minimum <= len(outputs) <= 128,
                        f"{key}.outputs: need {minimum}..128 item IDs")
                for value in outputs:
                    item(value, names, f"{key}.outputs")
                require(len(outputs) == len(set(outputs)),
                        f"{key}.outputs: item IDs must be distinct")
                require(entry["output"] in outputs,
                        f"{key}.outputs must include primary output {entry['output']}")
                integer(entry["deaths"], 1, 4096, f"{key}.deaths")
                require(type(entry["auto"]) is bool, f"{key}: auto must be boolean")
                if entry.get("enabled", True):
                    for output in outputs:
                        pair = (entry["input"], output)
                        require(pair not in advanced_pairs, f"ambiguous advanced input/output: {pair}")
                        advanced_pairs.add(pair)
                require(len(entry.get("returns", [])) <= 1, f"{key}: at most one return item")
                for remainder in entry.get("returns", []):
                    item(remainder["item"], names, f"{key}.returns")
                    integer(remainder["count"], 1, 4096, f"{key}.returns.count")
    require(expanded <= 50_000, "expanded viewer catalogue exceeds 50000")
    require(len(json.dumps(conversion, ensure_ascii=False, indent=2)) <= 250_000,
            "conversion JSON exceeds runtime character limit")
    require(growth.get("schema_version") == 2, "growth schema_version must be 2")
    seen, pairs = set(), set()
    for entry in growth["recipes"]:
        key = entry["id"]
        require(isinstance(key, str) and ID.fullmatch(key) and key not in seen,
                f"invalid/duplicate growth ID: {key}")
        seen.add(key)
        for field in ("catalyst", "source", "output"):
            item(entry[field], names, f"{key}.{field}")
        require(entry["source"] == entry["output"], f"{key}: original must match output")
        integer(entry["cost"], 1, 2_147_483_647, f"{key}.cost")
        pair = (entry["catalyst"], entry["output"])
        require(pair not in pairs, f"duplicate catalyst/output: {pair}")
        pairs.add(pair)
    for filename, recipe in crafts.items():
        if recipe["type"] == "minecraft:crafting_shaped":
            pattern = recipe["pattern"]
            require(1 <= len(pattern) <= 3 and 1 <= len(pattern[0]) <= 3
                    and len({len(row) for row in pattern}) == 1, f"{filename}: invalid pattern")
            used = set("".join(pattern)) - {" "}
            require(used == set(recipe["key"]), f"{filename}: pattern/key mismatch")
            for value in recipe["key"].values():
                item(value, names, filename)
        elif recipe["type"] == "minecraft:crafting_shapeless":
            ingredients = recipe["ingredients"]
            require(isinstance(ingredients, list) and 1 <= len(ingredients) <= 9,
                    f"{filename}: shapeless recipe needs 1..9 ingredients")
            for value in ingredients:
                item(value, names, filename)
        else:
            require(False, f"{filename}: unsupported crafting recipe type {recipe['type']!r}")
        item(recipe["result"]["id"], names, filename)
        integer(recipe["result"]["count"], 1, 64, f"{filename}.result.count")


def source_hashes():
    paths = [PLAN, DEFAULT, EXAMPLE, GROWTH, FISHING, NAMES, ROOT / "gradle.properties",
             Path(__file__), ROOT / "assets/tools/export_recipe_config.py", BUILDER]
    paths += sorted(CRAFTING.glob("*.json"))
    return {str(path.relative_to(ROOT)).replace("\\", "/"): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in paths}


def check_workbook(manifest):
    require(BOOK.exists(), "workbook missing; run python assets/tools/sync_recipes.py")
    require(manifest["sources"] == source_hashes(), "workbook is stale; run python assets/tools/sync_recipes.py")
    require(manifest["workbook_sha256"] == hashlib.sha256(BOOK.read_bytes()).hexdigest(),
            "workbook was edited or damaged; regenerate it from recipe sources")
    with zipfile.ZipFile(BOOK) as archive:
        require(archive.testzip() is None, "damaged workbook ZIP")
        ns = {"s": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
        root = ET.fromstring(archive.read("xl/workbook.xml"))
        require([s.get("name") for s in root.findall("s:sheets/s:sheet", ns)] ==
                ["配方总览", "普通转换", "幽匿进阶", "触媒增殖", "方块合成"], "unexpected workbook sheets")


def find_runtime(override):
    candidates = [Path(override)] if override else []
    candidates += [Path(sys.executable).resolve().parent.parent,
                   Path.home() / ".cache/codex-runtimes/codex-primary-runtime/dependencies"]
    if os.name == "nt":
        candidates += list(Path("C:/Users").glob("*/.cache/codex-runtimes/codex-primary-runtime/dependencies"))
    for candidate in candidates:
        node = candidate / ("node/bin/node.exe" if os.name == "nt" else "node/bin/node")
        modules = candidate / "node/node_modules"
        if node.exists() and (modules / "@oai/artifact-tool").exists():
            return node, modules
    raise ValueError("Bundled spreadsheet runtime unavailable. Use load_workspace_dependencies, then --runtime <dependencies directory>.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--find", metavar="ID", help="Print source entry by stable ID or item ID; does not write files")
    parser.add_argument("--runtime", help="Codex bundled dependencies directory")
    args = parser.parse_args()
    plan = tomllib.loads(PLAN.read_text(encoding="utf-8-sig"))
    conversion = export(plan)
    growth, names = read(GROWTH), read(NAMES)
    fishing = read(FISHING)
    crafts = {path.name: read(path) for path in sorted(CRAFTING.glob("*.json"))}
    validate(conversion, growth, crafts, names)
    require(fishing.get("schema_version") == 1, "fishing schema_version must be 1")
    fishing_pairs = set()
    for entry in fishing["entries"]:
        require(entry["table"] in ("fish", "treasure"), "unknown fishing pool")
        item(entry["item"], names, "fishing.item")
        integer(entry["weight"], 1, 2_147_483_647, "fishing.weight")
        integer(entry["min"], 1, 64, "fishing.min")
        integer(entry["max"], entry["min"], 64, "fishing.max")
        pair = (entry["table"], entry["item"])
        require(pair not in fishing_pairs, f"duplicate fishing entry: {pair}")
        fishing_pairs.add(pair)
    if args.find:
        matches = [entry for collection in (plan["group"], plan["recipe"], growth["recipes"])
                   for entry in collection if args.find.lower() in json.dumps(entry, ensure_ascii=False).lower()]
        print(json.dumps(matches, ensure_ascii=False, indent=2))
        require(matches, f"no entry matches {args.find}")
        return
    counts = {"groups": len(conversion["groups"]), "advanced": len(conversion["advanced"]),
              "growth": len(growth["recipes"]), "catalysts": len({r["catalyst"] for r in growth["recipes"]}),
              "crafting": len(crafts)}
    if args.check:
        for path in (DEFAULT, EXAMPLE):
            require(read(path) == conversion, f"{path.relative_to(ROOT)} differs from TOML; regenerate")
        require(MANIFEST.exists(), "workbook manifest missing; regenerate")
        manifest = read(MANIFEST)
        require(manifest["counts"] == counts, "workbook counts differ from sources")
        check_workbook(manifest)
        print("Recipe sources, runtime JSON, example and Excel agree: " + json.dumps(counts))
        return
    node, modules = find_runtime(args.runtime)  # fail before writing if unavailable
    for path in (DEFAULT, EXAMPLE):
        path.write_text(json.dumps(conversion, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    version = re.search(r"^version=(.+)$", (ROOT / "gradle.properties").read_text(), re.M).group(1).strip()
    payload = {"version": version, "conversion": conversion, "growth": growth, "fishing": fishing,
               "crafting": crafts, "names": names, "counts": counts,
               "sources": source_hashes()}
    OUT.mkdir(parents=True, exist_ok=True)
    # A temporary junction makes the bundled JS package available without npm installation.
    with tempfile.TemporaryDirectory(prefix="convert-table-recipes-") as temporary:
        directory = Path(temporary)
        link = directory / "node_modules"
        if os.name == "nt":
            subprocess.run(["cmd", "/c", "mklink", "/J", str(link), str(modules)], check=True, capture_output=True)
        else:
            link.symlink_to(modules, target_is_directory=True)
        try:
            runner = directory / BUILDER.name
            shutil.copyfile(BUILDER, runner)
            data = directory / "recipes.json"
            data.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8", newline="\n")
            subprocess.run([str(node), str(runner), str(data), str(OUT)], check=True)
        finally:
            # Remove the junction itself before cleaning the temporary directory.
            os.rmdir(link) if os.name == "nt" else link.unlink()
    manifest = {"version": version, "counts": counts, "sources": source_hashes(),
                "workbook_sha256": hashlib.sha256(BOOK.read_bytes()).hexdigest()}
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    check_workbook(manifest)
    print(f"Updated {BOOK.relative_to(ROOT)}: {counts}")


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    try:
        main()
    except (ValueError, KeyError, TypeError, OSError, subprocess.CalledProcessError) as error:
        print(f"Recipe sync failed: {error}", file=sys.stderr)
        sys.exit(1)
