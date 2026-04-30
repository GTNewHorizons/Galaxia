package com.gtnewhorizons.galaxia.handlers;

import net.minecraft.client.Minecraft;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.RocketLaunchPacket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocket;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class RocketInputHandler {

    private boolean wasJumping = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft mc = Minecraft.getMinecraft();

            if (mc.thePlayer != null && mc.thePlayer.ridingEntity instanceof EntityRocket rocket) {
                boolean isJumping = mc.gameSettings.keyBindJump.getIsKeyPressed();

                if (isJumping && !wasJumping) {
                    Galaxia.GALAXIA_NETWORK.sendToServer(new RocketLaunchPacket(rocket.getEntityId()));
                }
            }
        } else {
            wasJumping = false;
        }
    }
}
