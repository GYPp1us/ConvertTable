package com.example.converttable;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public final class ConvertTableClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModItemTooltips.initialize();
		ClientRecipeCatalog.initialize();
        ClientVersionGate.initialize();
        GrowthPlacementHint.initialize();
        BlockEntityRenderers.register(ConversionTables.BLOCK_ENTITY, ConversionTableRenderer::new);
		BlockEntityRenderers.register(GrowthBlocks.CATALYST_ENTITY, CatalystPedestalRenderer::new);
		net.minecraft.client.gui.screens.MenuScreens.register(ConversionMenus.PIGLIN, ConversionTableScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(ConversionMenus.END, ConversionTableScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(ConversionMenus.SCULK, ConversionTableScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(GrowthMenus.CRYSTAL, CrystalTableScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(GrowthMenus.CATALYST, CatalystPedestalScreen::new);
        ConvertTable.LOGGER.info("ConvertTable client initialized");
	}
}
