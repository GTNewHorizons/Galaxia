package com.gtnewhorizons.galaxia.handlers;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import com.cleanroommc.modularui.screen.ModularScreen;
import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.client.GalaxiaKeyBinds;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.GalacticMapWidget;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.OffscreenStarmapGui;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.RocketScheduleOpenPacket;
import com.gtnewhorizons.galaxia.core.network.RocketTransitPacket;
import com.gtnewhorizons.galaxia.core.network.ToggleRCSPacket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocketSeat;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class KeyHandler {

    private boolean transitOpenRequested;
    private boolean transitMapEntered;

    public static void registerKeyBindings() {
        GalaxiaKeyBinds.init();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getMinecraft();
        if (client.thePlayer == null || client.theWorld == null) {
            RocketTransitPacket.Handler.clear();
            transitOpenRequested = false;
            transitMapEntered = false;
            return;
        }
        var transit = RocketTransitPacket.Handler.current();
        var screen = ModularScreen.getCurrent();
        if (transit == null || !transit.restricted()) {
            transitOpenRequested = false;
            transitMapEntered = false;
            return;
        }
        if (screen != null && GalacticMapWidget.SCREEN_NAME.equals(
            screen.getMainPanel()
                .getName())) {
            transitMapEntered = true;
            transitOpenRequested = false;
            return;
        }
        if (transitMapEntered && client.currentScreen != null) return;
        if (!transitOpenRequested) {
            transitOpenRequested = true;
            GalacticMapWidget.openTransitMap();
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return;

        if (GalaxiaKeyBinds.TOGGLE_REACTION_CONTROL_SYSTEM.isPressed()) {
            boolean isEnabled = GalaxiaAPI.hasZeroGMovementCapability(player);
            Galaxia.GALAXIA_NETWORK.sendToServer(new ToggleRCSPacket(!isEnabled));
            GalaxiaAPI.setZeroGMovement(player, !isEnabled);
        }

        if (GalaxiaKeyBinds.OPEN_VEHICLE_SCHEDULE.isPressed()) {
            EntityRocket rocket = player.ridingEntity instanceof EntityRocket direct ? direct
                : player.ridingEntity instanceof EntityRocketSeat seat ? seat.getRocket() : null;
            if (rocket != null && rocket.vehicleId() != null)
                Galaxia.GALAXIA_NETWORK.sendToServer(new RocketScheduleOpenPacket(rocket.vehicleId()));
        }

        if (GalaxiaKeyBinds.OPEN_OFFSCREEN_STARMAP.isPressed()) {
            OffscreenStarmapGui.toggle();
        }
    }
}
