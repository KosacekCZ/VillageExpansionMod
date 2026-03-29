package com.jetbeans;

import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.*;

public class AStarPathfinder {

    private static final int MAX_NODES = 10000; // prevent infinite search

    private record Node(BlockPos pos, Node parent, float g, float h) {
        float f() { return g + h; }
    }

    public static List<BlockPos> findPath(ServerWorld world, BlockPos from, BlockPos to) {
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::f));
        Map<Long, Float> visited = new HashMap<>();

        Node start = new Node(flatten(world, from), null, 0, heuristic(from, to));
        open.add(start);
        visited.put(start.pos().asLong(), 0f);

        int iterations = 0;

        while (!open.isEmpty() && iterations < MAX_NODES) {
            iterations++;
            Node current = open.poll();

            // Reached destination (within 2 blocks)
            if (current.pos().isWithinDistance(to, 2)) {
                return reconstructPath(current);
            }

            // Explore neighbours (4 cardinal directions)
            for (BlockPos neighbour : getNeighbours(world, current.pos())) {
                float newG = current.g + 1;
                long key = neighbour.asLong();

                if (visited.containsKey(key) && visited.get(key) <= newG) continue;

                visited.put(key, newG);
                float h = heuristic(neighbour, to);
                open.add(new Node(neighbour, current, newG, h));
            }
        }

        // No path found — return straight line as fallback
        return straightLine(world, from, to);
    }

    private static List<BlockPos> getNeighbours(ServerWorld world, BlockPos pos) {
        List<BlockPos> neighbours = new ArrayList<>();
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int[] dir : directions) {
            BlockPos candidate = new BlockPos(pos.getX() + dir[0], 0, pos.getZ() + dir[1]);
            candidate = flatten(world, candidate);

            // Allow natural ground and existing paths
            BlockPos surface = candidate.down();
            net.minecraft.block.BlockState state = world.getBlockState(surface);

            if (isWalkable(state)) {
                neighbours.add(candidate);
            }
        }

        return neighbours;
    }

    private static boolean isWalkable(net.minecraft.block.BlockState state) {
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

    // Get surface-level position (air block above ground)
    private static BlockPos flatten(ServerWorld world, BlockPos pos) {
        return world.getTopPosition(
                Heightmap.Type.WORLD_SURFACE_WG,
                new BlockPos(pos.getX(), 0, pos.getZ())
        );
    }

    private static float heuristic(BlockPos a, BlockPos b) {
        // Manhattan distance
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ());
    }

    private static List<BlockPos> reconstructPath(Node node) {
        List<BlockPos> path = new ArrayList<>();
        while (node != null) {
            path.add(node.pos());
            node = node.parent();
        }
        Collections.reverse(path);
        return path;
    }

    // Straight line fallback using Bresenham
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