package com.fixpot47.parkour;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Client-side only: input is sent through ordinary Minecraft key mappings.
 * Press P to toggle; no packets are forged or sent directly.
 */
public final class ParkourBotClient implements ClientModInitializer {
    private final ParkourController controller = new ParkourController();

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("aibotparkour47", "controls"));
        KeyMapping toggle = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.aibotparkour47.toggle", InputConstants.Type.KEYSYM,
                InputConstants.KEY_P, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggle.consumeClick()) {
                controller.toggle(client);
            }
            controller.tick(client);
        });
    }
}
