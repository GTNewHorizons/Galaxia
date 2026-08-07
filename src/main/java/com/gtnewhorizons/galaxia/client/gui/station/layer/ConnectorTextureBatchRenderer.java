package com.gtnewhorizons.galaxia.client.gui.station.layer;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.utils.GlStateManager;

final class ConnectorTextureBatchRenderer {

    private ConnectorTextureBatchRenderer() {}

    static void draw(ResourceLocation texture, List<Quad> quads) {
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
        for (Quad quad : quads) {
            tess.addVertexWithUV(quad.x(), quad.y() + quad.h(), 0, 0, 1);
            tess.addVertexWithUV(quad.x() + quad.w(), quad.y() + quad.h(), 0, 1, 1);
            tess.addVertexWithUV(quad.x() + quad.w(), quad.y(), 0, 1, 0);
            tess.addVertexWithUV(quad.x(), quad.y(), 0, 0, 0);
        }
        tess.draw();
    }

    record Quad(int x, int y, int w, int h) {}
}
