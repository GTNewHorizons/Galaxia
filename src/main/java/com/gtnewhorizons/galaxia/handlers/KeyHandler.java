package com.gtnewhorizons.galaxia.handlers;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.client.KeyBinds;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.ToggleRCSPacket;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class KeyHandler {

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (KeyBinds.TOGGLE_REACTION_CONTROL_SYSTEM.isPressed()) {
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            boolean isEnabled = GalaxiaAPI.hasZeroGMovementCapability(player);
            Galaxia.GALAXIA_NETWORK.sendToServer(new ToggleRCSPacket(!isEnabled));
            GalaxiaAPI.setZeroGMovement(player, !isEnabled);
        }
    }
}
