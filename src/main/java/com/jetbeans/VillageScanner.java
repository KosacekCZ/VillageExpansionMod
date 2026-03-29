package com.jetbeans;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public class VillageScanner {

    private static final int SEARCH_RADIUS = 100;

    public static void scan(ServerWorld world) {
        VillageState state = VillageState.get(world);

        for (ServerPlayerEntity player : world.getPlayers()) {
            BlockPos playerPos = player.getBlockPos();

            BlockPos villagePos = world.locateStructure(
                    net.minecraft.tag.ConfiguredStructureFeatureTags.VILLAGE,
                    playerPos,
                    SEARCH_RADIUS,
                    false
            );

            if (villagePos != null && !state.hasVillageNear(villagePos)) {
                state.addVillage(villagePos, world.getTime());
                VillageExpansionMod.LOGGER.info("Registered new village at {}", villagePos);

                VillageData village = state.getVillageNear(villagePos);
                if (village != null) {
                    StructurePlacer.placeTownHall(world, village, state);
                }
            }
        }
    }
}