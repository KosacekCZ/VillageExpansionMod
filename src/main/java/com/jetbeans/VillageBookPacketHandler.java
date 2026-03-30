package com.jetbeans;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Registers the server-side channel so packets can be sent.
 * The client-side receiver is registered in VillageExpansionClient.
 */
public class VillageBookPacketHandler {

    public static void register() {
        // Nothing to register for the server→client direction here;
        // ServerPlayNetworking.send() is fire-and-forget.
        // This class is the hook point if we add client→server packets later
        // (e.g. player queues a specific build).
        VillageExpansionMod.LOGGER.info("VillageBook packet channel registered: {}",
                VillageBookPacket.ID);
    }
}