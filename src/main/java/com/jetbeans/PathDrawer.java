package com.jetbeans;

import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.List;

public class PathDrawer {

    public static void drawPath(ServerWorld world, BlockPos from, BlockPos to) {
        List<BlockPos> pathNodes = AStarPathfinder.findPath(world, from, to);

        placePathPatch(world, from, 2);
        placePathPatch(world, to, 2);

        for (int i = 0; i < pathNodes.size(); i++) {
            BlockPos node = pathNodes.get(i);

            int perpX = 0, perpZ = 1;
            if (i + 1 < pathNodes.size()) {
                BlockPos next = pathNodes.get(i + 1);
                int dx = Math.abs(next.getX() - node.getX());
                int dz = Math.abs(next.getZ() - node.getZ());
                perpX = (dx >= dz) ? 0 : 1;
                perpZ = (dx >= dz) ? 1 : 0;
            }

            for (int offset = -1; offset <= 1; offset++) {
                BlockPos surface = world.getTopPosition(
                        Heightmap.Type.WORLD_SURFACE_WG,
                        new BlockPos(
                                node.getX() + perpX * offset,
                                0,
                                node.getZ() + perpZ * offset
                        )
                ).down();

                if (isNaturalGround(world.getBlockState(surface))) {
                    world.setBlockState(surface, Blocks.DIRT_PATH.getDefaultState());
                }
            }
        }
    }

    public static BlockPos findNearestPathBlock(ServerWorld world, BlockPos origin, int maxRadius) {
        int originX = origin.getX();
        int originZ = origin.getZ();

        for (int radius = 4; radius <= maxRadius; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) != radius && Math.abs(z) != radius) continue;

                    BlockPos surfacePos = world.getTopPosition(
                            Heightmap.Type.WORLD_SURFACE_WG,
                            new BlockPos(originX + x, 0, originZ + z)
                    ).down();

                    if (world.getBlockState(surfacePos).getBlock() == Blocks.DIRT_PATH) {
                        return surfacePos;
                    }
                }
            }
        }
        return null;
    }

    public static void placePathPatch(ServerWorld world, BlockPos center, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos surface = world.getTopPosition(
                        Heightmap.Type.WORLD_SURFACE_WG,
                        new BlockPos(center.getX() + x, 0, center.getZ() + z)
                ).down();

                if (isNaturalGround(world.getBlockState(surface))) {
                    world.setBlockState(surface, Blocks.DIRT_PATH.getDefaultState());
                }
            }
        }
    }

    public static boolean isNaturalGround(BlockState state) {
        net.minecraft.block.Block block = state.getBlock();
        return block == Blocks.GRASS_BLOCK
                || block == Blocks.DIRT
                || block == Blocks.COARSE_DIRT
                || block == Blocks.SAND
                || block == Blocks.GRAVEL
                || block == Blocks.STONE
                || block == Blocks.DIRT_PATH
                || block == Blocks.SNOW_BLOCK
                || block == Blocks.PODZOL;
    }
}