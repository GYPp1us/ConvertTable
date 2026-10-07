package com.example.converttable;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.Vec3;

/** Checks the loaded vanilla loot tables, including their weighted rolls and actual registered relics. */
final class FishingMaterialsGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    static void run(MinecraftServer server) {
        var level=server.overworld();
        var registry=server.reloadableRegistries();
        var fish=registry.getLootTable(BuiltInLootTables.FISHING_FISH);
        var treasure=registry.getLootTable(BuiltInLootTables.FISHING_TREASURE);
        var ops=RegistryOps.create(JsonOps.INSTANCE,registry.lookup());
        for (var pair:List.of(java.util.Map.entry(fish,120),java.util.Map.entry(treasure,7))) {
            JsonObject data=LootTable.DIRECT_CODEC.encodeStart(ops,pair.getKey()).getOrThrow().getAsJsonObject();
            var pools=data.getAsJsonArray("pools");
            check(pools.size()==1,"Fishing material patch added a second roll/pool");
            int weight=0;
            for(var entry:pools.get(0).getAsJsonObject().getAsJsonArray("entries"))
                weight+=entry.getAsJsonObject().has("weight")?entry.getAsJsonObject().get("weight").getAsInt():1;
            check(weight==pair.getValue(),"Loaded fishing loot weight differs from the balanced catalogue: "+weight);
        }
        var params=new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,new Vec3(0,100,0))
            .withParameter(LootContextParams.TOOL,new ItemStack(net.minecraft.world.item.Items.FISHING_ROD))
            .create(LootContextParamSets.FISHING);
        var observed=new HashSet<Item>();
        for(int roll=0;roll<10_000;roll++) {
            var result=fish.getRandomItems(params,roll);
            check(result.size()==1 && result.getFirst().getCount()==1,"Fish-category roll duplicated a catch");
            observed.add(result.getFirst().getItem());
        }
        var poetry=java.util.Map.of(CraftMaterials.BOUGHBOUND_REVERIE,"枝节蔓生叶繁茂，百兽长栖息",
            CraftMaterials.STILLWATER_PALIMPSEST,"雪泥洗濯发与肤，明镜无风波",
            CraftMaterials.UNBROKEN_COGNIZANCE,"一日发蒙生灵智，代代相连结",
            CraftMaterials.UNWROUGHT_FACET,"泥土血肉皆不容，百相映其中");
        for(var item:poetry.keySet()) {
            check(observed.contains(item),"Registered shaping material was unobtainable from fishing: "+item);
            var tooltip=new java.util.ArrayList<net.minecraft.network.chat.Component>();
            item.appendHoverText(new ItemStack(item),Item.TooltipContext.of(level),TooltipDisplay.DEFAULT,tooltip::add,TooltipFlag.NORMAL);
            check(tooltip.size()==2 && tooltip.getFirst().getString().equals(poetry.get(item)),
                "Relic must preserve the user's exact single poetry line alongside its use hint");
            check(!tooltip.get(1).getString().isBlank() && !tooltip.get(1).getString().contains("tooltip.convert_table."),
                "Relic use hint is missing or untranslated");
            check(new ItemStack(item).getHoverName().getString().matches("[A-Za-z ]+"),"Relic name was translated or missing");
        }
        observed.clear();
        for(int roll=0;roll<1_000;roll++) {
            var result=treasure.getRandomItems(params,roll);
            check(result.size()==1,"Treasure-category roll duplicated a catch");
            var stack=result.getFirst();
            observed.add(stack.getItem());
            if(stack.is(net.minecraft.world.item.Items.AMETHYST_SHARD))
                check(stack.getCount()>=1 && stack.getCount()<=3,"Fishing crystal quantity escaped its range");
        }
        check(observed.contains(net.minecraft.world.item.Items.AMETHYST_SHARD)
            && observed.contains(net.minecraft.world.item.Items.NAME_TAG)
            && observed.contains(net.minecraft.world.item.Items.ENCHANTED_BOOK)
            && observed.size()==7,"Crystal addition removed a vanilla treasure");
        ConvertTable.LOGGER.info("FISHING_MATERIALS_TEST_PASS: loaded native fish/treasure pools retain 120/7 weights, one catch per roll, all four named relics and six vanilla treasures");
    }
}
