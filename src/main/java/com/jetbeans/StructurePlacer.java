package com.jetbeans;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.structure.Structure;
import net.minecraft.structure.StructureManager;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.Heightmap;
import net.minecraft.server.world.ServerWorld;

import java.util.Optional;
import java.util.Random;

public class StructurePlacer {

    private static final Identifier TEST_HOUSE =
            new Identifier("villageexpansion", "test_house");
    private static final Identifier TOWN_HALL =
            new Identifier("villageexpansion", "townhall");

    private static final int PLACEMENT_RADIUS = 32;

    private static final int TOWN_HALL_ENTRANCE_OFFSET_X = 4;
    private static final int TOWN_HALL_CHEST_OFFSET_X = 12;
    private static final int TOWN_HALL_CHEST_OFFSET_Y = 2;
    private static final int TOWN_HALL_CHEST_OFFSET_Z = 12;

    public static void placeTownHall(ServerWorld world, VillageData village, VillageState state) {
        if (village.hasTownHall) return;

        StructureManager manager = world.getServer().getStructureManager();
        Optional<Structure> optional = manager.getStructure(TOWN_HALL);

        if (optional.isEmpty()) {
            VillageExpansionMod.LOGGER.error("Could not find town hall structure");
            return;
        }

        Structure structure = optional.get();
        Random random = new Random();

        BlockPos base = findValidPlacementPos(world, village, structure, random);
        if (base == null) {
            VillageExpansionMod.LOGGER.warn("Could not find position for town hall");
            return;
        }

        BlockPos placementPos = base.down(1);

        StructurePlacementData placementData = new StructurePlacementData()
                .setIgnoreEntities(false);

        structure.place(world, placementPos, placementPos, placementData, random, 2);

        village.hasTownHall = true;
        village.townHallPos = placementPos;
        village.addPlacedStructure(base);
        village.townHallChestPos = placementPos.add(
                TOWN_HALL_CHEST_OFFSET_X,
                TOWN_HALL_CHEST_OFFSET_Y,
                TOWN_HALL_CHEST_OFFSET_Z
        );

        state.markDirty();

        VillageExpansionMod.LOGGER.info("Town hall placed at {}", placementPos);
        VillageExpansionMod.LOGGER.info("Chest expected at {}", village.townHallChestPos);

        // Draw path from entrance to village center
        BlockPos entranceXZ = new BlockPos(
                base.getX() + TOWN_HALL_ENTRANCE_OFFSET_X,
                64,
                base.getZ()
        );
        BlockPos villageXZ = new BlockPos(
                village.center.getX(),
                64,
                village.center.getZ()
        );

        PathDrawer.drawPath(world, entranceXZ, villageXZ);
        VillageExpansionMod.LOGGER.info("Path drawn from town hall to village center");
    }

    public static void placeBuilding(ServerWorld world, VillageData village, VillageState state, long currentTick) {
        StructureManager manager = world.getServer().getStructureManager();
        Optional<Structure> optional = manager.getStructure(TEST_HOUSE);

        if (optional.isEmpty()) {
            VillageExpansionMod.LOGGER.error("Could not find structure: {}", TEST_HOUSE);
            return;
        }

        if (village.activeProjects.size() >= ConstructionManager.MAX_CONCURRENT_BUILDS) {
            VillageExpansionMod.LOGGER.info("Construction slot full, cannot start new build");
            return;
        }

        Structure structure = optional.get();
        Random random = new Random();

        BlockPos base = findValidPlacementPos(world, village, structure, random);
        if (base == null) {
            VillageExpansionMod.LOGGER.warn("Could not find placement position");
            return;
        }

        village.addPlacedStructure(base);

        int constructionDays = BuildingRequirementsLoader.getNext()
                .map(r -> r.constructionDays)
                .orElse(1);

        // Create project with placeholder totalLayers — will be set after capture
        ConstructionProject project = new ConstructionProject(
                "test_house", base, 1, constructionDays
        );

        // Capture all block data from structure into project
        captureBlockData(world, structure, base, project);

        // Calculate actual layer count from captured data
        int maxY = project.blockData.keySet().stream()
                .mapToInt(BlockPos::getY)
                .max()
                .orElse(0);
        project.totalLayers = maxY + 1;
        project.ticksPerLayer = Math.max(1,
                ((long) constructionDays * ConstructionProject.TICKS_PER_DAY) / project.totalLayers);

        // Set to currentTick so first layer fires after exactly one interval
        project.lastLayerTick = currentTick;

        village.activeProjects.add(project);
        state.markDirty();

        VillageExpansionMod.LOGGER.info(
                "Construction started: test_house at {}, {} layers, {} ticks/layer, starts at tick {}",
                base, project.totalLayers, project.ticksPerLayer, currentTick);
    }

    private static void captureBlockData(ServerWorld world, Structure structure, BlockPos origin, ConstructionProject project) {
        Vec3i size = structure.getSize();

        // Place structure temporarily to a buffer, then read it back
        // We scan every position in the bounding box
        StructurePlacementData placementData = new StructurePlacementData();

        // Place full structure first into world temporarily
        structure.place(world, origin, origin, placementData, new Random(), 3);

        // Read every block in the bounding box and store it
        for (int y = 0; y < size.getY(); y++) {
            for (int x = 0; x < size.getX(); x++) {
                for (int z = 0; z < size.getZ(); z++) {
                    BlockPos worldPos = origin.add(x, y, z);
                    BlockState state = world.getBlockState(worldPos);
                    if (!state.isAir()) {
                        project.blockData.put(new BlockPos(x, y, z), state);
                    }
                    // Remove it again — construction manager will place it layer by layer
                    world.setBlockState(worldPos, Blocks.AIR.getDefaultState(), 3);
                }
            }
        }

        VillageExpansionMod.LOGGER.info("Captured {} blocks for construction",
                project.blockData.size());
    }

    public static BlockPos findValidPlacementPos(ServerWorld world, VillageData village, Structure structure, Random random) {
        VillageExpansionMod.LOGGER.info("Known placed structures: {}", village.placedStructures);

        Vec3i size = structure.getSize();
        int margin = 9;
        int baseRadius = PLACEMENT_RADIUS;
        int maxAttempts = 20;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int radius = baseRadius + (attempt / 4) * 16;
            int angle = random.nextInt(360);
            double rad = Math.toRadians(angle);
            int offsetX = (int)(Math.cos(rad) * radius);
            int offsetZ = (int)(Math.sin(rad) * radius);

            BlockPos candidate = world.getTopPosition(
                    Heightmap.Type.WORLD_SURFACE_WG,
                    village.center.add(offsetX, 0, offsetZ)
            );

            if (isAreaClear(world, candidate, size) &&
                    !village.isTooCloseToExisting(candidate, size, margin)) {
                return candidate;
            }

            VillageExpansionMod.LOGGER.info("Attempt {} failed at radius {}, retrying...",
                    attempt + 1, radius);
        }

        return null;
    }

    private static boolean isAreaClear(ServerWorld world, BlockPos origin, Vec3i size) {
        int margin = 9;

        for (int x = -margin; x < size.getX() + margin; x++) {
            for (int z = -margin; z < size.getZ() + margin; z++) {
                BlockPos checkPos = world.getTopPosition(
                        Heightmap.Type.WORLD_SURFACE_WG,
                        origin.add(x, 0, z)
                ).down();

                if (!PathDrawer.isNaturalGround(world.getBlockState(checkPos))) {
                    return false;
                }
            }
        }
        return true;
    }
}