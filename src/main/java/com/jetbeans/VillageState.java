package com.jetbeans;


import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class VillageState extends PersistentState {

    private static final String KEY = "villageexpansion_villages";
    private static final int MERGE_RADIUS = 64; // blocks — treat nearby positions as same village

    private final Map<BlockPos, VillageData> villages = new HashMap<>();

    // --- Public API ---

    public boolean hasVillageNear(BlockPos pos) {
        return findNear(pos) != null;
    }

    public void addVillage(BlockPos pos, long currentTick) {
        if (hasVillageNear(pos)) return;
        villages.put(pos, new VillageData(pos));
        VillageExpansionMod.LOGGER.info("Registered new village at {}", pos);
        markDirty(); // tells Minecraft to save this
    }

    public Collection<VillageData> getAll() {
        return villages.values(); // must return actual map values, not new ArrayList(villages.values())
    }

    // --- Internal ---

    private VillageData findNear(BlockPos pos) {
        for (BlockPos known : villages.keySet()) {
            if (known.isWithinDistance(pos, MERGE_RADIUS)) {
                return villages.get(known);
            }
        }
        return null;
    }

    // --- PersistentState lifecycle ---

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (VillageData data : villages.values()) {
            list.add(data.toNbt());
        }
        nbt.put("villages", list);
        return nbt;
    }

    public static VillageState fromNbt(NbtCompound nbt) {
        VillageState state = new VillageState();
        NbtList list = nbt.getList("villages", 10); // 10 = NbtCompound type
        for (int i = 0; i < list.size(); i++) {
            VillageData data = VillageData.fromNbt(list.getCompound(i));
            state.villages.put(data.center, data);
        }
        return state;
    }

    public VillageData getVillageNear(BlockPos pos) {
        return findNear(pos);
    }

    // --- Load from world ---

    public static VillageState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
                VillageState::fromNbt,
                VillageState::new,
                KEY
        );
    }
}