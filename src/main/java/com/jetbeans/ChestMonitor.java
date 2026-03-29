package com.jetbeans;

import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ChestMonitor {

    // Scan radius around town hall position for any chest
    private static final int SCAN_RADIUS = 16;

    public static boolean checkAndConsume(ServerWorld world, VillageData village) {
        if (village.townHallPos == null) return false;

        List<Inventory> chests = findChestsNearTownHall(world, village.townHallPos);

        if (chests.isEmpty()) {
            VillageExpansionMod.LOGGER.warn("No chests found near town hall at {}",
                    village.townHallPos);
            return false;
        }

        Optional<BuildingRequirement> reqOpt = BuildingRequirementsLoader.getNext();
        if (reqOpt.isEmpty()) return false;

        BuildingRequirement req = reqOpt.get();

        // Check across ALL chests combined
        if (!hasEnoughItemsAcrossChests(chests, req)) return false;

        // Consume from chests
        consumeItemsAcrossChests(chests, req);
        VillageExpansionMod.LOGGER.info("Resources consumed for building: {}", req.buildingId);
        return true;
    }

    private static List<Inventory> findChestsNearTownHall(ServerWorld world, BlockPos townHallPos) {
        List<Inventory> found = new ArrayList<>();

        for (int x = -SCAN_RADIUS; x <= SCAN_RADIUS; x++) {
            for (int y = 0; y <= SCAN_RADIUS; y++) {
                for (int z = -SCAN_RADIUS; z <= SCAN_RADIUS; z++) {
                    BlockPos checkPos = townHallPos.add(x, y, z);
                    BlockEntity be = world.getBlockEntity(checkPos);

                    if (be instanceof Inventory inv) {
                        // Avoid double-counting double chests by checking block type
                        if (world.getBlockState(checkPos).getBlock() == Blocks.CHEST
                                || world.getBlockState(checkPos).getBlock() == Blocks.TRAPPED_CHEST) {
                            found.add(inv);
                            VillageExpansionMod.LOGGER.info("Found chest at {}", checkPos);
                        }
                    }
                }
            }
        }

        return found;
    }

    private static boolean hasEnoughItemsAcrossChests(List<Inventory> chests,
                                                      BuildingRequirement req) {
        for (BuildingRequirement.StackRequirement needed : req.requirements) {
            int total = 0;
            for (Inventory chest : chests) {
                total += countItem(chest, needed.item);
            }
            if (total < needed.count) {
                VillageExpansionMod.LOGGER.info("Missing: {} x{} (have {})",
                        needed.item, needed.count, total);
                return false;
            }
        }
        return true;
    }

    private static void consumeItemsAcrossChests(List<Inventory> chests,
                                                 BuildingRequirement req) {
        for (BuildingRequirement.StackRequirement needed : req.requirements) {
            int toConsume = needed.count;

            outer:
            for (Inventory chest : chests) {
                for (int i = 0; i < chest.size() && toConsume > 0; i++) {
                    ItemStack stack = chest.getStack(i);
                    if (stack.getItem() == needed.item) {
                        int take = Math.min(stack.getCount(), toConsume);
                        stack.decrement(take);
                        toConsume -= take;
                        if (stack.isEmpty()) {
                            chest.setStack(i, ItemStack.EMPTY);
                        }
                    }
                }
                if (toConsume <= 0) break outer;
            }

            for (Inventory chest : chests) {
                chest.markDirty();
            }
        }
    }

    private static int countItem(Inventory chest, Item item) {
        int total = 0;
        for (int i = 0; i < chest.size(); i++) {
            ItemStack stack = chest.getStack(i);
            if (stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        return total;
    }
}