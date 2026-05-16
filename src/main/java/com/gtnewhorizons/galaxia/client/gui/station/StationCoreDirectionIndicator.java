package com.gtnewhorizons.galaxia.client.gui.station;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.utils.GlStateManager;
import com.gtnewhorizons.galaxia.client.EnumTextures;

public final class StationCoreDirectionIndicator {

    private static final int EDGE_INSET = 14;
    private static final int TEXTURE_WIDTH = 24;
    private static final int TEXTURE_HEIGHT = 16;

    private StationCoreDirectionIndicator() {}

    public record Arrow(int tipX, int tipY, double unitX, double unitY) {}

    public static @Nullable Arrow towardCore(int width, int height, int contentLeft, int contentRightPadding,
        int contentVerticalPadding, int panX, int panY) {
        int left = contentLeft + EDGE_INSET;
        int right = width - contentRightPadding - EDGE_INSET;
        int top = contentVerticalPadding + EDGE_INSET;
        int bottom = height - contentVerticalPadding - EDGE_INSET;
        if (right <= left || bottom <= top) return null;

        double centerX = (left + right) * 0.5;
        double centerY = (top + bottom) * 0.5;
        double coreX = StationMapViewport.tileLeftX(0, width, contentLeft, contentRightPadding, panX)
            + StationMapViewport.TILE_SIZE * 0.5;
        double coreY = StationMapViewport.tileTopY(0, height, contentVerticalPadding, panY)
            + StationMapViewport.TILE_SIZE * 0.5;
        double dx = coreX - centerX;
        double dy = coreY - centerY;
        double length = Math.hypot(dx, dy);
        if (length < 1.0) return null;
        double unitX = dx / length;
        double unitY = dy / length;
        double t = Double.POSITIVE_INFINITY;
        if (unitX > 0.0) t = Math.min(t, (right - centerX) / unitX);
        if (unitX < 0.0) t = Math.min(t, (left - centerX) / unitX);
        if (unitY > 0.0) t = Math.min(t, (bottom - centerY) / unitY);
        if (unitY < 0.0) t = Math.min(t, (top - centerY) / unitY);
        if (!Double.isFinite(t)) return null;
        double tipX = centerX + unitX * t;
        double tipY = centerY + unitY * t;
        int roundedTipX = (int) Math.round(tipX);
        int roundedTipY = (int) Math.round(tipY);
        double tipToCoreX = coreX - roundedTipX;
        double tipToCoreY = coreY - roundedTipY;
        double tipToCoreLength = Math.hypot(tipToCoreX, tipToCoreY);
        if (tipToCoreLength < 1.0) return null;
        return new Arrow(roundedTipX, roundedTipY, tipToCoreX / tipToCoreLength, tipToCoreY / tipToCoreLength);
    }

    public static boolean tileIntersectsViewport(int tileX, int tileY, int width, int height, int contentLeft,
        int contentRightPadding, int contentVerticalPadding) {
        int left = contentLeft;
        int right = width - contentRightPadding;
        int top = contentVerticalPadding;
        int bottom = height - contentVerticalPadding;
        return tileX < right && tileX + StationMapViewport.TILE_SIZE > left
            && tileY < bottom
            && tileY + StationMapViewport.TILE_SIZE > top;
    }

    public static boolean tileIntersectsScreen(int tileX, int tileY, int width, int height) {
        return tileX < width && tileX + StationMapViewport.TILE_SIZE > 0
            && tileY < height
            && tileY + StationMapViewport.TILE_SIZE > 0;
    }

    public static void draw(Arrow arrow, int fillColor, int borderColor) {
        if (arrow == null) return;

        double centerX = arrow.tipX() - arrow.unitX() * (TEXTURE_WIDTH * 0.5);
        double centerY = arrow.tipY() - arrow.unitY() * (TEXTURE_WIDTH * 0.5);
        float rotation = (float) Math.toDegrees(Math.atan2(arrow.unitY(), arrow.unitX()));

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(EnumTextures.ICON_STATION_CORE_DIRECTION.get());
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glPushMatrix();
        GL11.glTranslated(centerX, centerY, 0.0);
        GL11.glRotatef(rotation, 0.0F, 0.0F, 1.0F);
        drawTexturedQuad(-TEXTURE_WIDTH * 0.5, -TEXTURE_HEIGHT * 0.5, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        GL11.glPopMatrix();
    }

    private static void drawTexturedQuad(double x, double y, int width, int height) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, 0.0, 0.0, 1.0);
        tessellator.addVertexWithUV(x + width, y + height, 0.0, 1.0, 1.0);
        tessellator.addVertexWithUV(x + width, y, 0.0, 1.0, 0.0);
        tessellator.addVertexWithUV(x, y, 0.0, 0.0, 0.0);
        tessellator.draw();
    }
}
