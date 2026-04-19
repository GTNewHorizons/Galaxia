package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.utils.GlStateManager;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.outpost.WarningPriority;

/**
 * Texture catalogue + low-level sprite blitter for solar-system asset rows.
 * Placeholder PNGs live under {@code assets/galaxia/textures/gui/asset_panel/} and
 * {@code assets/galaxia/textures/gui/bodyicons/} — final art replaces these without code changes.
 */
final class AssetPanelIcons {

    static final ResourceLocation KIND_STATION = res("textures/gui/bodyicons/station.png");
    static final ResourceLocation KIND_STATION_AUTOMATED = res("textures/gui/bodyicons/station_automated.png");
    static final ResourceLocation KIND_OUTPOST_AUTOMATED = res("textures/gui/bodyicons/outpost_automated.png");

    static final ResourceLocation CAP_MINING = res("textures/gui/asset_panel/mining.png");
    static final ResourceLocation CAP_MINING_OFF = res("textures/gui/asset_panel/mining_off.png");
    static final ResourceLocation CAP_PRODUCTION = res("textures/gui/asset_panel/production.png");
    static final ResourceLocation CAP_PRODUCTION_OFF = res("textures/gui/asset_panel/production_off.png");
    static final ResourceLocation CAP_CONSTRUCTION = res("textures/gui/asset_panel/construction.png");
    static final ResourceLocation CAP_DECONSTRUCTION = res("textures/gui/asset_panel/deconstruction.png");
    static final ResourceLocation WARN_DANGER = res("textures/gui/asset_panel/warning.png");
    static final ResourceLocation WARN_IDLE = res("textures/gui/asset_panel/warning_idle.png");
    static final ResourceLocation MISSING = res("textures/gui/asset_panel/missing.png");

    private AssetPanelIcons() {}

    private static ResourceLocation res(String path) {
        return new ResourceLocation("galaxia", path);
    }

    static ResourceLocation kindIcon(CelestialAsset.Kind kind) {
        return switch (kind) {
            case STATION -> KIND_STATION;
            case AUTOMATED_STATION -> KIND_STATION_AUTOMATED;
            case AUTOMATED_OUTPOST -> KIND_OUTPOST_AUTOMATED;
        };
    }

    static ResourceLocation warningIcon(WarningPriority warning) {
        return switch (warning) {
            case NO_POWER, BLOCKED_LOGISTICS -> WARN_DANGER;
            case MISSING_INPUT, IDLE -> WARN_IDLE;
            case NONE -> null;
        };
    }

    /**
     * Per-class fallback color for body discs in the row, mirroring the starmap fallback in
     * {@code OrbitalScene.getFallbackBodyColor} so the asset panel and orbital map agree on body identity at a glance.
     */
    static int bodyDiscColor(CelestialObject.Class objectClass) {
        if (objectClass == null) return EnumColors.MAP_COLOR_BODY_PLANET.getColor();
        return switch (objectClass) {
            case GALAXY -> EnumColors.MAP_COLOR_BODY_GALAXY.getColor();
            case BLACK_HOLE -> EnumColors.MAP_COLOR_BODY_BLACK_HOLE.getColor();
            case STAR -> EnumColors.MAP_COLOR_BODY_STAR.getColor();
            case GAS_GIANT -> EnumColors.MAP_COLOR_BODY_GAS_GIANT.getColor();
            case PLANET -> EnumColors.MAP_COLOR_BODY_PLANET.getColor();
            case MOON -> EnumColors.MAP_COLOR_BODY_MOON.getColor();
            case ASTEROID, ASTEROID_BELT -> EnumColors.MAP_COLOR_BODY_ASTEROID.getColor();
            case STATION -> EnumColors.MAP_COLOR_BODY_STATION.getColor();
            case COMET -> EnumColors.MAP_COLOR_BODY_COMET.getColor();
        };
    }

    /** Filled-circle body placeholder; matches the starmap's missing-texture fallback. */
    static void drawBodyDisc(int cx, int cy, int radius, int color) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        float a = ((color >> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        GlStateManager.color(r, g, b, a == 0f ? 1f : a);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(cx, cy);
        int seg = 18;
        for (int i = 0; i <= seg; i++) {
            double angle = (Math.PI * 2.0 * i) / seg;
            GL11.glVertex2f(cx + (float) Math.cos(angle) * radius, cy + (float) Math.sin(angle) * radius);
        }
        GL11.glEnd();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableTexture2D();
    }

    /** Blits a 2D sprite at the given screen rect; falls back to the missing-art tile if {@code tex} is null. */
    static void drawSprite(ResourceLocation tex, int x, int y, int size) {
        ResourceLocation actual = tex == null ? MISSING : tex;
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(actual);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(x, y + size, 0, 0, 1);
        tess.addVertexWithUV(x + size, y + size, 0, 1, 1);
        tess.addVertexWithUV(x + size, y, 0, 1, 0);
        tess.addVertexWithUV(x, y, 0, 0, 0);
        tess.draw();
    }
}
