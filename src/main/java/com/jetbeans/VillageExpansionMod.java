package com.jetbeans;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillageExpansionMod implements ModInitializer {

	public static final String MOD_ID = "villageexpansion";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** The Village Ledger item */
	public static final VillageBookItem VILLAGE_LEDGER = new VillageBookItem(
			new net.minecraft.item.Item.Settings()
					.maxCount(1)
	);

	@Override
	public void onInitialize() {
		LOGGER.info("Village Expansion initialized");

		// Load building definitions
		BuildingRequirementsLoader.load();

		// Register the Village Ledger item
		Registry.register(
				Registry.ITEM,
				new Identifier(MOD_ID, "village_ledger"),
				VILLAGE_LEDGER
		);

		// Build the creative tab here — after VILLAGE_LEDGER is registered — to avoid
		// static field ordering issues. appendItems explicitly adds our item to the tab.
		FabricItemGroupBuilder.create(new Identifier(MOD_ID, "main"))
				.icon(() -> new ItemStack(VILLAGE_LEDGER))
				.appendItems(stacks -> stacks.add(new ItemStack(VILLAGE_LEDGER)))
				.build();

		LOGGER.info("Registered item: villageexpansion:village_ledger");

		// Register packet channel (server→client; no server-side receiver needed)
		VillageBookPacketHandler.register();
		LodestoneInteractionHandler.register();

		// World tick events
		ServerTickEvents.END_WORLD_TICK.register(this::onWorldTick);
	}

	private void onWorldTick(ServerWorld world) {
		if (!world.getRegistryKey().equals(net.minecraft.world.World.OVERWORLD)) return;

		if (world.getTime() % 200 == 0) {
			VillageScanner.scan(world);
		}

		if (world.getTime() % 100 == 0) {
			processGrowth(world);
		}

		// Tick construction every 20 ticks (once per second)
		if (world.getTime() % 20 == 0) {
			VillageState state = VillageState.get(world);
			long currentTick = world.getTime();
			for (VillageData village : state.getAll()) {
				if (!village.activeProjects.isEmpty()) {
					ConstructionManager.tick(world, village, state, currentTick);
				}
			}
		}
	}

	private void processGrowth(ServerWorld world) {
		VillageState state = VillageState.get(world);
		long currentTick = world.getTime();

		for (VillageData village : state.getAll()) {
			if (!village.hasTownHall) continue;
			if (!village.activeProjects.isEmpty()) continue;

			boolean resourcesDelivered = ChestMonitor.checkAndConsume(world, village);
			if (resourcesDelivered) {
				village.onGrowth(currentTick);
				state.markDirty();
				StructurePlacer.placeBuilding(world, village, state, currentTick);
			}
		}
	}
}