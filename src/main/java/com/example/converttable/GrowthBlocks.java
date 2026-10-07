package com.example.converttable;

import java.util.Set;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

/** The fourth-tier source and its separate, low catalyst pedestal. */
public final class GrowthBlocks {
    public static final CrystalTableBlock CRYSTAL = registerCrystal();
    public static final CatalystPedestalBlock CATALYST = registerCatalyst();
    public static final BlockEntityType<CrystalTableBlockEntity> CRYSTAL_ENTITY = Registry.register(
        BuiltInRegistries.BLOCK_ENTITY_TYPE, ConvertTable.id("crystal_table"),
        new BlockEntityType<>(CrystalTableBlockEntity::new, Set.of(CRYSTAL)));
    public static final BlockEntityType<CatalystPedestalBlockEntity> CATALYST_ENTITY = Registry.register(
        BuiltInRegistries.BLOCK_ENTITY_TYPE, ConvertTable.id("catalyst_pedestal"),
        new BlockEntityType<>(CatalystPedestalBlockEntity::new, Set.of(CATALYST)));

    private GrowthBlocks() { }

    private static CrystalTableBlock registerCrystal() {
        var id = ConvertTable.id("crystal_table");
        var key = ResourceKey.create(Registries.BLOCK, id);
        var block = new CrystalTableBlock(BlockBehaviour.Properties.of().setId(key)
            .strength(3.5F, 8F).requiresCorrectToolForDrops().sound(SoundType.AMETHYST)
            .noOcclusion().lightLevel(state -> 5).pushReaction(PushReaction.IMMOVEABLE));
        Registry.register(BuiltInRegistries.BLOCK, key, block);
        registerItem(id, block);
        return block;
    }

    private static CatalystPedestalBlock registerCatalyst() {
        var id = ConvertTable.id("catalyst_pedestal");
        var key = ResourceKey.create(Registries.BLOCK, id);
        var block = new CatalystPedestalBlock(BlockBehaviour.Properties.of().setId(key)
            .strength(3.5F, 8F).requiresCorrectToolForDrops().sound(SoundType.CALCITE)
            .noOcclusion().lightLevel(state -> 3).pushReaction(PushReaction.IMMOVEABLE));
        Registry.register(BuiltInRegistries.BLOCK, key, block);
        registerItem(id, block);
        return block;
    }

    private static void registerItem(net.minecraft.resources.Identifier id, net.minecraft.world.level.block.Block block) {
        var key = ResourceKey.create(Registries.ITEM, id);
        Registry.register(BuiltInRegistries.ITEM, key,
            new BlockItem(block, ModItemPresentation.apply(new Item.Properties().setId(key)
                .useBlockDescriptionPrefix(), id.getPath(), "block.convert_table." + id.getPath())));
    }

    public static void initialize() {
        GrowthMenus.initialize();
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> {
            entries.accept(CRYSTAL);
            entries.accept(CATALYST);
        });
    }
}
