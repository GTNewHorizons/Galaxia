package com.gtnewhorizons.galaxia.handlers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;

import com.gtnewhorizons.galaxia.rocketmodules.entities.EntityRocket;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

public class RocketCameraHandler {

    private static final double CAMERA_DISTANCE = 20.0D;

    private static final String[] THIRD_PERSON_DISTANCE = { "thirdPersonDistance", "field_78490_B" };
    private static final String[] THIRD_PERSON_DISTANCE_TEMP = { "thirdPersonDistanceTemp", "field_78491_C" };

    public static void register() {
        FMLCommonHandler.instance()
            .bus()
            .register(new RocketCameraHandler());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.thePlayer.ridingEntity == null) return;

        if (mc.thePlayer.ridingEntity instanceof EntityRocket) {
            try {
                EntityRenderer renderer = mc.entityRenderer;
                ReflectionHelper
                    .setPrivateValue(EntityRenderer.class, renderer, CAMERA_DISTANCE, THIRD_PERSON_DISTANCE);
                ReflectionHelper
                    .setPrivateValue(EntityRenderer.class, renderer, CAMERA_DISTANCE, THIRD_PERSON_DISTANCE_TEMP);
            } catch (Exception ignored) {}
        }
    }
}
