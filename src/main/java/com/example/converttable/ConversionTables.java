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

public final class ConversionTables {
	public static final ConversionTableBlock BLACK_GOLD = register("black_gold");
	public static final ConversionTableBlock END = register("end");
	public static final ConversionTableBlock SCULK = register("sculk");
	public static final BlockEntityType<ConversionTableBlockEntity> BLOCK_ENTITY = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE, ConvertTable.id("conversion_table"),
		new BlockEntityType<>(ConversionTableBlockEntity::new, Set.of(BLACK_GOLD, END, SCULK)));

	private ConversionTables() { }

	private static ConversionTableBlock register(String variant) {
		var id = ConvertTable.id(variant + "_conversion_table");
		var blockKey = ResourceKey.create(Registries.BLOCK, id);
		var block = new ConversionTableBlock(BlockBehaviour.Properties.of()
			.setId(blockKey).strength(3.5F, 8F).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE)
			.noOcclusion().lightLevel(state -> 6).pushReaction(PushReaction.IMMOVEABLE));
		Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
		var itemKey = ResourceKey.create(Registries.ITEM, id);
		Registry.register(BuiltInRegistries.ITEM, itemKey,
			new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	public static void initialize() {
        ConversionMenus.initialize();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> {
			entries.accept(BLACK_GOLD);
			entries.accept(END);
			entries.accept(SCULK);
		});
	}
}
