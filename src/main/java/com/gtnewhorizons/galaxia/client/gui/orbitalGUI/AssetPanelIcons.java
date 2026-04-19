package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.utils.GlStateManager;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
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
    static final ResourceLocation BODY_PLACEHOLDER = res("textures/gui/bodyicons/egora.png");

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

    private static final Map<CelestialObjectId, ResourceLocation> BODY_ICON_CACHE = new HashMap<>();

    /**
     * Resolves a per-body icon at {@code textures/gui/bodyicons/<id>.png}, caching the result.
     * Falls back to {@link #BODY_PLACEHOLDER} when no per-body PNG exists in the resource pack.
     */
    static ResourceLocation iconForBody(CelestialObject body) {
        if (body == null || body.id() == null) return BODY_PLACEHOLDER;
        CelestialObjectId key = body.id();
        ResourceLocation cached = BODY_ICON_CACHE.get(key);
        if (cached != null) return cached;
        ResourceLocation candidate = res("textures/gui/bodyicons/" + key.name().toLowerCase() + ".png");
        ResourceLocation resolved;
        try {
            Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(candidate);
            resolved = candidate;
        } catch (IOException e) {
            resolved = BODY_PLACEHOLDER;
        }
        BODY_ICON_CACHE.put(key, resolved);
        return resolved;
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
