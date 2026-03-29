package com.jetbeans;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillageExpansionMod implements ModInitializer {

	public static final String MOD_ID = "villageexpansion";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Village Expansion initialized");
		BuildingRequirementsLoader.load();
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

		// Tick construction every 20 ticks (every second)
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

			// Don't check resources if already building
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
