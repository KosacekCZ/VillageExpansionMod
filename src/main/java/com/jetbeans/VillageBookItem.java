package com.jetbeans;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A special book item the player can hold and right-click to view
 * the nearest village's status, active construction, and build queue.
 *
 * Register with:
 *   Registry.register(Registry.ITEM,
 *       new Identifier("villageexpansion", "village_ledger"),
 *       new VillageBookItem(new Item.Settings().maxCount(1)));
 */
public class VillageBookItem extends Item {

    /** How close the player must be to a known village center to open the book */
    private static final int MAX_DISTANCE = 256;

    public VillageBookItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient) {
            // Client-side: screen is opened by the client entrypoint on packet receive.
            // Return success so the animation plays.
            return TypedActionResult.success(user.getStackInHand(hand));
        }

        ServerWorld serverWorld = (ServerWorld) world;
        ServerPlayerEntity player = (ServerPlayerEntity) user;
        BlockPos playerPos = player.getBlockPos();

        VillageState state = VillageState.get(serverWorld);
        VillageData nearest = findNearest(state, playerPos);

        if (nearest == null) {
            player.sendMessage(
                    Text.of("No village found nearby."), true);
            return TypedActionResult.fail(user.getStackInHand(hand));
        }

        if (!nearest.hasTownHall) {
            player.sendMessage(Text.of("This village has no town hall yet."), true);
            return TypedActionResult.fail(user.getStackInHand(hand));
        }

        // Send data snapshot to client; client will open the screen
        VillageBookPacket.send(player, nearest);
        return TypedActionResult.success(user.getStackInHand(hand));
    }

    private VillageData findNearest(VillageState state, BlockPos playerPos) {
        VillageData nearest = null;
        double minDist = MAX_DISTANCE * MAX_DISTANCE;

        for (VillageData v : state.getAll()) {
            double dist = v.center.getSquaredDistance(playerPos);
            if (dist < minDist) {
                minDist = dist;
                nearest = v;
            }
        }
        return nearest;
    }
}