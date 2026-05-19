package com.gtnewhorizons.galaxia.client.gui.station.layer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.station.StationMapViewport;
import com.gtnewhorizons.galaxia.client.gui.station.layer.StationTextureRegistry.ConnectorKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class ConnectionLayerRenderer {

    private static final int INTERNAL_BRIDGE_INSET = 2;
    private static final float INTERNAL_HORIZONTAL_U0 = 0.42f;
    private static final float INTERNAL_HORIZONTAL_U1 = 0.58f;
    private static final float INTERNAL_VERTICAL_V0 = 0.42f;
    private static final float INTERNAL_VERTICAL_V1 = 0.58f;

    private ConnectionLayerRenderer() {}

    public static void draw(GuiContext ctx, Map<StationTileCoord, PlacedTile> tiles, int widgetWidth, int widgetHeight,
        int contentLeft, int contentRightPadding, int contentVerticalPadding, int panX, int panY) {
        if (tiles == null) return;
        int connW = StationMapViewport.connectorWidth();
        int connH = StationMapViewport.connectorHeight();
        int tileSize = StationMapViewport.TILE_SIZE;
        ResourceLocation horizontalTexture = StationTextureRegistry.connectorTexture(ConnectorKind.HORIZONTAL);
        ResourceLocation verticalTexture = StationTextureRegistry.connectorTexture(ConnectorKind.VERTICAL);
        boolean hasHorizontalTexture = StationTextureRegistry.hasTexture(horizontalTexture);
        boolean hasVerticalTexture = StationTextureRegistry.hasTexture(verticalTexture);
        List<ConnectorQuad> horizontalQuads = new ArrayList<>();
        List<ConnectorQuad> verticalQuads = new ArrayList<>();

        for (Map.Entry<StationTileCoord, PlacedTile> e : tiles.entrySet()) {
            StationTileCoord coord = e.getKey();
            PlacedTile tile = e.getValue();
            if (tile == null) continue;

            StationTileCoord right = StationTileCoord.of(coord.dx() + 1, coord.dy());
            PlacedTile rightTile = tiles.get(right);
            if (rightTile != null && sameModule(tile, rightTile)) {
                int cx = StationMapViewport.connectorLeftX(coord, widgetWidth, contentLeft, contentRightPadding, panX);
                int cy = StationMapViewport.tileTopY(coord, widgetHeight, contentVerticalPadding, panY)
                    + INTERNAL_BRIDGE_INSET;
                drawInternalBridge(cx, cy, connW, tileSize - INTERNAL_BRIDGE_INSET * 2, tile, true);
            } else if (rightTile != null) {
                int cx = StationMapViewport.connectorLeftX(coord, widgetWidth, contentLeft, contentRightPadding, panX);
                int cy = StationMapViewport.tileTopY(coord, widgetHeight, contentVerticalPadding, panY)
                    + (tileSize - connH) / 2;
                drawConnector(
                    cx,
                    cy,
                    connW,
                    connH,
                    connectorActive(tile, rightTile),
                    hasHorizontalTexture,
                    horizontalQuads);
            }

            StationTileCoord down = StationTileCoord.of(coord.dx(), coord.dy() + 1);
            PlacedTile downTile = tiles.get(down);
            if (downTile != null && sameModule(tile, downTile)) {
                int cx = StationMapViewport.tileLeftX(coord, widgetWidth, contentLeft, contentRightPadding, panX)
                    + INTERNAL_BRIDGE_INSET;
                int cy = StationMapViewport.connectorTopY(coord, widgetHeight, contentVerticalPadding, panY);
                drawInternalBridge(cx, cy, tileSize - INTERNAL_BRIDGE_INSET * 2, connH, tile, false);
            } else if (downTile != null) {
                int cx = StationMapViewport.tileLeftX(coord, widgetWidth, contentLeft, contentRightPadding, panX)
                    + (tileSize - connW) / 2;
                int cy = StationMapViewport.connectorTopY(coord, widgetHeight, contentVerticalPadding, panY);
                drawConnector(cx, cy, connW, connH, connectorActive(tile, downTile), hasVerticalTexture, verticalQuads);
            }
        }

        drawTextureBatch(horizontalTexture, horizontalQuads);
        drawTextureBatch(verticalTexture, verticalQuads);
    }

    private static void drawInternalBridge(int x, int y, int w, int h, PlacedTile tile, boolean horizontal) {
        FacilityModuleKind kind = moduleKindOf(tile);
        boolean textured = horizontal
            ? ModuleLayerRenderer
                .drawModuleTextureRegion(x, y, w, h, kind, INTERNAL_HORIZONTAL_U0, 0f, INTERNAL_HORIZONTAL_U1, 1f)
            : ModuleLayerRenderer
                .drawModuleTextureRegion(x, y, w, h, kind, 0f, INTERNAL_VERTICAL_V0, 1f, INTERNAL_VERTICAL_V1);
        if (!textured) {
            Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_STATION_CONNECTOR_ACTIVE.getColor());
        }
        int overlayColor = bridgeStateOverlayColor(tile);
        if (overlayColor != 0) {
            Gui.drawRect(x, y, x + w, y + h, overlayColor);
        }
    }

    private static void drawConnector(int x, int y, int w, int h, boolean active, boolean hasTexture,
        List<ConnectorQuad> textureQuads) {
        if (active && hasTexture) {
            textureQuads.add(new ConnectorQuad(x, y, w, h));
            return;
        }

        int color = active ? EnumColors.MAP_COLOR_STATION_CONNECTOR_ACTIVE.getColor()
            : EnumColors.MAP_COLOR_STATION_CONNECTOR_INACTIVE.getColor();
        Gui.drawRect(x, y, x + w, y + h, color);
    }

    private static void drawTextureBatch(ResourceLocation texture, List<ConnectorQuad> quads) {
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
        for (ConnectorQuad quad : quads) {
            tess.addVertexWithUV(quad.x, quad.y + quad.h, 0, 0, 1);
            tess.addVertexWithUV(quad.x + quad.w, quad.y + quad.h, 0, 1, 1);
            tess.addVertexWithUV(quad.x + quad.w, quad.y, 0, 1, 0);
            tess.addVertexWithUV(quad.x, quad.y, 0, 0, 0);
        }
        tess.draw();
    }

    private static boolean connectorActive(PlacedTile a, PlacedTile b) {
        if (a == null || b == null) return false;
        return a.state() != null && a.state()
            .isConnectorActive()
            && b.state() != null
            && b.state()
                .isConnectorActive();
    }

    private static boolean sameModule(PlacedTile a, PlacedTile b) {
        if (a == null || b == null) return false;
        ModuleInstance moduleA = a.module();
        ModuleInstance moduleB = b.module();
        return moduleA != null && moduleB != null && moduleA.id.equals(moduleB.id);
    }

    private static FacilityModuleKind moduleKindOf(PlacedTile tile) {
        if (tile == null) return null;
        ModuleInstance module = tile.module();
        return module == null ? null : module.kind();
    }

    private static int bridgeStateOverlayColor(PlacedTile tile) {
        if (tile == null || tile.state() == null) return 0;
        return switch (tile.state()) {
            case UNDER_CONSTRUCTION -> EnumColors.MAP_COLOR_STATION_TILE_UNDER_CONSTRUCTION.getColor();
            case UNDER_DECONSTRUCTION -> EnumColors.MAP_COLOR_STATION_TILE_UNDER_DECONSTRUCTION.getColor();
            case OCCUPIED_DISABLED -> EnumColors.MAP_COLOR_STATION_TILE_DISABLED_DIM.getColor();
            case BLOCKED -> EnumColors.MAP_COLOR_STATION_TILE_BLOCKED.getColor();
            case OCCUPIED_OPERATIONAL, EMPTY -> 0;
        };
    }

    private static final class ConnectorQuad {

        private final int x;
        private final int y;
        private final int w;
        private final int h;

        private ConnectorQuad(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }
}
