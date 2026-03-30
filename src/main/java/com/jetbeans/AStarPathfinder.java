package com.jetbeans;

import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.*;

public class AStarPathfinder {

    private static final int MAX_NODES = 10000;
    // Penalty for changing direction — encourages straight paths
    private static final float TURN_PENALTY = 2.0f;

    private static class Node implements Comparable<Node> {
        BlockPos pos;
        Node parent;
        float g, h;
        int dirX, dirZ; // direction we came from

        Node(BlockPos pos, Node parent, float g, float h, int dirX, int dirZ) {
            this.pos = pos;
            this.parent = parent;
            this.g = g;
            this.h = h;
            this.dirX = dirX;
            this.dirZ = dirZ;
        }

        float f() { return g + h; }

        @Override
        public int compareTo(Node other) {
            return Float.compare(this.f(), other.f());
        }
    }

    public static List<BlockPos> findPath(ServerWorld world, BlockPos from, BlockPos to) {
        // Flatten both to surface
        BlockPos start = flatten(world, from);
        BlockPos end = flatten(world, to);

        PriorityQueue<Node> open = new PriorityQueue<>();
        Map<Long, Float> visited = new HashMap<>();

        open.add(new Node(start, null, 0, heuristic(start, end), 0, 0));
        visited.put(start.asLong(), 0f);

        int iterations = 0;

        while (!open.isEmpty() && iterations < MAX_NODES) {
            iterations++;
            Node current = open.poll();

            if (current.pos.isWithinDistance(end, 2)) {
                return reconstructPath(current);
            }

            int[][] directions = {{1,0},{-1,0},{0,1},{0,-1}};

            for (int[] dir : directions) {
                BlockPos neighbourXZ = new BlockPos(
                        current.pos.getX() + dir[0],
                        0,
                        current.pos.getZ() + dir[1]
                );
                BlockPos neighbour = flatten(world, neighbourXZ);

                if (!isWalkable(world, neighbour)) continue;

                // Add turn penalty if direction changes
                float turnCost = (current.dirX != 0 && dir[0] == 0) ||
                        (current.dirZ != 0 && dir[1] == 0) ? TURN_PENALTY : 0;
                float newG = current.g + 1 + turnCost;
                long key = neighbour.asLong();

                if (visited.containsKey(key) && visited.get(key) <= newG) continue;

                visited.put(key, newG);
                float h = heuristic(neighbour, end);
                open.add(new Node(neighbour, current, newG, h, dir[0], dir[1]));
            }
        }

        // Fallback to straight line
        return straightLine(world, from, to);
    }

    private static boolean isWalkable(ServerWorld world, BlockPos pos) {
        BlockPos surface = pos.down();
        net.minecraft.block.BlockState state = world.getBlockState(surface);
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

    private static BlockPos flatten(ServerWorld world, BlockPos pos) {
        return world.getTopPosition(
                Heightmap.Type.WORLD_SURFACE_WG,
                new BlockPos(pos.getX(), 0, pos.getZ())
        );
    }

    private static float heuristic(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ());
    }

    private static List<BlockPos> reconstructPath(Node node) {
        List<BlockPos> path = new ArrayList<>();
        while (node != null) {
            path.add(node.pos);
            node = node.parent;
        }
        Collections.reverse(path);
        return path;
    }

    private static List<BlockPos> straightLine(ServerWorld world, BlockPos from, BlockPos to) {
        List<BlockPos> path = new ArrayList<>();
        int x0 = from.getX(), z0 = from.getZ();
        int x1 = to.getX(), z1 = to.getZ();
        int dx = Math.abs(x1 - x0), dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1, sz = z0 < z1 ? 1 : -1;
        int err = dx - dz;

        while (true) {
            path.add(flatten(world, new BlockPos(x0, 0, z0)));
            if (x0 == x1 && z0 == z1) break;
            int e2 = 2 * err;
            if (e2 > -dz) { err -= dz; x0 += sx; }
            if (e2 < dx)  { err += dx; z0 += sz; }
        }
        return path;
    }
}