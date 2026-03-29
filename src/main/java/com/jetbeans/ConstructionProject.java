package com.jetbeans;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

public class ConstructionProject {

    public final String buildingId;
    public final BlockPos pos;
    public int totalLayers;
    public long ticksPerLayer;
    public int currentLayer;
    public boolean scaffoldingPlaced;
    public boolean complete;
    public long lastLayerTick;

    // Stores all block positions and states from the structure
    public Map<BlockPos, BlockState> blockData = new HashMap<>();

    public static final long TICKS_PER_DAY = 100L; // 24000

    public ConstructionProject(String buildingId, BlockPos pos,
                               int totalLayers, int constructionDays) {
        this.buildingId = buildingId;
        this.pos = pos;
        this.totalLayers = totalLayers;
        this.ticksPerLayer = Math.max(1, (constructionDays * TICKS_PER_DAY) / Math.max(totalLayers, 1));
        this.currentLayer = 0;
        this.scaffoldingPlaced = false;
        this.complete = false;
        this.lastLayerTick = 0;
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("buildingId", buildingId);
        nbt.putLong("pos", pos.asLong());
        nbt.putInt("totalLayers", totalLayers);
        nbt.putLong("ticksPerLayer", ticksPerLayer);
        nbt.putInt("currentLayer", currentLayer);
        nbt.putBoolean("scaffoldingPlaced", scaffoldingPlaced);
        nbt.putBoolean("complete", complete);
        nbt.putLong("lastLayerTick", lastLayerTick);

        // Save block data
        NbtList blockList = new NbtList();
        for (Map.Entry<BlockPos, BlockState> entry : blockData.entrySet()) {
            NbtCompound blockEntry = new NbtCompound();
            blockEntry.putLong("pos", entry.getKey().asLong());
            blockEntry.put("state", NbtHelper.fromBlockState(entry.getValue()));
            blockList.add(blockEntry);
        }
        nbt.put("blockData", blockList);

        return nbt;
    }

    public static ConstructionProject fromNbt(NbtCompound nbt) {
        String buildingId = nbt.getString("buildingId");
        BlockPos pos = BlockPos.fromLong(nbt.getLong("pos"));
        int totalLayers = nbt.getInt("totalLayers");

        ConstructionProject project = new ConstructionProject(buildingId, pos, totalLayers, 1);
        project.ticksPerLayer = nbt.getLong("ticksPerLayer");
        project.currentLayer = nbt.getInt("currentLayer");
        project.scaffoldingPlaced = nbt.getBoolean("scaffoldingPlaced");
        project.complete = nbt.getBoolean("complete");
        project.lastLayerTick = nbt.getLong("lastLayerTick");

        // Load block data
        NbtList blockList = nbt.getList("blockData", 10);
        for (int i = 0; i < blockList.size(); i++) {
            NbtCompound blockEntry = blockList.getCompound(i);
            BlockPos blockPos = BlockPos.fromLong(blockEntry.getLong("pos"));
            BlockState state = NbtHelper.toBlockState(blockEntry.getCompound("state"));
            project.blockData.put(blockPos, state);
        }

        return project;
    }
}