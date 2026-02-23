package com.gtnewhorizons.galaxia.handlers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import org.lwjgl.opengl.GL11;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.client.TextureEnum;
import com.gtnewhorizons.galaxia.core.config.GalaxiaConfigOverlay;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class GalaxiaOverlayHandler {

    private final Minecraft mc = Minecraft.getMinecraft();

    // HUD POSITIONING CONSTANTS
    private static final int HOTBAR_HALF_WIDTH = 91; // hotbar center = screenWidth/2 - 91
    private static final int HOTBAR_FULL_WIDTH = 182;
    private static final int HOTBAR_SIDE_PADDING = 6; // padding from hotbar to bars
    private static final int ABOVE_HOTBAR_BASE_Y = 49; // screenHeight - 49

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        if (mc.currentScreen != null) return;

        EntityPlayer player = mc.thePlayer;
        if (player == null) return;

        ScaledResolution res = event.resolution;
        int screenWidth = res.getScaledWidth();
        int screenHeight = res.getScaledHeight();

        float oxygenLevel = getOxygenLevel(player);
        float temperatureLevel = getTemperature(player);

        BarScreenPositions pos = calculateBarPositions(screenWidth, screenHeight);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        if (GalaxiaConfigOverlay.showOxygenBar) {
            boolean oxygenCritical = oxygenLevel < GalaxiaConfigOverlay.lowOxygenThreshold;
            drawBar(
                pos.oxygenX,
                pos.oxygenY,
                oxygenLevel,
                TextureEnum.OXYGEN_BG.get(),
                TextureEnum.OXYGEN_FILL.get(),
                oxygenCritical,
                GalaxiaConfigOverlay.oxygenTextureWidth,
                GalaxiaConfigOverlay.oxygenTextureHeight,
                GalaxiaConfigOverlay.barOrientation);
        }

        if (GalaxiaConfigOverlay.showTemperatureBar) {
            boolean tempCritical = temperatureLevel < GalaxiaConfigOverlay.temperatureLowThreshold
                || temperatureLevel > GalaxiaConfigOverlay.temperatureHighThreshold;

            drawBar(
                pos.temperatureX,
                pos.temperatureY,
                temperatureLevel,
                TextureEnum.TEMP_BG.get(),
                TextureEnum.TEMP_FILL.get(),
                tempCritical,
                GalaxiaConfigOverlay.temperatureTextureWidth,
                GalaxiaConfigOverlay.temperatureTextureHeight,
                GalaxiaConfigOverlay.barOrientation);
        }

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private BarScreenPositions calculateBarPositions(int screenWidth, int screenHeight) {
        int oxygenX, oxygenY;
        int temperatureX, temperatureY;

        int centerX = screenWidth / 2;
        int hotbarLeft = centerX - HOTBAR_HALF_WIDTH;
        int leftSideX = hotbarLeft - GalaxiaConfigOverlay.oxygenTextureWidth - HOTBAR_SIDE_PADDING;
        int rightSideX = hotbarLeft + HOTBAR_FULL_WIDTH + HOTBAR_SIDE_PADDING;
        int baseY = screenHeight - ABOVE_HOTBAR_BASE_Y;

        oxygenX = leftSideX + GalaxiaConfigOverlay.hudOffsetX + GalaxiaConfigOverlay.oxygenOffsetX;
        oxygenY = baseY + GalaxiaConfigOverlay.hudOffsetY + GalaxiaConfigOverlay.oxygenOffsetY;

        temperatureX = rightSideX + GalaxiaConfigOverlay.hudOffsetX + GalaxiaConfigOverlay.temperatureOffsetX;
        temperatureY = baseY + GalaxiaConfigOverlay.hudOffsetY + GalaxiaConfigOverlay.temperatureOffsetY;

        return new BarScreenPositions(oxygenX, oxygenY, temperatureX, temperatureY);
    }

    private void drawBar(int x, int y, float fillPercent, ResourceLocation bgTex, ResourceLocation fillTex,
        boolean pulsing, int texWidth, int texHeight, GalaxiaConfigOverlay.BarOrientation orientation) {

        // Background
        mc.getTextureManager()
            .bindTexture(bgTex);
        drawTexturedQuad(x, y, texWidth, texHeight, texWidth, texHeight);

        if (fillPercent <= 0f) return;

        // Fill
        mc.getTextureManager()
            .bindTexture(fillTex);

        float pulse = pulsing
            ? (float) (Math.sin(System.currentTimeMillis() / GalaxiaConfigOverlay.pulseSpeed)
                * GalaxiaConfigOverlay.pulseAmplitude + (1.0f - GalaxiaConfigOverlay.pulseAmplitude))
            : 1.0f;

        GL11.glPushMatrix();
        GL11.glColor4f(pulse, pulse, pulse, 1.0f);

        if (orientation == GalaxiaConfigOverlay.BarOrientation.VERTICAL) {
            int fillHeightPx = Math.max(0, (int) (texHeight * clamp01(fillPercent)));
            if (fillHeightPx > 0) {
                int drawY = y + texHeight - fillHeightPx;
                drawTexturedSubQuad(
                    x,
                    drawY,
                    texWidth,
                    fillHeightPx,
                    0,
                    texHeight - fillHeightPx,
                    texWidth,
                    fillHeightPx,
                    texWidth,
                    texHeight);
            }
        } else {
            int fillWidthPx = Math.max(0, (int) (texWidth * clamp01(fillPercent)));
            if (fillWidthPx > 0) {
                drawTexturedSubQuad(x, y, fillWidthPx, texHeight, 0, 0, fillWidthPx, texHeight, texWidth, texHeight);
            }
        }

        GL11.glPopMatrix();
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private int applyPulse() {
        float pulse = (float) (Math.sin(System.currentTimeMillis() / GalaxiaConfigOverlay.pulseSpeed)
            * GalaxiaConfigOverlay.pulseAmplitude + (1.0f - GalaxiaConfigOverlay.pulseAmplitude));

        int r = (int) (255 * pulse);
        int g = (int) (255 * pulse);
        int b = (int) (255 * pulse);

        // Clamp components so we don't get negative values due to rounding
        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));

        return (r << 16) | (g << 8) | b;
    }

    private void drawTexturedQuad(int x, int y, int w, int h, int texW, int texH) {
        Tessellator t = Tessellator.instance;
        float uMax = (float) w / texW;
        float vMax = (float) h / texH;

        t.startDrawingQuads();
        t.addVertexWithUV(x, y + h, 0, 0f, vMax);
        t.addVertexWithUV(x + w, y + h, 0, uMax, vMax);
        t.addVertexWithUV(x + w, y, 0, uMax, 0f);
        t.addVertexWithUV(x, y, 0, 0f, 0f);
        t.draw();
    }

    private void drawTexturedSubQuad(int x, int y, int w, int h, int srcX, int srcY, int srcW, int srcH, int texW,
        int texH) {
        Tessellator t = Tessellator.instance;
        float u0 = srcX / (float) texW;
        float v0 = srcY / (float) texH;
        float u1 = (srcX + srcW) / (float) texW;
        float v1 = (srcY + srcH) / (float) texH;

        t.startDrawingQuads();
        t.addVertexWithUV(x, y + h, 0, u0, v1);
        t.addVertexWithUV(x + w, y + h, 0, u1, v1);
        t.addVertexWithUV(x + w, y, 0, u1, v0);
        t.addVertexWithUV(x, y, 0, u0, v0);
        t.draw();
    }

    private float getOxygenLevel(EntityPlayer p) {
        // Example: oscillating value in [0,1] based on world time. Replace with real logic as needed.
        float speed = 0.01f;
        long time = mc.theWorld != null ? mc.theWorld.getTotalWorldTime() : System.currentTimeMillis();
        return (float) ((Math.sin(time * speed) + 1.0) / 2.0);
    }

    private float getTemperature(EntityPlayer p) {
        // Example: oscillating value shifted by phase so it differs from oxygen. Replace with real logic as needed.
        float speed = 0.01f;
        long time = mc.theWorld != null ? mc.theWorld.getTotalWorldTime() : System.currentTimeMillis();
        return (float) ((Math.sin(time * speed + Math.PI / 2) + 1.0) / 2.0);
    }

    @Desugar
    private record BarScreenPositions(int oxygenX, int oxygenY, int temperatureX, int temperatureY) {}
}
