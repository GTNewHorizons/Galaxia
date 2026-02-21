package com.gtnewhorizons.galaxia.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.model.IModelCustom;

import org.lwjgl.opengl.GL11;

public class ModuleTESR extends TileEntitySpecialRenderer {

    @Override
    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partialTicks) {
        if (!(tile instanceof TileEntityModuleController ctrl)) return;

        ModuleType type = ctrl.getType();
        if (type == null) return;

        IModelCustom model = type.getModel();
        if (model != null) {
            GL11.glPushMatrix();
            GL11.glTranslated(x + 0.5 + type.getOffsetX(), y + 0.5 + type.getOffsetY(), z + 0.5 + type.getOffsetZ());
            GL11.glScalef(type.getScale(), type.getScale(), type.getScale());

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);

            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(type.getTextureLocation());
            model.renderAll();

            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glPopMatrix();
        }
    }
}
