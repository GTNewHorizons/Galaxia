package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.outpost.WarningPriority;

/**
 * Shared asset sprite choices and drawing for orbital views and rows. Textures live in {@link EnumTextures}.
 */
final class AssetPanelIcons {

    static final ResourceLocation CAP_MINING = EnumTextures.ICON_CAP_MINING.get();
    static final ResourceLocation CAP_PRODUCTION = EnumTextures.ICON_CAP_PRODUCTION.get();
    static final ResourceLocation CAP_CONSTRUCTION = EnumTextures.ICON_CAP_CONSTRUCTION.get();
    static final ResourceLocation CAP_DECONSTRUCTION = EnumTextures.ICON_CAP_DECONSTRUCTION.get();

    private AssetPanelIcons() {}

    static float assetMarkerAlpha(CelestialAsset asset) {
        if (asset == null) return 0.0f;
        return switch (asset.status()) {
            case OPERATIONAL -> 1.0f;
            case CONSTRUCTION_SITE -> 0.85f;
            case DECONSTRUCTION -> 0.65f;
            case DISABLED -> 0.45f;
            case IN_CONSTRUCTION -> 0.0F;
            case DESTROYED -> 0.0f;
        };
    }

    static ResourceLocation kindIcon(CelestialAsset.Kind kind) {
        return switch (kind) {
            case STATION -> EnumTextures.ICON_STATION.get();
            case AUTOMATED_STATION -> EnumTextures.ICON_STATION_AUTOMATED.get();
            case AUTOMATED_OUTPOST -> EnumTextures.ICON_OUTPOST_AUTOMATED.get();
            case SATELLITE -> EnumTextures.ICON_SATELLITE.get();
            case TRANSPORT_SITE -> EnumTextures.SILO_ENTER_ROCKET_ICON.get();
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
        GuiDraw.drawTexture(x, y, x + size, y + size, 0f, 0f, 1f, 1f);
    }
}
