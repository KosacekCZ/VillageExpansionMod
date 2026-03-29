package com.jetbeans;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConstructionManager {

    public static final int MAX_CONCURRENT_BUILDS = 1;

    public static void tick(ServerWorld world, VillageData village,
                            VillageState state, long currentTick) {
        List<ConstructionProject> toRemove = new ArrayList<>();

        for (ConstructionProject project : village.activeProjects) {
            if (project.complete) {
                toRemove.add(project);
                continue;
            }
            tickProject(world, village, project, state, currentTick);
        }

        if (!toRemove.isEmpty()) {
            village.activeProjects.removeAll(toRemove);
            state.markDirty();
        }
    }

    private static void tickProject(ServerWorld world, VillageData village,
                                    ConstructionProject project, VillageState state,
                                    long currentTick) {
        if (project.totalLayers <= 0) {
            VillageExpansionMod.LOGGER.warn("Project {} has 0 layers, skipping",
                    project.buildingId);
            project.complete = true;
            return;
        }

        // Wait for next layer tick
        if (currentTick - project.lastLayerTick < project.ticksPerLayer) return;

        // All layers done
        if (project.currentLayer >= project.totalLayers) {
            BlockPos pathTarget = PathDrawer.findNearestPathBlock(world, project.pos, 64);
            if (pathTarget == null) {
                pathTarget = new BlockPos(
                        village.center.getX(), 64, village.center.getZ());
            }
            PathDrawer.drawPath(world, project.pos, pathTarget);

            project.complete = true;
            state.markDirty();
            VillageExpansionMod.LOGGER.info("Construction complete: {} at {}",
                    project.buildingId, project.pos);
            return;
        }

        // Place current layer
        int blocksPlaced = placeLayer(world, project, project.currentLayer);
        project.currentLayer++;
        project.lastLayerTick = currentTick;
        state.markDirty();

        VillageExpansionMod.LOGGER.info("Built layer {}/{} of {} ({} blocks placed)",
                project.currentLayer, project.totalLayers,
                project.buildingId, blocksPlaced);
    }

    private static int placeLayer(ServerWorld world, ConstructionProject project, int layer) {
        // Separate blocks into placement groups
        List<Map.Entry<BlockPos, BlockState>> solid = new ArrayList<>();
        List<Map.Entry<BlockPos, BlockState>> doors = new ArrayList<>();
        List<Map.Entry<BlockPos, BlockState>> fragile = new ArrayList<>();

        for (Map.Entry<BlockPos, BlockState> entry : project.blockData.entrySet()) {
            BlockPos localPos = entry.getKey();
            if (localPos.getY() != layer) continue;

            BlockState state = entry.getValue();
            if (state.isAir()) continue;

            if (isDoor(state)) {
                doors.add(entry);
            } else if (isFragile(state)) {
                fragile.add(entry);
            } else {
                solid.add(entry);
            }
        }

        int count = 0;

        // Place solid blocks first, and handle door tops immediately after bottoms
        for (Map.Entry<BlockPos, BlockState> entry : solid) {
            BlockPos worldPos = project.pos.add(entry.getKey());
            world.setBlockState(worldPos, entry.getValue(), 3);
            count++;
        }

        // Place door bottoms and immediately place their tops
        for (Map.Entry<BlockPos, BlockState> entry : doors) {
            BlockPos localPos = entry.getKey();
            BlockPos worldPos = project.pos.add(localPos);
            BlockState state = entry.getValue();

            world.setBlockState(worldPos, state, 3);
            count++;

            // If this is a door bottom, place the top half immediately
            if (state.contains(net.minecraft.state.property.Properties.DOUBLE_BLOCK_HALF)) {
                net.minecraft.util.math.Direction.Axis axis =
                        net.minecraft.util.math.Direction.Axis.Y;

                boolean isBottom = state.get(
                        net.minecraft.state.property.Properties.DOUBLE_BLOCK_HALF)
                        == net.minecraft.block.enums.DoubleBlockHalf.LOWER;

                if (isBottom) {
                    BlockPos topLocal = localPos.up();
                    BlockState topState = project.blockData.get(topLocal);
                    if (topState != null) {
                        world.setBlockState(worldPos.up(), topState, 3);
                        count++;
                    }
                }
            }
        }

        // Place fragile blocks last
        for (Map.Entry<BlockPos, BlockState> entry : fragile) {
            BlockPos worldPos = project.pos.add(entry.getKey());
            world.setBlockState(worldPos, entry.getValue(), 3);
            count++;
        }

        return count;
    }

    private static boolean isDoor(BlockState state) {
        return state.getBlock() instanceof net.minecraft.block.DoorBlock;
    }

    private static boolean isFragile(BlockState state) {
        net.minecraft.block.Block block = state.getBlock();
        return block instanceof net.minecraft.block.FlowerBlock
                || block instanceof net.minecraft.block.TallPlantBlock
                || block instanceof net.minecraft.block.AbstractPressurePlateBlock
                || block instanceof net.minecraft.block.TorchBlock
                || block instanceof net.minecraft.block.LanternBlock
                || block instanceof net.minecraft.block.CarpetBlock
                || block instanceof net.minecraft.block.SlabBlock
                || state.getBlock() == net.minecraft.block.Blocks.SNOW;
    }
}