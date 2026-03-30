package com.jetbeans;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Sent from server → client when the player opens a VillageBook near a village.
 * Carries a lightweight snapshot of VillageData so the screen can render
 * without touching server-side state.
 */
public class VillageBookPacket {

    public static final Identifier ID =
            new Identifier(VillageExpansionMod.MOD_ID, "open_village_book");

    // ---------- Snapshot record (client-only, no world refs) ----------

    public static class Snapshot {
        public final BlockPos center;
        public final int level;
        public final int buildingsCompleted;
        public final boolean hasTownHall;

        // Active project summary (one project for now; list for future)
        public final List<ProjectSnapshot> activeProjects;

        // Resources: what the next build requires vs. what's in chests
        public final List<ResourceEntry> required;
        public final List<ResourceEntry> available;

        // All building requirement IDs defined in JSON
        public final List<String> allBuildingIds;

        public Snapshot(BlockPos center, int level, int buildingsCompleted,
                        boolean hasTownHall,
                        List<ProjectSnapshot> activeProjects,
                        List<ResourceEntry> required,
                        List<ResourceEntry> available,
                        List<String> allBuildingIds) {
            this.center = center;
            this.level = level;
            this.buildingsCompleted = buildingsCompleted;
            this.hasTownHall = hasTownHall;
            this.activeProjects = activeProjects;
            this.required = required;
            this.available = available;
            this.allBuildingIds = allBuildingIds;
        }
    }

    public static class ProjectSnapshot {
        public final String buildingId;
        public final int currentLayer;
        public final int totalLayers;
        public final boolean complete;

        public ProjectSnapshot(String buildingId, int currentLayer,
                               int totalLayers, boolean complete) {
            this.buildingId = buildingId;
            this.currentLayer = currentLayer;
            this.totalLayers = totalLayers;
            this.complete = complete;
        }
    }

    public static class ResourceEntry {
        public final String itemId;   // e.g. "minecraft:oak_log"
        public final int count;

        public ResourceEntry(String itemId, int count) {
            this.itemId = itemId;
            this.count = count;
        }
    }

    // ---------- Server-side send ----------

    /**
     * Called on the server thread. Builds and sends the packet to the player.
     */
    public static void send(ServerPlayerEntity player, VillageData village) {
        PacketByteBuf buf = PacketByteBufs.create();

        // Village overview
        buf.writeLong(village.center.asLong());
        buf.writeInt(village.level);
        buf.writeInt(village.buildingsCompleted);
        buf.writeBoolean(village.hasTownHall);

        // Active projects
        buf.writeInt(village.activeProjects.size());
        for (ConstructionProject p : village.activeProjects) {
            buf.writeString(p.buildingId);
            buf.writeInt(p.currentLayer);
            buf.writeInt(p.totalLayers);
            buf.writeBoolean(p.complete);
        }

        // Next building requirements (what the chest needs)
        BuildingRequirementsLoader.getNext().ifPresentOrElse(req -> {
            buf.writeInt(req.requirements.size());
            for (BuildingRequirement.StackRequirement sr : req.requirements) {
                buf.writeString(net.minecraft.util.registry.Registry.ITEM
                        .getId(sr.item).toString());
                buf.writeInt(sr.count);
            }
        }, () -> buf.writeInt(0));

        // Available items in nearby chests (scan once for display)
        List<net.minecraft.inventory.Inventory> chests = scanChestsForDisplay(player, village);
        java.util.Map<String, Integer> totals = new java.util.HashMap<>();

        BuildingRequirementsLoader.getNext().ifPresent(req -> {
            for (BuildingRequirement.StackRequirement sr : req.requirements) {
                String id = net.minecraft.util.registry.Registry.ITEM
                        .getId(sr.item).toString();
                int count = 0;
                for (net.minecraft.inventory.Inventory chest : chests) {
                    for (int i = 0; i < chest.size(); i++) {
                        net.minecraft.item.ItemStack stack = chest.getStack(i);
                        if (stack.getItem() == sr.item) count += stack.getCount();
                    }
                }
                totals.put(id, count);
            }
        });

        buf.writeInt(totals.size());
        for (java.util.Map.Entry<String, Integer> entry : totals.entrySet()) {
            buf.writeString(entry.getKey());
            buf.writeInt(entry.getValue());
        }

        // All building IDs known to the loader
        var allReqs = BuildingRequirementsLoader.getAll();
        buf.writeInt(allReqs.size());
        for (BuildingRequirement req : allReqs) {
            buf.writeString(req.buildingId);
        }

        ServerPlayNetworking.send(player, ID, buf);
    }

    private static List<net.minecraft.inventory.Inventory> scanChestsForDisplay(
            ServerPlayerEntity player, VillageData village) {
        if (village.townHallPos == null) return java.util.Collections.emptyList();
        net.minecraft.server.world.ServerWorld world =
                (net.minecraft.server.world.ServerWorld) player.getWorld();
        List<net.minecraft.inventory.Inventory> found = new ArrayList<>();
        int radius = 16;
        for (int x = -radius; x <= radius; x++) {
            for (int y = 0; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = village.townHallPos.add(x, y, z);
                    net.minecraft.block.entity.BlockEntity be = world.getBlockEntity(pos);
                    if (be instanceof net.minecraft.inventory.Inventory inv) {
                        net.minecraft.block.Block block =
                                world.getBlockState(pos).getBlock();
                        if (block == net.minecraft.block.Blocks.CHEST
                                || block == net.minecraft.block.Blocks.TRAPPED_CHEST) {
                            found.add(inv);
                        }
                    }
                }
            }
        }
        return found;
    }

    // ---------- Client-side decode ----------

    public static Snapshot decode(PacketByteBuf buf) {
        BlockPos center = BlockPos.fromLong(buf.readLong());
        int level = buf.readInt();
        int buildingsCompleted = buf.readInt();
        boolean hasTownHall = buf.readBoolean();

        int projectCount = buf.readInt();
        List<ProjectSnapshot> projects = new ArrayList<>();
        for (int i = 0; i < projectCount; i++) {
            projects.add(new ProjectSnapshot(
                    buf.readString(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readBoolean()
            ));
        }

        int reqCount = buf.readInt();
        List<ResourceEntry> required = new ArrayList<>();
        for (int i = 0; i < reqCount; i++) {
            required.add(new ResourceEntry(buf.readString(), buf.readInt()));
        }

        int availCount = buf.readInt();
        List<ResourceEntry> available = new ArrayList<>();
        for (int i = 0; i < availCount; i++) {
            available.add(new ResourceEntry(buf.readString(), buf.readInt()));
        }

        int allCount = buf.readInt();
        List<String> allIds = new ArrayList<>();
        for (int i = 0; i < allCount; i++) {
            allIds.add(buf.readString());
        }

        return new Snapshot(center, level, buildingsCompleted, hasTownHall,
                projects, required, available, allIds);
    }
}