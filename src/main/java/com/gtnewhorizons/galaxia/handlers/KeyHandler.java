package com.gtnewhorizons.galaxia.handlers;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.client.GalaxiaKeyBinds;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.RocketLaunchPacket;
import com.gtnewhorizons.galaxia.core.network.ToggleRCSPacket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocket;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class KeyHandler {

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;

        if (GalaxiaKeyBinds.TOGGLE_REACTION_CONTROL_SYSTEM.isPressed()) {
            boolean isEnabled = GalaxiaAPI.hasZeroGMovementCapability(player);
            Galaxia.GALAXIA_NETWORK.sendToServer(new ToggleRCSPacket(!isEnabled));
            GalaxiaAPI.setZeroGMovement(player, !isEnabled);
        }

        if (GalaxiaKeyBinds.LAUNCH_ROCKET.isPressed()) {
            if (player.ridingEntity instanceof EntityRocket rocket) {
                Galaxia.GALAXIA_NETWORK.sendToServer(new RocketLaunchPacket(rocket.getEntityId()));
            }
        }
    }
}
