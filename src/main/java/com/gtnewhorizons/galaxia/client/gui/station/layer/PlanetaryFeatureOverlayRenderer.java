package com.gtnewhorizons.galaxia.client.gui.station.layer;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.utils.GlStateManager;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.station.StationMapViewport;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureDefinition;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureKey;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureRegistry;

public final class PlanetaryFeatureOverlayRenderer {

    private static final int MARKER_SIZE = 4;
    private static final int MARKER_GAP = 1;
    private static final int MARKER_PADDING = 2;

    private PlanetaryFeatureOverlayRenderer() {}

    public static void draw(int tileX, int tileY, Iterable<PlanetaryFeatureKey> features) {
        if (features == null) return;
        List<PlanetaryFeatureDefinition> definitions = new ArrayList<>();
        for (PlanetaryFeatureKey key : features) {
            PlanetaryFeatureDefinition definition = PlanetaryFeatureRegistry.get(key);
            if (definition != null) definitions.add(definition);
        }
        for (Marker marker : markers(tileX, tileY, definitions)) {
            drawIcon(marker.texture(), marker.x(), marker.y(), marker.size());
        }
    }

    public static void drawIcon(ResourceLocation texture, int x, int y, int size) {
        drawTexture(resolveTexture(texture), x, y, size);
    }

    public static void drawIcon(PlanetaryFeatureDefinition feature, int x, int y, int size) {
        if (feature == null) return;
        drawIcon(feature.texture(), x, y, size);
    }

    static List<Marker> markers(int tileX, int tileY, Iterable<PlanetaryFeatureDefinition> features) {
        List<Marker> markers = new ArrayList<>();
        if (features == null) return markers;
        int maxMarkers = (StationMapViewport.TILE_SIZE - 2 * MARKER_PADDING + MARKER_GAP) / (MARKER_SIZE + MARKER_GAP);
        for (PlanetaryFeatureDefinition feature : features) {
            if (feature == null || markers.size() >= maxMarkers) continue;
            int index = markers.size();
            int x = tileX + MARKER_PADDING + index * (MARKER_SIZE + MARKER_GAP);
            int y = tileY + StationMapViewport.TILE_SIZE - MARKER_PADDING - MARKER_SIZE;
            markers.add(new Marker(x, y, MARKER_SIZE, feature.texture()));
        }
        return markers;
    }

    private static ResourceLocation resolveTexture(ResourceLocation texture) {
        return texture != null && StationTextureRegistry.hasTexture(texture) ? texture
            : EnumTextures.ICON_MISSING.get();
    }

    private static void drawTexture(ResourceLocation texture, int x, int y, int size) {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(texture);
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

    record Marker(int x, int y, int size, ResourceLocation texture) {}
}
