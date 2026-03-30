package com.jetbeans;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.structure.Structure;
import net.minecraft.structure.StructureManager;
import net.minecraft.structure.StructurePlacementData;
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
    private static final int TOWN_HALL_CHEST_OFFSET_X    = 12;
    private static final int TOWN_HALL_CHEST_OFFSET_Y    = 2;
    private static final int TOWN_HALL_CHEST_OFFSET_Z    = 12;

    /**
     * Offset from placement origin to where the lodestone should sit.
     * Adjust these to match your townhall.nbt layout — place it somewhere
     * central and accessible (e.g. on a lectern-height block in the main room).
     */
    private static final int LODESTONE_OFFSET_X = 5;
    private static final int LODESTONE_OFFSET_Y = 1;  // floor level inside hall
    private static final int LODESTONE_OFFSET_Z = 5;

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

        // ── Find and register the lodestone already in the structure NBT ─────
        // Instead of hardcoding offsets, scan the placed structure's bounding box
        // for the first lodestone block and record its position.
        Vec3i size = structure.getSize();
        BlockPos foundLodestone = null;

        outer:
        for (int y = 0; y < size.getY(); y++) {
            for (int x = 0; x < size.getX(); x++) {
                for (int z = 0; z < size.getZ(); z++) {
                    BlockPos candidate = placementPos.add(x, y, z);
                    if (world.getBlockState(candidate).getBlock() == Blocks.LODESTONE) {
                        foundLodestone = candidate;
                        break outer;
                    }
                }
            }
        }

        if (foundLodestone != null) {
            village.lodestonePos = foundLodestone;
            VillageExpansionMod.LOGGER.info("[VillageBook] Found lodestone in structure at {}", foundLodestone);
        } else {
            // Fallback: place one at the hardcoded offset if the structure has none
            BlockPos fallback = placementPos.add(LODESTONE_OFFSET_X, LODESTONE_OFFSET_Y, LODESTONE_OFFSET_Z);
            world.setBlockState(fallback, Blocks.LODESTONE.getDefaultState(), 3);
            village.lodestonePos = fallback;
            VillageExpansionMod.LOGGER.warn("[VillageBook] No lodestone found in structure, placed fallback at {}", fallback);
        }

        state.markDirty();

        VillageExpansionMod.LOGGER.info("Town hall placed at {}", placementPos);
        VillageExpansionMod.LOGGER.info("Chest expected at {}", village.townHallChestPos);

        // Draw path from entrance to village centre
        BlockPos entranceXZ = new BlockPos(
                base.getX() + TOWN_HALL_ENTRANCE_OFFSET_X, 64, base.getZ());
        BlockPos villageXZ = new BlockPos(
                village.center.getX(), 64, village.center.getZ());

        PathDrawer.drawPath(world, entranceXZ, villageXZ);
        VillageExpansionMod.LOGGER.info("Path drawn from town hall to village center");
    }

    public static void placeBuilding(ServerWorld world, VillageData village,
                                     VillageState state, long currentTick) {
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

        ConstructionProject project = new ConstructionProject(
                "test_house", base, 1, constructionDays);

        captureBlockData(world, structure, base, project);

        int maxY = project.blockData.keySet().stream()
                .mapToInt(BlockPos::getY)
                .max()
                .orElse(0);
        project.totalLayers = maxY + 1;
        project.ticksPerLayer = Math.max(1,
                ((long) constructionDays * ConstructionProject.TICKS_PER_DAY) / project.totalLayers);
        project.lastLayerTick = currentTick;

        village.activeProjects.add(project);
        state.markDirty();

        VillageExpansionMod.LOGGER.info(
                "Construction started: test_house at {}, {} layers, {} ticks/layer",
                base, project.totalLayers, project.ticksPerLayer);
    }

    private static void captureBlockData(ServerWorld world, Structure structure,
                                         BlockPos origin, ConstructionProject project) {
        Vec3i size = structure.getSize();
        StructurePlacementData placementData = new StructurePlacementData();
        structure.place(world, origin, origin, placementData, new Random(), 3);

        for (int y = 0; y < size.getY(); y++) {
            for (int x = 0; x < size.getX(); x++) {
                for (int z = 0; z < size.getZ(); z++) {
                    BlockPos worldPos = origin.add(x, y, z);
                    BlockState blockState = world.getBlockState(worldPos);
                    if (!blockState.isAir()) {
                        project.blockData.put(new BlockPos(x, y, z), blockState);
                    }
                    world.setBlockState(worldPos, Blocks.AIR.getDefaultState(), 3);
                }
            }
        }

        VillageExpansionMod.LOGGER.info("Captured {} blocks for construction",
                project.blockData.size());
    }

    public static BlockPos findValidPlacementPos(ServerWorld world, VillageData village,
                                                 Structure structure, Random random) {
        Vec3i size = structure.getSize();
        int margin = 9;
        int baseRadius = PLACEMENT_RADIUS;
        int maxAttempts = 20;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int radius = baseRadius + (attempt / 4) * 16;
            int angle = random.nextInt(360);
            double rad = Math.toRadians(angle);
            int offsetX = (int) (Math.cos(rad) * radius);
            int offsetZ = (int) (Math.sin(rad) * radius);

            BlockPos candidate = world.getTopPosition(
                    Heightmap.Type.WORLD_SURFACE_WG,
                    village.center.add(offsetX, 0, offsetZ));

            if (isAreaClear(world, candidate, size)
                    && !village.isTooCloseToExisting(candidate, size, margin)) {
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
                        origin.add(x, 0, z)).down();
                if (!PathDrawer.isNaturalGround(world.getBlockState(checkPos))) {
                    return false;
                }
            }
        }
        return true;
    }
}