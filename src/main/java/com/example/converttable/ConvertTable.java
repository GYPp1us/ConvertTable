package com.example.converttable;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConvertTable implements ModInitializer {
	public static final String MOD_ID = "convert_table";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ConversionTables.initialize();
        GrowthBlocks.initialize();
        RecipeConfig.initialize();
        SculkDeathCharging.initialize();
		LOGGER.info("ConvertTable loaded on Fabric for Minecraft 26.3");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
