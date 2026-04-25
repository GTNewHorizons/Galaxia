package com.gtnewhorizons.galaxia.handlers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.Entity;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocketSeat;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RocketCameraHandler {

    private boolean isZoomModified = false;
    private final float DEFAULT_ZOOM = 4.0f;

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        // We only need to run this once per frame
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.gameSettings == null || mc.entityRenderer == null) return;

        Entity riding = mc.thePlayer.ridingEntity;
        EntityRocket rocket = null;

        // Determine if the player is riding the rocket directly or via a passenger seat
        if (riding instanceof EntityRocket) {
            rocket = (EntityRocket) riding;
        } else if (riding instanceof EntityRocketSeat) {
            // NOTE: Ensure your EntityRocketSeat has a getter for the main rocket!
            rocket = ((EntityRocketSeat) riding).getRocket();
        }

        // If riding the rocket and the player is in a third-person view
        if (rocket != null && mc.gameSettings.thirdPersonView > 0) {

            float targetZoom = DEFAULT_ZOOM;

            // Dynamically scale the camera zoom based on the rocket's height
            if (rocket.getAssembly() != null) {
                // Adjust this formula to taste.
                // E.g., Height * 0.8 + 2.0 ensures large rockets stay in frame.
                targetZoom = (float) Math.max(
                    DEFAULT_ZOOM,
                    rocket.getAssembly()
                        .getTotalHeight() * 0.8F + 2.0F);
            }

            setZoomDistance(mc.entityRenderer, targetZoom);
            isZoomModified = true;

        } else if (isZoomModified) {
            // The player dismounted or switched to first-person view. Revert to vanilla
            // zoom.
            setZoomDistance(mc.entityRenderer, DEFAULT_ZOOM);
            isZoomModified = false;
        }
    }

    /**
     * Uses Forge ReflectionHelper to overwrite the hardcoded camera distance
     * fields.
     */
    private void setZoomDistance(EntityRenderer renderer, float zoom) {
        try {
            // field_78490_B is the SRG name for thirdPersonDistance
            // field_78491_C is the SRG name for thirdPersonDistanceTemp
            ReflectionHelper
                .setPrivateValue(EntityRenderer.class, renderer, zoom, "thirdPersonDistance", "field_78490_B");
            ReflectionHelper
                .setPrivateValue(EntityRenderer.class, renderer, zoom, "thirdPersonDistanceTemp", "field_78491_C");
        } catch (Exception e) {}
    }
}
