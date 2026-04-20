package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.utils.GlStateManager;
import com.gtnewhorizons.galaxia.client.EnumTextures;

/**
 * Sprite blitter for transfer-package dots on the orbital map. Textures live in {@link EnumTextures}.
 * Add a new {@link TransferPackageKind} value and a matching {@code case} below to extend.
 */
final class TransferPackageIcons {

    private TransferPackageIcons() {}

    static ResourceLocation texture(TransferPackageKind kind) {
        if (kind == null) return EnumTextures.ICON_MISSING.get();
        return switch (kind) {
            case HAMMER -> EnumTextures.ICON_TRANSFER_HAMMER.get();
        };
    }

    /** Centered textured quad. {@code alpha} multiplies the sprite's color so it fades with the path. */
    static void drawCentered(TransferPackageKind kind, float cx, float cy, float size, float alpha) {
        ResourceLocation tex = texture(kind);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(tex);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, alpha);
        float half = size * 0.5f;
        float x0 = cx - half;
        float y0 = cy - half;
        float x1 = cx + half;
        float y1 = cy + half;
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(x0, y1, 0, 0, 1);
        tess.addVertexWithUV(x1, y1, 0, 1, 1);
        tess.addVertexWithUV(x1, y0, 0, 1, 0);
        tess.addVertexWithUV(x0, y0, 0, 0, 0);
        tess.draw();
    }
}
