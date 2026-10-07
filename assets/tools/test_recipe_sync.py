"""Regression checks for recipe maintenance; does not modify project data."""
import copy
import tomllib
import unittest

import sync_recipes as sync


class RecipeSyncTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.plan = tomllib.loads(sync.PLAN.read_text(encoding="utf-8-sig"))
        cls.conversion = sync.export(cls.plan)
        cls.growth = sync.read(sync.GROWTH)
        cls.names = sync.read(sync.NAMES)
        cls.crafts = {p.name: sync.read(p) for p in sorted(sync.CRAFTING.glob("*.json"))}

    def validate(self, conversion=None, growth=None, names=None):
        sync.validate(conversion or self.conversion, growth or self.growth,
                      self.crafts, names or self.names)

    def test_current_sources_and_generated_files_agree(self):
        self.validate()
        self.assertEqual(self.conversion, sync.read(sync.DEFAULT))
        self.assertEqual(self.conversion, sync.read(sync.EXAMPLE))

    def test_export_is_repeatable_without_mutating_source(self):
        plan = copy.deepcopy(self.plan)
        original = copy.deepcopy(plan)
        self.assertEqual(sync.export(plan), sync.export(plan))
        self.assertEqual(plan, original)

    def test_export_preserves_disabled_entries(self):
        plan = copy.deepcopy(self.plan)
        plan["group"][0]["enabled"] = False
        plan["recipe"][0]["enabled"] = False
        result = sync.export(plan)
        self.assertIs(result["groups"][0]["enabled"], False)
        self.assertIs(result["advanced"][0]["enabled"], False)

    def test_coral_templates_expand_by_species_with_bucket(self):
        for template in ("ADV-009", "ADV-010", "ADV-011"):
            entries = [r for r in self.conversion["advanced"] if r["source_id"] == template]
            self.assertEqual(len(entries), 5)
            self.assertEqual(len({r["input"] for r in entries}), 5)
            for entry in entries:
                self.assertEqual(entry["returns"], [{"item": "minecraft:bucket", "count": 1}])
                self.assertNotIn("*", entry["input"] + entry["output"])
                self.assertNotIn("same_species", entry["output"])

    def test_duplicate_growth_target_is_rejected(self):
        growth = copy.deepcopy(self.growth)
        duplicate = dict(growth["recipes"][0], id="convert_table:growth/duplicate")
        growth["recipes"].append(duplicate)
        with self.assertRaisesRegex(ValueError, "duplicate catalyst/output"):
            self.validate(growth=growth)

    def test_ambiguous_advanced_recipe_is_rejected(self):
        conversion = copy.deepcopy(self.conversion)
        conversion["advanced"].append(dict(conversion["advanced"][0], id="another_recipe"))
        with self.assertRaisesRegex(ValueError, "ambiguous advanced"):
            self.validate(conversion=conversion)

    def test_random_advanced_outputs_keep_primary_and_check_all_targets(self):
        entry = next(recipe for recipe in self.conversion["advanced"] if len(recipe.get("outputs", [])) > 1)
        self.assertIn(entry["output"], entry["outputs"])
        self.validate()
        conversion = copy.deepcopy(self.conversion)
        entry = next(recipe for recipe in conversion["advanced"] if len(recipe.get("outputs", [])) > 1)
        entry["outputs"].remove(entry["output"])
        with self.assertRaisesRegex(ValueError, "must include primary output"):
            self.validate(conversion=conversion)
        conversion = copy.deepcopy(self.conversion)
        entry = next(recipe for recipe in conversion["advanced"] if len(recipe.get("outputs", [])) > 1)
        entry["outputs"] = [entry["output"]]
        with self.assertRaisesRegex(ValueError, "need 2..128 item IDs"):
            self.validate(conversion=conversion)

    def test_shapeless_single_stick_recipe_is_valid(self):
        recipe = self.crafts["connection_rod.json"]
        self.assertEqual(recipe["type"], "minecraft:crafting_shapeless")
        self.assertEqual(recipe["ingredients"], ["minecraft:stick"])
        self.validate()
        crafts = dict(self.crafts)
        crafts["connection_rod.json"] = copy.deepcopy(recipe)
        crafts["connection_rod.json"]["ingredients"] = []
        with self.assertRaisesRegex(ValueError, "1..9 ingredients"):
            sync.validate(self.conversion, self.growth, crafts, self.names)

    def test_missing_chinese_name_is_rejected(self):
        names = dict(self.names)
        del names[self.growth["recipes"][0]["catalyst"]]
        with self.assertRaisesRegex(ValueError, "verified Chinese name"):
            self.validate(names=names)

    def test_zero_fractional_boolean_costs_are_rejected(self):
        for cost in (0, -1, 1.5, True):
            with self.subTest(cost=cost):
                growth = copy.deepcopy(self.growth)
                growth["recipes"][0]["cost"] = cost
                with self.assertRaisesRegex(ValueError, "expected integer"):
                    self.validate(growth=growth)

    def test_sculk_advanced_phase_zero_and_legacy_values_are_accepted(self):
        conversion = copy.deepcopy(self.conversion)
        conversion["settings"]["sculk"]["advanced_charge_per_operation"] = 0
        self.validate(conversion=conversion)
        conversion["settings"]["sculk"]["advanced_charge_per_operation"] = 12
        self.validate(conversion=conversion)
        self.assertEqual(conversion["settings"]["sculk"]["ordinary_souls_per_batch"], 3)
        for value in (0, -1, 4097, 1.5, True):
            with self.subTest(ordinary_souls_per_batch=value):
                invalid = copy.deepcopy(self.conversion)
                invalid["settings"]["sculk"]["ordinary_souls_per_batch"] = value
                with self.assertRaisesRegex(ValueError, "ordinary_souls_per_batch: expected integer 1..4096"):
                    self.validate(conversion=invalid)

    def test_duplicate_group_item_is_rejected(self):
        conversion = copy.deepcopy(self.conversion)
        conversion["groups"][0]["items"].append(conversion["groups"][0]["items"][0])
        with self.assertRaisesRegex(ValueError, "distinct items"):
            self.validate(conversion=conversion)


if __name__ == "__main__":
    unittest.main()
