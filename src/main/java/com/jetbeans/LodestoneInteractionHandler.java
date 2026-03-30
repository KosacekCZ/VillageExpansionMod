package com.jetbeans;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Handles right-clicking the lodestone inside a town hall.
 * Uses Fabric's UseBlockCallback event — no mixin needed.
 *
 * Register by calling LodestoneInteractionHandler.register() from onInitialize().
 */
public class LodestoneInteractionHandler {

    public static void register() {
        UseBlockCallback.EVENT.register(LodestoneInteractionHandler::onUseBlock);
    }

    private static ActionResult onUseBlock(PlayerEntity player, World world,
                                           Hand hand, net.minecraft.util.hit.BlockHitResult hitResult) {
        // Server-side only, main hand only
        if (world.isClient || hand != Hand.MAIN_HAND) return ActionResult.PASS;

        // hitResult.getBlockPos() returns a BlockPos.Mutable — convert to immutable
        // so that BlockPos.equals() works correctly against the stored lodestonePos
        BlockPos pos = hitResult.getBlockPos().toImmutable();

        // Must be a lodestone block
        if (world.getBlockState(pos).getBlock() != Blocks.LODESTONE) return ActionResult.PASS;

        // Log every lodestone click so we can compare positions
        //VillageExpansionMod.LOGGER.info("[VillageBook] Lodestone clicked at {}", pos);

        VillageState villageState = VillageState.get((net.minecraft.server.world.ServerWorld) world);
        VillageData village = villageState.getVillageByLodestone(pos);

        if (village == null) {
            // Not one of ours — let vanilla compass-linking proceed
            return ActionResult.PASS;
        }

        // Check the player doesn't already carry the ledger
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).isOf(VillageExpansionMod.VILLAGE_LEDGER)) {
                player.sendMessage(Text.of("You already carry this village's ledger."), true);
                return ActionResult.SUCCESS;
            }
        }

        // Require a book in hand to "bind" as the ledger
        ItemStack heldStack = player.getStackInHand(hand);
        if (heldStack.isOf(net.minecraft.item.Items.BOOK)) {
            heldStack.decrement(1);
        } else {
            // Check rest of inventory
            boolean foundBook = false;
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack stack = player.getInventory().getStack(i);
                if (stack.isOf(net.minecraft.item.Items.BOOK)) {
                    stack.decrement(1);
                    foundBook = true;
                    break;
                }
            }
            if (!foundBook) {
                player.sendMessage(Text.of("You need a book to create the ledger."), true);
                return ActionResult.SUCCESS;
            }
        }

        // Give the ledger
        ItemStack ledger = new ItemStack(VillageExpansionMod.VILLAGE_LEDGER);
        if (!player.getInventory().insertStack(ledger)) {
            player.dropItem(ledger, false);
        }

        player.sendMessage(Text.of("You have received the village's ledger."), true);
        VillageExpansionMod.LOGGER.info("[VillageBook] Gave village ledger to {} at lodestone {}",
                ((ServerPlayerEntity) player).getName().getString(), pos);

        return ActionResult.SUCCESS;
    }
}