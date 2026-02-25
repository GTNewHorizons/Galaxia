package com.gtnewhorizons.galaxia.rocketmodules.client.render;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.rocketmodules.ModuleRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo;

public class SiloRenderer extends TileEntitySpecialRenderer {

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        if (!(te instanceof TileEntitySilo silo)) return;
        if (!silo.shouldRender) return;

        double yOff = 1.0;

        for (int i = 0; i < silo.getNumModules(); i++) {
            int type = silo.getModuleType(i);
            ModuleRegistry.ModuleInfo info = ModuleRegistry.getModule(type);
            if (info == null) {
                yOff += 2.0;
                continue;
            }

            bindTexture(info.texture());

            GL11.glPushMatrix();
            GL11.glTranslated(x + 0.5, y + yOff, z + 0.5);
            info.model()
                .renderAll();
            GL11.glPopMatrix();

            yOff += info.height();
        }
    }
}
