from pathlib import Path
import re,tomllib,json
root=Path(__file__).resolve().parents[1]
src=root/'conversion_tables_26.3_v1.3.toml'
text=src.read_text(encoding='utf-8')
text=text.replace('version = "1.3"','version = "1.4"').replace('schema = 1','schema = 2')
text=text.replace('xp_is_points = true','''# v1.4 planning draft; ordinary conversion still inherits End phase fuel.
advanced_charge_per_operation = 1
advanced_cost = "death_count"
death_cost_balance = "draft_preserve_previous_numeric_costs"
death_count_per_mob = 1
death_count_capacity = 4096
death_surface = "minecraft:sculk"
requires_connected_surface = true
requires_player_kill = false
consume_player_xp = false
intercept_xp_orbs = false
cancel_vanilla_death_effects = false

[sculk.network]
status = "draft"
connectivity = "face_adjacent_with_actual_vein_face_paths"
max_manhattan_distance = 16
max_nodes = 1024
loaded_chunks_only = true
nodes = ["minecraft:sculk", "minecraft:sculk_vein", "minecraft:sculk_catalyst", "minecraft:sculk_sensor", "minecraft:calibrated_sculk_sensor", "minecraft:sculk_shrieker"]
shared_death_owner = "nearest_table_by_network_path_then_coordinates"
unknown_region = "show_incomplete_not_disconnected"

[sculk.counting]
status = "draft"
eligible = "mob_death_supported_on_connected_sculk_top"
exclude = ["minecraft:player", "minecraft:armor_stand", "non_living_entity"]
unique_death_event = true
store = "table_block_entity"
refund_uncommitted = true
retain_when_surface_disconnects = true

[ui]
status = "draft"
input_modes = ["slot", "linked_containers", "container_contents"]
ordinary_match_modes = ["exact_item", "same_group"]
default_match_mode = "exact_item"
skip_target_item = true
preview_before_start = true
target_selection = ["search", "current_group", "favorites", "recent"]
container_roles = ["input", "output", "ignore"]
linked_container_limit_counts_both_roles = true
contents_mode_requires_explicit_selection = true
piglin_target_selector = false
sculk_projection = ["connected_nodes", "eligible_ground", "ownership", "disconnected", "unknown"]
advanced_auto_false = "manual_single_operation_only"''')
text='\n'.join(('deaths = '+line[5:]) if line.startswith('xp = ') else line for line in text.split('\n'))
text=text.replace('鸡蛋 + 生物样本 + XP。','鸡蛋 + 生物样本 + 死亡计数。')
text='# v1.4 DESIGN DRAFT ONLY; not loaded by the mod.\n# Death costs retain v1.3 numbers provisionally; they need gameplay balancing.\n'+text
dst=root/'conversion_tables_26.3_v1.4.toml';dst.write_text(text,encoding='utf-8')
a=tomllib.loads(src.read_text(encoding='utf-8'));b=tomllib.loads(text)
assert a['group']==b['group']
assert len(b['recipe'])==40
for old,new in zip(a['recipe'],b['recipe']):
 assert new['deaths']==old['xp'] and 'xp' not in new
 assert {k:v for k,v in old.items() if k not in ('xp','note')}=={k:v for k,v in new.items() if k not in ('deaths','note')}
print('v1.4: 99 groups preserved; 40 recipe costs migrated xp -> deaths; materials and auto/unlock preserved.')


