package com.jetbeans;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.List;

public class VillageData {

    public boolean hasTownHall = false;
    public boolean pendingBuild = false;
    public BlockPos townHallPos = null;
    public BlockPos townHallChestPos = null;
    public BlockPos center;
    public int level;
    public long lastGrowthTick;
    public int buildingsCompleted;
    public List<BlockPos> placedStructures = new ArrayList<>();
    public List<ConstructionProject> activeProjects = new ArrayList<>();

    public static final long GROWTH_INTERVAL = 200L;

    public VillageData(BlockPos center) {
        this.center = center;
        this.level = 1;
        this.lastGrowthTick = 0;
        this.buildingsCompleted = 0;
    }

    public boolean isReadyToGrow(long currentTick) {
        return (currentTick - lastGrowthTick) >= GROWTH_INTERVAL;
    }

    public void onGrowth(long currentTick) {
        lastGrowthTick = currentTick;
        buildingsCompleted++;
        level = 1 + (buildingsCompleted / 3);
    }

    public void addPlacedStructure(BlockPos pos) {
        placedStructures.add(pos);
    }

    public boolean isTooCloseToExisting(BlockPos candidate, Vec3i size, int margin) {
        // Always enforce at least 24 block minimum distance between any two structures
        int minDist = Math.max(Math.max(size.getX(), size.getZ()) + margin, 24);

        for (BlockPos existing : placedStructures) {
            double dist = Math.sqrt(
                    Math.pow(candidate.getX() - existing.getX(), 2) +
                            Math.pow(candidate.getZ() - existing.getZ(), 2)
            );
            if (dist < minDist) {
                return true;
            }
        }
        return false;
    }

    // --- NBT serialization ---

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putLong("center", center.asLong());
        nbt.putInt("level", level);
        nbt.putLong("lastGrowthTick", lastGrowthTick);
        nbt.putInt("buildingsCompleted", buildingsCompleted);
        nbt.putBoolean("hasTownHall", hasTownHall);
        nbt.putBoolean("pendingBuild", pendingBuild);


        NbtList projectList = new NbtList();
        for (ConstructionProject project : activeProjects) {
            projectList.add(project.toNbt());
        }
        nbt.put("activeProjects", projectList);


        if (townHallPos != null) {
            nbt.putLong("townHallPos", townHallPos.asLong());
        }
        if (townHallChestPos != null) {
            nbt.putLong("townHallChestPos", townHallChestPos.asLong());
        }

        NbtList structureList = new NbtList();
        for (BlockPos pos : placedStructures) {
            NbtCompound entry = new NbtCompound();
            entry.putLong("pos", pos.asLong());
            structureList.add(entry);
        }
        nbt.put("placedStructures", structureList);

        return nbt;
    }

    public static VillageData fromNbt(NbtCompound nbt) {
        BlockPos center = BlockPos.fromLong(nbt.getLong("center"));
        VillageData data = new VillageData(center);
        data.level = nbt.getInt("level");
        data.lastGrowthTick = nbt.getLong("lastGrowthTick");
        data.buildingsCompleted = nbt.getInt("buildingsCompleted");
        data.hasTownHall = nbt.getBoolean("hasTownHall");
        data.pendingBuild = nbt.getBoolean("pendingBuild");

        NbtList projectList = nbt.getList("activeProjects", 10);
        for (int i = 0; i < projectList.size(); i++) {
            data.activeProjects.add(ConstructionProject.fromNbt(projectList.getCompound(i)));
        }

        if (nbt.contains("townHallPos")) {
            data.townHallPos = BlockPos.fromLong(nbt.getLong("townHallPos"));
        }
        if (nbt.contains("townHallChestPos")) {
            data.townHallChestPos = BlockPos.fromLong(nbt.getLong("townHallChestPos"));
        }

        NbtList structureList = nbt.getList("placedStructures", 10);
        for (int i = 0; i < structureList.size(); i++) {
            NbtCompound entry = structureList.getCompound(i);
            data.placedStructures.add(BlockPos.fromLong(entry.getLong("pos")));
        }

        return data;
    }
}