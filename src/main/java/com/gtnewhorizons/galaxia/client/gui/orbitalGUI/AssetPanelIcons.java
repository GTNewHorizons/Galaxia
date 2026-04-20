package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.utils.GlStateManager;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.outpost.WarningPriority;

/**
 * Low-level sprite blitter for solar-system asset rows. Textures live in {@link EnumTextures}.
 */
final class AssetPanelIcons {

    static final ResourceLocation CAP_MINING = EnumTextures.ICON_CAP_MINING.get();
    static final ResourceLocation CAP_PRODUCTION = EnumTextures.ICON_CAP_PRODUCTION.get();
    static final ResourceLocation CAP_CONSTRUCTION = EnumTextures.ICON_CAP_CONSTRUCTION.get();
    static final ResourceLocation CAP_DECONSTRUCTION = EnumTextures.ICON_CAP_DECONSTRUCTION.get();

    private AssetPanelIcons() {}

    static ResourceLocation kindIcon(CelestialAsset.Kind kind) {
        return switch (kind) {
            case STATION -> EnumTextures.ICON_STATION.get();
            case AUTOMATED_STATION -> EnumTextures.ICON_STATION_AUTOMATED.get();
            case AUTOMATED_OUTPOST -> EnumTextures.ICON_OUTPOST_AUTOMATED.get();
        };
    }

    static ResourceLocation warningIcon(WarningPriority warning) {
        return switch (warning) {
            case NO_POWER -> EnumTextures.ICON_WARN_POWERFAIL.get();
            case BLOCKED_LOGISTICS, MISSING_INPUT, IDLE -> EnumTextures.ICON_WARN_GENERIC.get();
            case NONE -> null;
        };
    }

    static ResourceLocation iconForBody(CelestialObject body) {
        if (body == null) return EnumTextures.ICON_MISSING.get();
        ResourceLocation tex = body.texture();
        return tex != null ? tex : EnumTextures.ICON_MISSING.get();
    }

    /** Blits a 2D sprite at the given screen rect; falls back to the missing-art tile if {@code tex} is null. */
    static void drawSprite(ResourceLocation tex, int x, int y, int size) {
        ResourceLocation actual = tex == null ? EnumTextures.ICON_MISSING.get() : tex;
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
