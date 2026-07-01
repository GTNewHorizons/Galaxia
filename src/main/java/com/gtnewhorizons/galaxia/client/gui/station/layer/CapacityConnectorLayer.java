package com.gtnewhorizons.galaxia.client.gui.station.layer;

import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.gtnewhorizons.galaxia.client.gui.station.StationMapViewport;
import com.gtnewhorizons.galaxia.client.gui.station.layer.StationTextureRegistry.ConnectorKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class CapacityConnectorLayer {

    private static final Map<ResourceLocation, List<CapacityConnectorQuad>> QUADS_BY_TEXTURE = new java.util.HashMap<>();

    private CapacityConnectorLayer() {}

    public static void draw(GuiContext ctx, Map<StationTileCoord, PlacedTile> tiles, int widgetWidth, int widgetHeight,
        int contentLeft, int contentRightPadding, int contentVerticalPadding, int panX, int panY) {
        if (tiles == null) return;
        int connW = StationMapViewport.connectorWidth();
        int connH = StationMapViewport.connectorHeight();
        int tileSize = StationMapViewport.TILE_SIZE;
        QUADS_BY_TEXTURE.clear();

        for (Map.Entry<StationTileCoord, PlacedTile> e : tiles.entrySet()) {
            StationTileCoord coord = e.getKey();
            PlacedTile a = e.getValue();
            FacilityModuleKind kindA = ConnectorRoutePolicy.moduleKindOf(a);
            if (kindA == null || !kindA.isCapacityModule()) continue;

            // Check right neighbor
            StationTileCoord right = StationTileCoord.of(coord.dx() + 1, coord.dy());
            PlacedTile b = tiles.get(right);
            FacilityModuleKind horizontalKind = ConnectorRoutePolicy.capacityConnectorKind(a, b);
            if (horizontalKind != null) {
                int cx = StationMapViewport.connectorLeftX(coord, widgetWidth, contentLeft, contentRightPadding, panX);
                int cy = StationMapViewport.tileTopY(coord, widgetHeight, contentVerticalPadding, panY)
                    + (tileSize - connH) / 2;
                addConnector(cx, cy, connW, connH, horizontalKind, ConnectorKind.HORIZONTAL);
            }

            // Check down neighbor
            StationTileCoord down = StationTileCoord.of(coord.dx(), coord.dy() + 1);
            PlacedTile c = tiles.get(down);
            FacilityModuleKind verticalKind = ConnectorRoutePolicy.capacityConnectorKind(a, c);
            if (verticalKind != null) {
                int cx = StationMapViewport.tileLeftX(coord, widgetWidth, contentLeft, contentRightPadding, panX)
                    + (tileSize - connW) / 2;
                int cy = StationMapViewport.connectorTopY(coord, widgetHeight, contentVerticalPadding, panY);
                addConnector(cx, cy, connW, connH, verticalKind, ConnectorKind.VERTICAL);
            }
        }

        for (Map.Entry<ResourceLocation, List<CapacityConnectorQuad>> entry : QUADS_BY_TEXTURE.entrySet()) {
            drawBatch(entry.getKey(), entry.getValue());
        }
    }

    private static void addConnector(int x, int y, int w, int h, FacilityModuleKind kind, ConnectorKind connectorKind) {
        ResourceLocation texture = StationTextureRegistry.capacityConnectorTexture(kind, connectorKind);
        if (texture == null) return;
        QUADS_BY_TEXTURE.computeIfAbsent(texture, ignored -> new java.util.ArrayList<>())
            .add(new CapacityConnectorQuad(x, y, w, h));
    }

    private static void drawBatch(ResourceLocation texture, List<CapacityConnectorQuad> quads) {
        if (texture == null || quads.isEmpty()) return;
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(texture);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        for (CapacityConnectorQuad quad : quads) {
            tess.addVertexWithUV(quad.x(), quad.y() + quad.h(), 0, 0, 1);
            tess.addVertexWithUV(quad.x() + quad.w(), quad.y() + quad.h(), 0, 1, 1);
            tess.addVertexWithUV(quad.x() + quad.w(), quad.y(), 0, 1, 0);
            tess.addVertexWithUV(quad.x(), quad.y(), 0, 0, 0);
        }
        tess.draw();
    }

    private record CapacityConnectorQuad(int x, int y, int w, int h) {}
}
