"""Export the approved TOML plan into the runtime JSON catalogue.
Run from any directory. --example also refreshes the workspace example (never the launcher instance).
"""
from pathlib import Path
import argparse, json, tomllib

ROOT = Path(__file__).resolve().parents[2]
def export(plan):
    def item(value): return "minecraft:" + value.split("|")[0]
    groups = [dict(g, items=[item(i) for i in g["items"]], enabled=True) for g in plan["group"]]
    advanced = []
    for recipe in plan["recipe"]:
        species_list = ["tube", "brain", "bubble", "fire", "horn"] if "*" in recipe["input"] else [None]
        for species in species_list:
            value = dict(recipe, enabled=True, source_id=recipe["id"])
            value["id"] = recipe["id"].lower() + ("_" + species if species else "")
            for key in ["input", "output", "catalyst"]:
                if key in value:
                    value[key] = item(value[key]).replace("*", species or "").replace("same_species", species or "same_species")
            value["returns"] = [{"item": "minecraft:bucket", "count": 1}] if recipe["id"] in ["ADV-009", "ADV-010", "ADV-011"] else []
            advanced.append(value)
    settings = {key: plan[key] for key in ["piglin", "end", "sculk", "rules"]}
    settings["piglin"]["cost"] = item(settings["piglin"]["cost"])
    settings["end"]["fuel"] = item(settings["end"]["fuel"])
    return dict(schema_version=1, source_plan="26.3-v1.4", execution_enabled=True,
                settings=settings, groups=groups, advanced=advanced)
if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--example", action="store_true", help="Overwrite workspace config example too")
    args = parser.parse_args()
    plan = tomllib.loads((ROOT / "瑙勫垝/conversion_tables_26.3_v1.4.toml").read_text(encoding="utf-8-sig"))
    data = export(plan)
    paths = [ROOT / "src/main/resources/convert_table/default_recipes.json"]
    if args.example: paths.append(ROOT / "config/convert_table/recipes.json")
    for path in paths:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{len(data['groups'])} groups; {len(data['advanced'])} concrete advanced recipes")
