package com.jetbeans;

import com.jetbeans.VillageBookPacket;
import com.jetbeans.VillageExpansionMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public class VillageExpansionClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		VillageExpansionMod.LOGGER.info("Village Expansion client initialised");

		// When the server sends a VillageBook packet, decode it and open the screen
		ClientPlayNetworking.registerGlobalReceiver(
				VillageBookPacket.ID,
				(client, handler, buf, responseSender) -> {
					// Decode on the network thread
					VillageBookPacket.Snapshot snapshot = VillageBookPacket.decode(buf);

					// Open screen on the render thread
					client.execute(() -> {
						MinecraftClient.getInstance()
								.setScreen(new VillageBookScreen(snapshot));
					});
				}
		);
	}
}