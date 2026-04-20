package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.utils.GlStateManager;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.outpost.WarningPriority;

/**
 * Texture catalogue + low-level sprite blitter for solar-system asset rows.
 */
final class AssetPanelIcons {

    static final ResourceLocation KIND_STATION = res("textures/gui/bodyicons/station.png");
    static final ResourceLocation KIND_STATION_AUTOMATED = res("textures/gui/bodyicons/station_automated.png");
    static final ResourceLocation KIND_OUTPOST_AUTOMATED = res("textures/gui/bodyicons/outpost_automated.png");

    static final ResourceLocation CAP_MINING = res("textures/gui/outpost_mining.png");
    static final ResourceLocation CAP_PRODUCTION = res("textures/gui/outpost_processing.png");
    static final ResourceLocation CAP_CONSTRUCTION = res("textures/gui/outpost_building.png");
    static final ResourceLocation CAP_DECONSTRUCTION = res("textures/gui/outpost_destroying.png");
    static final ResourceLocation WARN_POWERFAIL = res("textures/gui/outpost_powerfail.png");
    static final ResourceLocation WARN_GENERIC = res("textures/gui/outpost_warning.png");
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
            case NO_POWER -> WARN_POWERFAIL;
            case BLOCKED_LOGISTICS, MISSING_INPUT, IDLE -> WARN_GENERIC;
            case NONE -> null;
        };
    }

    static ResourceLocation iconForBody(CelestialObject body) {
        if (body == null) return MISSING;
        ResourceLocation tex = body.texture();
        return tex != null ? tex : MISSING;
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
