package com.gtnewhorizons.galaxia.utility;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.client.config.GalaxiaConfigOverlay;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class GalaxiaOverlayHandler {

    private final Minecraft mc = Minecraft.getMinecraft();

    private static final ResourceLocation OXYGEN_BG = new ResourceLocation("galaxia", "textures/gui/oxygen_bar_bg.png");
    private static final ResourceLocation OXYGEN_FILL = new ResourceLocation(
        "galaxia",
        "textures/gui/oxygen_bar_fill.png");
    private static final ResourceLocation TEMP_BG = new ResourceLocation("galaxia", "textures/gui/temp_bar_bg.png");
    private static final ResourceLocation TEMP_FILL = new ResourceLocation("galaxia", "textures/gui/temp_bar_fill.png");

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        if (mc.currentScreen != null) return;

        EntityPlayer player = mc.thePlayer;
        if (player == null) return;

        ScaledResolution res = event.resolution;
        int w = res.getScaledWidth();
        int h = res.getScaledHeight();

        float oxygen = getOxygenLevel(player);
        float temp = getTemperature(player);

        int ox = 0, oy = 0, tx = 0, ty = 0;

        GalaxiaConfigOverlay.HudPreset preset = GalaxiaConfigOverlay.hudPreset;
        if (preset == null) {
            preset = GalaxiaConfigOverlay.HudPreset.ABOVE_HOTBAR;
        }

        switch (preset) {
            case ABOVE_HOTBAR:
                ox = 10 + GalaxiaConfigOverlay.hudOffsetX;
                oy = h - 55 + GalaxiaConfigOverlay.hudOffsetY;

                tx = w - 28 + GalaxiaConfigOverlay.hudOffsetX;
                ty = h - 55 + GalaxiaConfigOverlay.hudOffsetY;
                break;

            case CLASSIC_GC:
                int right = w - 28 + GalaxiaConfigOverlay.hudOffsetX;
                ox = right;
                oy = h / 2 - 85 + GalaxiaConfigOverlay.hudOffsetY;
                tx = right;
                ty = oy + 52;
                break;
        }

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        if (GalaxiaConfigOverlay.showOxygenBar) {
            drawVerticalBar(ox, oy, oxygen, OXYGEN_BG, OXYGEN_FILL, 0x88FFFF);
        }
        if (GalaxiaConfigOverlay.showTemperatureBar) {
            drawVerticalBar(tx, ty, temp, TEMP_BG, TEMP_FILL, getTempColor(temp));
        }

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private void drawVerticalBar(int x, int y, float fill, ResourceLocation bg, ResourceLocation fillTex, int color) {
        mc.getTextureManager()
            .bindTexture(bg);
        mc.ingameGUI.drawTexturedModalRect(x, y, 0, 0, 14, 42);

        int fillPixels = (int) (38 * fill);
        if (fillPixels > 0) {
            mc.getTextureManager()
                .bindTexture(fillTex);
            GL11.glColor4f(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f, 1.0f);
            mc.ingameGUI.drawTexturedModalRect(x + 1, y + 41 - fillPixels, 1, 41 - fillPixels, 12, fillPixels);
        }

        if (fill < 0.2f) {
            float pulse = (float) (Math.sin(System.currentTimeMillis() / 150D) * 0.3 + 0.7);
            GL11.glColor4f(1f, pulse, pulse, 1f);
        }
    }

    private int getTempColor(float temp) {
        if (temp > 0.65f) return 0xFF4444;
        if (temp < 0.35f) return 0x4488FF;
        return 0xFFFFFF;
    }

    private float getOxygenLevel(EntityPlayer p) {
        return 1f;
    }

    private float getTemperature(EntityPlayer p) {
        return 0.5f;
    }
}
